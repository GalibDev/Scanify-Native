package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Teal = Color(0xFF00B89C)
private val ReaderTeal = Color(0xFF008B7D)

@Composable
private fun ToolAction(icon: String, label: String, enabled: Boolean = true, color: Color = WebText, onClick: () -> Unit) {
 Column(Modifier.widthIn(min = 48.dp).heightIn(min = 54.dp).clip(RoundedCornerShape(7.dp)).clickable(enabled = enabled, onClick = onClick).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
  WebIcon(icon, Modifier.size(23.dp), if (enabled) color else WebMuted, label)
  if (label.isNotEmpty()) Text(label, fontSize = 11.sp, color = if (enabled) color else WebMuted, textAlign = TextAlign.Center)
 }
}

@Composable
internal fun ScanReviewScreen(
 store: DocumentStore, pages: List<String>, name: String, rotation: Int, grayscale: Boolean, busy: Boolean,
 onName: (String) -> Unit, onBack: () -> Unit, onRemove: (Int) -> Unit, onRetake: (Int) -> Unit,
 onRotate: () -> Unit, onFilter: (Boolean) -> Unit, onImport: () -> Unit, onSave: () -> Unit, onCrop: (Int) -> Unit, onUnavailable: (String) -> Unit, page: Int, onPage: (Int) -> Unit
) {
 val current = page.coerceIn(0, pages.lastIndex)
 val bitmap by produceState<Bitmap?>(null, pages[current]) {
  value = withContext(Dispatchers.IO) { runCatching { store.bitmap(Uri.parse(pages[current])) }.getOrNull() }
 }
 val filter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (grayscale) 0f else 1f) })
 Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
  Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = onBack, enabled = !busy) { WebIcon("back", color = WebText, description = "Back from scan review") }
   BasicTextField(name, onName, Modifier.weight(1f).padding(vertical = 9.dp), enabled = !busy, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = WebText), decorationBox = { inner -> Column { inner(); HorizontalDivider(color = WebMuted, modifier = Modifier.padding(top = 5.dp)) } })
   WebIcon("edit", Modifier.padding(horizontal = 12.dp).size(20.dp), WebMuted)
  }
  Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
   bitmap?.let { Image(it.asImageBitmap(), "Scanned page ${current + 1}", Modifier.fillMaxSize().rotate(rotation.toFloat()), contentScale = ContentScale.Fit, colorFilter = filter) } ?: Text("Loading page…", color = WebMuted)
   FilledIconButton(onClick = { onRemove(current); onPage((current - 1).coerceAtLeast(0)) }, enabled = !busy, modifier = Modifier.align(Alignment.TopStart).size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x88555555), contentColor = Color.White)) { WebIcon("trash", Modifier.size(19.dp), Color.White, "Delete current page") }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
   Surface(shape = RoundedCornerShape(5.dp), color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically) {
     IconButton(onClick = { onPage(current - 1) }, enabled = current > 0 && !busy) { WebIcon("left", Modifier.size(20.dp), if (current > 0) WebText else WebMuted, "Previous scanned page") }
     Text("${current + 1} / ${pages.size}", fontSize = 14.sp)
     IconButton(onClick = { onPage(current + 1) }, enabled = current < pages.lastIndex && !busy) { WebIcon("right", Modifier.size(20.dp), if (current < pages.lastIndex) WebText else WebMuted, "Next scanned page") }
    }
   }
   Spacer(Modifier.width(12.dp))
   TextButton(onClick = onImport, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WebText)) { WebIcon("plus", Modifier.size(17.dp), WebText); Text(" Add") }
  }
  Column(Modifier.background(Color.White, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
   Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
    listOf(false to "Original", true to "Grayscale").forEach { (gray, label) ->
     Column(Modifier.width(86.dp).clip(RoundedCornerShape(6.dp)).border(2.dp, if (grayscale == gray) Teal else WebLine, RoundedCornerShape(6.dp)).clickable(enabled = !busy) { onFilter(gray) }, horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFFF4F4F4)), contentAlignment = Alignment.Center) {
       bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (gray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null) }
      }
      Text(label, Modifier.fillMaxWidth().background(if (grayscale == gray) Teal else Color(0xFFF4F4F4)).padding(vertical = 6.dp), textAlign = TextAlign.Center, color = if (grayscale == gray) Color.White else WebText, fontSize = 12.sp)
     }
    }
    Text("Filter and rotation\napply to all pages", Modifier.align(Alignment.CenterVertically).weight(1f), fontSize = 11.sp, lineHeight = 17.sp, color = WebMuted, textAlign = TextAlign.Center)
   }
   HorizontalDivider(color = WebLine)
   Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    ToolAction("camera", "Retake", !busy) { onRetake(current) }
    ToolAction("rotate", "Left", !busy, onClick = onRotate)
    ToolAction("crop", "Crop", !busy) { onCrop(current) }
    ToolAction("text", "Extract Text", !busy) { onUnavailable("Extract Text") }
    Button(onClick = onSave, enabled = !busy && name.isNotBlank(), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), contentPadding = PaddingValues(10.dp), modifier = Modifier.size(52.dp, 46.dp)) { WebIcon("check", Modifier.size(27.dp), Color.White, "Save document") }
   }
  }
 }
}

