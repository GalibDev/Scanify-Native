package com.scanify.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private data class ToolShape(val fill: String, val details: String)

private val toolShapes = mapOf(
 "id" to ToolShape("M2 4H22V21H2Z", "M4 8H11V10H4Z M4 11H8V13H4Z M4 16H20V18H4Z M20 10A2.5 2.5 0 1 1 15 10A2.5 2.5 0 1 1 20 10Z"),
 "text" to ToolShape("M4 2H17L21 6V22H4Z", "M7 8H18V11H14V19H11V11H7Z"),
 "portrait" to ToolShape("M4 2H21V22H4Z", "M15 10A2.5 2.5 0 1 1 10 10A2.5 2.5 0 1 1 15 10Z M7 18V16Q7 14 10 14H15Q18 14 18 16V18Z"),
 "formula" to ToolShape("M3 2H22V22H3Z", "M5 14H8L9 17L12 7H20V9H14L10 20H8L6 16H5Z M15 10H20V12H17L19 15L17 17H20V19H14V17L17 15L14 12Z"),
 "translate" to ToolShape("M1 2H13V14H1Z M11 11H23V23H11Z M14 4Q20 5 21 10H18Q17 7 14 7Z M9 20Q4 20 3 15H6Q7 17 9 17Z", "M3 11L6 4H8L11 11H9L8 9H6L5 11Z M13 14H21V16H20Q19 19 17 20L14 22L13 20L16 18L14 16H13Z"),
 "book" to ToolShape("M2 4H10L12 6L14 4H22V21H14L12 23L10 21H2Z", "M4 8H9V10H4Z M4 13H8V15H4Z M14 7H20V18H14Z"),
 "ppt" to ToolShape("M3 3H21V16H14V19H18V21H6V19H10V16H3Z", "M9 6L16 10L9 14Z"),
 "board" to ToolShape("M3 3H21V15H3Z M2 17H22V19H17L19 22H16L14 19H10L8 22H5L7 19H2Z", "M5 10Q8 6 11 9Q15 12 19 7V9Q15 14 11 11Q8 9 5 12Z"),
 "timestamp" to ToolShape("M2 3H21V11Q13 8 10 16L10 21H2Z M23 17A6 6 0 1 1 11 17A6 6 0 1 1 23 17Z", "M8 8A1.5 1.5 0 1 1 5 8A1.5 1.5 0 1 1 8 8Z M16 13H18V18H14V16H16Z"),
 "mega" to ToolShape("M3 3H9V5H5V9H3Z M15 3H21V9H19V5H15Z M3 15H5V19H9V21H3Z M19 15H21V21H15V19H19Z M7 7H17V17H7Z", "M8 14L11 11L12 12L9 15H11V16H8Z M16 8V11H15V10L12 13L11 12L14 9H13V8Z"),
 "images" to ToolShape("M2 4H22V21H2Z", "M10 9A2.5 2.5 0 1 1 5 9A2.5 2.5 0 1 1 10 9Z M4 18L9 12L12 15L16 10L21 18Z"),
 "folder" to ToolShape("M2 4H9L11 6H22V21H2Z", "M7 13L12 9V12Q18 12 18 18Q16 15 12 15V17Z"),
 "file" to ToolShape("M4 2H16L21 7V22H4Z", "M15 4V8H19Z M7 11H18V13H7Z M7 15H18V17H7Z")
)

@Composable
internal fun CatalogToolGlyph(icon: String, color: Color, backdrop: Color) {
 val shape = toolShapes[icon]
 if (shape == null) {
  WebIcon(icon, Modifier.size(31.dp), color)
  return
 }
 val fill = remember(shape) { PathParser().parsePathString(shape.fill).toPath() }
 val details = remember(shape) { PathParser().parsePathString(shape.details).toPath() }
 Canvas(Modifier.size(34.dp)) {
  scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
   drawPath(fill, color)
   drawPath(details, backdrop)
  }
 }
}
