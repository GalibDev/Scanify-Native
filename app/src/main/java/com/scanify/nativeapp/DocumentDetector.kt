package com.scanify.nativeapp

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/** On-device contour heuristic, not semantic recognition of paper. Never crops without confirmation. */
object DocumentDetector {
 val available: Boolean by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

 fun detect(bitmap: Bitmap): List<CropPoint>? {
  if (!available) return null
  val scale = minOf(1f, 720f / max(bitmap.width, bitmap.height))
  val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).roundToInt(),(bitmap.height*scale).roundToInt(),true) else bitmap
  val rgba=Mat();val gray=Mat()
  return try { Utils.bitmapToMat(small,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);detectGray(gray) }
  finally { rgba.release();gray.release();if(small!==bitmap)small.recycle() }
 }

 fun detectLuma(bytes: ByteArray, width: Int, height: Int): List<CropPoint>? {
  if (!available) return null
  val gray=Mat(height,width,CvType.CV_8UC1)
  return try { gray.put(0,0,bytes);detectGray(gray) } finally { gray.release() }
 }

 private fun detectGray(gray: Mat): List<CropPoint>? {
  val blur=Mat();val edges=Mat();val threshold=Mat();val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(3.0,3.0))
  var best:List<CropPoint>?=null;var bestScore=0.0
  try {
   Imgproc.GaussianBlur(gray,blur,Size(5.0,5.0),0.0)
   Imgproc.Canny(blur,edges,40.0,120.0)
   Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel)
   Imgproc.threshold(blur,threshold,0.0,255.0,Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
   for (mask in listOf(edges,threshold)) {
    val contours=ArrayList<MatOfPoint>();val hierarchy=Mat()
    try {
     Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
     for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }.take(16)) {
      val area=Imgproc.contourArea(contour)/(gray.cols().toDouble()*gray.rows())
      if(area !in 0.14..0.94)continue
      val curve=MatOfPoint2f(*contour.toArray());val approx=MatOfPoint2f()
      try {
       val perimeter=Imgproc.arcLength(curve,true)
       for (epsilon in listOf(0.015,0.025,0.04)) {
        Imgproc.approxPolyDP(curve,approx,epsilon*perimeter,true)
        if(approx.total()!=4L)continue
        val raw=approx.toArray().toList()
        val cx=raw.sumOf{it.x}/4;val cy=raw.sumOf{it.y}/4
        val sorted=raw.sortedBy{atan2(it.y-cy,it.x-cx)}
        val first=sorted.indices.minBy { sorted[it].x+sorted[it].y }
        val ordered=(0..3).map{sorted[(first+it)%4]}
        val points=ordered.map{CropPoint((it.x/gray.cols()).toFloat(),(it.y/gray.rows()).toFloat())}
        if(!PerspectiveCrop.isValid(points)||points.any{it.x<0.012f||it.y<0.012f||it.x>0.988f||it.y>0.988f})continue
        var maxCos=0.0
        for(i in 0..3){val a=ordered[(i+3)%4];val b=ordered[i];val c=ordered[(i+1)%4];val ux=a.x-b.x;val uy=a.y-b.y;val vx=c.x-b.x;val vy=c.y-b.y;maxCos=max(maxCos,abs((ux*vx+uy*vy)/max(1.0,hypot(ux,uy)*hypot(vx,vy))))}
        if(maxCos>0.72)continue
        val score=area*(1.0-0.25*maxCos)
        if(score>bestScore){bestScore=score;best=points}
       }
      } finally {curve.release();approx.release()}
     }
    } finally {contours.forEach{it.release()};hierarchy.release()}
   }
   return best
  } finally {blur.release();edges.release();threshold.release();kernel.release()}
 }
}

/** Suppresses one-frame guesses and drops old outlines after three misses. */
class DetectionTracker {
 private var previous:List<CropPoint>?=null
 private var hits=0;private var misses=0
 fun update(points:List<CropPoint>?):List<CropPoint>? {
  if(points==null){misses++;if(misses>=3){previous=null;hits=0};return if(hits>=2)previous else null}
  misses=0
  val old=previous
  val close=old!=null&&points.indices.all{hypot(points[it].x-old[it].x,points[it].y-old[it].y)<0.08f}
  previous=if(close)points.mapIndexed{i,p->CropPoint(old!![i].x*0.6f+p.x*0.4f,old[i].y*0.6f+p.y*0.4f)}else points
  hits=if(close)hits+1 else 1
  return if(hits>=2)previous else null
 }
}