@Composable
internal fun DocumentReaderScreen(doc: ScanDocument, file: File, onBack: () -> Unit, onRename: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onUnavailable: (String) -> Unit) {
 var page by rememberSaveable(doc.id) { mutableIntStateOf(0) }
 var more by remember { mutableStateOf(false) }
 var grid by rememberSaveable(doc.id) { mutableStateOf(false) }
 val maxPreviewHeight = (LocalConfiguration.current.screenHeightDp * 0.53f).dp
 var renderError by remember { mutableStateOf(false) }
 val bitmap by produceState<Bitmap?>(null, doc.id, page) {
  renderError = false
  value = withContext(Dispatchers.IO) { runCatching {
   val sourcePreview = File(file.parentFile, "${doc.id}-$page.jpg")
   if (sourcePreview.exists()) BitmapFactory.decodeFile(sourcePreview.absolutePath) ?: error("Cannot decode preview") else
   PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
    renderer.openPage(page).use { pdf -> Bitmap.createBitmap(850, 1202, Bitmap.Config.ARGB_8888).also { image -> image.eraseColor(android.graphics.Color.WHITE); pdf.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } }
   }
  }.getOrElse { renderError = true; null } }
 }
 Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
  Row(Modifier.fillMaxWidth().height(62.dp).background(Color.White).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) { WebIcon("left", Modifier.size(23.dp), Color(0xFF292929), "Back to documents") }
   Text(doc.name, Modifier.weight(1f).clickable(onClick = onRename).padding(end = 4.dp), fontSize = 16.sp, color = Color(0xFF292929), fontWeight = FontWeight.Normal, textDecoration = TextDecoration.Underline, maxLines = 1, overflow = TextOverflow.Ellipsis)
   Surface(onClick = { onUnavailable("Tags") }, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFDDDDDD)), color = Color.White) { Text("Tag +", Modifier.padding(horizontal = 5.dp, vertical = 3.dp), fontSize = 11.sp, color = Color.Gray) }
   IconButton(onClick = { grid = !grid }, modifier = Modifier.size(34.dp)) { WebIcon("grid", Modifier.size(20.dp), if(grid) ReaderTeal else Color(0xFF292929), "Show pages") }
   IconButton(onClick = { onUnavailable("Page selection") }, modifier = Modifier.size(34.dp)) { WebIcon("select", Modifier.size(21.dp), Color(0xFF292929), "Select pages") }
   Box {
    IconButton(onClick = { more = true }, modifier = Modifier.size(34.dp)) { WebIcon("more", Modifier.size(23.dp), Color(0xFF292929), "More document actions") }
    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
     DropdownMenuItem(text = { Text("Save PDF") }, onClick = { more = false; onSave() })
     DropdownMenuItem(text = { Text("Rename") }, onClick = { more = false; onRename() })
     DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { more = false; onDelete() })
    }
   }
  }
  Box(Modifier.fillMaxWidth().weight(1f)) {
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    if (grid) LazyRow(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     items((0 until doc.pages).toList()) { index -> FilterChip(selected = page == index, onClick = { page = index; grid = false }, label = { Text("Page ${index + 1}") }) }
    }
    val image = bitmap
    if (image != null) {
     val ratio = image.width.toFloat() / image.height
     BoxWithConstraints(Modifier.fillMaxWidth().background(Color.White)) {
      val naturalHeight = maxWidth / ratio
      Image(image.asImageBitmap(), "PDF page ${page + 1}", Modifier.fillMaxWidth().height(minOf(naturalHeight, maxPreviewHeight)), contentScale = ContentScale.Fit)
     }
    } else Box(Modifier.fillMaxWidth().height(220.dp).background(Color.White), contentAlignment = Alignment.Center) {
     Text(if (renderError) "Preview unavailable. Save or share the PDF to open it." else "Loading page…", Modifier.padding(24.dp), color = WebMuted)
    }
    if (doc.pages > 1) Row(Modifier.fillMaxWidth().background(Color.White), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
     IconButton(onClick = { page-- }, enabled = page > 0) { WebIcon("left", Modifier.size(19.dp), if (page > 0) Color.DarkGray else WebMuted, "Previous PDF page") }
     Text("${page + 1} / ${doc.pages}", fontSize = 13.sp)
     IconButton(onClick = { page++ }, enabled = page + 1 < doc.pages) { WebIcon("right", Modifier.size(19.dp), if (page + 1 < doc.pages) Color.DarkGray else WebMuted, "Next PDF page") }
    }
    Row(Modifier.fillMaxWidth().height(62.dp).background(Color.White).clickable { onUnavailable("Add Page") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
     Box(Modifier.size(20.dp).background(Color(0xFF20B5A8), CircleShape), contentAlignment = Alignment.Center) { WebIcon("plus", Modifier.size(15.dp), Color.White) }
     Text("  Add Page", fontSize = 16.sp, color = Color(0xFF666666))
    }
   }
   Surface(onClick = { onUnavailable("AI assistance") }, modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(43.dp), shape = CircleShape, color = Color.White, border = BorderStroke(1.5.dp, Color(0xFF13B4A4))) {
    Box(contentAlignment = Alignment.Center) { Text("AI✦", color = Color(0xFF13B4A4), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
   }
  }
  HorizontalDivider(color = Color(0xFFF0F0F0))
  Row(Modifier.fillMaxWidth().background(Color.White).height(74.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Top) {
   ToolAction("camera", "Add", color = Color(0xFF292929)) { onUnavailable("Add Page") }
   ToolAction("edit", "Edit", color = Color(0xFF292929)) { onUnavailable("PDF editing") }
   ToolAction("share", "Share", color = Color(0xFF292929), onClick = onShare)
   Column(Modifier.widthIn(min = 48.dp).clickable { onUnavailable("To Word") }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("Word", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF292929), modifier = Modifier.height(24.dp))
    Text("To Word", fontSize = 11.sp, color = Color(0xFF292929))
   }
   ToolAction("sign", "Sign", color = Color(0xFF292929)) { onUnavailable("Signature") }
  }
 }

}

