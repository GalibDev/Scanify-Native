package com.scanify.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val FileBlue=Color(0xFF0098F5)
private data class ImportFile(val uri:String,val name:String)

/** Recents contains only documents explicitly granted through Android's picker. */
@Composable
internal fun FileImportScreen(onClose:()->Unit,onImport:(List<Uri>)->Unit) {
 val context=LocalContext.current
 val preferences=remember { context.getSharedPreferences("scanify-file-import",0) }
 var recent by rememberSaveable { mutableStateOf(arrayListOf<String>().apply {
  runCatching { val data=JSONArray(preferences.getString("recent","[]"));repeat(data.length()){add(data.getString(it))} }
 }) }
 var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
 var query by rememberSaveable { mutableStateOf("") }
 var grid by rememberSaveable { mutableStateOf(true) }
 var revision by remember { mutableIntStateOf(0) }
 val browse=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  if(uris.isNotEmpty()) {
   uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
   recent=ArrayList((uris.map{it.toString()}+recent).distinct().take(100))
   selected=ArrayList(uris.map{it.toString()})
   preferences.edit().putString("recent",JSONArray(recent).toString()).apply()
   revision++
  }
 }
 val files by produceState<List<ImportFile>?>(null,recent,revision) {
  value=withContext(Dispatchers.IO) { recent.mapNotNull { value -> runCatching {
   val uri=Uri.parse(value)
   context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
    if(cursor.moveToFirst())ImportFile(value,cursor.getString(0) ?: "Document.pdf") else null
   }
  }.getOrNull() } }
 }
 LaunchedEffect(files) { files?.let { list -> selected=ArrayList(selected.filter { uri -> list.any{it.uri==uri} }) } }
 BackHandler(onBack=onClose)
 val visible=files.orEmpty().filter{it.name.contains(query,true)}
 Column(Modifier.fillMaxSize().background(Color(0xFFF0F1F0)).padding(top=12.dp).clip(RoundedCornerShape(topStart=30.dp,topEnd=30.dp)).background(Color.White)) {
  Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick=onClose) { WebIcon("left",Modifier.size(26.dp),Color.Black,"Back") }
   Text("Recents",Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=Color.Black)
   IconButton(onClick={grid=!grid}) { WebIcon(if(grid)"grid" else "file",Modifier.size(24.dp),Color.Black,"Change file layout") }
   if(selected.isEmpty()) IconButton(onClick=onClose) { WebIcon("close",Modifier.size(25.dp),Color.Black,"Close files") }
   else Button(onClick={onImport(selected.map(Uri::parse))},colors=ButtonDefaults.buttonColors(containerColor=FileBlue),contentPadding=PaddingValues(horizontal=16.dp),shape=CircleShape) { Text("Open") }
  }
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Search")},leadingIcon={WebIcon("search",Modifier.size(22.dp),Color.Black)},singleLine=true,shape=CircleShape,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=FileBlue,unfocusedContainerColor=Color(0xFFF4F4F4)))
  Box(Modifier.weight(1f)) {
   if(files==null) CircularProgressIndicator(Modifier.align(Alignment.Center))
   else if(visible.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    WebIcon("folder",Modifier.size(48.dp),FileBlue)
    Text(if(query.isNotEmpty())"No matching PDFs" else "Choose PDFs from your device or cloud storage",Modifier.padding(16.dp),textAlign=TextAlign.Center)
    Button(onClick={browse.launch(arrayOf("application/pdf"))},colors=ButtonDefaults.buttonColors(containerColor=FileBlue)) { Text("Browse Files") }
   } else LazyVerticalGrid(columns=GridCells.Fixed(if(grid)3 else 1),modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp,22.dp,12.dp,16.dp),verticalArrangement=Arrangement.spacedBy(24.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    items(visible,key={it.uri}) { item ->
     val checked=item.uri in selected
     Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { selected=ArrayList(if(checked)selected-item.uri else selected+item.uri) }.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally) {
      FilePreview(item.uri,checked)
      Text(item.name.removeSuffix(".pdf"),Modifier.padding(top=9.dp).background(if(checked)Color(0xFFD5D5D8) else Color.Transparent,RoundedCornerShape(3.dp)),fontSize=14.sp,lineHeight=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,color=Color.Black)
      Text("PDF",Modifier.padding(top=5.dp),fontSize=12.sp,color=Color.Gray)
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   if(selected.isNotEmpty()) {
    TextButton(onClick={selected=ArrayList((selected+visible.map{it.uri}).distinct())}){Text("Select All",color=Color.Black)}
    Text("${selected.size}",color=FileBlue)
    TextButton(onClick={selected=arrayListOf()}){Text("Deselect All",color=Color.Black)}
   } else {
    Text("Recents",color=FileBlue,fontWeight=FontWeight.SemiBold)
    TextButton(onClick={browse.launch(arrayOf("application/pdf"))}) { WebIcon("folder",Modifier.size(24.dp),Color.Black);Text("  Browse",color=Color.Black) }
   }
  }
 }
}

@Composable
private fun FilePreview(uri:String,selected:Boolean) {
 val context=LocalContext.current
 val image by produceState<Bitmap?>(null,uri) {
  value=withContext(Dispatchers.IO) { runCatching {
   context.contentResolver.openFileDescriptor(Uri.parse(uri),"r")?.use { descriptor -> PdfRenderer(descriptor).use { renderer ->
    renderer.openPage(0).use { page ->
     val scale=240f/maxOf(page.width,page.height)
     Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {
      it.eraseColor(android.graphics.Color.WHITE);page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
     }
    }
   } }
  }.getOrNull() }
 }
 Box(Modifier.fillMaxWidth().height(90.dp),contentAlignment=Alignment.Center) {
  if(image!=null)Image(image!!.asImageBitmap(),"PDF preview",Modifier.fillMaxSize().padding(4.dp),contentScale=ContentScale.Fit)
  else WebIcon("file",Modifier.size(50.dp),Color.Gray)
  Box(Modifier.align(Alignment.BottomCenter).size(23.dp).background(if(selected)FileBlue else Color.White.copy(alpha=.7f),CircleShape).border(1.dp,if(selected)Color.White else Color.LightGray,CircleShape),contentAlignment=Alignment.Center) {
   if(selected)Text("✓",color=Color.White,fontSize=17.sp)
  }
 }
}
