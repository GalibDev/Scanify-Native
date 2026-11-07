package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Normalized corners in clockwise order: top-left, top-right, bottom-right, bottom-left. */
data class CropPoint(val x: Float, val y: Float)

object PerspectiveCrop {
 val fullImage = listOf(CropPoint(0f, 0f), CropPoint(1f, 0f), CropPoint(1f, 1f), CropPoint(0f, 1f))

 fun isValid(points: List<CropPoint>): Boolean {
  if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
  var twiceArea = 0f
  for (i in points.indices) {
   val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]
   if ((b.x-a.x)*(c.y-b.y) - (b.y-a.y)*(c.x-b.x) <= 0.0001f) return false
   if (hypot(b.x-a.x, b.y-a.y) < 0.02f) return false
   twiceArea += a.x*b.y - b.x*a.y
  }
  return twiceArea >= 0.01f
 }

 fun correct(source: Bitmap, points: List<CropPoint>): Bitmap {
  require(isValid(points)) { "Choose four corners that do not overlap." }
  val pixels = points.map { CropPoint(it.x * source.width, it.y * source.height) }
  fun edge(a: Int, b: Int) = hypot(pixels[a].x-pixels[b].x, pixels[a].y-pixels[b].y)
  val width = max(edge(0, 1), edge(3, 2))
  val height = max(edge(0, 3), edge(1, 2))
  require(width >= 32f && height >= 32f) { "Select a larger part of the document." }
  val scale = minOf(1f, 2400f / max(width, height))
  val outWidth = (width*scale).roundToInt().coerceAtLeast(32)
  val outHeight = (height*scale).roundToInt().coerceAtLeast(32)
  val from = pixels.flatMap { listOf(it.x, it.y) }.toFloatArray()
  val to = floatArrayOf(0f, 0f, outWidth.toFloat(), 0f, outWidth.toFloat(), outHeight.toFloat(), 0f, outHeight.toFloat())
  val transform = Matrix()
  require(transform.setPolyToPoly(from, 0, to, 0, 4)) { "Could not straighten these corners." }
  val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
  try {
   Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(source, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
   return output
  } catch (error: Throwable) { output.recycle(); throw error }
 }
}
