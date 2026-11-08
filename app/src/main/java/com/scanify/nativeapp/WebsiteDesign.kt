package com.scanify.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val WebPurple = Color(0xFF7C3AED)
internal val WebBackground = Color(0xFFF8F7FC)
internal val WebText = Color(0xFF201A33)
internal val WebMuted = Color(0xFF8C879B)
internal val WebLine = Color(0xFFEEEBF4)
private val scanGradient = Brush.linearGradient(listOf(Color(0xFFAA45F4), Color(0xFF6927CE)))

// Native vector paths; no web runtime or bitmap UI screenshots.
private val paths = mapOf(
 "star" to "M12 2L15 8L22 9L17 14L18 22L12 18L6 22L7 14L2 9L9 8Z",
 "list" to "M4 6H20 M4 12H20 M4 18H20",
 "down" to "M5 9L12 16L19 9",
 "book" to "M12 5Q7 2 2 4V20Q7 18 12 21Q17 18 22 20V4Q17 2 12 5Z M12 5V21",
 "board" to "M3 3H21V16H3Z M12 16V22 M7 22L12 18L17 22",
 "columns" to "M3 3H21V21H3Z M12 3V21",
 "stamp" to "M4 15H20V20H4Z M9 15V9A4 4 0 1 1 15 9V15 M4 23H20",
 "merge" to "M5 3V7Q5 12 12 12 M19 3V7Q19 12 12 12 M12 12V22 M8 18L12 22L16 18",
 "split" to "M12 22V16Q12 9 5 9V3 M12 16Q12 9 19 9V3 M2 6L5 3L8 6 M16 6L19 3L22 6",
 "compress" to "M3 8H8V3 M8 8L3 3 M21 8H16V3 M16 8L21 3 M3 16H8V21 M8 16L3 21 M21 16H16V21 M16 16L21 21",
 "lock" to "M7 11V7A5 5 0 0 1 17 7V11 M4 11H20V22H4Z M12 15V18",
 "erase" to "M3 14L13 4Q15 2 17 4L21 8Q23 10 21 12L12 21H8Z M8 9L16 17 M12 21H22",
 "ruler" to "M3 17L17 3L22 8L8 22Z M8 12L11 15 M12 8L15 11 M16 4L19 7",
 "sparkles" to "M12 3L14 9L20 11L14 13L12 19L10 13L4 11L10 9Z M20 2V6 M18 4H22 M4 17V21 M2 19H6",
 "print" to "M6 8V2H18V8 M6 17H3V8H21V17H18 M6 14H18V22H6Z M17 11H17.01",
 "qr" to "M3 3H9V9H3Z M15 3H21V9H15Z M3 15H9V21H3Z M15 15H18V18H21V21H15Z M21 12V15 M12 3V6 M12 12H15 M12 18V21",
 "close" to "M5 5L19 19 M19 5L5 19",
 "flash" to "M13 2L3 14H11L10 22L21 10H13Z",
 "switch" to "M3 7H17L14 4 M21 17H7L10 20 M3 7V12 M21 17V12",
 "settings" to "M4 6H20 M4 12H20 M4 18H20 M8 3V9 M16 9V15 M10 15V21",
 "select" to "M7 3H21V17 M3 7H17V21H3Z M6 13L9 16L14 10",
 "sign" to "M3 21H21 M5 17L8 10L17 2L21 6L12 14Z M8 10L12 14 M4 19L5 17",
 "back" to "M20 12H4 M11 5L4 12L11 19",
 "left" to "M15 5L8 12L15 19",
 "right" to "M9 5L16 12L9 19",
 "more" to "M5 12H5.01 M12 12H12.01 M19 12H19.01",
 "download" to "M12 3V16 M7 11L12 16L17 11 M4 17V21H20V17",
 "share" to "M12 16V3 M7 8L12 3L17 8 M5 12V21H19V12",
 "edit" to "M15 5L19 9 M4 20L5 15L17 3L21 7L9 19Z",
 "trash" to "M3 6H21 M9 6V3H15V6 M5 6L6 21H18L19 6 M10 10V17 M14 10V17",
 "rotate" to "M3 10A9 9 0 1 1 5 19 M3 3V10H10",
 "camera" to "M3 6H7L9 3H15L17 6H21V21H3Z M16 13A4 4 0 1 1 8 13A4 4 0 1 1 16 13",
 "crop" to "M6 3V18H21 M3 6H18V21",
 "check" to "M4 12L9 17L20 6",
 "shield" to "M12 2L21 6V12Q21 19 12 22Q3 19 3 12V6Z M8 12L11 15L16 9",
 "sun" to "M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12 M12 2V4 M12 20V22 M2 12H4 M20 12H22 M5 5L6 6 M18 18L19 19 M5 19L6 18 M18 6L19 5",
 "drive" to "M5 3H19L22 16V21H2V16Z M2 16H22 M6 19H6.01 M10 19H10.01",
 "scan" to "M4 7V4H7 M17 4H20V7 M20 17V20H17 M7 20H4V17 M7 12H17",
 "file" to "M14 2H6Q4 2 4 4V20Q4 22 6 22H18Q20 22 20 20V8Z M14 2V8H20 M8 12H16 M8 16H16 M8 8H10",
 "images" to "M8 3H20Q22 3 22 5V17Q22 19 20 19H8Q6 19 6 17V5Q6 3 8 3 M2 7V21Q2 23 4 23H18 M6 15L11 10L16 15L19 12L22 15 M16 7H16.01",
 "folder" to "M3 7V5Q3 3 5 3H9L11 5H19Q21 5 21 7V19Q21 21 19 21H5Q3 21 3 19Z M3 9H21",
 "upload" to "M3 7V5Q3 3 5 3H9L11 5H19Q21 5 21 7V19Q21 21 19 21H5Q3 21 3 19Z M12 18V10 M8 14L12 10L16 14",
 "id" to "M4 5H20Q22 5 22 7V17Q22 19 20 19H4Q2 19 2 17V7Q2 5 4 5 M6 9H8V11H6Z M5 16Q5 13 7 13Q9 13 9 16 M13 9H18 M13 13H18 M13 16H16",
 "text" to "M4 7V4H7 M17 4H20V7 M20 17V20H17 M7 20H4V17 M8 8H16 M8 12H16 M8 16H13",
 "layers" to "M12 3L22 8L12 13L2 8Z M2 12L12 17L22 12 M2 16L12 21L22 16",
 "grid" to "M3 3H21V21H3Z M12 3V21 M3 12H21",
 "home" to "M3 10L12 3L21 10V20Q21 21 20 21H15V14H9V21H4Q3 21 3 20Z",
 "user" to "M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7 M4 21V19Q4 13 12 13Q20 13 20 19V21",
 "search" to "M19 11A8 8 0 1 1 3 11A8 8 0 1 1 19 11 M17 17L22 22",
 "bell" to "M18 8A6 6 0 0 0 6 8Q6 15 3 17H21Q18 15 18 8 M10 21H14",
 "arrow" to "M4 12H20 M13 5L20 12L13 19",
 "plus" to "M12 5V19 M5 12H19"
)

