package com.scanify.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CatalogTool(val label:String,val icon:String,val tone:String="violet",val letters:String="",val action:String="")
private data class CatalogSection(val title:String,val icon:String,val tools:List<CatalogTool>)
private val catalog = listOf(
 CatalogSection("Scan","scan",listOf(
  CatalogTool("ID Cards","id","mint"), CatalogTool("Extract Text","text","mint"), CatalogTool("ID Photo Maker","portrait","blue"), CatalogTool("Formula","formula","mint"),
  CatalogTool("photo translation","translate","blue"), CatalogTool("Book","book","cyan"), CatalogTool("PPT","ppt","orange"), CatalogTool("Whiteboard","board","cyan"),
  CatalogTool("Timestamp","timestamp","blue"), CatalogTool("Mega Scan","mega","mint"), CatalogTool("Smart Scan","scan","mint",action="scan")
 )),
 CatalogSection("Import","images",listOf(CatalogTool("Import Images","images","mint",action="import"), CatalogTool("Import Files","folder","blue"))),
 CatalogSection("PDF Tools","file",listOf(CatalogTool("Merge PDF","layers"),CatalogTool("Split PDF","columns"),CatalogTool("Compress PDF","download"),CatalogTool("Organize Pages","rotate"))),
 CatalogSection("Convert","switch",listOf(CatalogTool("To Word","file","blue","W"),CatalogTool("To Excel","file","mint","X"),CatalogTool("To PPT","board","orange","P"),CatalogTool("PDF to Images","images","blue"),CatalogTool("Images to PDF","images","blue",action="import"))),
 CatalogSection("Edit & Secure","edit",listOf(CatalogTool("Sign","sign","blue"),CatalogTool("Watermark PDF","stamp"),CatalogTool("Lock","lock","orange"),CatalogTool("Erase Marks","erase"),CatalogTool("Annotate PDF","edit"))),
 CatalogSection("Utilities","grid",listOf(CatalogTool("Measure","ruler","mint"),CatalogTool("Ask AI","sparkles","mint"),CatalogTool("Print","print","blue"),CatalogTool("QR Code","qr","mint")))
)

