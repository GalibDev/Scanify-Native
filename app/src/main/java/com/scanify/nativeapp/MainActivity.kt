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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
  val documentsState = rememberSaveableStateHolder()
  LaunchedEffect(tab) { if(tab != "Documents") documentsState.removeState("library") }
  val toolsState = rememberSaveableStateHolder()
  LaunchedEffect(tab) { if(tab != "Tools") toolsState.removeState("catalog") }
  var draft by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
  var retakeIndex by rememberSaveable { mutableIntStateOf(-1) }
  var cameraOpen by rememberSaveable { mutableStateOf(false) }
  var cropIndex by rememberSaveable { mutableIntStateOf(-1) }
  var reviewPage by rememberSaveable { mutableIntStateOf(0) }
  DisposableEffect(cameraOpen, cropIndex) {
   val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
   bars.isAppearanceLightStatusBars = !cameraOpen && cropIndex < 0
   bars.isAppearanceLightNavigationBars = !cameraOpen && cropIndex < 0
   onDispose { }
  }
  var exportId by rememberSaveable { mutableStateOf("") }
  var shareId by rememberSaveable { mutableStateOf("") }
  var shareStatus by rememberSaveable { mutableStateOf("") }
  var selectedId by rememberSaveable { mutableStateOf("") }
  var editingId by rememberSaveable { mutableStateOf("") }
  var addPage by remember { mutableStateOf(false) }
  var morePage by rememberSaveable { mutableIntStateOf(-1) }
  var showDocumentInfo by rememberSaveable { mutableStateOf(false) }
  var photoDocumentId by rememberSaveable { mutableStateOf("") }
  var photoPage by rememberSaveable { mutableIntStateOf(-1) }
  var name by rememberSaveable { mutableStateOf("Scan ${DateFormat.getDateInstance().format(Date())}") }
  var adjustments by rememberSaveable { mutableStateOf(arrayListOf<PageAdjustment>()) }
  var grayscale by rememberSaveable { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var search by rememberSaveable { mutableStateOf("") }
  var rename by remember { mutableStateOf(false) }
  var actionId by rememberSaveable { mutableStateOf("") }
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

  val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
   if (uris.isNotEmpty()) {
    uris.forEach { try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { } }
    val imported = uris.map(Uri::toString)
    reviewPage=if(retakeIndex in draft.indices)retakeIndex else draft.size
    adjustments=if(retakeIndex in draft.indices)ArrayList(adjustments.mapIndexed{index,value->if(index==retakeIndex)PageAdjustment() else value}+List(imported.size-1){PageAdjustment()}) else ArrayList(adjustments+List(imported.size){PageAdjustment()})
    draft = if (retakeIndex in draft.indices) ArrayList(draft.mapIndexed { index, value -> if (index == retakeIndex) imported.first() else value } + imported.drop(1)) else ArrayList(draft + imported)
    cameraOpen = false; retakeIndex = -1
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
     if(shareId==doc.id)shareStatus="PDF saved to your chosen folder."
    } catch (_: Exception) { notice("Could not save this PDF. Please try again.");if(shareId==doc.id)shareStatus="Could not save this PDF. Please try again." }
    finally { busy = false }
   }
  }
  fun capture() { cameraOpen = true }
  fun share(doc: ScanDocument) { shareStatus="";shareId=doc.id }
  fun exportImages(doc:ScanDocument,photos:Boolean,longImage:Boolean=false,pageIndex:Int?=null) {
   if(busy)return
   busy=true;shareStatus=if(longImage)"Preparing long image…" else "Preparing page images…"
   scope.launch {
    var rendered:List<File> = emptyList()
    try {
     rendered=withContext(Dispatchers.IO){
      if(longImage) listOf(DocumentImageExport.renderLongImage(store.file(doc),cacheDir,doc.name))
      else DocumentImageExport.render(store.file(doc),cacheDir,doc.name,pageIndex)
     }
     if(photos) {
      val count=withContext(Dispatchers.IO){DocumentImageExport.savePhotos(this@MainActivity,rendered)}
      shareStatus="$count page image(s) saved to Pictures/Scanify."
      if(shareId!=doc.id)notice(shareStatus)
     } else {
      val uris=ArrayList(rendered.map{FileProvider.getUriForFile(this@MainActivity,"$packageName.files",it)})
      val intent=Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
       type="image/jpeg";putExtra(Intent.EXTRA_SUBJECT,doc.name)
       if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
       clipData=ClipData.newRawUri(doc.name,uris.first()).apply{uris.drop(1).forEach{addItem(ClipData.Item(it))}}
       addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      startActivity(Intent.createChooser(intent,if(longImage)"Share ${doc.name} as a long image" else "Share ${doc.name} as images"))
      shareStatus=if(longImage)"Choose an app to share the long image." else "Choose an app to share ${uris.size} page image(s)."
     }
    } catch(_:Exception) {
     shareStatus="Could not export page images. Check storage and try again."
     if(shareId!=doc.id)notice(shareStatus)
    }
    finally {
     if(photos)withContext(Dispatchers.IO){rendered.firstOrNull()?.parentFile?.deleteRecursively()}
     busy=false
    }
   }
  }
  val photoPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
   val doc=documents.find{it.id==photoDocumentId}
   val pageIndex=photoPage.takeIf{it>=0}
   photoDocumentId="";photoPage=-1
   if(granted && doc!=null)exportImages(doc,true,pageIndex=pageIndex)
   else if(!granted){
    shareStatus="Photo access was denied. You can still share images to another app."
    if(shareId.isEmpty())notice(shareStatus)
   }
  }
  fun savePhotos(doc:ScanDocument,pageIndex:Int?=null) {
   if(android.os.Build.VERSION.SDK_INT<29 && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
    photoDocumentId=doc.id;photoPage=pageIndex ?: -1
    photoPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
   } else exportImages(doc,true,pageIndex=pageIndex)
  }
  fun sendPdf(doc: ScanDocument):String? {
   return try {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", store.file(doc))
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
     type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, doc.name)
     clipData = ClipData.newRawUri(doc.name, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share ${doc.name}"))
    null
   } catch (_: Exception) { "No app is available to share this PDF." }
  }
  val selected = documents.find { it.id == selectedId }
  val shareDoc = documents.find { it.id == shareId }
  val actionDoc = documents.find { it.id == actionId } ?: selected
  BackHandler(enabled = !busy && (draft.isNotEmpty() || selected != null || tab != "Home")) {
   when { draft.isNotEmpty() -> discard = true; selected != null -> selectedId = ""; else -> tab = "Home" }
  }
  fun saveScan() {
        scope.launch {
         busy = true
         try {
          val existing=documents.find{it.id==editingId}
          check(editingId.isEmpty()||existing!=null) { "Original document is unavailable" }
          val doc = withContext(Dispatchers.IO) { if(existing!=null)store.replace(existing,draft.map(Uri::parse),name,0,false,adjustments=adjustments) else store.create(draft.map(Uri::parse), name, 0, false,adjustments=adjustments) }
          draft.forEach { value -> val uri = Uri.parse(value); if (uri.authority == "$packageName.files") { val fileName = uri.lastPathSegment; if (fileName != null) File(cacheDir, "camera/$fileName").delete() } }
          draft = arrayListOf(); adjustments = arrayListOf(); grayscale = false; reviewPage = 0;editingId=""
          refresh(); selectedId = doc.id; notice(if(existing!=null)"Document updated." else "PDF created and saved in Documents.")
         } catch (_: Exception) { notice("Could not create PDF. Try fewer or smaller images.") }
         finally { busy = false }
        }
  }
  fun savePdf(doc: ScanDocument) {
   exportId = doc.id
   export.launch(doc.name.replace('/', '_').replace('\\', '_') + ".pdf")
  }
  fun renameDocument(doc: ScanDocument) { actionId=doc.id;renameText=doc.name;rename=true }
  fun openDocumentMore(doc:ScanDocument) { selectedId=doc.id;morePage=0 }
  fun deleteDocument(doc: ScanDocument) { actionId=doc.id;deleting=true }
  fun editDocument(doc:ScanDocument,source:String="") {
   if(busy)return
   busy=true;addPage=false
   scope.launch {
    try {
     val pages=withContext(Dispatchers.IO){store.pageSources(doc)}
     draft=ArrayList(pages);adjustments=ArrayList(List(pages.size){PageAdjustment()});editingId=doc.id;name=doc.name;grayscale=false;reviewPage=0;retakeIndex=-1
     if(source=="camera")capture() else if(source=="images")images.launch(arrayOf("image/*"))
    } catch(_:Exception){notice("Could not open pages for editing. The saved document is unchanged.")}
    finally{busy=false}
   }
  }
  Scaffold(
   containerColor = if (cameraOpen || cropIndex >= 0) Color.Black else if (selected != null) Color.White else Background,
   snackbarHost = { SnackbarHost(snackbar) },
   bottomBar = {
    if (!cameraOpen && draft.isEmpty() && selected == null) WebNavigation(tab, { tab = it }, ::capture)
   }
  ) { padding ->
   Box(Modifier.fillMaxSize().padding(padding)) {
    if (cropIndex in draft.indices) CropScreen(store, draft[cropIndex],
     onCancel = { cropIndex = -1 }, onApply = { result ->
      draft = ArrayList(draft.mapIndexed { index, value -> if (index == cropIndex) result else value })
      cropIndex = -1
     }
    ) else if (cameraOpen) NativeCameraScreen(
     owner = this@MainActivity, pageCount = draft.size, retaking = retakeIndex >= 0, grayscale = grayscale,
     onFilter = { grayscale = it }, onCaptured = { uri, finish, detected ->
      val capturedIndex = if (retakeIndex in draft.indices) retakeIndex else draft.size
      adjustments=if(retakeIndex in draft.indices)ArrayList(adjustments.mapIndexed{index,value->if(index==retakeIndex)PageAdjustment(grayscale=grayscale) else value}) else ArrayList(adjustments+PageAdjustment(grayscale=grayscale))
      draft = if (retakeIndex in draft.indices) ArrayList(draft.mapIndexed { index, value -> if (index == retakeIndex) uri else value }) else ArrayList(draft + uri)
      retakeIndex = -1
      if (finish) { cameraOpen = false; reviewPage = capturedIndex; if(detected) cropIndex = capturedIndex }
     },
     onClose = { cameraOpen = false; retakeIndex = -1 }, onImport = { images.launch(arrayOf("image/*")) }
    ) else if (draft.isNotEmpty()) ScanReviewScreen(
     store, draft, name, adjustments.getOrElse(reviewPage.coerceIn(0,draft.lastIndex)){PageAdjustment()}.rotation, adjustments.getOrElse(reviewPage.coerceIn(0,draft.lastIndex)){PageAdjustment()}.grayscale, busy,
     onName = { name = it }, onBack = { discard = true },
     onRemove = { index -> draft = ArrayList(draft.filterIndexed { i, _ -> i != index });adjustments=ArrayList(adjustments.filterIndexed{i,_->i!=index}) },
     onRetake = { index -> retakeIndex = index; capture() },
     onRotate = { val current=reviewPage.coerceIn(0,draft.lastIndex);adjustments=ArrayList(adjustments.mapIndexed{i,value->if(i==current)value.copy(rotation=(value.rotation+270)%360)else value}) },
     onFilter = { gray -> val current=reviewPage.coerceIn(0,draft.lastIndex);adjustments=ArrayList(adjustments.mapIndexed{i,value->if(i==current)value.copy(grayscale=gray)else value}) },
     onImport = { images.launch(arrayOf("image/*")) }, onSave = ::saveScan, onCrop = { cropIndex = it }, onUnavailable = { unavailable = it }, page = reviewPage, onPage = { reviewPage = it },
     editing=editingId.isNotEmpty(),onMove={from,to->if(from in draft.indices && to in draft.indices){val reordered=ArrayList(draft);val item=reordered.removeAt(from);reordered.add(to,item);val edits=ArrayList(adjustments);edits.add(to,edits.removeAt(from));draft=reordered;adjustments=edits;reviewPage=to}}
    ) else if (selected != null) DocumentReaderScreen(
     selected, store.file(selected), onBack = { selectedId = "" },
     onRename = { renameDocument(selected) }, onMore = { morePage=it },
     onShare = { share(selected) }, onUnavailable = { unavailable = it },onAdd={addPage=true},onEdit={editDocument(selected)}
    ) else if(tab == "Documents") documentsState.SaveableStateProvider("library") {
     DocumentsScreen(store,documents,onProfile={tab="Settings"},onImport={images.launch(arrayOf("image/*"))},
      onOpen={selectedId=it.id},onMore=::openDocumentMore,onUnavailable={unavailable=it},onNotifications={notice("No new notifications.")})
    } else if(tab == "Tools") toolsState.SaveableStateProvider("catalog") { ToolsCatalogScreen(onBack = { tab = "Home" }, onScan = ::capture, onImport = { images.launch(arrayOf("image/*")) }) } else
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
       if (tab != "Documents") {
        item { WebSection("Quick Tools", "View All Tools", { tab = "Tools" }) }
        item { WebTools { tool ->
         when (tool) {
          "Smart Scan" -> capture()
          "Import Images" -> images.launch(arrayOf("image/*"))
          "PDF Tools", "All Tools" -> tab = "Tools"
          else -> unavailable = tool
         }
        } }
        item { Spacer(Modifier.height(4.dp)); WebScanButton(::capture) }
       }
       val visible = documents.filter { it.name.contains(search, ignoreCase = true) }
       item { WebSection(if (search.isNotBlank()) "Search Results" else "Recent Documents", "View All", { tab = "Documents" }, visible.size) }
       if (visible.isEmpty()) item { WebEmpty({ images.launch(arrayOf("image/*")) }, search.isNotBlank()) }
       items(if (tab == "Home" && search.isBlank()) visible.take(3) else visible, key = { it.id }) { doc ->
        DocumentLibraryCard(doc,store.file(doc),onOpen={selectedId=doc.id},onMore={openDocumentMore(doc)})
       }

      }
     }
    }
    if (busy) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.65f)).clickable { }, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
   }
  }
  if(morePage>=0 && selected!=null) DocumentMorePanel(selected,store.file(selected),onClose={morePage=-1}) { action ->
   val page=morePage
   morePage=-1
   when(action) {
    "Rename" -> renameDocument(selected)
    "Document Info" -> showDocumentInfo=true
    "Edit PDF", "Manage Pages" -> editDocument(selected)
    "Send to PC" -> savePdf(selected)
    "Save Image" -> savePhotos(selected,page)
    "PDF to Images" -> exportImages(selected,photos=false)
    "Share PDF" -> sendPdf(selected)?.let(::notice)
    "Delete This Document" -> deleteDocument(selected)
    else -> unavailable=action
   }
  }
  if(showDocumentInfo && selected!=null) AlertDialog(
   onDismissRequest={showDocumentInfo=false},title={Text("Document Info")},
   text={Text("${selected.name}\nPDF · ${selected.pages} page(s)\n${DateFormat.getDateInstance().format(Date(selected.created))}")},
   confirmButton={TextButton(onClick={showDocumentInfo=false}){Text("Done")}}
  )
  if(addPage && selected!=null)AlertDialog(onDismissRequest={addPage=false},title={Text("Add Page")},text={Column{
   Text("Add a scan or import images. Review your pages before saving.")
   TextButton(onClick={editDocument(selected,"camera")}){Text("Scan with Camera")}
   TextButton(onClick={editDocument(selected,"images")}){Text("Import Images")}
  }},confirmButton={TextButton(onClick={addPage=false}){Text("Cancel")}})
  if(shareDoc!=null) DocumentSharePanel(shareDoc,store.file(shareDoc),busy,shareStatus,
   onClose={shareId="";shareStatus=""},onRename={renameDocument(shareDoc)},
   onSave={shareStatus="";savePdf(shareDoc)},onSend={shareStatus="";sendPdf(shareDoc)},
   onImages={exportImages(shareDoc,false)},onPhotos={savePhotos(shareDoc)},
   onLongImage={exportImages(shareDoc,photos=false,longImage=true)})
  if (rename && actionDoc != null) AlertDialog(onDismissRequest = { if(!busy){rename = false; actionId = ""} }, title = { Text("Rename document") }, text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, enabled = !busy) }, confirmButton = {
   TextButton(enabled = !busy && renameText.isNotBlank(), onClick = { scope.launch { busy=true;try { withContext(Dispatchers.IO) { store.rename(actionDoc, renameText) }; refresh(); rename = false; actionId = "" } catch (_: Exception) { notice("Could not rename document.") } finally { busy=false } } }) { Text("Save") }
  }, dismissButton = { TextButton(enabled = !busy, onClick = { rename = false; actionId = "" }) { Text("Cancel") } })
  if (deleting && actionDoc != null) AlertDialog(onDismissRequest = { if(!busy){deleting = false; actionId = ""} }, title = { Text("Delete this document?") }, text = { Text("This removes the local PDF permanently. Exported copies are kept.") }, confirmButton = {
   TextButton(enabled = !busy, onClick = { scope.launch { busy=true;try { withContext(Dispatchers.IO) { store.delete(actionDoc) }; if(selectedId==actionDoc.id)selectedId=""; deleting=false; actionId=""; refresh() } catch (_: Exception) { notice("Could not delete document.") } finally { busy=false } } }) { Text("Delete") }
  }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = false; actionId = "" }) { Text("Cancel") } })
  if (unavailable.isNotEmpty()) AlertDialog(
   onDismissRequest = { unavailable = "" },
   title = { Text(unavailable) },
   text = { Text("This tool is not available in the native app yet. You can scan documents or import images to create a PDF.") },
   confirmButton = { TextButton(onClick = { unavailable = "" }) { Text("Got it") } }
  )
  if (discard) AlertDialog(
   onDismissRequest = { discard = false }, title = { Text("Discard this scan?") },
   text = { Text("Your unsaved pages will be removed. Saved documents are kept.") },
   confirmButton = { TextButton(onClick = { draft = arrayListOf(); adjustments = arrayListOf(); grayscale = false; discard = false;editingId="";reviewPage=0;retakeIndex=-1 }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
   dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } }
  )
 }
}
