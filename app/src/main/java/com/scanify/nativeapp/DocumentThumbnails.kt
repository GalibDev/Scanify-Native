package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal data class DocumentThumbnail(val image:Bitmap?,val bytes:Long)

/** Bounded software thumbnails. Evicted images are collected, never recycled while Compose draws them. */
internal object DocumentThumbnailLoader {
 private val cache=object:LruCache<String,Bitmap>(8*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
 fun load(file:File,id:String):DocumentThumbnail {
  val bytes=file.length()
  val preview=File(file.parentFile,"$id-0.jpg")
  val key="${file.absolutePath}:${file.lastModified()}:$bytes:${preview.lastModified()}"
  cache.get(key)?.let{return DocumentThumbnail(it,bytes)}
  val image=runCatching {
   val decoded=if(preview.exists()) {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(preview.absolutePath,bounds)
    var sample=1
    while(max(bounds.outWidth,bounds.outHeight)/sample>384)sample*=2
    BitmapFactory.decodeFile(preview.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})
   } else null
   decoded ?: PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    renderer.openPage(0).use { page ->
     val scale=384f/max(page.width,page.height)
     Bitmap.createBitmap((page.width*scale).roundToInt().coerceAtLeast(1),(page.height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
    }
   }
  }.getOrNull()
  if(image!=null)cache.put(key,image)
  return DocumentThumbnail(image,bytes)
 }
}
