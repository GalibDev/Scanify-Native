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

data class ScanDocument(val id: String, val name: String, val pages: Int, val created: Long)

class DocumentStore(private val context: Context) {
 private val root = File(context.filesDir, "documents").apply { mkdirs() }
 private val index = File(root, "index.json")
 fun list(): List<ScanDocument> {
  if (!index.exists()) return emptyList()
  val data = JSONArray(index.readText())
  return (0 until data.length()).map { data.getJSONObject(it).let { row ->
   ScanDocument(row.getString("id"), row.getString("name"), row.getInt("pages"), row.getLong("created"))
  } }.sortedByDescending { it.created }
 }
 private fun write(documents: List<ScanDocument>) {
  val array = JSONArray()
  documents.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("pages", it.pages).put("created", it.created)) }
  val temp = File(root, "index.tmp").apply { writeText(array.toString()) }
  check(temp.renameTo(index)) { "Could not update document library" }
 }
 fun file(doc: ScanDocument) = File(root, "${doc.id}.pdf")
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
 fun create(uris: List<Uri>, name: String, rotation: Int, grayscale: Boolean): ScanDocument {
  require(uris.isNotEmpty())
  val doc = ScanDocument(UUID.randomUUID().toString(), name.trim().ifEmpty { "Scan" }, uris.size, System.currentTimeMillis())
  val target = file(doc)
  try {
   val pdf = PdfDocument()
   try {
    uris.forEachIndexed { i, uri ->
     val original = bitmap(uri)
     val image = if (rotation == 0) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
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
     if (image !== original) image.recycle()
     original.recycle()
    }
    target.outputStream().use { pdf.writeTo(it) }
   } finally { pdf.close() }
   write(list() + doc)
   return doc
  } catch (error: Exception) { target.delete(); uris.indices.forEach { File(root, "${doc.id}-$it.jpg").delete() }; throw error }
 }
}