@Composable
internal fun WebIcon(name: String, modifier: Modifier = Modifier.size(24.dp), color: Color = WebPurple, description: String? = null) {
 val vector = remember(name) {
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
   addPath(PathParser().parsePathString(paths[name] ?: paths.getValue("file")).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
  }.build()
 }
 Icon(vector, description, modifier, tint = color)
}

@Composable
internal fun WebHeader(onProfile: () -> Unit, onNotifications: () -> Unit) {
 Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
  Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(scanGradient), contentAlignment = Alignment.Center) { WebIcon("scan", Modifier.size(27.dp), Color.White) }
  Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
   Text("Scanify", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, color = WebText)
   Text(".", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = WebPurple)
  }
  IconButton(onClick = onNotifications) { WebIcon("bell", color = WebMuted, description = "Notifications") }
  Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF0E8FF)).clickable(onClick = onProfile), contentAlignment = Alignment.Center) { Text("Y", fontWeight = FontWeight.SemiBold, color = WebPurple, fontSize = 14.sp) }
 }
}

@Composable
internal fun WebSearch(value: String, onChange: (String) -> Unit, placeholder: String = "Search your documents") {
 Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(CircleShape).background(Color.White).border(1.dp, WebLine, CircleShape).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  WebIcon("search", Modifier.size(21.dp), WebMuted)
  BasicTextField(value, onChange, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = WebText, fontSize = 15.sp), cursorBrush = SolidColor(WebPurple), decorationBox = { inner ->
   if (value.isEmpty()) Text(placeholder, color = WebMuted, fontSize = 15.sp)
   inner()
  })
 }
}

@Composable
internal fun WebSection(title: String, action: String, onClick: () -> Unit, count: Int? = null) {
 Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
  Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = WebText)
  if (count != null) Surface(color = Color(0xFFF0EAF7), shape = RoundedCornerShape(5.dp)) { Text("$count", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 10.sp, color = WebMuted) }
  Spacer(Modifier.weight(1f))
  Row(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
   Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WebPurple)
   WebIcon("arrow", Modifier.size(17.dp))
  }
 }
}

