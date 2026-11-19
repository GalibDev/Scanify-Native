package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

internal object ScanFilters {
 val names = listOf("Original", "Enhance", "Omnifix", "No Shadow", "Remove Handwriting", "Lighten", "Magic Color", "Eco", "Grayscale", "B&W", "Invert")
 fun apply(image: Bitmap, filter: String): Bitmap {
  require(filter in names && filter != "Remove Handwriting")
  if (filter == "Original") return image
  val width = image.width
  val height = image.height
  val pixels = IntArray(width * height)
  image.getPixels(pixels, 0, width, 0, 0, width, height)
  if (filter == "Omnifix" || filter == "Enhance") {
   PaperEnhancement.apply(pixels,width,height)
   if(filter=="Enhance") for(i in pixels.indices) {
    val p=pixels[i]
    fun enhance(v:Int)=(((v-190f)*1.25f)+220f).roundToInt().coerceIn(0,255)
    pixels[i]=Color.rgb(enhance(Color.red(p)),enhance(Color.green(p)),enhance(Color.blue(p)))
   }
   return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
  }
  val tile = max(12, (max(width, height) / 24f).roundToInt())
  val cols = (width + tile - 1) / tile
  val rows = (height + tile - 1) / tile
  val paper = FloatArray(cols * rows)
  if (filter == "No Shadow") {
   for (y in 0 until height step 3) for (x in 0 until width step 3) {
    val pixel = pixels[y * width + x]
    val brightness = .299f * Color.red(pixel) + .587f * Color.green(pixel) + .114f * Color.blue(pixel)
    val index = y / tile * cols + x / tile
    paper[index] = max(paper[index], brightness)
   }
  }
  val channels = IntArray(3)
  for (y in 0 until height) for (x in 0 until width) {
   val index = y * width + x
   val pixel = pixels[index]
   channels[0] = Color.red(pixel)
   channels[1] = Color.green(pixel)
   channels[2] = Color.blue(pixel)
   val gray = .299f * channels[0] + .587f * channels[1] + .114f * channels[2]
   val gx = (x.toFloat() / tile - .5f).coerceIn(0f, (cols - 1).toFloat())
   val gy = (y.toFloat() / tile - .5f).coerceIn(0f, (rows - 1).toFloat())
   val x0 = gx.toInt()
   val y0 = gy.toInt()
   val x1 = min(cols - 1, x0 + 1)
   val y1 = min(rows - 1, y0 + 1)
   val u = gx - x0
   val v = gy - y0
   val upper = paper[y0 * cols + x0] * (1-u) + paper[y0 * cols + x1] * u
   val lower = paper[y1 * cols + x0] * (1-u) + paper[y1 * cols + x1] * u
   val gain = min(2.35f, 245f / max(1f, upper * (1-v) + lower * v))
   for (channel in channels.indices) {
    val value = channels[channel].toFloat()
    channels[channel] = when (filter) {
     "Grayscale" -> gray
     "B&W" -> if (gray > 155) 255f else 0f
     "Invert" -> 255-value
     "Lighten" -> value*1.15f
     "Eco" -> (gray-128)*1.12f+136
     "Magic Color" -> (gray+(value-gray)*1.55f-128)*1.13f+128
     "No Shadow" -> value*gain
     else -> value
    }.roundToInt().coerceIn(0,255)
   }
   pixels[index] = Color.rgb(channels[0],channels[1],channels[2])
  }
  return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
 }
}
