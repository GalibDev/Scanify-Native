package com.scanify.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DocumentsScreen(
 store:DocumentStore,documents:List<ScanDocument>,onProfile:()->Unit,onImport:()->Unit,
 onOpen:(ScanDocument)->Unit,
 onMore:(ScanDocument)->Unit,onUnavailable:(String)->Unit,onNotifications:()->Unit
) {
 val preferences=LocalContext.current.getSharedPreferences("scanify-library",0)
 var grid by rememberSaveable { mutableStateOf(preferences.getBoolean("grid",false)) }
 var sort by rememberSaveable { mutableStateOf(preferences.getString("sort","recent")?:"recent") }
 var query by rememberSaveable { mutableStateOf("") }
 var category by rememberSaveable { mutableStateOf("All") }
 var filters by rememberSaveable { mutableStateOf(false) }
 var pageFilter by rememberSaveable { mutableStateOf("All") }
 var sortMenu by remember { mutableStateOf(false) }
 val focus=remember { FocusRequester() }
 val state=rememberLazyListState()
 val visible=documents.filter { doc -> doc.name.contains(query.trim(),ignoreCase=true)&&category!="Images"&&when(pageFilter){"Single page"->doc.pages==1;"Multiple pages"->doc.pages>1;else->true} }.let { docs->when(sort){"oldest"->docs.sortedBy{it.created};"name"->docs.sortedBy{it.name.lowercase(Locale.ROOT)};else->docs.sortedByDescending{it.created}} }
 LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(start=14.dp,end=14.dp,top=6.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  item { WebHeader(onProfile,onNotifications) }
  item {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text("My Documents",fontSize=24.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp);Text("Manage, organize, and access your files.",fontSize=11.sp,lineHeight=16.sp,color=WebMuted) }
    Surface(shape=RoundedCornerShape(12.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)) { IconButton(onClick={focus.requestFocus()},modifier=Modifier.size(39.dp)) { WebIcon("search",Modifier.size(20.dp),WebText,"Focus document search") } }
    Surface(shape=RoundedCornerShape(12.dp),color=if(filters||pageFilter!="All")Color(0xFFF0E8FF)else Color.White,border=BorderStroke(1.dp,WebLine)) { IconButton(onClick={filters=!filters},modifier=Modifier.size(39.dp)) { WebIcon("settings",Modifier.size(20.dp),if(filters||pageFilter!="All")WebPurple else WebText,"Document filters") } }
   }
  }
  item {
   Row(Modifier.fillMaxWidth().heightIn(min=43.dp).clip(RoundedCornerShape(30.dp)).background(Color.White).border(1.dp,WebLine,RoundedCornerShape(30.dp)).padding(horizontal=15.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    WebIcon("search",Modifier.size(20.dp),WebMuted)
    BasicTextField(query,{query=it},Modifier.weight(1f).focusRequester(focus),singleLine=true,textStyle=TextStyle(color=WebText,fontSize=14.sp),cursorBrush=SolidColor(WebPurple),decorationBox={inner->if(query.isEmpty())Text("Search documents…",color=WebMuted,fontSize=14.sp);inner()})
   }
  }
  item { LibraryCategories(category){if(it in listOf("All","PDF","Images"))category=it else onUnavailable(it)} }
  if(filters)item {
   Surface(shape=RoundedCornerShape(12.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)) {
    Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
     Text("Page count",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
      listOf("All","Single page","Multiple pages").forEach { option->FilterChip(selected=pageFilter==option,onClick={pageFilter=option},label={Text(option,fontSize=11.sp)}) }
     }
    }
   }
  }
  item {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
    Box {
     TextButton(onClick={sortMenu=true},modifier=Modifier.height(34.dp),contentPadding=PaddingValues(horizontal=0.dp,vertical=4.dp)) { Text("Sort by: ",fontSize=12.sp,color=WebMuted);Text(when(sort){"oldest"->"Oldest";"name"->"Name";else->"Recent"},fontSize=12.sp,fontWeight=FontWeight.Bold,color=WebText);WebIcon("down",Modifier.padding(start=5.dp).size(14.dp),WebMuted) }
     DropdownMenu(expanded=sortMenu,onDismissRequest={sortMenu=false}) { listOf("recent" to "Recent","oldest" to "Oldest","name" to "Name").forEach{(value,label)->DropdownMenuItem(text={Text(label)},onClick={sort=value;preferences.edit().putString("sort",value).apply();sortMenu=false})} }
    }
    Surface(shape=RoundedCornerShape(10.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)) {
     Row(Modifier.padding(2.dp)) { listOf(false to "list",true to "grid").forEach{(value,icon)->
      IconButton(onClick={grid=value;preferences.edit().putBoolean("grid",value).apply()},modifier=Modifier.size(36.dp,30.dp).background(if(grid==value)Color(0xFFF0E8FF)else Color.Transparent,RoundedCornerShape(7.dp))) { WebIcon(icon,Modifier.size(19.dp),if(grid==value)WebPurple else WebMuted,if(value)"Grid view" else "List view") }
     } }
    }
   }
  }
  if(category=="All")item {
   Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
    Text("Folders",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     listOf("Import Files" to "upload","Import Images" to "images","Create Folder" to "folder").forEach{(label,icon)->
      Surface(onClick={if(label=="Import Images")onImport()else onUnavailable(if(label=="Import Files")"File import" else "Folders")},modifier=Modifier.weight(1f).heightIn(min=94.dp),shape=RoundedCornerShape(12.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)) {
       Column(Modifier.padding(9.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(34.dp).background(Color(0xFFF0E8FF),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){WebIcon(icon,Modifier.size(21.dp))}
        Text(label,fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold)
        if(label!="Import Images")Text("Soon",fontSize=9.sp,lineHeight=11.sp,color=WebMuted)
       }
      }
     }
    }
   }
  }
  item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Text(if(query.isNotBlank())"Search Results" else "All Documents",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("(${visible.size})",color=WebMuted,fontSize=16.sp) } }
  if(visible.isEmpty())item {
   if(documents.isEmpty()&&query.isBlank()&&pageFilter=="All"&&category!="Images")WebEmpty(onImport)
   else Surface(shape=RoundedCornerShape(14.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)){Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("No matching documents",fontWeight=FontWeight.SemiBold);Text(if(category=="Images")"This library currently saves PDFs. Imported images become PDF pages."else"Try another search or page-count filter.",fontSize=12.sp,color=WebMuted);TextButton(onClick={query="";category="All";pageFilter="All"}){Text("Clear filters")}}}
  }
  if(grid)items(visible.chunked(2),key={row->row.joinToString{it.id}}){row->
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    row.forEach{doc->DocumentLibraryCard(doc,store.file(doc),true,Modifier.weight(1f),{onOpen(doc)},{onMore(doc)})}
    if(row.size==1)Spacer(Modifier.weight(1f))
   }
  } else items(visible,key={it.id}){doc->DocumentLibraryCard(doc,store.file(doc),false,onOpen={onOpen(doc)},onMore={onMore(doc)})}
 }
}

