package com.scanify.nativeapp

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import androidx.exifinterface.media.ExifInterface

data class ScanDocument(val id: String, val name: String, val pages: Int, val created: Long, val importedPdf: Boolean = false)
data class PageAdjustment(val rotation:Int=0,val grayscale:Boolean=false,val filter:String="Original"):java.io.Serializable

class DocumentStore(private val context: Context) {
 private val root = File(context.filesDir, "documents").apply { mkdirs() }
 private val index = File(root, "index.json")
 fun list(): List<ScanDocument> {
  if (!index.exists()) return emptyList()
  val data = JSONArray(index.readText())
  return (0 until data.length()).map { data.getJSONObject(it).let { row ->
   ScanDocument(row.getString("id"), row.getString("name"), row.getInt("pages"), row.getLong("created"), row.optBoolean("importedPdf", false))
  } }.sortedByDescending { it.created }
 }
 private fun write(documents: List<ScanDocument>) {
  val array = JSONArray()
  documents.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("pages", it.pages).put("created", it.created).put("importedPdf", it.importedPdf)) }
  val temp = File(root, "index.tmp").apply { writeText(array.toString()) }
  check(temp.renameTo(index)) { "Could not update document library" }
 }
 fun file(doc: ScanDocument) = File(root, "${doc.id}.pdf")
 // Publish the batch only after all original PDFs validate successfully.
 fun importPdfs(uris: List<Uri>): List<ScanDocument> {
  require(uris.isNotEmpty())
  val staged = mutableListOf<ScanDocument>()
  val files = mutableListOf<File>()
  try {
   uris.forEach { uri ->
    var title = "Imported document"
    runCatching { context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) title=it.getString(0) ?: title } }
    if(uri.scheme=="file") title=File(requireNotNull(uri.path)).name
    val id=UUID.randomUUID().toString()
    val target=File(root,"$id.pdf");files.add(target)
    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: error("File is no longer accessible")
    val count=android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(target,android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
     require(renderer.pageCount>0)
     repeat(renderer.pageCount) { index -> renderer.openPage(index).use { require(it.width>0 && it.height>0) } }
     renderer.pageCount
    }
    staged.add(ScanDocument(id,title.removeSuffix(".pdf").removeSuffix(".PDF"),count,System.currentTimeMillis(),true))
   }
   write(list()+staged)
   return staged
  } catch(error:Exception) { files.forEach{it.delete()};throw error }
 }
 fun stageImages(uris: List<Uri>): List<String> {
  require(uris.isNotEmpty())
  val directory = File(context.cacheDir,"camera").apply { check(isDirectory || mkdirs()) }
  val staged = mutableListOf<File>()
  try {
   return uris.map { uri ->
    val destination = File.createTempFile("import-",".img",directory)
    staged.add(destination)
    context.contentResolver.openInputStream(uri)?.use { input ->
     destination.outputStream().use { input.copyTo(it) }
    } ?: error("Photo is no longer accessible")
    val copiedUri = androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",destination)
    bitmap(copiedUri).recycle()
    copiedUri.toString()
   }
  } catch(error:Exception) {
   staged.forEach { it.delete() }
   throw error
  }
 }
 fun rename(doc: ScanDocument, name: String) { require(name.isNotBlank()); write(list().map { if (it.id == doc.id) it.copy(name = name.trim()) else it }) }
 fun delete(doc: ScanDocument) { write(list().filterNot { it.id == doc.id }); file(doc).delete(); (0 until doc.pages).forEach { File(root, "${doc.id}-$it.jpg").delete() } }
 fun bitmap(uri: Uri): Bitmap = context.contentResolver.openInputStream(uri).use { input ->
  requireNotNull(input)
  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeStream(input, null, options)
  var sample = 1
  while (options.outWidth / sample > 2400 || options.outHeight / sample > 2400) sample *= 2
  context.contentResolver.openInputStream(uri).use { stream ->
   val decoded = requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })) { "Unsupported image" }
   val exif = context.contentResolver.openInputStream(uri).use { source -> source?.let { ExifInterface(it) } }
   val matrix = Matrix()
   if (exif?.isFlipped == true) matrix.postScale(-1f, 1f)
   matrix.postRotate((exif?.rotationDegrees ?: 0).toFloat())
   if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
  }
 }
 fun crop(uri: Uri, points: List<CropPoint>): String {
  val source = bitmap(uri)
  val output = try { PerspectiveCrop.correct(source, points) } finally { source.recycle() }
  val directory = File(context.cacheDir, "camera").apply { mkdirs() }
  val target = File.createTempFile("crop-", ".jpg", directory)
  try {
   target.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "Could not save cropped page." } }
   return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString()
  } catch (error: Throwable) { target.delete(); throw error }
  finally { output.recycle() }
 }
 fun pageSources(doc:ScanDocument):List<String> {
  val previews=(0 until doc.pages).map{File(root,"${doc.id}-$it.jpg")}
  val sources=if(previews.all{it.isFile && it.length()>0})previews else DocumentImageExport.render(file(doc),context.cacheDir,doc.name)
  return sources.map{androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",it).toString()}
 }
 // Publish only after every page and the PDF have been written. Existing data survives failures.
 fun replace(doc:ScanDocument,uris:List<Uri>,name:String,rotation:Int,grayscale:Boolean,adjustments:List<PageAdjustment> = List(uris.size){PageAdjustment(rotation,grayscale)}):ScanDocument {
  require(list().any{it.id==doc.id}) { "Document no longer exists" }
  val staged=create(uris,name,rotation,grayscale,publish=false,adjustments=adjustments).copy(created=doc.created)
  try {
   val current=list()
   check(current.any{it.id==doc.id})
   write(current.map{if(it.id==doc.id)staged else it})
  } catch(error:Exception) { removeFiles(staged);throw error }
  removeFiles(doc)
  return staged
 }
 private fun removeFiles(doc:ScanDocument) { file(doc).delete();(0 until doc.pages).forEach{File(root,"${doc.id}-$it.jpg").delete()} }
 fun create(uris: List<Uri>, name: String, rotation: Int, grayscale: Boolean, publish:Boolean=true,adjustments:List<PageAdjustment> = List(uris.size){PageAdjustment(rotation,grayscale)}): ScanDocument {
  require(uris.isNotEmpty())
  require(adjustments.size==uris.size && adjustments.all{it.rotation in listOf(0,90,180,270) && it.filter in ScanFilters.names && it.filter!="Remove Handwriting"})
  val doc = ScanDocument(UUID.randomUUID().toString(), name.trim().ifEmpty { "Scan" }, uris.size, System.currentTimeMillis())
  val target = file(doc)
  try {
   val pdf = PdfDocument()
   try {
    uris.forEachIndexed { i, uri ->
     val rotation=adjustments[i].rotation
     val grayscale=adjustments[i].grayscale
     val original = bitmap(uri)
     val filtered = try { ScanFilters.apply(original,adjustments[i].filter) } catch(error:Exception) { original.recycle();throw error }
     val image = if (rotation == 0) filtered else Bitmap.createBitmap(filtered, 0, 0, filtered.width, filtered.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
     val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
     page.canvas.drawColor(Color.WHITE)
     val scale = minOf(555f / image.width, 802f / image.height)
     val width = image.width * scale; val height = image.height * scale
     val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
     if (grayscale) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
     page.canvas.drawBitmap(image, null, RectF((595-width)/2, (842-height)/2, (595+width)/2, (842+height)/2), paint)
     pdf.finishPage(page)
     val preview = if (grayscale) Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(image, 0f, 0f, paint) } else image
     File(root, "${doc.id}-$i.jpg").outputStream().use { preview.compress(Bitmap.CompressFormat.JPEG, 92, it) }
     if (preview !== image) preview.recycle()
     if (image !== filtered) image.recycle()
     if (filtered !== original) filtered.recycle()
     original.recycle()
    }
    target.outputStream().use { pdf.writeTo(it) }
   } finally { pdf.close() }
   if(publish)write(list() + doc)
   return doc
  } catch (error: Exception) { target.delete(); uris.indices.forEach { File(root, "${doc.id}-$it.jpg").delete() }; throw error }
 }
}
