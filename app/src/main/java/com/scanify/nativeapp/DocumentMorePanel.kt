package com.scanify.nativeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class MoreAction(val label: String, val icon: String, val accent: Color? = null, val enabled: Boolean = true)

private val MoreGroups = listOf(
 "" to listOf(
  MoreAction("Edit PDF", "file", Color(0xFFF04B10)),
  MoreAction("Send to PC", "monitor", Color(0xFF2375DC)),
  MoreAction("Save Image", "download", Color(0xFF00897E)),
  MoreAction("Print Page", "print", Color(0xFF00897E))
 ),
 "Convert Formats" to listOf(MoreAction("To Word", "file"), MoreAction("To Excel", "columns"), MoreAction("PDF to Images", "download")),
 "Smart Tools" to listOf(MoreAction("Extract Text", "text"), MoreAction("Collage", "grid"), MoreAction("Translate", "translate", enabled = false), MoreAction("Compress", "download"), MoreAction("Read Mode", "book")),
 "Manage" to listOf(MoreAction("Manage Pages", "grid"), MoreAction("Lock", "lock"), MoreAction("Merge Files", "layers"), MoreAction("Move to Folder", "folder"), MoreAction("Favorite", "star")),
 "Send" to listOf(MoreAction("Share PDF", "share"), MoreAction("Email to Myself", "mail", enabled = false))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentMorePanel(doc: ScanDocument, file: File, onClose: () -> Unit, onAction: (String) -> Unit) {
 val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 val sheetHeight = (LocalConfiguration.current.screenHeightDp * .79f).dp
 val thumbnail by produceState<DocumentThumbnail?>(null, doc.id) {
  value = withContext(Dispatchers.IO) { DocumentThumbnailLoader.load(file, doc.id) }
 }
 ModalBottomSheet(
  onDismissRequest = onClose, sheetState = state,
  shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
  containerColor = Color.White, scrimColor = Color.Transparent, dragHandle = null
 ) {
  Column(Modifier.fillMaxWidth().height(sheetHeight)) {
  Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFFF5F1FD)).padding(horizontal = 16.dp),
   verticalAlignment = Alignment.CenterVertically) {
   Text("More", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WebText)
   IconButton(onClick = onClose, modifier = Modifier.size(34.dp).background(Color(0xFFEEEAF6), CircleShape)) {
    WebIcon("close", Modifier.size(21.dp), WebText, "Close More")
   }
  }
  Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
   Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(47.dp, 41.dp).clip(RoundedCornerShape(6.dp)).background(WebBackground), contentAlignment = Alignment.Center) {
     val preview = thumbnail?.image
     if (preview == null) WebIcon("file", Modifier.size(22.dp))
     else Image(preview.asImageBitmap(), "First page", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    Column(Modifier.weight(1f)) {
     Row(Modifier.clickable { onAction("Rename") }, verticalAlignment = Alignment.CenterVertically) {
      Text(doc.name, Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.Bold,
       color = WebText, textDecoration = TextDecoration.Underline, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.width(4.dp))
      WebIcon("edit", Modifier.size(14.dp), WebText, "Rename document")
     }
     OutlinedButton(onClick = { onAction("Tags") }, shape = RoundedCornerShape(5.dp),
      contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.padding(top = 5.dp).height(27.dp)) {
      Text("Tags +", fontSize = 11.sp, color = WebText)
     }
    }
    IconButton(onClick = { onAction("Document Info") }, modifier = Modifier.size(28.dp)) {
     WebIcon("info", Modifier.size(18.dp), WebMuted, "Document Info")
    }
   }
   MoreGroups.forEach { (title, actions) ->
    if (title.isNotEmpty()) Text(title, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp), fontSize = 15.sp, color = WebText)
    Column(Modifier.padding(horizontal = 8.dp)) {
     actions.chunked(4).forEach { row ->
      Row(Modifier.fillMaxWidth()) {
       row.forEach { action ->
        Column(Modifier.weight(1f).heightIn(min = 72.dp).clickable(enabled = action.enabled) { onAction(action.label) }.padding(vertical = 5.dp),
         horizontalAlignment = Alignment.CenterHorizontally) {
         val foreground = when {
          !action.enabled -> WebMuted
          action.accent != null -> Color.White
          else -> WebText
         }
         Box(Modifier.size(38.dp).background(action.accent ?: Color(0xFFF5F1FC), CircleShape), contentAlignment = Alignment.Center) {
          WebIcon(action.icon, Modifier.size(23.dp), foreground)
         }
         Text(action.label, Modifier.padding(top = 6.dp), color = if (action.enabled) WebText else WebMuted, fontSize = 11.sp)
        }
       }
       repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
      }
     }
    }
    if (title.isEmpty()) HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 3.dp, color = Color(0xFFF5F1FD))
    else Spacer(Modifier.height(12.dp))
   }
   TextButton(onClick = { onAction("Delete This Document") }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)) {
    Text("Delete This Document", color = Color(0xFFC94029), fontWeight = FontWeight.Bold)
   }
  }
  }
 }
}