@Composable
internal fun ToolsCatalogScreen(onBack:()->Unit,onScan:()->Unit,onImport:()->Unit) {
 var category by rememberSaveable { mutableStateOf("") }
 var searching by rememberSaveable { mutableStateOf(false) }
 var query by rememberSaveable { mutableStateOf("") }
 var showNew by rememberSaveable { mutableStateOf(false) }
 val focus=remember { FocusRequester() }
 val listState=rememberLazyListState()
 LaunchedEffect(category) { listState.scrollToItem(0) }
 fun back() { when { searching->{searching=false;query=""};category.isNotEmpty()->category="";else->onBack() } }
 BackHandler(enabled=searching||category.isNotEmpty()) { back() }
 val search=query.trim()
 LazyColumn(Modifier.fillMaxSize().drawBehind { drawRect(WebBackground);drawRect(Brush.radialGradient(listOf(Color(0xFFF0E6FF),WebBackground),center=Offset(size.width*.45f,0f),radius=size.height*.55f)) },state=listState,contentPadding=PaddingValues(start=15.dp,end=15.dp,bottom=20.dp)) {
  item {
   Row(Modifier.fillMaxWidth().heightIn(min=64.dp).drawBehind {
    val diagonal=Path().apply{moveTo(size.width*.55f,0f);lineTo(size.width*.85f,0f);lineTo(size.width*.65f,size.height);lineTo(size.width*.35f,size.height);close()}
    drawPath(diagonal,Color(0x1CA66FF5))
   },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    IconButton(onClick=::back,modifier=Modifier.size(38.dp)) { WebIcon("back",Modifier.size(24.dp),WebText,if(category.isEmpty())"Back to Home" else "Back to tools") }
    Text(category.ifEmpty{"Tools"},Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold,color=WebText,letterSpacing=(-.7).sp)
    Surface(onClick={showNew=!showNew},shape=CircleShape,color=Color(0xFFF0E8FF)) {
     Row(Modifier.padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) { WebIcon("sparkles",Modifier.size(16.dp));Text("What's New",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=WebPurple) }
    }
    IconButton(onClick={searching=!searching;query=""},modifier=Modifier.size(38.dp)) { WebIcon(if(searching)"close" else "search",Modifier.size(22.dp),WebText,if(searching)"Close search" else "Search tools") }
   }
  }
  if(showNew) item {
   Surface(modifier=Modifier.padding(bottom=16.dp),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,WebLine)) {
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("Native scanner updates",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
     Text("Live camera, document edge suggestions and four-corner perspective crop are available. More native tools are on the way.",fontSize=12.sp,lineHeight=18.sp,color=WebMuted)
     TextButton(onClick={category="Scan";query="";searching=false;showNew=false},contentPadding=PaddingValues(0.dp)) { Text("Explore Scan",fontSize=12.sp);Spacer(Modifier.width(5.dp));WebIcon("arrow",Modifier.size(16.dp)) }
    }
   }
  }
  if(searching) item {
   Row(Modifier.fillMaxWidth().padding(bottom=16.dp).heightIn(min=46.dp).clip(CircleShape).background(Color.White).border(1.dp,WebLine,CircleShape).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    WebIcon("search",Modifier.size(19.dp),WebMuted)
    BasicTextField(query,{query=it},Modifier.weight(1f).focusRequester(focus),singleLine=true,textStyle=TextStyle(color=WebText,fontSize=15.sp),cursorBrush=SolidColor(WebPurple),decorationBox={inner->if(query.isEmpty())Text("Search tools",fontSize=15.sp,color=WebMuted);inner()})
   }
   LaunchedEffect(Unit) { focus.requestFocus() }
  }
  if(search.isNotEmpty()) {
   item { Text("Search Results",fontSize=20.sp,fontWeight=FontWeight.Bold,color=WebText) }
   val results=catalog.flatMap{it.tools}.filter{it.label.contains(search,ignoreCase=true)}
   if(results.isEmpty())item { Text("No matching tools.",Modifier.fillMaxWidth().padding(28.dp),textAlign=TextAlign.Center,color=WebMuted,fontSize=13.sp) }
   else item { CatalogGrid(results,onScan,onImport) }
  } else catalog.filter{category.isEmpty()||it.title==category}.forEach { section ->
   item {
    Column(Modifier.padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Color(0xFFC788FF),Color(0xFF7426ED)))),contentAlignment=Alignment.Center) { WebIcon(section.icon,Modifier.size(20.dp),Color.White) }
      Text(section.title,Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.Normal,color=WebText,letterSpacing=(-.5).sp)
      if(category.isEmpty())TextButton(onClick={category=section.title},modifier=Modifier.height(30.dp),contentPadding=PaddingValues(0.dp)) { Text("See All",fontSize=12.sp);Spacer(Modifier.width(5.dp));WebIcon("arrow",Modifier.size(16.dp)) }
     }
     CatalogGrid(if(category.isEmpty())section.tools.take(if(section.title=="Scan") 10 else 4) else section.tools,onScan,onImport)
    }
   }
  }
 }
}

@Composable
private fun CatalogGrid(tools:List<CatalogTool>,onScan:()->Unit,onImport:()->Unit) {
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  tools.chunked(4).forEach { row ->
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    row.forEach { tool ->
     val (color, backdrop) = when (tool.tone) {
      "blue" -> Color(0xFF437EE1) to Color(0xFFE7ECFA)
      "mint" -> Color(0xFF0FAF9F) to Color(0xFFDCF1EE)
      "cyan" -> Color(0xFF09A9C8) to Color(0xFFDDF1F6)
      "orange" -> Color(0xFFEE692E) to Color(0xFFFBEAE4)
      else -> Color(0xFF7954D4) to Color(0xFFEFE8FA)
     }
     Column(
      Modifier.weight(1f).heightIn(min = 99.dp)
       .clickable(enabled = tool.action.isNotEmpty()) {
        when (tool.action) { "scan" -> onScan(); "import" -> onImport() }
       }.padding(horizontal = 2.dp, vertical = 5.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
     ) {
      Box(Modifier.size(64.dp).background(backdrop, CircleShape), contentAlignment = Alignment.Center) {
       if (tool.letters.isNotEmpty()) {
        Box(Modifier.size(29.dp).background(color, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
         Text(tool.letters, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
       } else CatalogToolGlyph(tool.icon, color, backdrop)
      }
      Text(tool.label, fontSize = 12.sp, lineHeight = 14.sp, color = WebText, textAlign = TextAlign.Center)
     }
    }
    repeat(4-row.size) { Spacer(Modifier.weight(1f)) }
   }
  }
 }
}
