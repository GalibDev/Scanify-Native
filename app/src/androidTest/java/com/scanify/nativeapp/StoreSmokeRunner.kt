package com.scanify.nativeapp

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File

/** Runs the real image -> PDF -> persistent library pipeline on Android, in isolated storage. */
class StoreSmokeRunner : Instrumentation() {
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
 override fun onStart() {
  val results = Bundle()
  val root = File(targetContext.cacheDir, "store-test-${System.nanoTime()}").apply { mkdirs() }
  try {
   val isolated = object : ContextWrapper(targetContext) { override fun getFilesDir() = root }
   val image = File(root, "source.png")
   Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).also { bitmap ->
    bitmap.eraseColor(Color.RED)
    image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
   }
   val store = DocumentStore(isolated)
   testPerspectiveCrop(store, root)
   check(store.list().isEmpty())
   val doc = store.create(listOf(Uri.fromFile(image), Uri.fromFile(image)), "Test scan", 90, true)
   check(doc.pages == 2 && store.file(doc).length() > 0)
   val previewFile = File(store.file(doc).parentFile, "${doc.id}-0.jpg")
   val preview = android.graphics.BitmapFactory.decodeFile(previewFile.absolutePath)
   check(preview != null && preview.width == 80 && preview.height == 120) { "Preview must preserve the rotated image proportions" }
   val previewPixel = preview.getPixel(40, 60)
   check(kotlin.math.abs(Color.red(previewPixel) - Color.green(previewPixel)) <= 3) { "Preview must match the grayscale PDF" }
   preview.recycle()
   check(DocumentStore(isolated).list().single().id == doc.id)
   PdfRenderer(ParcelFileDescriptor.open(store.file(doc), ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    check(renderer.pageCount == 2)
    renderer.openPage(0).use { page ->
     val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
     page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
     val pixel = bitmap.getPixel(297, 421)
     check(kotlin.math.abs(Color.red(pixel) - Color.green(pixel)) <= 2) { "Grayscale was not applied" }
     check(Color.red(pixel) < 240) { "PDF page is blank" }
     bitmap.recycle()
    }
   }
   store.rename(doc, "Renamed")
   check(store.list().single().name == "Renamed")
   store.delete(doc)
   check(store.list().isEmpty() && !store.file(doc).exists())
   check(!previewFile.exists()) { "Deleting the document must remove its previews" }
   results.putString("stream", "PASS: perspective correction, invalid corners, non-destructive crop, multipage PDF, grayscale, persistence, rename and delete\n")
   finish(Activity.RESULT_OK, results)
  } catch (error: Throwable) {
   results.putString("stream", "FAIL: ${error.stackTraceToString()}")
   finish(Activity.RESULT_CANCELED, results)
  } finally { root.deleteRecursively() }
 }
 private fun testPerspectiveCrop(store: DocumentStore, root: File) {
  val corners = listOf(CropPoint(0.1f,0.1f),CropPoint(0.9f,0.2f),CropPoint(0.8f,0.9f),CropPoint(0.15f,0.8f))
  check(PerspectiveCrop.isValid(corners))
  check(!PerspectiveCrop.isValid(listOf(corners[0],corners[2],corners[1],corners[3])))
  check(!PerspectiveCrop.isValid(List(4) { CropPoint(0.5f,0.5f) }))
  check(!PerspectiveCrop.isValid(corners.toMutableList().apply { set(0,CropPoint(Float.NaN,0f)) }))
  val flat = Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
  val paint = android.graphics.Paint()
  android.graphics.Canvas(flat).apply {
   drawRect(0f,0f,160f,120f,paint.apply { color=Color.RED })
   drawRect(160f,0f,320f,120f,paint.apply { color=Color.GREEN })
   drawRect(160f,120f,320f,240f,paint.apply { color=Color.BLUE })
   drawRect(0f,120f,160f,240f,paint.apply { color=Color.YELLOW })
  }
  val skewed = Bitmap.createBitmap(500,450,Bitmap.Config.ARGB_8888)
  val warp = android.graphics.Matrix()
  check(warp.setPolyToPoly(floatArrayOf(0f,0f,320f,0f,320f,240f,0f,240f),0,corners.flatMap { listOf(it.x*500,it.y*450) }.toFloatArray(),0,4))
  android.graphics.Canvas(skewed).apply { drawColor(Color.DKGRAY); drawBitmap(flat,warp,android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
  val corrected = PerspectiveCrop.correct(skewed,corners)
  check(corrected.width in 390..410 && corrected.height in 315..335)
  fun pixel(x: Float,y: Float)=corrected.getPixel((x*corrected.width).toInt(),(y*corrected.height).toInt())
  check(Color.red(pixel(.2f,.2f))>240 && Color.green(pixel(.2f,.2f))<15)
  check(Color.green(pixel(.8f,.2f))>240 && Color.red(pixel(.8f,.2f))<15)
  check(Color.blue(pixel(.8f,.8f))>240 && Color.red(pixel(.8f,.8f))<15)
  check(Color.red(pixel(.2f,.8f))>240 && Color.green(pixel(.2f,.8f))>240)
  val source = File(root,"skewed.png")
  source.outputStream().use { skewed.compress(Bitmap.CompressFormat.PNG,100,it) }
  val before=source.readBytes()
  val croppedUri=Uri.parse(store.crop(Uri.fromFile(source),corners))
  check(before.contentEquals(source.readBytes())) { "Crop changed the original file" }
  val cropped=store.bitmap(croppedUri)
  check(cropped.width==corrected.width && cropped.height==corrected.height)
  check(runCatching { PerspectiveCrop.correct(skewed,listOf(corners[0],corners[2],corners[1],corners[3])) }.isFailure)
  targetContext.contentResolver.delete(croppedUri,null,null)
  cropped.recycle();corrected.recycle();skewed.recycle();flat.recycle()
 }
}
