package com.scanify.nativeapp

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private val CropAccent = Color(0xFF27E5D3)

@Composable
internal fun CropScreen(store: DocumentStore, uri: String, onCancel: () -> Unit, onApply: (String) -> Unit) {
 val scope = rememberCoroutineScope()
 var coords by rememberSaveable(uri) { mutableStateOf(arrayListOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)) }
 val points = coords.chunked(2).map { CropPoint(it[0], it[1]) }
 val currentPoints by rememberUpdatedState(points)
 var working by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf("") }
 var preview by remember { mutableStateOf<Bitmap?>(null) }
 var showPreview by remember { mutableStateOf(false) }
 var failedLoad by remember { mutableStateOf(false) }
 val source by produceState<Bitmap?>(null, uri) {
  value = withContext(Dispatchers.IO) { runCatching { store.bitmap(Uri.parse(uri)) }.getOrElse { failedLoad = true; null } }
 }
 var canvasSize by remember { mutableStateOf(IntSize.Zero) }
 var dragging by remember { mutableIntStateOf(-1) }
 val hitRadius = with(LocalDensity.current) { 32.dp.toPx() }
 val view = LocalView.current
 var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
 val previousExclusions = remember(view) { if (android.os.Build.VERSION.SDK_INT >= 29) view.systemGestureExclusionRects else emptyList() }
 DisposableEffect(view) { onDispose { if (android.os.Build.VERSION.SDK_INT >= 29) view.systemGestureExclusionRects = previousExclusions } }
 SideEffect {
  if (android.os.Build.VERSION.SDK_INT >= 29) {
   val image = source
   view.systemGestureExclusionRects = if (image == null || canvasSize.width == 0 || showPreview) previousExclusions else {
    val scale = min(canvasSize.width.toFloat()/image.width,canvasSize.height.toFloat()/image.height)
    val width=image.width*scale; val height=image.height*scale
    val left=canvasOrigin.x+(canvasSize.width-width)/2; val top=canvasOrigin.y+(canvasSize.height-height)/2
    previousExclusions + points.map { point ->
     val x=left+point.x*width;val y=top+point.y*height
     android.graphics.Rect((x-hitRadius).toInt().coerceAtLeast(0),(y-hitRadius).toInt().coerceAtLeast(0),(x+hitRadius).toInt().coerceAtMost(view.width),(y+hitRadius).toInt().coerceAtMost(view.height))
    }
   }
  }
 }
 BackHandler { if (!working) { if (showPreview) showPreview = false else onCancel() } }
 Column(Modifier.fillMaxSize().background(Color(0xFF151515))) {
  Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = { if (showPreview) showPreview = false else onCancel() }, enabled = !working) { WebIcon("back", color = Color.White, description = "Cancel crop") }
   Column(Modifier.weight(1f)) { Text(if (showPreview) "Corrected preview" else "Adjust corners", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("Crop & perspective", color = Color.LightGray, fontSize = 11.sp) }
   TextButton(enabled = !working, onClick = { coords = arrayListOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f); showPreview = false; error = "" }) { Text("Reset", color = CropAccent) }
  }
  Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 36.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
   val bitmap = source
   if (showPreview && preview != null) Image(preview!!.asImageBitmap(), "Perspective-corrected preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
   else if (bitmap != null) {
    Image(bitmap.asImageBitmap(), "Original page for cropping", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    Canvas(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.onGloballyPositioned {
     val location=IntArray(2);view.getLocationInWindow(location)
     canvasOrigin=it.positionInWindow()-Offset(location[0].toFloat(),location[1].toFloat())
    }.semantics { contentDescription = "Crop corners: drag top left, top right, bottom right or bottom left" }.pointerInput(bitmap, canvasSize) {
     val scale = min(size.width.toFloat()/bitmap.width, size.height.toFloat()/bitmap.height)
     val imageWidth = bitmap.width*scale; val imageHeight = bitmap.height*scale
     val left = (size.width-imageWidth)/2; val top = (size.height-imageHeight)/2
     detectDragGestures(
      onDragStart = { start ->
       if (!working) dragging = currentPoints.indices.minByOrNull { i -> val p = currentPoints[i]; (Offset(left+p.x*imageWidth, top+p.y*imageHeight)-start).getDistance() }?.takeIf { i -> val p = currentPoints[i]; (Offset(left+p.x*imageWidth, top+p.y*imageHeight)-start).getDistance() <= hitRadius } ?: -1
      },
      onDragEnd = { dragging = -1 }, onDragCancel = { dragging = -1 },
      onDrag = { change, amount ->
       if (dragging >= 0 && !working) {
        change.consume()
        val old = currentPoints[dragging]
        val next = currentPoints.toMutableList().apply { set(dragging, CropPoint((old.x+amount.x/imageWidth).coerceIn(0f,1f), (old.y+amount.y/imageHeight).coerceIn(0f,1f))) }
        if (PerspectiveCrop.isValid(next)) { coords = ArrayList(next.flatMap { listOf(it.x,it.y) }); error = "" }
       }
      }
     )
    }) {
     val scale = min(size.width/bitmap.width, size.height/bitmap.height)
     val w = bitmap.width*scale; val h = bitmap.height*scale
     val origin = Offset((size.width-w)/2, (size.height-h)/2)
     val corners = points.map { origin + Offset(it.x*w, it.y*h) }
     val polygon = Path().apply { moveTo(corners[0].x,corners[0].y); corners.drop(1).forEach { lineTo(it.x,it.y) }; close() }
     val shade = Path().apply { fillType = PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(origin, Size(w,h))); addPath(polygon) }
     drawPath(shade, Color.Black.copy(alpha=0.58f))
     drawPath(polygon, CropAccent, style = Stroke(2.dp.toPx()))
     for (i in 1..2) {
      val fraction = i/3f
      fun between(a: Offset,b: Offset) = a+(b-a)*fraction
      drawLine(CropAccent.copy(alpha=0.45f), between(corners[0],corners[3]),between(corners[1],corners[2]),1.dp.toPx())
      drawLine(CropAccent.copy(alpha=0.45f), between(corners[0],corners[1]),between(corners[3],corners[2]),1.dp.toPx())
     }
     corners.forEachIndexed { index, point -> drawCircle(Color.Black.copy(alpha=0.4f), if(index==dragging) 16.dp.toPx() else 12.dp.toPx(), point); drawCircle(CropAccent, 8.dp.toPx(),point); drawCircle(Color.White,3.dp.toPx(),point) }
    }
   } else if (failedLoad) Text("Could not load this page. Go back and try another image.", color=Color.White, textAlign=TextAlign.Center)
   else CircularProgressIndicator(color=CropAccent)
   if (working) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment=Alignment.Center) { CircularProgressIndicator(color=CropAccent) }
  }
  Column(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal=18.dp,vertical=14.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(error.isNotEmpty()) error else if(showPreview) "Check the result. Apply replaces only this page." else "Drag the four corners around the paper.\nRotation and filters apply after cropping.", color=if(error.isNotEmpty()) Color(0xFFFF9D93) else Color.LightGray, fontSize=12.sp,lineHeight=18.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    OutlinedButton(enabled=source!=null && !working, modifier=Modifier.weight(1f), onClick={
     if(showPreview) showPreview=false else scope.launch {
      working=true;error=""
      try { preview=withContext(Dispatchers.Default) { PerspectiveCrop.correct(requireNotNull(source),points) };showPreview=true }
      catch(e:Exception) { error=e.message?:"Could not preview the crop." }
      finally { working=false }
     }
    }) { Text(if(showPreview) "Adjust" else "Preview",color=Color.White) }
    Button(enabled=source!=null && !working && PerspectiveCrop.isValid(points),modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=CropAccent,contentColor=Color.Black),onClick={
     scope.launch {
      working=true;error=""
      try { val result=withContext(Dispatchers.IO) { store.crop(Uri.parse(uri),points) };onApply(result) }
      catch(e:Exception) { error=e.message?:"Could not apply crop." }
      finally { working=false }
     }
    }) { Text("Apply crop") }
   }
  }
 }
}
