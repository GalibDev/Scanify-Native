package com.scanify.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSharePanel(
 doc:ScanDocument,file:File,busy:Boolean,statusMessage:String,
 onClose:()->Unit,onRename:()->Unit,onSave:()->Unit,onSend:()->String?
) {
 val state=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={!busy})
 var linkInfo by rememberSaveable(doc.id){mutableStateOf(false)}
 var hint by rememberSaveable(doc.id){mutableStateOf("")}
 val result by produceState<DocumentThumbnail?>(null,doc.id,file.absolutePath){value=withContext(Dispatchers.IO){DocumentThumbnailLoader.load(file,doc.id)}}
 val available=(result?.bytes?:0L)>0 && !busy
 val previewWidth=(LocalConfiguration.current.screenWidthDp*.42f).coerceIn(165f,280f).dp
 fun send(message:String="Choose an app to share this PDF.") { hint=message;onSend()?.let{hint=it} }
 fun save() { hint="Choose where to save the PDF. Transfer the saved file to your PC if needed.";onSave() }
 ModalBottomSheet(onDismissRequest={if(!busy)onClose()},sheetState=state,modifier=Modifier.fillMaxHeight(.92f),shape=RoundedCornerShape(topStart=16.dp,topEnd=16.dp),containerColor=Color(0xFFF7F7F7),scrimColor=Color.Black.copy(alpha=.4f),dragHandle=null) {
  Column(Modifier.fillMaxSize()) {
   Row(Modifier.fillMaxWidth().background(Color(0xFFF0F0F0)).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    Row(Modifier.weight(1f).heightIn(min=44.dp).clickable(enabled=!busy,onClick=onRename),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Text(doc.name,Modifier.weight(1f,fill=false),fontSize=17.sp,lineHeight=22.sp,color=Color(0xFF262626),maxLines=2,overflow=TextOverflow.Ellipsis)
     WebIcon("edit",Modifier.size(18.dp),Color(0xFF262626),"Rename shared document")
    }
    IconButton(onClick=onClose,enabled=!busy,modifier=Modifier.size(36.dp).background(Color(0xFFDDDDDD),CircleShape)) { WebIcon("close",Modifier.size(21.dp),Color(0xFF262626),"Close sharing") }
   }
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
    Box(Modifier.align(Alignment.CenterHorizontally).width(previewWidth).aspectRatio(210f/270).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp),contentAlignment=Alignment.Center) {
     result?.image?.let{Image(it.asImageBitmap(),"Share preview of ${doc.name}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}?:WebIcon("file",Modifier.size(40.dp),Color(0xFFAAAAAA))
    }
    Surface(color=Color.White,shape=RoundedCornerShape(12.dp)) {
     Column(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
       Text("Share",fontSize=13.sp,color=Color(0xFF888888))
       Text("Link Settings ›",Modifier.clickable{linkInfo=!linkInfo}.padding(vertical=5.dp),fontSize=12.sp,color=Color(0xFF00AE95))
      }
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       Shortcut("WhatsApp","message",Color(0xFF00C665),available){send("Choose WhatsApp in your device’s share menu.")}
       Shortcut("Gmail","mail",Color(0xFFDF493F),available){send("Choose Gmail or your mail app in the share menu.")}
       Shortcut("Copy Link","link",Color(0xFF00B89C),false){}
       Shortcut("Send to PC","monitor",Color(0xFFFF715D),available,::save)
       Shortcut("More","more",Color(0xFF00B89C),available){send()}
      }
      Text("Choose WhatsApp or Gmail in your device’s share menu. Send to PC saves the PDF for manual transfer.",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF888888))
      if(linkInfo)Text("Public links and expiry require cloud storage, which is not configured. Files are shared directly from your device.",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF888888))
     }
    }
    Text("More Sharing Options",Modifier.padding(start=7.dp,top=6.dp),fontSize=14.sp,color=Color(0xFF999999))
    Surface(color=Color.White,shape=RoundedCornerShape(12.dp)) {
     Column(Modifier.padding(horizontal=14.dp,vertical=4.dp)) {
      ShareOption("Share as PDF","file",available,result?.let{String.format(Locale.US,"%.2f MB",it.bytes/1048576f)}?:"Preparing…"){send()}
      HorizontalDivider(color=Color(0xFFEEEEEE))
      ShareOption("Save PDF","download",available){save()}
      HorizontalDivider(color=Color(0xFFEEEEEE))
      listOf("Share as Word" to "file","Share as Images" to "images","Share as Long Image" to "images","Save to Photos" to "download").forEachIndexed{index,(title,icon)->
       ShareOption(title,icon,false,"Soon"){}
       if(index<3)HorizontalDivider(color=Color(0xFFEEEEEE))
      }
     }
    }
    if(busy)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text("Saving PDF…",fontSize=13.sp,color=Color(0xFF777777))}
    val status=when{statusMessage.isNotEmpty()->statusMessage;result==null->"Preparing PDF on your device…";result!!.bytes==0L->"This PDF is unavailable. Close sharing and reopen the document.";else->hint}
    if(status.isNotEmpty())Text(status,fontSize=12.sp,lineHeight=18.sp,color=Color(0xFF777777))
   }
  }
 }
}

@Composable
private fun Shortcut(label:String,icon:String,color:Color,enabled:Boolean,onClick:()->Unit) {
 val alpha=if(enabled)1f else .45f
 Column(Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled=enabled,onClick=onClick).padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Box(Modifier.size(48.dp).background(Color(0xFFF7F7F7),RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center){WebIcon(icon,Modifier.size(24.dp),color.copy(alpha=alpha))}
  Text(label,fontSize=12.sp,lineHeight=16.sp,color=Color(0xFF262626).copy(alpha=alpha))
 }
}

@Composable
private fun ShareOption(title:String,icon:String,enabled:Boolean,trailing:String="",onClick:()->Unit) {
 val color=if(enabled)Color(0xFF262626)else Color(0xFFAAAAAA)
 Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clickable(enabled=enabled,onClick=onClick).padding(9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
  WebIcon(icon,Modifier.size(24.dp),color)
  Text(title,Modifier.weight(1f),fontSize=14.sp,color=color)
  if(trailing.isNotEmpty())Text(trailing,fontSize=12.sp,color=Color(0xFF999999))
 }
}
