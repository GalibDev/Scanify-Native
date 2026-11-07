package com.scanify.nativeapp

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val Background = WebBackground

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = Background, surface = Color.White, onSurface = WebText, onBackground = WebText)) {
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
  var retakeIndex by rememberSaveable { mutableIntStateOf(-1) }
  var category by rememberSaveable { mutableStateOf("All") }
  var oldestFirst by rememberSaveable { mutableStateOf(false) }
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
  var unavailable by remember { mutableStateOf("") }
  var discard by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  LaunchedEffect(tab) { listState.scrollToItem(0) }
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  fun notice(message: String) { scope.launch { snackbar.showSnackbar(message) } }
  suspend fun refresh() { documents = withContext(Dispatchers.IO) { store.list() } }
  LaunchedEffect(Unit) { try { refresh() } catch (_: Exception) { notice("Could not read the document library.") } }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
   if (success && cameraPath.isNotEmpty()) {
    val file = File(cameraPath)
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file).toString()
    draft = if (retakeIndex in draft.indices) ArrayList(draft.mapIndexed { index, value -> if (index == retakeIndex) uri else value }) else ArrayList(draft + uri)
   } else if (cameraPath.isNotEmpty()) File(cameraPath).delete()
   retakeIndex = -1
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
   when { draft.isNotEmpty() -> discard = true; selected != null -> selectedId = ""; else -> tab = "Home" }
  }
  fun saveScan() {
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
  }
  fun savePdf(doc: ScanDocument) {
   exportId = doc.id
   export.launch(doc.name.replace('/', '_').replace('\\', '_') + ".pdf")
  }
  Scaffold(
   containerColor = Background,
   snackbarHost = { SnackbarHost(snackbar) },
   bottomBar = {
    if (draft.isEmpty() && selected == null) WebNavigation(tab, { tab = it }, ::capture)
   }
  ) { padding ->
   Box(Modifier.fillMaxSize().padding(padding)) {
    if (draft.isNotEmpty()) ScanReviewScreen(
     store, draft, name, rotation, grayscale, busy,
     onName = { name = it }, onBack = { discard = true },
     onRemove = { index -> draft = ArrayList(draft.filterIndexed { i, _ -> i != index }) },
     onRetake = { index -> retakeIndex = index; capture() },
     onRotate = { rotation = (rotation + 270) % 360 }, onFilter = { grayscale = it },
     onImport = { images.launch(arrayOf("image/*")) }, onSave = ::saveScan, onUnavailable = { unavailable = it }
    ) else if (selected != null) DocumentReaderScreen(
     selected, store.file(selected), onBack = { selectedId = "" },
     onRename = { renameText = selected.name; rename = true }, onSave = { savePdf(selected) },
     onShare = { share(selected) }, onDelete = { deleting = true }, onUnavailable = { unavailable = it }
    ) else
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
     item {
      WebHeader({ tab = "Settings" }, { notice("No new notifications.") })
     }
     when {
      tab == "Settings" -> {
       item { WorkspaceSettings(documents.size) { unavailable = "Dark appearance" } }
      }
      else -> {
       if (tab == "Home") item { WebSearch(search, { search = it }) }
       if (tab == "Documents") {
        item { SectionTitle("My Documents", "Manage, organize, and access your files.") }
        item { WebSearch(search, { search = it }, "Search documents and folders…") }
        item { LibraryCategories(category) { if (it == "All" || it == "PDF" || it == "Images") category = it else unavailable = it } }
        item { LibraryActions(::capture, { images.launch(arrayOf("image/*")) }, { unavailable = it }) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
         TextButton(onClick = { oldestFirst = !oldestFirst }) { Text(if (oldestFirst) "Sort by: Oldest first" else "Sort by: Recent", color = WebMuted) }
         WebIcon("grid", Modifier.size(20.dp), WebMuted)
        } }
       }
       if (tab != "Documents") {
        item { WebSection(if (tab == "Tools") "All Tools" else "Quick Tools", if (tab == "Tools") "Documents" else "View All Tools", { tab = if (tab == "Tools") "Documents" else "Tools" }) }
        item { WebTools { tool ->
         when (tool) {
          "Smart Scan" -> capture()
          "Import Images" -> images.launch(arrayOf("image/*"))
          "PDF Tools", "All Tools" -> tab = "Tools"
          else -> unavailable = tool
         }
        } }
        item { Spacer(Modifier.height(4.dp)); WebScanButton(::capture) }
        if (tab == "Tools") item { InfoCard("Image to PDF", "Import images to create a PDF. Other native tools are being built.") }
       }
       val visible = documents.filter { it.name.contains(search, ignoreCase = true) && !(tab == "Documents" && category == "Images") }.let { if (oldestFirst && tab == "Documents") it.reversed() else it }
       item { WebSection(if (search.isNotBlank()) "Search Results" else if (tab == "Documents") "All Documents" else "Recent Documents", if (tab == "Documents") "Import" else "View All", { if (tab == "Documents") images.launch(arrayOf("image/*")) else tab = "Documents" }, visible.size) }
       if (visible.isEmpty()) item { WebEmpty({ images.launch(arrayOf("image/*")) }, search.isNotBlank()) }
       items(if (tab == "Home" && search.isBlank()) visible.take(3) else visible, key = { it.id }) { doc ->
        LibraryDocument(doc) { selectedId = doc.id }
       }

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
  if (unavailable.isNotEmpty()) AlertDialog(
   onDismissRequest = { unavailable = "" },
   title = { Text(unavailable) },
   text = { Text("This tool is not available in the native app yet. You can scan documents or import images to create a PDF.") },
   confirmButton = { TextButton(onClick = { unavailable = "" }) { Text("Got it") } }
  )
  if (discard) AlertDialog(
   onDismissRequest = { discard = false }, title = { Text("Discard this scan?") },
   text = { Text("Your unsaved pages will be removed. Saved documents are kept.") },
   confirmButton = { TextButton(onClick = { draft = arrayListOf(); rotation = 0; grayscale = false; discard = false }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
   dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } }
  )
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
