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
   testDocumentDetection()
   testPerspectiveCrop(store, root)
   check(store.list().isEmpty())
   val doc = store.create(listOf(Uri.fromFile(image), Uri.fromFile(image)), "Test scan", 90, true)
   check(doc.pages == 2 && store.file(doc).length() > 0)
   val exported=DocumentImageExport.render(store.file(doc),root,"Test / export")
   check(exported.size==2 && exported.map{it.name}.distinct().size==2)
   exported.forEach { file ->
    val bitmap=checkNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
    check(maxOf(bitmap.width,bitmap.height)==2400)
    val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
    check(Color.red(pixel)<240 && kotlin.math.abs(Color.red(pixel)-Color.green(pixel))<4) { "Exported image lost PDF content" }
    check(Color.red(bitmap.getPixel(2,2))>245) { "PDF background must be white" }
    bitmap.recycle()
   }
   val failedBefore=File(root,"shared-images").listFiles()!!.size
   check(runCatching{DocumentImageExport.render(image,root,"broken")}.isFailure)
   check(File(root,"shared-images").listFiles()!!.size==failedBefore) { "Failed export left temporary files" }
   val previewFile = File(store.file(doc).parentFile, "${doc.id}-0.jpg")
   val preview = android.graphics.BitmapFactory.decodeFile(previewFile.absolutePath)
   check(preview != null && preview.width == 80 && preview.height == 120) { "Preview must preserve the rotated image proportions" }
   val previewPixel = preview.getPixel(40, 60)
   check(kotlin.math.abs(Color.red(previewPixel) - Color.green(previewPixel)) <= 3) { "Preview must match the grayscale PDF" }
   preview.recycle()
   val thumbnail=DocumentThumbnailLoader.load(store.file(doc),doc.id)
   check(thumbnail.bytes>0 && thumbnail.image!=null)
   check(maxOf(thumbnail.image!!.width,thumbnail.image!!.height)<=384)
   val thumbnailPixel=thumbnail.image!!.getPixel(thumbnail.image!!.width/2,thumbnail.image!!.height/2)
   check(kotlin.math.abs(Color.red(thumbnailPixel)-Color.green(thumbnailPixel))<=3 && Color.red(thumbnailPixel)<240) { "Image thumbnail is blank or lost its grayscale" }
   val legacy=File(root,"legacy.pdf").apply{writeBytes(store.file(doc).readBytes())}
   val pdfThumbnail=DocumentThumbnailLoader.load(legacy,"legacy")
   check(pdfThumbnail.image!=null && pdfThumbnail.bytes==legacy.length()) { "Legacy PDF thumbnail failed" }
   check(maxOf(pdfThumbnail.image!!.width,pdfThumbnail.image!!.height)<=384)
   val pdfPixel=pdfThumbnail.image!!.getPixel(pdfThumbnail.image!!.width/2,pdfThumbnail.image!!.height/2)
   check(Color.red(pdfPixel)<240) { "Rendered PDF thumbnail is blank" }
   val broken=File(root,"broken.pdf").apply{writeText("Not a PDF")}
   check(DocumentThumbnailLoader.load(broken,"broken").image==null) { "Broken PDF should fall back to the file icon" }
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
   val blue=File(root,"blue.png")
   Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888).also{bitmap->bitmap.eraseColor(Color.BLUE);blue.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
   val editable=store.create(listOf(Uri.fromFile(image),Uri.fromFile(blue)),"Editable",0,false)
   val before=store.file(editable).readBytes()
   check(runCatching{store.replace(editable,listOf(Uri.fromFile(File(root,"missing.png"))),"Failed",0,false)}.isFailure)
   check(store.file(editable).readBytes().contentEquals(before)) { "Failed edit changed the saved PDF" }
   check(store.list().any{it==editable}) { "Failed edit changed the document index" }
   val updated=store.replace(editable,listOf(Uri.fromFile(File(store.file(editable).parentFile,"${editable.id}-1.jpg")),Uri.fromFile(image),Uri.fromFile(blue)),"Updated",0,false)
   check(updated.pages==3 && updated.created==editable.created && updated.name=="Updated")
   check(store.list().none{it.id==editable.id} && !store.file(editable).exists())
   val first=checkNotNull(android.graphics.BitmapFactory.decodeFile(File(store.file(updated).parentFile,"${updated.id}-0.jpg").absolutePath))
   check(Color.blue(first.getPixel(30,30))>240 && Color.red(first.getPixel(30,30))<10) { "Reordered first page is incorrect" };first.recycle()
   val reduced=store.replace(updated,listOf(Uri.fromFile(File(store.file(updated).parentFile,"${updated.id}-1.jpg"))),"Updated",0,false)
   check(reduced.pages==1 && store.list().count{it.name=="Updated"}==1)
   check(runCatching{store.replace(reduced,emptyList(),"Empty",0,false)}.isFailure)
   check(store.file(reduced).exists() && store.list().any{it.id==reduced.id}) { "Removing every page must not destroy the document" }
   store.delete(reduced)
   check(store.list().single().name == "Renamed")
   store.delete(doc)
   check(store.list().isEmpty() && !store.file(doc).exists())
   check(!previewFile.exists()) { "Deleting the document must remove its previews" }
   check(DocumentThumbnailLoader.load(store.file(doc),doc.id).image==null) { "Deleted files must not reuse a stale thumbnail" }
   results.putString("stream", "PASS: append/reorder/remove pages, failed-edit preservation, image export, bounded thumbnails, detection, crop, PDF and document library\n")
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
 private fun testDocumentDetection() {
  check(DocumentDetector.available) { "OpenCV could not initialize" }
  val paper=Bitmap.createBitmap(500,450,Bitmap.Config.ARGB_8888)
  val expected=listOf(CropPoint(.1f,.1f),CropPoint(.9f,.2f),CropPoint(.8f,.9f),CropPoint(.15f,.8f))
  val canvas=android.graphics.Canvas(paper)
  canvas.drawColor(Color.rgb(35,40,45))
  val polygon=android.graphics.Path().apply { moveTo(50f,45f);lineTo(450f,90f);lineTo(400f,405f);lineTo(75f,360f);close() }
  canvas.drawPath(polygon,android.graphics.Paint().apply{color=Color.WHITE})
  for(y in 140..280 step 35)canvas.drawRect(130f,y.toFloat(),340f,y+5f,android.graphics.Paint().apply{color=Color.DKGRAY})
  val detected=requireNotNull(DocumentDetector.detect(paper)) { "Did not find the test paper" }
  check(PerspectiveCrop.isValid(detected))
  check(detected.indices.all { kotlin.math.hypot(detected[it].x-expected[it].x,detected[it].y-expected[it].y)<.035f }) { "Incorrect paper corners: $detected" }
  val rotated=Bitmap.createBitmap(paper,0,0,500,450,android.graphics.Matrix().apply{postRotate(90f)},false)
  check(DocumentDetector.detect(rotated)?.let(PerspectiveCrop::isValid)==true) { "Rotated paper was not detected" }
  val blank=Bitmap.createBitmap(500,450,Bitmap.Config.ARGB_8888)
  blank.eraseColor(Color.WHITE);check(DocumentDetector.detect(blank)==null) { "Blank frame must not detect a page" }
  blank.eraseColor(Color.DKGRAY)
  android.graphics.Canvas(blank).drawCircle(250f,225f,140f,android.graphics.Paint().apply{color=Color.WHITE})
  check(DocumentDetector.detect(blank)==null) { "Circle must not detect as a page" }
  val tracker=DetectionTracker()
  check(tracker.update(detected)==null);check(tracker.update(detected)!=null)
  tracker.update(null);tracker.update(null);check(tracker.update(null)==null)
  check(tracker.update(detected)==null) { "Old outline must not reappear immediately" }
  // A padded Y plane with a nonzero ByteBuffer position exercises actual analyzer indexing.
  val rowStride=512;val bytes=ByteArray(7+rowStride*450)
  for(y in 0 until 450)for(x in 0 until 500){val pixel=paper.getPixel(x,y);bytes[7+y*rowStride+x]=((Color.red(pixel)*77+Color.green(pixel)*150+Color.blue(pixel)*29) shr 8).toByte()}
  val buffer=java.nio.ByteBuffer.wrap(bytes).apply{position(7)}
  var closed=0
  val sensorToBuffer=android.graphics.Matrix().apply{setScale(.5f,.5f)}
  val desired=android.graphics.Matrix().apply{setRotate(90f);postTranslate(450f,0f)}
  val sensorToView=android.graphics.Matrix().apply{setConcat(desired,sensorToBuffer)}
  fun proxy(type:Class<*>,call:(String)->Any?):Any=java.lang.reflect.Proxy.newProxyInstance(type.classLoader,arrayOf(type)){_,method,_->call(method.name)}
  val info=proxy(androidx.camera.core.ImageInfo::class.java){when(it){"getSensorToBufferTransformMatrix"->sensorToBuffer;"getRotationDegrees"->0;"getTimestamp"->0L;else->null}} as androidx.camera.core.ImageInfo
  val plane=proxy(androidx.camera.core.ImageProxy.PlaneProxy::class.java){when(it){"getBuffer"->buffer;"getRowStride"->rowStride;"getPixelStride"->1;else->null}} as androidx.camera.core.ImageProxy.PlaneProxy
  val frame=proxy(androidx.camera.core.ImageProxy::class.java){when(it){"getWidth"->500;"getHeight"->450;"getPlanes"->arrayOf(plane);"getImageInfo"->info;"close"->{closed++;null};else->null}} as androidx.camera.core.ImageProxy
  var result:List<CropPoint>?=null
  val analyzer=DocumentAnalyzer{result=it}
  analyzer.updateTransform(sensorToView);analyzer.analyze(frame)
  val mapped=requireNotNull(result) { "Analyzer did not return a boundary" }
  check(mapped.indices.all { kotlin.math.hypot(mapped[it].x-(450-expected[it].y*450),mapped[it].y-expected[it].x*500)<18 }) { "Incorrect sensor/view mapping: $mapped" }
  analyzer.analyze(frame);check(closed==2) { "Analyzer must close even a throttled frame" }
  paper.recycle();rotated.recycle();blank.recycle()
 }
}
