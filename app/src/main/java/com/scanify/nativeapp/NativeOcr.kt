package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object NativeOcr {
 // Await on a worker so the bitmap stays alive until recognition has completed.
 fun recognize(bitmap:Bitmap):String {
  val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  return try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0))).text }
  finally { recognizer.close() }
 }
 suspend fun image(store:DocumentStore,uri:Uri,adjustment:PageAdjustment,recognizeImage:(Bitmap)->String=::recognize):String=withContext(Dispatchers.IO) {
  val original=store.bitmap(uri)
  var filtered:Bitmap=original
  var rotated:Bitmap=original
  try {
   filtered=ScanFilters.apply(original,if(adjustment.grayscale)"Grayscale" else adjustment.filter)
   rotated=if(adjustment.rotation==0)filtered else Bitmap.createBitmap(filtered,0,0,filtered.width,filtered.height,Matrix().apply{postRotate(adjustment.rotation.toFloat())},true)
   recognizeImage(rotated)
  } finally {
   if(rotated!==original && rotated!==filtered)rotated.recycle()
   if(filtered!==original)filtered.recycle()
   original.recycle()
  }
 }
 suspend fun pdf(file:File,recognizeImage:(Bitmap)->String=::recognize,onProgress:suspend(Int,Int)->Unit):String=withContext(Dispatchers.IO) {
  PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
   val output=StringBuilder()
   repeat(renderer.pageCount) { index ->
    coroutineContext.ensureActive()
    onProgress(index+1,renderer.pageCount)
    val text=renderer.openPage(index).use { page ->
     val scale=2400f/maxOf(page.width,page.height)
     val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
     try { bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);recognizeImage(bitmap) }
     finally { bitmap.recycle() }
    }
    if(text.isNotBlank()) {
     if(output.isNotEmpty())output.append("\n\n")
     if(renderer.pageCount>1)output.append("Page ${index+1}\n")
     output.append(text)
    }
   }
   output.toString()
  }
 }
}
