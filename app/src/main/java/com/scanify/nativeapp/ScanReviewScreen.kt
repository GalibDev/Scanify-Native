package com.scanify.nativeapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ReviewTeal = Color(0xFF00B899)

private data class ReviewImages(val original: Bitmap, val thumbnails: Map<String, Bitmap>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScanReviewScreen(
 store: DocumentStore, pages: List<String>, name: String, rotation: Int, selectedFilter: String, busy: Boolean,
 onName: (String) -> Unit, onBack: () -> Unit, onRemove: (Int) -> Unit, onRetake: (Int) -> Unit,
 onRotate: () -> Unit, onFilter: (String) -> Unit, onImport: () -> Unit, onSave: () -> Unit,
 onCrop: (Int) -> Unit, onUnavailable: (String) -> Unit, page: Int, onPage: (Int) -> Unit,
 onMove: (Int,Int) -> Unit, onRestoreOriginal: () -> Unit, editing: Boolean = false
) {
 val current = page.coerceIn(0,pages.lastIndex)
 var compare by rememberSaveable(pages[current]) { mutableStateOf(false) }
 var rename by remember { mutableStateOf(false) }
 var renameText by remember { mutableStateOf(name) }
 var pageMenu by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf(false) }
 val images by produceState<ReviewImages?>(null,pages[current],rotation) {
  value=null
  error=false
  value=withContext(Dispatchers.IO) {
   runCatching {
    val source=store.bitmap(Uri.parse(pages[current]))
    val original=if(rotation==0) source else try {
     Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply{postRotate(rotation.toFloat())},true)
    } finally { source.recycle() }
    val scale=minOf(1f,160f/maxOf(original.width,original.height))
    val small=Bitmap.createScaledBitmap(original,(original.width*scale).toInt().coerceAtLeast(1),(original.height*scale).toInt().coerceAtLeast(1),true)
    val thumbnails=ScanFilters.names.filter{it!="Remove Handwriting"}.associateWith{ScanFilters.apply(small,it)}
    ReviewImages(original,thumbnails)
   }.getOrElse{error=true;null}
  }
 }
 val filtered by produceState<Bitmap?>(null,images,selectedFilter) {
  value=null
  val source=images?.original
   if(source!=null) {
    error=false
    value=withContext(Dispatchers.IO){runCatching{ScanFilters.apply(source,selectedFilter)}.getOrElse{error=true;null}}
   }
 }
 val preview=if(compare)images?.original else filtered
 Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
  Row(Modifier.fillMaxWidth().height(64.dp).background(Color.White).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick=onBack,enabled=!busy){WebIcon("left",Modifier.size(24.dp),WebText,"Back from scan review")}
   Row(Modifier.weight(1f).clickable(enabled=!busy){renameText=name;rename=true},verticalAlignment=Alignment.CenterVertically) {
    Text(name,Modifier.weight(1f,fill=false),fontSize=17.sp,color=WebText,textDecoration=TextDecoration.Underline,maxLines=1,overflow=TextOverflow.Ellipsis)
    WebIcon("edit",Modifier.padding(start=8.dp).size(20.dp),WebText,"Rename scan")
   }
   Box {
    if(editing || pages.size>1)IconButton(onClick={pageMenu=true},enabled=!busy){WebIcon("more",Modifier.size(21.dp),WebText,"Page actions")}
    DropdownMenu(expanded=pageMenu,onDismissRequest={pageMenu=false}) {
     DropdownMenuItem(text={Text("Restore Original Capture")},onClick={pageMenu=false;onRestoreOriginal()})
     DropdownMenuItem(text={Text("Retake")},onClick={pageMenu=false;onRetake(current)})
     DropdownMenuItem(text={Text("Move Earlier")},enabled=current>0,onClick={pageMenu=false;onMove(current,current-1)})
     DropdownMenuItem(text={Text("Move Later")},enabled=current<pages.lastIndex,onClick={pageMenu=false;onMove(current,current+1)})
    }
   }
  }
  Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal=29.dp,vertical=20.dp),contentAlignment=Alignment.Center) {
   if(preview!=null)BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
    val ratio=preview.width.toFloat()/preview.height
    val imageWidth=minOf(maxWidth,maxHeight*ratio)
    val imageHeight=imageWidth/ratio
    Box(Modifier.width(imageWidth).height(imageHeight).combinedClickable(onClick={},onLongClick={pageMenu=true})) {
     Image(preview.asImageBitmap(),if(compare)"Original page ${current+1}" else "Filtered page ${current+1}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
     IconButton(onClick={onRemove(current);onPage((current-1).coerceAtLeast(0))},enabled=!busy&&(!editing||pages.size>1),
      modifier=Modifier.align(Alignment.TopStart).padding(6.dp).size(27.dp).background(Color(0x88606060),CircleShape)) {
      WebIcon("trash",Modifier.size(18.dp),Color.White,"Delete current page")
     }
    }
   } else if(error)Column(horizontalAlignment=Alignment.CenterHorizontally) {
    Text("Could not load this photo.",color=WebMuted,textAlign=TextAlign.Center)
    TextButton(enabled=!busy&&(!editing||pages.size>1),onClick={onRemove(current);onPage((current-1).coerceAtLeast(0))}){Text("Remove photo",color=ReviewTeal)}
   }
   else CircularProgressIndicator(Modifier.size(26.dp),color=ReviewTeal,strokeWidth=2.dp)
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
   Spacer(Modifier.weight(1f))
   Row(Modifier.background(Color.White,RoundedCornerShape(3.dp)).height(31.dp),verticalAlignment=Alignment.CenterVertically) {
    IconButton(onClick={compare=false;onPage(current-1)},enabled=!busy&&current>0,modifier=Modifier.size(32.dp)){WebIcon("left",Modifier.size(13.dp),if(current>0)WebText else Color.LightGray,"Previous scanned page")}
    Text("${current+1}/${pages.size}",Modifier.widthIn(min=44.dp),fontSize=12.sp,textAlign=TextAlign.Center,color=WebText)
    IconButton(onClick={compare=false;onPage(current+1)},enabled=!busy&&current<pages.lastIndex,modifier=Modifier.size(32.dp)){WebIcon("right",Modifier.size(13.dp),if(current<pages.lastIndex)WebText else Color.LightGray,"Next scanned page")}
   }
   Spacer(Modifier.weight(1f))
   Surface(onClick={compare=!compare},enabled=!busy,shape=RoundedCornerShape(3.dp),color=Color.White) {
    Row(Modifier.padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     WebIcon("columns",Modifier.size(17.dp),if(compare)ReviewTeal else WebText)
     Text("Compare",color=if(compare)ReviewTeal else WebText,fontSize=12.sp)
    }
   }
  }
  LazyRow(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(topStart=14.dp,topEnd=14.dp)),
   contentPadding=PaddingValues(horizontal=15.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(15.dp)) {
   items(ScanFilters.names.size) { index ->
    val filter=ScanFilters.names[index]
    val enabled=filter!="Remove Handwriting" && !busy
    val selected=selectedFilter==filter
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(3.dp)).border(if(selected)2.dp else 0.dp,if(selected)ReviewTeal else Color.Transparent,RoundedCornerShape(3.dp))
     .clickable(enabled=enabled){compare=false;onFilter(filter)}.semantics{contentDescription="Filter $filter"}) {
     val thumb=images?.thumbnails?.get(filter) ?: images?.thumbnails?.get("Original")
     thumb?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
     Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(if(selected)ReviewTeal else Color(0xAA17455B)).padding(vertical=1.dp)) {
      Text(if(filter=="Remove Handwriting")"Remove H..." else filter,fontSize=9.sp,lineHeight=12.sp,color=if(enabled)Color.White else Color.LightGray,textAlign=TextAlign.Center,maxLines=1,modifier=Modifier.fillMaxWidth())
     }
    }
   }
  }
  HorizontalDivider(color=Color(0xFFEEEEEE))
  Row(Modifier.fillMaxWidth().height(76.dp).background(Color.White).padding(horizontal=7.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top) {
   ReviewAction("images","Import",!busy,onImport)
   ReviewAction("rotate","Left",!busy,onRotate)
   ReviewAction("crop","Crop",!busy){onCrop(current)}
   ReviewAction("text","Extract Text",!busy){onUnavailable("Extract Text")}
   ReviewAction("sign","Sign",!busy){onUnavailable("Signature")}
   Button(onClick=onSave,enabled=!busy&&name.isNotBlank()&&images!=null&&!error,shape=RoundedCornerShape(3.dp),
    colors=ButtonDefaults.buttonColors(containerColor=ReviewTeal),contentPadding=PaddingValues(5.dp),modifier=Modifier.size(52.dp,33.dp)) {
    WebIcon("check",Modifier.size(27.dp),Color.White,"Save document")
   }
  }
 }
 if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Rename document")},
  text={OutlinedTextField(renameText,{renameText=it},singleLine=true)},
  confirmButton={TextButton(enabled=renameText.isNotBlank(),onClick={onName(renameText.trim());rename=false}){Text("Save")}},
  dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})
}

@Composable
private fun ReviewAction(icon:String,label:String,enabled:Boolean,onClick:()->Unit) {
 Column(Modifier.widthIn(min=37.dp).clickable(enabled=enabled,onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
  WebIcon(icon,Modifier.size(23.dp),if(enabled)Color(0xFF202020)else WebMuted,label)
  Text(label,fontSize=10.sp,color=if(enabled)Color(0xFF202020)else WebMuted)
 }
}