@Composable
internal fun DocumentLibraryCard(doc:ScanDocument,file:File,grid:Boolean=false,modifier:Modifier=Modifier,onOpen:()->Unit,onMore:()->Unit) {
 val result by produceState<DocumentThumbnail?>(null,doc.id,file.absolutePath){value=withContext(Dispatchers.IO){DocumentThumbnailLoader.load(file,doc.id)}}
 val metadata="PDF · ${DateFormat.getDateInstance(DateFormat.MEDIUM,Locale.US).format(Date(doc.created))} · ${doc.pages} ${if(doc.pages==1)"page"else"pages"}" + (result?.let{" · ${fileSize(it.bytes)}"}?:"")
 @Composable fun thumbnail(modifier:Modifier) {
  Box(modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF7F4FF)).border(1.dp,WebLine,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center) {
   result?.image?.let{Image(it.asImageBitmap(),"First page of ${doc.name}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}?:WebIcon("file",Modifier.size(25.dp))
  }
 }
 @Composable fun actions() {
  IconButton(onClick=onMore,modifier=Modifier.size(34.dp)){WebIcon("more",Modifier.size(20.dp),WebMuted,"Actions for ${doc.name}")}
 }
 Surface(onClick=onOpen,modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(11.dp),color=Color.White,border=BorderStroke(1.dp,WebLine)) {
  if(grid)Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   thumbnail(Modifier.fillMaxWidth().height(105.dp))
   Row(verticalAlignment=Alignment.CenterVertically){PdfBadge();Spacer(Modifier.weight(1f));actions()}
   Text(doc.name,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
   Text(metadata,fontSize=10.sp,lineHeight=15.sp,color=WebMuted)
  }else Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
   thumbnail(Modifier.size(49.dp,43.dp));PdfBadge()
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(doc.name,fontSize=14.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(metadata,fontSize=10.sp,lineHeight=14.sp,color=WebMuted)}
   actions()
  }
 }
}

@Composable private fun PdfBadge(){Surface(color=Color(0xFFCC464D),shape=RoundedCornerShape(5.dp)){Text("PDF",Modifier.padding(horizontal=4.dp,vertical=2.dp),color=Color.White,fontSize=9.sp,lineHeight=11.sp,fontWeight=FontWeight.Bold)}}
private fun fileSize(bytes:Long)=when{bytes>=1024*1024->String.format(Locale.US,"%.1f MB",bytes/(1024f*1024));bytes>=1024->"${bytes/1024} KB";else->"$bytes B"}
