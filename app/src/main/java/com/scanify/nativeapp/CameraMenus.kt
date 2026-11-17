package com.scanify.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

internal val CaptureTeal = Color(0xFF27DCC9)
@Composable
internal fun CameraFilterMenu(selected: String, onSelect: (String)->Unit) {
 Column(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal=8.dp,vertical=10.dp)) {
  ScanFilters.names.chunked(6).forEach { row ->
   Row(Modifier.fillMaxWidth()) {
    row.forEach { name ->
     val chosen = selected == name
     Column(Modifier.weight(1f).clickable(enabled=name!="Remove Handwriting"){onSelect(name)}.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally) {
      Box(Modifier.size(43.dp).background(if(chosen)Color(0xFF075A50) else Color(0xFF303030),RoundedCornerShape(5.dp)).border(if(chosen)1.dp else 0.dp,if(chosen)CaptureTeal else Color.Transparent,RoundedCornerShape(5.dp)),contentAlignment=Alignment.Center) {
       when(name) {
        "Omnifix" -> Text("AI✦",color=CaptureTeal,fontSize=18.sp)
        "Eco","Grayscale","B&W","Invert" -> Row {
         Box(Modifier.size(13.dp).background(if(name=="Eco")Color.Gray else Color.Black,CircleShape))
         Box(Modifier.offset(x=(-5).dp).size(13.dp).background(if(name=="Grayscale")Color.Gray else Color.White,CircleShape))
        }
        else -> WebIcon(when(name){"Original"->"file";"No Shadow"->"board";"Remove Handwriting"->"sign";"Lighten"->"sun";else->"images"},Modifier.size(24.dp),when(name){"Lighten"->Color(0xFFFFC928);"Magic Color"->Color(0xFF1688FF);else->Color(0xFF70B5FF)})
       }
      }
      Text(if(name=="Remove Handwriting")"Remove\nHandwr..." else name.replace(" ","\n"),Modifier.padding(top=5.dp),fontSize=10.sp,lineHeight=12.sp,textAlign=TextAlign.Center,color=if(chosen)CaptureTeal else if(name=="Remove Handwriting")Color.Gray else Color.White)
     }
    }
    repeat(6-row.size){Spacer(Modifier.weight(1f))}
   }
   Spacer(Modifier.height(8.dp))
  }
 }
}
@Composable
internal fun CameraResolutionMenu(selected: Int, onSelect:(Int)->Unit) {
 Row(Modifier.fillMaxWidth().background(Color.Black).padding(12.dp)) {
  listOf("HD" to "4032×3024","Large" to "3264×2488","Standard" to "2880×2156","Small" to "1920×1440").forEachIndexed { index, (title, size) ->
   Column(Modifier.weight(1f).background(if(selected==index)Color(0xFF303030)else Color.Transparent,RoundedCornerShape(4.dp)).clickable{onSelect(index)}.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    Text(title,color=if(selected==index)CaptureTeal else Color.LightGray,fontSize=12.sp)
    Text(size,color=if(selected==index)CaptureTeal else Color.Gray,fontSize=9.sp)
   }
  }
 }
}
@Composable
internal fun CameraSettingsMenu(auto:Boolean,grid:Boolean,volume:Boolean,crop:Boolean,onAuto:(Boolean)->Unit,onGrid:(Boolean)->Unit,onVolume:(Boolean)->Unit,onCrop:(Boolean)->Unit,onClose:()->Unit,onMore:()->Unit) {
 Surface(Modifier.fillMaxWidth().padding(18.dp),color=Color(0xE6111111),shape=RoundedCornerShape(17.dp)) {
  Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically){Text("Settings",Modifier.weight(1f),color=Color.White,fontSize=20.sp);IconButton(onClick=onClose){WebIcon("close",Modifier.size(20.dp),Color.White)}}
   @Composable fun option(label:String,icon:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp),verticalAlignment=Alignment.CenterVertically) {
     WebIcon(icon,Modifier.size(19.dp),if(enabled)Color.White else Color.Gray)
     Text(label,Modifier.weight(1f).padding(start=10.dp),fontSize=12.sp,color=if(enabled)Color.White else Color.Gray)
     Box(Modifier.size(46.dp,26.dp).clip(CircleShape).background(if(checked)Color(0xFF2BC65B) else Color(0xFF29292D)).toggleable(value=checked,enabled=enabled,role=androidx.compose.ui.semantics.Role.Switch,onValueChange=change).padding(2.dp)) {
      Box(Modifier.align(if(checked)Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).background(if(enabled)Color.White else Color.Gray,CircleShape))
     }
    }
   }
   option("Auto Capture","scan",auto,change=onAuto)
   option("Grid","grid",grid,change=onGrid)
   option("Rotate by Text Orientation","text",false,false){}
   option("Capture with Volume Button","volume",volume,change=onVolume)
   option("Auto Crop","crop",crop,change=onCrop)
   HorizontalDivider(color=Color.DarkGray)
   TextButton(onClick=onMore,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("More Settings",color=CaptureTeal)}
  }
 }
}

@Composable
internal fun CameraCirclesIcon() {
 Canvas(Modifier.size(24.dp).semantics { contentDescription="Camera filters" }) {
  drawCircle(Color(0xFFAAAAAA),size.width*.24f,androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.32f))
  drawCircle(Color(0xFF777777),size.width*.24f,androidx.compose.ui.geometry.Offset(size.width*.32f,size.height*.62f))
  drawCircle(Color.White,size.width*.24f,androidx.compose.ui.geometry.Offset(size.width*.68f,size.height*.62f))
 }
}
@Composable
internal fun CameraNineDots() {
 Canvas(Modifier.size(23.dp).semantics { contentDescription="Import images" }) {
  for(y in 0..2)for(x in 0..2)drawRect(Color.White,androidx.compose.ui.geometry.Offset(x*size.width/3,y*size.height/3),androidx.compose.ui.geometry.Size(size.width/5,size.height/5))
 }
}