private data class Tool(val title: String, val icon: String, val start: Color, val end: Color, val plain: Boolean = false)
private val tools = listOf(
 Tool("Smart Scan", "scan", Color(0xFFA044F5), Color(0xFFA044F5), true),
 Tool("PDF Tools", "file", Color(0xFFFF858E), Color(0xFFFF5D6B)),
 Tool("Import Images", "images", Color(0xFF759DFF), Color(0xFF5365F5)),
 Tool("Import Files", "upload", Color(0xFFBF91FF), Color(0xFF8B61F2)),
 Tool("ID Cards", "id", Color(0xFF53D0C2), Color(0xFF35A9AE)),
 Tool("Extract Text", "text", Color(0xFF53D0C2), Color(0xFF35A9AE)),
 Tool("Merge PDF", "layers", Color(0xFFA044F5), Color(0xFFA044F5), true),
 Tool("All Tools", "grid", Color(0xFFBF91FF), Color(0xFF8B61F2))
)

@Composable
internal fun WebTools(onTool: (String) -> Unit) {
 Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
  tools.chunked(4).forEach { row ->
   Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    row.forEach { tool ->
     Surface(onClick = { onTool(tool.title) }, modifier = Modifier.weight(1f).heightIn(min = 98.dp), color = Color.White, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, WebLine), shadowElevation = 0.3.dp) {
      Column(Modifier.padding(horizontal = 2.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
       Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).then(if (tool.plain) Modifier else Modifier.background(Brush.linearGradient(listOf(tool.start, tool.end)))), contentAlignment = Alignment.Center) {
        WebIcon(tool.icon, Modifier.size(25.dp), if (tool.plain) tool.start else Color.White)
       }
       Text(tool.title, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = WebText, minLines = 2)
      }
     }
    }
   }
  }
 }
}

@Composable
internal fun WebScanButton(onClick: () -> Unit) {
 Row(Modifier.fillMaxWidth().height(55.dp).clip(RoundedCornerShape(20.dp)).background(scanGradient).clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
  WebIcon("scan", Modifier.size(27.dp), Color.White)
  Text("Scan Document", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
  Box(Modifier.size(25.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { WebIcon("arrow", Modifier.size(17.dp)) }
 }
}

@Composable
internal fun WebEmpty(onImport: () -> Unit, noMatch: Boolean = false) {
 Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, WebLine)) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 27.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
   Box(Modifier.size(55.dp).background(Color(0xFFF7F4FF), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { WebIcon("folder", Modifier.size(29.dp), Color(0xFFAF83DF)) }
   Text(if (noMatch) "No matching documents" else "A little space for your big ideas", color = WebMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
   Text(if (noMatch) "Try another search." else "Your documents will appear here. Start with a scan or import a file.", color = WebMuted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
   if (!noMatch) Button(onClick = onImport, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
    WebIcon("plus", Modifier.size(17.dp), Color.White)
    Spacer(Modifier.width(7.dp)); Text("Import your first document", fontSize = 12.sp)
   }
  }
 }
}

@Composable
internal fun WebNavigation(selected: String, onSelect: (String) -> Unit, onScan: () -> Unit) {
 Box(Modifier.fillMaxWidth().navigationBarsPadding().height(88.dp)) {
  Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp), shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), color = Color.White, shadowElevation = 2.dp) {
   Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf("Home", "Documents", "Scan", "Tools", "Profile").forEach { item ->
     Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
      if (item != "Scan") {
       val active = selected == item || (item == "Profile" && selected == "Settings")
       val color = if (active) WebPurple else Color(0xFF9B90AB)
       Column(Modifier.fillMaxWidth().clickable { onSelect(if (item == "Profile") "Settings" else item) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        WebIcon(when(item) { "Home" -> "home"; "Documents" -> "folder"; "Tools" -> "grid"; else -> "user" }, Modifier.size(23.dp), color)
        Text(item, fontSize = 10.sp, color = color, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
       }
      }
     }
    }
   }
  }
  Box(Modifier.align(Alignment.TopCenter).size(66.dp).background(WebBackground, CircleShape).padding(5.dp).clip(CircleShape).background(scanGradient).clickable(onClick = onScan), contentAlignment = Alignment.Center) {
   WebIcon("scan", Modifier.size(30.dp), Color.White, "Scan document")
  }
 }
}
