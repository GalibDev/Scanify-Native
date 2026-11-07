package com.scanify.nativeapp

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Purple = Color(0xFF7C3AED)
private val Background = Color(0xFFF7F5FC)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = Background, surface = Color.White)) {
    Scanify()
   }
  }
 }

 @Composable
 private fun Scanify() {
  val store = remember { DocumentStore(this) }
  var documents by remember { mutableStateOf<List<ScanDocument>>(emptyList()) }
  var tab by rememberSaveable { mutableStateOf("Home") }
  var draft by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
  var cameraPath by rememberSaveable { mutableStateOf("") }
  var exportId by rememberSaveable { mutableStateOf("") }
  var selectedId by rememberSaveable { mutableStateOf("") }
  var name by rememberSaveable { mutableStateOf("Scan ${DateFormat.getDateInstance().format(Date())}") }
  var rotation by rememberSaveable { mutableIntStateOf(0) }
  var grayscale by rememberSaveable { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var search by rememberSaveable { mutableStateOf("") }
  var rename by remember { mutableStateOf(false) }
  var renameText by remember { mutableStateOf("") }
  var deleting by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  fun notice(message: String) { scope.launch { snackbar.showSnackbar(message) } }
  suspend fun refresh() { documents = withContext(Dispatchers.IO) { store.list() } }
  LaunchedEffect(Unit) { try { refresh() } catch (_: Exception) { notice("Could not read the document library.") } }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
   if (success && cameraPath.isNotEmpty()) {
    val file = File(cameraPath)
    draft = ArrayList(draft + FileProvider.getUriForFile(this, "$packageName.files", file).toString())
   } else if (cameraPath.isNotEmpty()) File(cameraPath).delete()
  }
  val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
   if (uris.isNotEmpty()) {
    uris.forEach { try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { } }
    draft = ArrayList(draft + uris.map(Uri::toString))
   }
  }
  val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
   val doc = documents.find { it.id == exportId }
   exportId = ""
   if (uri != null && doc != null) scope.launch {
    busy = true
    try {
     withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { output -> store.file(doc).inputStream().use { it.copyTo(output) } } ?: error("Cannot open destination") }
     notice("PDF saved to your chosen folder.")
    } catch (_: Exception) { notice("Could not save this PDF. Please try again.") }
    finally { busy = false }
   }
  }
  fun capture() {
   try {
    val dir = File(cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("scan-", ".jpg", dir)
    cameraPath = file.absolutePath
    camera.launch(FileProvider.getUriForFile(this, "$packageName.files", file))
   } catch (_: Exception) { notice("No camera app is available. You can import an image instead.") }
  }
  fun share(doc: ScanDocument) {
   try {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", store.file(doc))
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
     type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, doc.name)
     clipData = ClipData.newRawUri(doc.name, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share ${doc.name}"))
   } catch (_: Exception) { notice("No app is available to share this PDF.") }
  }
  val selected = documents.find { it.id == selectedId }
  BackHandler(enabled = !busy && (draft.isNotEmpty() || selected != null || tab != "Home")) {
   when { draft.isNotEmpty() -> draft = arrayListOf(); selected != null -> selectedId = ""; else -> tab = "Home" }
  }
  Scaffold(
   containerColor = Background,
   snackbarHost = { SnackbarHost(snackbar) },
   bottomBar = {
    if (draft.isEmpty() && selected == null) NavigationBar(containerColor = Color.White) {
     listOf("Home", "Documents", "Tools", "Settings").forEach { item ->
      NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(when(item) { "Home" -> "⌂"; "Documents" -> "▤"; "Tools" -> "▦"; else -> "⚙" }) }, label = { Text(item) })
     }
    }
   }
  ) { padding ->
   Box(Modifier.fillMaxSize().padding(padding)) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
     item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
       Column { Text("Scanify.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("PRIVATE · ON YOUR DEVICE", style = MaterialTheme.typography.labelSmall, color = Purple) }
       if (selected != null || draft.isNotEmpty()) TextButton(enabled = !busy, onClick = { selectedId = ""; draft = arrayListOf() }) { Text("Close") }
      }
     }
     when {
      draft.isNotEmpty() -> {
       item { SectionTitle("Review your scan", "${draft.size} page(s) · A4 PDF") }
       item { ImagePreview(store, Uri.parse(draft.first()), rotation, grayscale) }
       item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy) }
       item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { rotation = (rotation + 90) % 360 }) { Text("Rotate all") }
        FilterChip(selected = grayscale, enabled = !busy, onClick = { grayscale = !grayscale }, label = { Text("Grayscale") })
       } }
       items(draft.indices.toList(), key = { it }) { index ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
         Text("Page ${index + 1}")
         TextButton(enabled = !busy, onClick = { draft = ArrayList(draft.filterIndexed { i, _ -> i != index }) }) { Text("Remove") }
        }
       }
       item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = ::capture) { Text("Add photo") }
        OutlinedButton(enabled = !busy, onClick = { images.launch(arrayOf("image/*")) }) { Text("Add images") }
       } }
       item { Button(enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), onClick = {
        scope.launch {
         busy = true
         try {
          val doc = withContext(Dispatchers.IO) { store.create(draft.map(Uri::parse), name, rotation, grayscale) }
          draft.forEach { value -> val uri = Uri.parse(value); if (uri.authority == "$packageName.files") { val fileName = uri.lastPathSegment; if (fileName != null) File(cacheDir, "camera/$fileName").delete() } }
          draft = arrayListOf(); rotation = 0; grayscale = false
          refresh(); selectedId = doc.id; notice("PDF created and saved in Documents.")
         } catch (_: Exception) { notice("Could not create PDF. Try fewer or smaller images.") }
         finally { busy = false }
        }
       }) { Text(if (busy) "Creating PDF…" else "Create PDF") } }
      }
      selected != null -> {
       item { SectionTitle(selected.name, "${selected.pages} page(s) · Stored locally") }
       item { PdfPreview(store.file(selected)) }
       item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = { exportId = selected.id; export.launch("${selected.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.pdf") }) { Text("Save PDF") }
        OutlinedButton(onClick = { share(selected) }) { Text("Share") }
       } }
       item { Row {
        TextButton(onClick = { renameText = selected.name; rename = true }) { Text("Rename") }
        TextButton(onClick = { deleting = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
       } }
      }
      tab == "Settings" -> {
       item { SectionTitle("Your workspace", "Native Android · version 0.1.0") }
       item { InfoCard("Private by default", "Scans and PDFs stay in this app's private storage. Export important documents before uninstalling.") }
       item { InfoCard("Available now", "Camera capture, image import, rotation, grayscale, multipage PDF creation, library, rename, delete, export and sharing.") }
       item { InfoCard("Next milestones", "In-app camera, crop and perspective correction, OCR, PDF import and advanced editing. These features are not included yet.") }
      }
      else -> {
       if (tab != "Documents") {
        item { SectionTitle(if (tab == "Tools") "Native tools" else "A little space for your big ideas", "Scan, organize and share your documents.") }
        item { Card(colors = CardDefaults.cardColors(containerColor = Purple), shape = RoundedCornerShape(24.dp)) {
         Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Paper to PDF.\nAll on your phone.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Button(onClick = ::capture, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple), modifier = Modifier.fillMaxWidth()) { Text("Scan a document") }
          OutlinedButton(onClick = { images.launch(arrayOf("image/*")) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), modifier = Modifier.fillMaxWidth()) { Text("Import images") }
         }
        } }
        if (tab == "Tools") item { InfoCard("Image to PDF", "Import several images, rotate or apply grayscale, then create one A4 PDF. Crop, OCR and PDF editing are planned next.") }
       }
       item { SectionTitle(if (tab == "Documents") "Documents" else "Recent documents", "${documents.size} document(s) · Local storage") }
       item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search your documents") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) }
       val visible = documents.filter { it.name.contains(search, ignoreCase = true) }
       if (visible.isEmpty()) item { InfoCard(if (documents.isEmpty()) "Your first scan starts here" else "No matching documents", if (documents.isEmpty()) "Take a photo or import images to create your first native PDF." else "Try another search.") }
       items(visible, key = { it.id }) { doc ->
        Card(Modifier.fillMaxWidth().clickable { selectedId = doc.id }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
         Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Surface(color = Color(0xFFEDE5FF), shape = RoundedCornerShape(12.dp)) { Text("PDF", Modifier.padding(12.dp), color = Purple, fontWeight = FontWeight.Bold) }
          Column(Modifier.weight(1f)) { Text(doc.name, fontWeight = FontWeight.SemiBold); Text("${doc.pages} page(s) · ${DateFormat.getDateInstance().format(Date(doc.created))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
          Text("›", color = Purple, style = MaterialTheme.typography.headlineSmall)
         }
        }
       }
       if (tab == "Documents") item { Button(onClick = { images.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import images") } }
      }
     }
    }
    if (busy) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.65f)).clickable { }, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
   }
  }
  if (rename && selected != null) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename document") }, text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) }, confirmButton = {
   TextButton(enabled = renameText.isNotBlank(), onClick = { scope.launch { try { withContext(Dispatchers.IO) { store.rename(selected, renameText) }; refresh(); rename = false } catch (_: Exception) { notice("Could not rename document.") } } }) { Text("Save") }
  }, dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } })
  if (deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete this document?") }, text = { Text("This removes the local PDF permanently. Exported copies are kept.") }, confirmButton = {
   TextButton(onClick = { scope.launch { try { withContext(Dispatchers.IO) { store.delete(selected) }; selectedId = ""; deleting = false; refresh() } catch (_: Exception) { notice("Could not delete document.") } } }) { Text("Delete") }
  }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
 }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
 Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
}

