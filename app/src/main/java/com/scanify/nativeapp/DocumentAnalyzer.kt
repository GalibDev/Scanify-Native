package com.scanify.nativeapp

import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max

/** Works in raw Y-plane coordinates, then maps sensor/buffer coordinates into PreviewView. */
class DocumentAnalyzer(private val onResult:(List<CropPoint>?)->Unit):ImageAnalysis.Analyzer {
 @Volatile var enabled=true
 @Volatile private var sensorToView:Matrix?=null
 private var last=0L
 override fun getTargetCoordinateSystem()=ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
 override fun updateTransform(matrix:Matrix?){sensorToView=matrix?.let{Matrix(it)}}
 override fun analyze(image:ImageProxy){
  try {
   val now=SystemClock.elapsedRealtime()
   if(!enabled)return
   if(now-last<200)return
   last=now
   val plane=image.planes[0];val buffer=plane.buffer.duplicate();val base=buffer.position()
   val sample=max(1,(max(image.width,image.height)+511)/512)
   val width=image.width/sample;val height=image.height/sample
   val luma=ByteArray(width*height)
   for(y in 0 until height)for(x in 0 until width)luma[y*width+x]=buffer.get(base+y*sample*plane.rowStride+x*sample*plane.pixelStride)
   val found=DocumentDetector.detectLuma(luma,width,height)
   val transform=sensorToView
   if(found==null||transform==null){onResult(null);return}
   val inverse=Matrix()
   if(!image.imageInfo.sensorToBufferTransformMatrix.invert(inverse)){onResult(null);return}
   val bufferToView=Matrix().apply{setConcat(transform,inverse)}
   val coords=found.flatMap{listOf(it.x*image.width,it.y*image.height)}.toFloatArray()
   bufferToView.mapPoints(coords)
   onResult(coords.toList().chunked(2).map{CropPoint(it[0],it[1])})
  }catch(_:Exception){onResult(null)}finally{image.close()}
 }
}
