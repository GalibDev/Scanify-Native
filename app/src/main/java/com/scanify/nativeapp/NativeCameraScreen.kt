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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val CameraAccent = Color(0xFF27E5D3)

@Composable
internal fun NativeCameraScreen(
 owner: LifecycleOwner, pageCount: Int, retaking: Boolean, grayscale: Boolean,
 onFilter: (Boolean) -> Unit, onCaptured: (String, Boolean) -> Unit,
 onClose: () -> Unit, onImport: () -> Unit
) {
 val context = LocalContext.current
 val executor = remember { ContextCompat.getMainExecutor(context) }
 var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
 var requested by rememberSaveable { mutableStateOf(false) }
 val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; requested = true }
 var front by rememberSaveable { mutableStateOf(false) }
 var hd by rememberSaveable { mutableStateOf(true) }
 var batch by rememberSaveable { mutableStateOf(false) }
 var idGuide by rememberSaveable { mutableStateOf(false) }
 var grid by rememberSaveable { mutableStateOf(false) }
 var panel by remember { mutableStateOf("") }
 var message by remember { mutableStateOf("") }
 var error by remember { mutableStateOf("") }
 var ready by remember { mutableStateOf(false) }
 var flashAvailable by remember { mutableStateOf(false) }
 var switchAvailable by remember { mutableStateOf(false) }
 var torch by remember { mutableStateOf(false) }
 var capturing by remember { mutableStateOf(false) }
 var retry by remember { mutableIntStateOf(0) }
 val active = remember { AtomicBoolean(true) }
 val latestCapture by rememberUpdatedState(onCaptured)
 val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
 val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } }
 LaunchedEffect(Unit) { if (!granted && !requested) permission.launch(Manifest.permission.CAMERA) }
 DisposableEffect(owner) {
  val observer = LifecycleEventObserver { _, event ->
   if (event == Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }
  owner.lifecycle.addObserver(observer)
  onDispose { active.set(false); owner.lifecycle.removeObserver(observer) }
 }
 DisposableEffect(granted, front, hd, retry) {
  var disposed = false
  ready = false; error = ""; torch = false; flashAvailable = false; switchAvailable = false
  val streamObserver = Observer<PreviewView.StreamState> { state ->
   ready = state == PreviewView.StreamState.STREAMING
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
      controller.imageCaptureMode = if (hd) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
      controller.imageCaptureResolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(if (hd) ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY else ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()
      controller.bindToLifecycle(owner)
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
  capturing = true; error = ""
  val finish = !batch || retaking
  var output: File? = null
  try {
   val dir = File(context.cacheDir, "camera").apply { mkdirs() }
   val file = File.createTempFile("scan-", ".jpg", dir)
   output = file
   controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
     if (!active.get()) { file.delete(); return }
     capturing = false
     val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
     latestCapture(uri, finish)
    }
    override fun onError(exception: ImageCaptureException) {
     file.delete()
     if (active.get()) { capturing = false; error = "Photo capture failed. Please try again." }
    }
   })
  } catch (_: Exception) { output?.delete(); capturing = false; error = "Could not capture a photo. Please try again." }
 }
 Column(Modifier.fillMaxSize().background(Color.Black)) {
  Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = onClose, enabled = !capturing) { WebIcon("close", color = Color.White, description = "Close camera") }
   if (pageCount > 0) TextButton(onClick = onClose, enabled = !capturing) { Text("$pageCount ✓", color = CameraAccent, fontSize = 13.sp) }
   Spacer(Modifier.weight(1f))
   IconButton(enabled = flashAvailable && ready && !capturing, onClick = {
    val next = !torch
    val future = controller.enableTorch(next)
    future.addListener({ if (active.get()) try { future.get(); torch = next } catch (_: Exception) { message = "Flashlight is unavailable on this camera." } }, executor)
   }) { WebIcon("flash", color = if (torch) CameraAccent else if (flashAvailable) Color.White else Color.Gray, description = "Flashlight") }
   TextButton(enabled = !capturing, onClick = { panel = if (panel == "resolution") "" else "resolution" }) { Text(if (hd) "HD" else "STD", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
   IconButton(enabled = !capturing, onClick = { panel = if (panel == "filters") "" else "filters" }) { WebIcon("layers", color = if (grayscale) CameraAccent else Color.White, description = "Camera filters") }
   IconButton(enabled = !capturing, onClick = { panel = if (panel == "settings") "" else "settings" }) { WebIcon("settings", color = Color.White, description = "Camera settings") }
  }
  Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF111111))) {
   if (granted) AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
   if (ready) {
    if (grid) Canvas(Modifier.fillMaxSize()) {
     for (i in 1..2) { drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(size.width*i/3, 0f), androidx.compose.ui.geometry.Offset(size.width*i/3, size.height), strokeWidth = 1f); drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, size.height*i/3), androidx.compose.ui.geometry.Offset(size.width, size.height*i/3), strokeWidth = 1f) }
    }
    Box((if (idGuide) Modifier.align(Alignment.Center).fillMaxWidth(0.78f).aspectRatio(1.59f) else Modifier.fillMaxSize()).border(1.dp, CameraAccent.copy(alpha = 0.7f)))
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
   if (capturing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CameraAccent) }
   Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (ready) Text(if (capturing) "Saving photo…" else if (idGuide) "ID framing guide · manual capture" else "Position your document · tap to focus", color = Color.White, fontSize = 11.sp)
    Row(Modifier.background(Color(0xDD181818), CircleShape).padding(3.dp)) {
     listOf(false to "Single", true to "Batch").forEach { (value, title) ->
      Text(title, Modifier.clip(CircleShape).background(if (batch == value) Color(0xFF555555) else Color.Transparent).clickable(enabled = !capturing && !retaking) { batch = value }.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 13.sp)
     }
    }
   }
   if (panel.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color(0xFF191919), shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
     when (panel) {
      "resolution" -> {
       Text("Capture size", color = Color.White, fontWeight = FontWeight.Bold)
       Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(true to "HD · Full size", false to "Standard · 1280px").forEach { (value, label) -> OutlinedButton(onClick = { hd = value; panel = "" }, border = BorderStroke(1.dp, if (hd == value) CameraAccent else Color.Gray)) { Text(label, color = if (hd == value) CameraAccent else Color.White, fontSize = 11.sp) } } }
       Text("Camera capabilities determine the actual resolution.", color = Color.LightGray, fontSize = 11.sp)
      }
      "filters" -> {
       Text("Camera filters", color = Color.White, fontWeight = FontWeight.Bold)
       Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(false to "Original", true to "Grayscale").forEach { (value, label) -> OutlinedButton(onClick = { onFilter(value); panel = "" }, border = BorderStroke(1.dp, if (grayscale == value) CameraAccent else Color.Gray)) { Text(label, color = if (grayscale == value) CameraAccent else Color.White) } } }
       Text("Filters apply in review and PDF. Live preview remains in color.", color = Color.LightGray, fontSize = 11.sp)
      }
      else -> {
       Row(verticalAlignment = Alignment.CenterVertically) { Text("Grid lines", Modifier.weight(1f), color = Color.White); Switch(checked = grid, onCheckedChange = { grid = it }, colors = SwitchDefaults.colors(checkedTrackColor = CameraAccent)) }
       Text("Manual capture · tap to focus · pinch to zoom. Automatic detection and auto-crop are not available yet.", color = Color.LightGray, fontSize = 12.sp, lineHeight = 18.sp)
      }
     }
     TextButton(onClick = { panel = "" }, modifier = Modifier.align(Alignment.End)) { Text("Done", color = CameraAccent) }
    }
   }
  }
  Column(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { IconButton(onClick = onImport, enabled = !capturing) { WebIcon("grid", Modifier.size(25.dp), Color.White, "Import images") } }
    Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) { Box(Modifier.size(68.dp).clip(CircleShape).border(3.dp, if (ready) CameraAccent else Color.Gray, CircleShape).padding(6.dp).clip(CircleShape).background(if (ready) Color.White else Color.DarkGray).clickable(enabled = ready && !capturing, onClick = ::takePhoto).semantics { contentDescription = "Capture document" }) }
    Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) { IconButton(onClick = onImport, enabled = !capturing) { WebIcon("images", Modifier.size(24.dp), Color.White, "Open gallery") } }
    Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) { IconButton(onClick = { front = !front }, enabled = switchAvailable && !capturing && granted) { WebIcon("switch", Modifier.size(26.dp), if (switchAvailable) Color.White else Color.Gray, "Switch camera") } }
   }
  }
 }
 if (message.isNotEmpty()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Camera") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("Got it") } })
}