@Composable
internal fun LibraryCategories(selected: String, onSelect: (String) -> Unit) {
 LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
  items(listOf("All" to "grid", "PDF" to "file", "Images" to "images", "Folders" to "folder", "Favorites" to "star")) { (title, icon) ->
   FilterChip(selected = selected == title, onClick = { onSelect(title) }, label = { Text(title, fontSize = 12.sp) }, leadingIcon = { WebIcon(icon, Modifier.size(14.dp), if (selected == title) Color.White else WebPurple) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = WebPurple, selectedLabelColor = Color.White, containerColor = Color.White), border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected == title, borderColor = WebLine))
  }
 }
}

@Composable
internal fun WorkspaceSettings(documents: Int, onAppearance: () -> Unit) {
 Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Text("YOUR PERSONAL WORKSPACE", color = WebPurple, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
   Text("Make yourself at home.", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
   Text("Your workspace, your preferences, your privacy.", fontSize = 12.sp, color = WebMuted)
  }
  Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, WebLine)) {
   Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
    Box(Modifier.size(57.dp).background(Color(0xFFF0E8FF), CircleShape), contentAlignment = Alignment.Center) { Text("Y", fontSize = 22.sp, color = WebPurple) }
    Text("Your personal workspace", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Scan freely. No account required.", fontSize = 11.sp, color = WebMuted)
    SettingRow("Appearance", "A workspace that feels like you", "sun", "Light", onAppearance)
    SettingRow("Documents on this device", "$documents documents · Stored locally", "drive")
    SettingRow("Cloud backup & sign in", "Planned for a later phase", "folder")
   }
  }
  Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, WebLine)) {
   Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    WebIcon("shield", Modifier.size(40.dp))
    Text("Privacy comes standard.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Your images are processed on your device and PDFs are saved in private app storage. No document is uploaded to a server.", fontSize = 11.sp, lineHeight = 20.sp, color = WebMuted)
    Text("Export important documents for a backup. Uninstalling the app or clearing app data removes your library.", fontSize = 11.sp, lineHeight = 20.sp, color = WebMuted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { WebIcon("shield", Modifier.size(14.dp)); Text("No tracking. No uploads.", color = WebPurple, fontSize = 10.sp) }
   }
  }
 }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: String, value: String = "", onClick: (() -> Unit)? = null) {
 HorizontalDivider(color = WebLine)
 Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 10.sp, color = WebMuted) }
  if (value.isNotEmpty()) OutlinedButton(onClick = { onClick?.invoke() }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { WebIcon(icon, Modifier.size(16.dp)); Text(" $value", fontSize = 11.sp) }
  else WebIcon(icon, Modifier.size(22.dp), WebMuted)
 }
}
