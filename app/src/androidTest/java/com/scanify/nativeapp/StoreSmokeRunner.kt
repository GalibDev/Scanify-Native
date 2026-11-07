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
   check(store.list().isEmpty())
   val doc = store.create(listOf(Uri.fromFile(image), Uri.fromFile(image)), "Test scan", 90, true)
   check(doc.pages == 2 && store.file(doc).length() > 0)
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
   results.putString("stream", "PASS: multipage PDF, grayscale rendering, library persistence, rename and delete\n")
   finish(Activity.RESULT_OK, results)
  } catch (error: Throwable) {
   results.putString("stream", "FAIL: ${error.stackTraceToString()}")
   finish(Activity.RESULT_CANCELED, results)
  } finally { root.deleteRecursively() }
 }
}
