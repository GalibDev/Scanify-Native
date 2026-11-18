package com.scanify.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OcrScreen(store:DocumentStore,pdf:File?,image:String?,adjustment:PageAdjustment,onClose:()->Unit) {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var source by rememberSaveable { mutableStateOf(image) }
 // Keep potentially large OCR results out of the saved-instance-state Bundle.
 var result by remember { mutableStateOf("") }
 var status by remember { mutableStateOf("") }
 var running by remember { mutableStateOf(false) }
 var finished by remember { mutableStateOf(false) }
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
  if(uri!=null) { val text=result;scope.launch {
   try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use{it.write(text)} ?: error("Cannot open destination") };status="Text saved." }
   catch(error:CancellationException){throw error}
   catch(_:Exception){status="Could not save text. Try another location."}
  } }
 }
 val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) {
   runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
   source=uri.toString();finished=false;result="";status="Photo selected. Tap Extract Text."
  }
 }
 fun extract() {
  if(running)return
  running=true;finished=false;status="Recognizing text…"
  scope.launch {
   try {
    val text=if(pdf!=null)NativeOcr.pdf(pdf){page,total->withContext(Dispatchers.Main){status="Reading page $page of $total…"}}
     else NativeOcr.image(store,Uri.parse(requireNotNull(source)),if(source==image)adjustment else PageAdjustment())
    result=text;finished=true;status=if(text.isBlank())"No readable Latin text found. Try a sharper, upright photo." else "Text extracted. Check and correct any recognition mistakes."
   } catch(error:CancellationException){throw error}
   catch(_:Exception){status="Could not read this source. Check file access and try again."}
   finally{running=false}
  }
 }
 BackHandler { onClose() }
 Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth().height(60.dp),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick=onClose){WebIcon("left",Modifier.size(24.dp),WebText,"Back from OCR")}
   Text("Extract Text",fontSize=22.sp)
  }
  Text("Offline OCR · English / Latin script",color=Color(0xFF009E8D),fontSize=14.sp)
  Text("Bangla is not supported in this version.",fontSize=12.sp,color=WebMuted)
  if(pdf!=null)Text("All PDF pages",fontSize=13.sp)
  else if(image!=null)Text("Current page with selected filter and rotation",fontSize=13.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   if(pdf==null && image==null)OutlinedButton(onClick={pick.launch(arrayOf("image/*"))},enabled=!running){Text("Choose Image")}
   Button(onClick=::extract,enabled=!running && (pdf!=null || source!=null)){Text(if(finished)"Extract Again" else "Extract Text")}
  }
  if(running)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(status.isNotEmpty())Text(status,fontSize=13.sp,color=WebMuted)
  OutlinedTextField(result,{result=it},Modifier.fillMaxWidth().weight(1f),enabled=!running,placeholder={Text("Recognized text appears here")})
  Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
   TextButton(enabled=result.isNotBlank()&&!running,onClick={
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Extracted text",result));status="Text copied."
   }) { Text("Copy") }
   TextButton(enabled=result.isNotBlank()&&!running,onClick={
    runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,result)},"Share extracted text"))}.onFailure{status="No app is available to share text."}
   }) { Text("Share") }
   TextButton(enabled=result.isNotBlank()&&!running,onClick={save.launch("Scanify-text.txt")}) { Text("Save TXT") }
  }
 }
}
