package com.scanify.nativeapp

import android.graphics.Color
import kotlin.math.*

/** Native port of the web paper illumination, CLAHE and edge-preserving enhancement. */
internal object PaperEnhancement {
 fun apply(pixels: IntArray, width: Int, height: Int) {
  val tile = max(32, ceil(max(width,height)/24.0).toInt())
  val cols = (width+tile-1)/tile
  val rows = (height+tile-1)/tile
  val white = FloatArray(cols*rows*3)
  val confidence = BooleanArray(cols*rows)
  val global = FloatArray(3)
  var trusted = 0
  fun channel(pixel:Int,c:Int) = when(c){0->Color.red(pixel);1->Color.green(pixel);else->Color.blue(pixel)}
  fun gray(pixel:Int) = .299f*Color.red(pixel)+.587f*Color.green(pixel)+.114f*Color.blue(pixel)
  for(ty in 0 until rows) for(tx in 0 until cols) {
   val histogram = IntArray(768)
   var count=0
   for(y in ty*tile until min(height,(ty+1)*tile) step 2) for(x in tx*tile until min(width,(tx+1)*tile) step 2) {
    val p=pixels[y*width+x]
    val brightest=maxOf(Color.red(p),Color.green(p),Color.blue(p))
    val darkest=minOf(Color.red(p),Color.green(p),Color.blue(p))
    if(brightest<95 || brightest-darkest>brightest*.34)continue
    for(c in 0..2)histogram[c*256+channel(p,c)]++
    count++
   }
   if(count<max(8.0,tile*tile/160.0))continue
   confidence[ty*cols+tx]=true
   trusted++
   for(c in 0..2) {
    var sum=0
    var value=0
    while(value<255 && sum<count*.9) {sum+=histogram[c*256+value];value++}
    white[(ty*cols+tx)*3+c]=max(90,value).toFloat()
    global[c]+=max(90,value)
   }
  }
  val estimated=white.copyOf()
  for(ty in 0 until rows)for(tx in 0 until cols)for(c in 0..2) {
   var sum=0f
   var weight=0
   for(dy in -1..1)for(dx in -1..1) {
    val x=tx+dx
    val y=ty+dy
    if(x !in 0 until cols || y !in 0 until rows || !confidence[y*cols+x])continue
    val w=if(dx==0&&dy==0)4 else 1
    sum+=estimated[(y*cols+x)*3+c]*w
    weight+=w
   }
   white[(ty*cols+tx)*3+c]=if(weight>0)sum/weight else if(trusted>0)global[c]/trusted else 255f
  }
  fun interpolate(values:FloatArray,x:Int,y:Int,stride:Int,c:Int):Float {
   val gx=(x.toFloat()/tile-.5f).coerceIn(0f,(cols-1).toFloat())
   val gy=(y.toFloat()/tile-.5f).coerceIn(0f,(rows-1).toFloat())
   val x0=gx.toInt()
   val y0=gy.toInt()
   val x1=min(cols-1,x0+1)
   val y1=min(rows-1,y0+1)
   val u=gx-x0
   val v=gy-y0
   return (values[(y0*cols+x0)*stride+c]*(1-u)+values[(y0*cols+x1)*stride+c]*u)*(1-v)+
    (values[(y1*cols+x0)*stride+c]*(1-u)+values[(y1*cols+x1)*stride+c]*u)*v
  }
  val luma=IntArray(pixels.size)
  val colors=IntArray(3)
  for(y in 0 until height)for(x in 0 until width) {
   val p=y*width+x
   for(c in 0..2)colors[c]=(channel(pixels[p],c)*min(2.5f,250f/interpolate(white,x,y,3,c))).roundToInt().coerceIn(0,255)
   pixels[p]=Color.rgb(colors[0],colors[1],colors[2])
   luma[p]=gray(pixels[p]).roundToInt().coerceIn(0,255)
  }
  val maps=FloatArray(cols*rows*256)
  for(ty in 0 until rows)for(tx in 0 until cols) {
   val hist=IntArray(256)
   val right=min(width,(tx+1)*tile)
   val bottom=min(height,(ty+1)*tile)
   val count=(right-tx*tile)*(bottom-ty*tile)
   for(y in ty*tile until bottom)for(x in tx*tile until right)hist[luma[y*width+x]]++
   val limit=max(1,(1.6*count/256).toInt())
   var excess=0
   for(b in 0..255){excess+=max(0,hist[b]-limit);hist[b]=min(limit,hist[b])}
   var cumulative=0
   for(b in 0..255) {
    cumulative+=hist[b]+excess/256+if(b<excess%256)1 else 0
    maps[(ty*cols+tx)*256+b]=(255f*cumulative/count).roundToInt().toFloat()
   }
  }
  val contrast=IntArray(pixels.size)
  for(y in 0 until height)for(x in 0 until width) {
   val p=y*width+x
   val local=interpolate(maps,x,y,256,luma[p])
   val mixed=luma[p]*.7f+local*.3f
   val anchored=when{luma[p]<110->min(luma[p].toFloat(),mixed);luma[p]>215->max(luma[p].toFloat(),mixed);else->mixed}
   val pixel=pixels[p]
   val spread=maxOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))-minOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))
   val neutral=max(0f,1-spread/45f)
   val ink=((165-luma[p])/130f).coerceIn(0f,1f)
   contrast[p]=(anchored*(1-.3f*neutral*ink)).roundToInt().coerceIn(0,255)
  }
  val clean=contrast.copyOf()
  val offsets=intArrayOf(-width,-1,1,width)
  for(y in 1 until height-1)for(x in 1 until width-1) {
   val p=y*width+x
   val center=contrast[p]
   var sum=center*4
   var weight=4
   for(offset in offsets)if(abs(contrast[p+offset]-center)<9){sum+=contrast[p+offset];weight++}
   clean[p]=(sum.toFloat()/weight).roundToInt()
  }
  for(y in 0 until height)for(x in 0 until width) {
   val p=y*width+x
   val blur=(clean[max(0,y-1)*width+x]+clean[min(height-1,y+1)*width+x]+clean[y*width+max(0,x-1)]+clean[y*width+min(width-1,x+1)])/4f
   val detail=clean[p]-blur
   val sharpen=if(abs(detail)>3)(detail*.4f).coerceIn(-8f,6f)else 0f
   val target=(clean[p]+sharpen).coerceIn(0f,255f)
   val pixel=pixels[p]
   val spread=maxOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))-minOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))
   val neutral=((32-spread)/24f).coerceIn(0f,1f)
   val paper=((target-218)/29f).coerceIn(0f,1f)
   val whiteBlend=paper*paper*(3-2*paper)*neutral
   val gain=target/max(1,luma[p])
   for(c in 0..2) {
    val color=channel(pixel,c)*gain
    colors[c]=(color+(255-color)*whiteBlend).roundToInt().coerceIn(0,255)
   }
   pixels[p]=Color.rgb(colors[0],colors[1],colors[2])
  }
 }
}