@Composable
private fun InfoCard(title: String, body: String) {
 Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
  Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = Color.Gray) }
 }
}

@Composable
private fun ImagePreview(store: DocumentStore, uri: Uri, rotation: Int, grayscale: Boolean) {
 val bitmap by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { runCatching { store.bitmap(uri) }.getOrNull() } }
 Card(Modifier.fillMaxWidth().height(300.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
   bitmap?.let { source ->
    val matrix = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(if (grayscale) 0f else 1f) }
    Image(source.asImageBitmap(), "First page preview", Modifier.fillMaxSize().padding(12.dp).rotate(rotation.toFloat()), contentScale = ContentScale.Fit, colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix))
   } ?: Text("Loading image…")
  }
 }
}

@Composable
private fun PdfPreview(file: File) {
 var page by remember(file) { mutableIntStateOf(0) }
 var count by remember(file) { mutableIntStateOf(1) }
 val bitmap by produceState<Bitmap?>(null, file, page) {
  value = withContext(Dispatchers.IO) { runCatching {
   PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    count = renderer.pageCount
    renderer.openPage(page).use { pdfPage ->
     Bitmap.createBitmap(850, 1202, Bitmap.Config.ARGB_8888).also { image -> image.eraseColor(android.graphics.Color.WHITE); pdfPage.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
    }
   }
  }.getOrNull() }
 }
 Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
  Card(Modifier.fillMaxWidth().height(400.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { bitmap?.let { Image(it.asImageBitmap(), "PDF page ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: Text("Loading preview…") } }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
   TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous") }
   Text("${page + 1} / $count")
   TextButton(enabled = page + 1 < count, onClick = { page++ }) { Text("Next") }
  }
 }
}
