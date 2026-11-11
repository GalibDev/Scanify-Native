package com.scanify.nativeapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

internal object DocumentImageExport {
 fun renderLongImage(pdf: File, cache: File, name: String): File {
  val maxHeight = 8000
  val folder = File(cache, "shared-images/${UUID.randomUUID()}").apply { check(mkdirs()) }
  try {
   return PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    require(renderer.pageCount in 1 until maxHeight)
    val ratios = (0 until renderer.pageCount).map { index ->
     renderer.openPage(index).use { page -> page.height.toDouble() / page.width }
    }
    // Leave space for rounding each page up to a whole pixel.
    val targetWidth = minOf(1200.0, (maxHeight - ratios.size) / ratios.sum())
    val width = kotlin.math.ceil(targetWidth).toInt()
    val heights = ratios.map { kotlin.math.ceil(it * targetWidth).toInt().coerceAtLeast(1) }
    val stitched = Bitmap.createBitmap(width, heights.sum(), Bitmap.Config.ARGB_8888)
    try {
     stitched.eraseColor(Color.WHITE)
     val canvas = Canvas(stitched)
     var top = 0
     heights.forEachIndexed { index, height ->
      renderer.openPage(index).use { page ->
       val tile = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
       try {
        tile.eraseColor(Color.WHITE)
        page.render(tile, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        canvas.drawBitmap(tile, 0f, top.toFloat(), null)
       } finally {
        tile.recycle()
       }
      }
      top += height
     }
     File(folder, "${safeName(name)}-long.jpg").also { output ->
      output.outputStream().use { check(stitched.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
     }
    } finally {
     stitched.recycle()
    }
   }
  } catch (error: Exception) {
   folder.deleteRecursively()
   throw error
  }
 }

 private fun safeName(name: String) =
  name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).ifBlank { "Scanify" }

 // Render one page at a time; never hold a complete document's bitmaps in memory.
 fun render(pdf:File,cache:File,name:String,pageIndex:Int?=null):List<File> {
  val folder=File(cache,"shared-images/${UUID.randomUUID()}").apply{check(mkdirs())}
  val safe=safeName(name)
  try {
   return PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    check(renderer.pageCount>0)
    val pages = if (pageIndex == null) 0 until renderer.pageCount else pageIndex..pageIndex
    pages.map { index ->
     renderer.openPage(index).use { page ->
      val scale=2400f/maxOf(page.width,page.height)
      val bitmap=Bitmap.createBitmap((page.width*scale).roundToInt().coerceAtLeast(1),(page.height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
      try {
       bitmap.eraseColor(Color.WHITE)
       page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
       File(folder,"$safe-page-${index+1}.jpg").also { output -> output.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,92,it))} }
      } finally { bitmap.recycle() }
     }
    }
   }
  } catch(error:Exception) { folder.deleteRecursively();throw error }
 }

 @Suppress("DEPRECATION")
 fun savePhotos(context:Context,images:List<File>):Int {
  val resolver=context.contentResolver
  val inserted=mutableListOf<Uri>()
  val legacy=mutableListOf<File>()
  try {
   images.forEach { image ->
    if(Build.VERSION.SDK_INT>=29) {
     val values=ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME,image.name)
      put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
      put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Scanify")
      put(MediaStore.Images.Media.IS_PENDING,1)
     }
     val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
     inserted.add(uri)
     resolver.openOutputStream(uri)?.use{output->image.inputStream().use{it.copyTo(output)}}?:error("Cannot open photo destination")
    } else {
     val folder=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Scanify").apply{check(isDirectory||mkdirs())}
     val output=File(folder,"${UUID.randomUUID()}-${image.name}")
     legacy.add(output);image.copyTo(output)
    }
   }
   if(Build.VERSION.SDK_INT>=29) inserted.forEach{uri->check(resolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)==1)}
   else MediaScannerConnection.scanFile(context,legacy.map{it.absolutePath}.toTypedArray(),Array(legacy.size){"image/jpeg"},null)
   return images.size
  } catch(error:Exception) {
   inserted.forEach{uri->runCatching{resolver.delete(uri,null,null)}}
   legacy.forEach{it.delete()}
   throw error
  }
 }
}
