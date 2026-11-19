package com.scanify.nativeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val CameraAccent = Color(0xFF27E5D3)

@Composable
internal fun NativeCameraScreen(
 owner: LifecycleOwner, pageCount: Int, retaking: Boolean, grayscale: Boolean,
 onFilter: (Boolean) -> Unit, onCaptured: (String, Boolean, Boolean) -> Unit,
 onClose: () -> Unit, onImport: () -> Unit
) {
 val context = LocalContext.current
 val executor = remember { ContextCompat.getMainExecutor(context) }
 var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
 var requested by rememberSaveable { mutableStateOf(false) }
 val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; requested = true }
 var front by rememberSaveable { mutableStateOf(false) }
 var resolution by rememberSaveable { mutableIntStateOf(0) }
 var selectedFilter by rememberSaveable { mutableStateOf("Enhance") }
 var autoCapture by rememberSaveable { mutableStateOf(false) }
 var autoArmed by remember { mutableStateOf(true) }
 var autoCrop by rememberSaveable { mutableStateOf(true) }
 var volumeCapture by rememberSaveable { mutableStateOf(true) }
 var capturePreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
 val scope = rememberCoroutineScope()
 var batch by rememberSaveable { mutableStateOf(false) }
 var idGuide by rememberSaveable { mutableStateOf(false) }
 var detectEnabled by rememberSaveable { mutableStateOf(true) }
 var detectionSupported by remember { mutableStateOf(true) }
 var outline by remember { mutableStateOf<List<CropPoint>?>(null) }
 var tracker by remember { mutableStateOf(DetectionTracker()) }
 val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
 var grid by rememberSaveable { mutableStateOf(false) }
 var panel by remember { mutableStateOf("") }
 var message by remember { mutableStateOf("") }
 var error by remember { mutableStateOf("") }
 var ready by remember { mutableStateOf(false) }
 var flashAvailable by remember { mutableStateOf(false) }
 var switchAvailable by remember { mutableStateOf(false) }
 var torch by remember { mutableStateOf(false) }
 var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
 var capturing by remember { mutableStateOf(false) }
 var retry by remember { mutableIntStateOf(0) }
 val active = remember { AtomicBoolean(true) }
 val latestCapture by rememberUpdatedState(onCaptured)
 val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS) } }
 val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } }
 val analyzer = remember { DocumentAnalyzer { result ->
  executor.execute {
   if (active.get() && ready && detectEnabled && detectionSupported && preview.width > 0 && preview.height > 0) {
    val normalized = result?.map { CropPoint(it.x/preview.width,it.y/preview.height) }?.takeIf { points -> points.all { it.x in 0f..1f && it.y in 0f..1f } }
    outline = tracker.update(normalized)
   } else { outline = null; tracker = DetectionTracker() }
  }
 } }
 SideEffect { analyzer.enabled = detectEnabled }
 LaunchedEffect(Unit) { if (!granted && !requested) permission.launch(Manifest.permission.CAMERA) }
 DisposableEffect(owner) {
  val observer = LifecycleEventObserver { _, event ->
   if (event == Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }
  owner.lifecycle.addObserver(observer)
  onDispose { active.set(false); controller.clearImageAnalysisAnalyzer(); analysisExecutor.shutdown(); owner.lifecycle.removeObserver(observer) }
 }
 DisposableEffect(granted, front, resolution, retry) {
  var disposed = false
  outline = null; tracker = DetectionTracker()
  ready = false; error = ""; torch = false; flashAvailable = false; switchAvailable = false
  val streamObserver = Observer<PreviewView.StreamState> { state ->
   ready = state == PreviewView.StreamState.STREAMING
   if(!ready){outline=null;tracker=DetectionTracker()}
   if (ready) flashAvailable = controller.cameraInfo?.hasFlashUnit() == true
  }
  if (granted) {
   preview.previewStreamState.observe(owner, streamObserver)
   val future = ProcessCameraProvider.getInstance(context)
   future.addListener({
    if (!disposed) try {
     val provider = future.get()
     val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
     if (!provider.hasCamera(selector)) error = "This camera is unavailable. Try switching cameras or import an image."
     else {
      switchAvailable = provider.hasCamera(if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA)
      controller.cameraSelector = selector
      controller.imageCaptureMode = if (resolution == 0) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
      controller.imageCaptureResolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(listOf(Size(4032,3024),Size(3264,2488),Size(2880,2156),Size(1920,1440))[resolution], ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
      try {
       controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
       controller.setImageAnalysisAnalyzer(analysisExecutor,analyzer)
       controller.bindToLifecycle(owner)
       detectionSupported = true
      } catch (_: Exception) {
       controller.clearImageAnalysisAnalyzer()
       controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
       controller.bindToLifecycle(owner)
       detectionSupported = false
      }
     }
    } catch (_: Exception) { error = "Could not start the camera. Retry or import an image." }
   }, executor)
  }
  onDispose {
   disposed = true
   preview.previewStreamState.removeObserver(streamObserver)
   controller.enableTorch(false)
   controller.unbind()
  }
 }
 BackHandler { if (!capturing) { if (panel.isNotEmpty()) panel = "" else onClose() } }
 fun takePhoto() {
  if (!granted || !ready || capturing) return
  capturing = true; error = ""; panel = ""; autoArmed = false
  controller.imageCaptureFlashMode = flashMode
  val finish = !batch || retaking
  var output: File? = null
  try {
   val dir = File(context.cacheDir, "camera").apply { mkdirs() }
   val file = File.createTempFile("scan-", ".jpg", dir)
   output = file
   controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
     if (!active.get()) { file.delete(); return }
     val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
     val filter = selectedFilter
     val shouldDetect = autoCrop && detectEnabled && detectionSupported
     scope.launch {
      try {
       val result = withContext(Dispatchers.IO) {
        val original = DocumentStore(context).bitmap(Uri.parse(uri))
        try {
         val corners = if(shouldDetect) DocumentDetector.detect(original) else null
         val corrected = if(corners!=null) PerspectiveCrop.correct(original,corners) else original
         val filtered = try { ScanFilters.apply(corrected,filter) } catch(error:Exception) { if(corrected!==original)corrected.recycle();throw error }
         try {
          file.outputStream().use { check(filtered.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it)) }
          val scale = minOf(1f,900f/maxOf(filtered.width,filtered.height))
          val thumbnail = android.graphics.Bitmap.createScaledBitmap(filtered,(filtered.width*scale).toInt().coerceAtLeast(1),(filtered.height*scale).toInt().coerceAtLeast(1),true)
          Triple(shouldDetect && corners==null, if(thumbnail===original || thumbnail===filtered) thumbnail.copy(android.graphics.Bitmap.Config.ARGB_8888,false) else thumbnail, uri)
         } finally { if(filtered!==corrected)filtered.recycle();if(corrected!==original)corrected.recycle() }
        } finally { original.recycle() }
       }
       capturePreview = result.second
       delay(1100)
       if(active.get()) {
        capturePreview=null
        capturing=false
        latestCapture(result.third,finish,result.first)
       }
      } catch (_:Exception) {
       file.delete();capturePreview=null;capturing=false;error="Could not process the photo. Please try again."
      }
     }
    }
    override fun onError(exception: ImageCaptureException) {
     file.delete()
     if (active.get()) { capturing = false; error = "Photo capture failed. Please try again." }
    }
   })
  } catch (_: Exception) { output?.delete(); capturing = false; error = "Could not capture a photo. Please try again." }
 }
 val latestTakePhoto by rememberUpdatedState(::takePhoto)
 DisposableEffect(volumeCapture) {
  val activity = context as? MainActivity
  activity?.cameraVolumeAction = if(volumeCapture) ({ latestTakePhoto() }) else null
  onDispose { activity?.cameraVolumeAction = null }
 }
 LaunchedEffect(outline != null, autoCapture, capturing, ready) {
  if(outline == null && !capturing) autoArmed = true
  if(autoCapture && autoArmed && outline != null && ready && !capturing && panel.isEmpty()) {
   delay(1400)
   if(outline != null && !capturing && panel.isEmpty())takePhoto()
  }
 }
 Column(Modifier.fillMaxSize().background(Color.Black)) {
  Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = onClose, enabled = !capturing) { WebIcon("close", color = Color.White, description = "Close camera") }
   if (pageCount > 0) TextButton(onClick = onClose, enabled = !capturing) { Text("$pageCount ✓", color = CameraAccent, fontSize = 13.sp) }
   Spacer(Modifier.weight(1f))
   IconButton(enabled = flashAvailable && ready && !capturing, onClick = {
    flashMode = when(flashMode) { ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF; else -> ImageCapture.FLASH_MODE_AUTO }
   }) {
    Box {
     WebIcon("flash", color = if (flashAvailable) Color.White else Color.Gray, description = "Flash ${when(flashMode){ImageCapture.FLASH_MODE_AUTO->"Auto";ImageCapture.FLASH_MODE_ON->"On";else->"Off"}}")
     Text(when(flashMode){ImageCapture.FLASH_MODE_AUTO->"A";ImageCapture.FLASH_MODE_OFF->"×";else->""},Modifier.align(Alignment.BottomEnd),color=Color.White,fontSize=8.sp)
    }
   }
   TextButton(enabled = !capturing, onClick = { panel = if (panel == "resolution") "" else "resolution" }) { Text(listOf("HD","L","STD","S")[resolution],Modifier.background(Color.White,RoundedCornerShape(2.dp)).padding(2.dp), color = Color.Black, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold) }
   IconButton(enabled = !capturing, onClick = { panel = if (panel == "filters") "" else "filters" }) { CameraCirclesIcon() }
   IconButton(enabled = !capturing, onClick = { panel = if (panel == "settings") "" else "settings" }) { WebIcon("more", color = Color.White, description = "Camera settings") }
  }
  Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF111111))) {
   if (granted) AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
   if (ready) {
    if (grid) Canvas(Modifier.fillMaxSize()) {
     for (i in 1..2) { drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(size.width*i/3, 0f), androidx.compose.ui.geometry.Offset(size.width*i/3, size.height), strokeWidth = 1f); drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, size.height*i/3), androidx.compose.ui.geometry.Offset(size.width, size.height*i/3), strokeWidth = 1f) }
    }
    val detected = outline
    if (detected != null && detectEnabled) Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Detected document boundary" }) {
     val path = androidx.compose.ui.graphics.Path().apply { moveTo(detected[0].x*size.width,detected[0].y*size.height); detected.drop(1).forEach { lineTo(it.x*size.width,it.y*size.height) };close() }
     drawPath(path,CameraAccent.copy(alpha=0.15f));drawPath(path,CameraAccent,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
     detected.forEach { drawCircle(CameraAccent,4.dp.toPx(),androidx.compose.ui.geometry.Offset(it.x*size.width,it.y*size.height)) }
    }
    if (detected == null) Box((if (idGuide) Modifier.align(Alignment.Center).fillMaxWidth(0.78f).aspectRatio(1.59f) else Modifier.fillMaxSize()).border(1.dp, CameraAccent.copy(alpha = 0.7f)))
   }
   if (!granted) Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    WebIcon("camera", Modifier.size(42.dp), CameraAccent)
    Text("Allow camera access to scan documents", color = Color.White, textAlign = TextAlign.Center)
    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, colors = ButtonDefaults.buttonColors(containerColor = CameraAccent, contentColor = Color.Black)) { Text("Allow camera") }
    if (requested) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings", color = CameraAccent) }
   } else if (error.isNotEmpty()) Column(Modifier.align(Alignment.Center).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(error, color = Color.White, textAlign = TextAlign.Center)
    TextButton(onClick = { retry++ }) { Text("Retry camera", color = CameraAccent) }
   } else if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = CameraAccent)
   if(capturing) {
    val transition=rememberInfiniteTransition(label="Scanning")
    val position by transition.animateFloat(0f,1f,infiniteRepeatable(tween(850,easing=LinearEasing),RepeatMode.Reverse),label="Scan line")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.35f)),contentAlignment=Alignment.Center) {
     capturePreview?.let { Image(it.asImageBitmap(),"Scanning captured image",Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
     Canvas(Modifier.fillMaxSize()) {
      val y=size.height*position
      drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent,CameraAccent.copy(alpha=.3f))),topLeft=androidx.compose.ui.geometry.Offset(0f,(y-90).coerceAtLeast(0f)),size=androidx.compose.ui.geometry.Size(size.width,90f))
      drawLine(CameraAccent,androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),3.dp.toPx())
     }
    }
   }
   Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {

    Row(Modifier.background(Color(0xDD181818), CircleShape).padding(3.dp)) {
     listOf(false to "Single", true to "Batch").forEach { (value, title) ->
      Text(title, Modifier.clip(CircleShape).background(if (batch == value) Color(0xFF555555) else Color.Transparent).clickable(enabled = !capturing && !retaking) { batch = value }.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 13.sp)
     }
    }
   }
   if(panel=="filters")Box(Modifier.align(Alignment.TopCenter)){CameraFilterMenu(selectedFilter){selectedFilter=it;onFilter(false)}}
   if(panel=="resolution")Box(Modifier.align(Alignment.TopCenter)){CameraResolutionMenu(resolution){resolution=it;panel=""}}
   if(panel=="settings")Box(Modifier.align(Alignment.Center)) {
    CameraSettingsMenu(autoCapture,grid,volumeCapture,autoCrop,
     {autoCapture=it},{grid=it},{volumeCapture=it},{autoCrop=it},
     {panel=""},{panel="";message="Tap to focus and pinch to zoom. Capture dimensions depend on the camera. Text orientation is not available yet."})
   }
  }
  Column(Modifier.fillMaxWidth().heightIn(min = 210.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf("Extract Text", "ID Cards", "Sign", "Scan", "To Word", "Question Set").forEach { mode ->
     val selected = (mode == "Scan" && !idGuide) || (mode == "ID Cards" && idGuide)
     Column(Modifier.clickable(enabled = !capturing) { when(mode) { "Scan" -> idGuide = false; "ID Cards" -> idGuide = true; else -> message = "$mode is not available yet. Capture a document to continue." } }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.width(28.dp).height(2.dp).background(if (selected) CameraAccent else Color.Transparent))
      Text(mode, color = if (selected) CameraAccent else if (mode == "Scan" || mode == "ID Cards") Color.White else Color.Gray, fontSize = 12.sp)
     }
    }
   }
   Row(Modifier.fillMaxWidth().padding(top = 22.dp, start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { IconButton(onClick = onImport, enabled = !capturing) { CameraNineDots() } }
    Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) { Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, if (ready) CameraAccent else Color.Gray, CircleShape).padding(6.dp).clip(CircleShape).background(if (ready) Color.White else Color.DarkGray).clickable(enabled = ready && !capturing, onClick = ::takePhoto).semantics { contentDescription = "Capture document" }) }
    Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) { IconButton(onClick = onImport, enabled = !capturing) { WebIcon("images", Modifier.size(24.dp), Color.White, "Open gallery") } }
    Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) { IconButton(onClick = { front = !front }, enabled = switchAvailable && !capturing && granted) { WebIcon("switch", Modifier.size(26.dp), if (switchAvailable) Color.White else Color.Gray, "Switch camera") } }
   }
  }
 }
 if (message.isNotEmpty()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Camera") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("Got it") } })
}
