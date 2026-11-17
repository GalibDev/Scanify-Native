package com.scanify.nativeapp

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class GalleryPhoto(val uri: String, val album: String)
private val ImportTeal = Color(0xFF00B899)

private fun photoPermissions(): Array<String> = when {
 Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
 Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
 else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun fullPhotoAccess(context: Context): Boolean {
 val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
 return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun galleryPhotos(context: Context): List<GalleryPhoto> {
 val photos = mutableListOf<GalleryPhoto>()
 context.contentResolver.query(
  MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
  arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
  null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
 )?.use { cursor ->
  while (cursor.moveToNext()) {
   photos.add(GalleryPhoto(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)).toString(), cursor.getString(1) ?: "Other"))
  }
 }
 return photos
}

private fun galleryThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
 if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(240,240), null)
 else {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
  var sample = 1
  while (maxOf(bounds.outWidth,bounds.outHeight)/sample > 400) sample *= 2
  val decoded=context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }
  if(decoded==null)null else {
   val exif=context.contentResolver.openInputStream(uri).use { it?.let { input -> androidx.exifinterface.media.ExifInterface(input) } }
   val matrix=android.graphics.Matrix()
   if(exif?.isFlipped==true)matrix.postScale(-1f,1f)
   matrix.postRotate((exif?.rotationDegrees ?: 0).toFloat())
   if(matrix.isIdentity)decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}
  }
 }
}.getOrNull()

@Composable
internal fun ImageImportScreen(onClose: () -> Unit, onImport: (List<Uri>) -> Unit) {
 val context = LocalContext.current
 val preferences = remember { context.getSharedPreferences("scanify-import",Context.MODE_PRIVATE) }
 var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
 var extraPhotos by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
 var requested by rememberSaveable { mutableStateOf(false) }
 var refresh by remember { mutableIntStateOf(0) }
 var album by rememberSaveable { mutableStateOf("Recents") }
 var albumsOpen by remember { mutableStateOf(false) }
 var tutorial by remember { mutableStateOf(!preferences.getBoolean("selection-tip",false)) }
 var loading by remember { mutableStateOf(true) }

 val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
 val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
  extraPhotos=ArrayList((extraPhotos+uris.map(Uri::toString)).distinct())
  selected=ArrayList((selected+uris.map(Uri::toString)).distinct())
 }
 DisposableEffect(context) {
  val owner=context as? androidx.lifecycle.LifecycleOwner
  val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)refresh++
  }
  owner?.lifecycle?.addObserver(observer)
  onDispose { owner?.lifecycle?.removeObserver(observer) }
 }
 LaunchedEffect(Unit) {
  val partialAccess=Build.VERSION.SDK_INT>=34 && ContextCompat.checkSelfPermission(context,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED
  if (!requested && !fullPhotoAccess(context) && !partialAccess) {
   requested=true
   permission.launch(photoPermissions())
  }
 }
 val photos by produceState<List<GalleryPhoto>>(emptyList(),refresh,extraPhotos.toList()) {
  loading=true

  value=withContext(Dispatchers.IO) {
   val accessible=runCatching { galleryPhotos(context) }.getOrElse { emptyList() }
   (extraPhotos.map { GalleryPhoto(it,"Selected Photos") }+accessible).distinctBy { it.uri }
  }
  loading=false
 }
 val allAccess = fullPhotoAccess(context)
 LaunchedEffect(photos,loading) {
  if(!loading)selected=ArrayList(selected.filter{uri->photos.any{it.uri==uri}})
 }
 val visible = photos.filter { album=="Recents" || it.album==album }
 val gridState = rememberLazyGridState()
 val scope = rememberCoroutineScope()
 val currentPhotos by rememberUpdatedState(visible)
 val currentSelection by rememberUpdatedState(selected)
 BackHandler(onBack=onClose)
 fun toggle(uri: String) { selected=ArrayList(if(uri in selected)selected-uri else selected+uri) }
 Column(Modifier.fillMaxSize().background(Color.White)) {
  Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=10.dp)) {
   IconButton(onClick=onClose,modifier=Modifier.align(Alignment.CenterStart)) { WebIcon("close",Modifier.size(26.dp),Color(0xFF252525),"Close image import") }
   Box(Modifier.align(Alignment.Center),contentAlignment=Alignment.Center) {
    TextButton(onClick={albumsOpen=true}) {
     Text(album,color=Color(0xFF222222),fontSize=17.sp)
     WebIcon("down",Modifier.padding(start=6.dp).size(13.dp),ImportTeal)
    }
    DropdownMenu(expanded=albumsOpen,onDismissRequest={albumsOpen=false}) {
     (listOf("Recents")+photos.map{it.album}.distinct()).forEach { name ->
      DropdownMenuItem(text={Text(name)},onClick={album=name;albumsOpen=false;scope.launch{gridState.scrollToItem(0)}})
     }
     DropdownMenuItem(text={Text("Choose from device")},onClick={albumsOpen=false;browse.launch(arrayOf("image/*"))})
    }
   }
   Button(onClick={onImport(selected.map(Uri::parse))},enabled=selected.isNotEmpty(),shape=RoundedCornerShape(3.dp),
    colors=ButtonDefaults.buttonColors(containerColor=ImportTeal),contentPadding=PaddingValues(horizontal=10.dp),modifier=Modifier.align(Alignment.CenterEnd).height(36.dp)) {
    Text("Import (${selected.size})",fontSize=14.sp)
   }
  }
  if(!allAccess) Row(Modifier.fillMaxWidth().background(Color(0xFFEBFAF7)).padding(horizontal=16.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
   Box(Modifier.size(21.dp).background(ImportTeal,CircleShape),contentAlignment=Alignment.Center){Text("!",color=Color.White)}
   Text("Only selected photos can be accessed.\nYou can allow access to All Photos.",Modifier.weight(1f).padding(horizontal=10.dp),fontSize=11.sp,lineHeight=16.sp,color=Color(0xFF596364))
   Text("Settings",Modifier.clickable{permission.launch(photoPermissions())}.padding(8.dp),color=ImportTeal,fontSize=12.sp,lineHeight=16.sp)
  }
  if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=ImportTeal)}
  else if(visible.isEmpty())Column(Modifier.fillMaxWidth().weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
   Text("No accessible photos",color=WebMuted)
   TextButton(onClick={browse.launch(arrayOf("image/*"))}){Text("Choose Photos",color=ImportTeal)}
  } else LazyVerticalGrid(columns=GridCells.Fixed(3),state=gridState,
   modifier=Modifier.fillMaxWidth().weight(1f).pointerInput(album,photos) {
    var origin = -1
    var adding = true
    var base = emptyList<String>()
    fun itemAt(position: Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
     position.x>=it.offset.x && position.x<it.offset.x+it.size.width && position.y>=it.offset.y && position.y<it.offset.y+it.size.height
    }?.index
    detectDragGesturesAfterLongPress(
     onDragStart={ position ->
      origin=itemAt(position) ?: -1
      base=currentSelection.toList()
      currentPhotos.getOrNull(origin)?.let { photo ->
       adding=photo.uri !in base
       selected=ArrayList(if(adding)base+photo.uri else base-photo.uri)
      }
     },
     onDrag={ change,_ ->
      change.consume()
      val end=itemAt(change.position)
      if(origin>=0 && end!=null) {
       val range=currentPhotos.subList(minOf(origin,end),maxOf(origin,end)+1).map{it.uri}
       selected=ArrayList(if(adding)(base+range).distinct() else base-range.toSet())
      }
      val viewport=gridState.layoutInfo.viewportEndOffset
      if(change.position.y>viewport-60)scope.launch { gridState.scrollBy(24f) }
      else if(change.position.y<60)scope.launch { gridState.scrollBy(-24f) }
     },onDragEnd={origin=-1},onDragCancel={origin=-1}
    )
   },contentPadding=PaddingValues(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
    items(visible,key={it.uri}) { photo ->
     val image by produceState<Bitmap?>(null,photo.uri) { value=withContext(Dispatchers.IO){galleryThumbnail(context,Uri.parse(photo.uri))} }
     val number=selected.indexOf(photo.uri)+1
     Box(Modifier.aspectRatio(1f).clickable{toggle(photo.uri)}.semantics { contentDescription="Photo ${visible.indexOf(photo)+1}";this.selected=number>0 }) {
      image?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
      Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).background(if(number>0)ImportTeal else Color.Transparent,CircleShape).border(1.dp,if(number>0)ImportTeal else Color.White.copy(alpha=.8f),CircleShape),contentAlignment=Alignment.Center) {
       if(number>0)Text(number.toString(),color=Color.White,fontSize=13.sp)
      }
     }
    }
   }
 }
 if(tutorial && photos.isNotEmpty() && !loading)Dialog(onDismissRequest={tutorial=false;preferences.edit().putBoolean("selection-tip",true).apply()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
  Surface(modifier=Modifier.fillMaxWidth(.66f),shape=RoundedCornerShape(5.dp),color=Color.White) {
   Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    SelectionIllustration()
    Text("Swipe to select multiple photos",Modifier.padding(vertical=18.dp),fontSize=15.sp,textAlign=TextAlign.Center,color=Color(0xFF222222))
    Button(onClick={tutorial=false;preferences.edit().putBoolean("selection-tip",true).apply()},modifier=Modifier.fillMaxWidth().height(45.dp),shape=RoundedCornerShape(3.dp),colors=ButtonDefaults.buttonColors(containerColor=ImportTeal)){Text("Got It")}
   }
  }
 }
}

@Composable
private fun SelectionIllustration() {
 Canvas(Modifier.padding(top=22.dp).size(185.dp)) {
  val cell=size.width/3
  drawRoundRect(Color(0xFFF7F7F7),cornerRadius=androidx.compose.ui.geometry.CornerRadius(18f,18f))
  for(row in 0..2)for(col in 0..2) {
   val left=col*cell+8
   val top=row*cell+8
   drawRoundRect(Color.White,topLeft=Offset(left,top),size=androidx.compose.ui.geometry.Size(cell-12,cell-12),cornerRadius=androidx.compose.ui.geometry.CornerRadius(5f,5f))
   for(line in 0..3)drawRect(if(line==0)if((row+col)%2==0)Color(0xFFEF927C)else ImportTeal else Color(0xFFDDDDDD),topLeft=Offset(left+9,top+12+line*9),size=androidx.compose.ui.geometry.Size(cell-32-(line%2)*8,2f))
   drawCircle(if(row==2)ImportTeal else Color(0xFFE9E9E9),7f,Offset(left+cell-19,top+10))
  }
  val hand=androidx.compose.ui.graphics.Path().apply{
   moveTo(size.width*.64f,size.height*.72f);lineTo(size.width*.60f,size.height*.60f)
   quadraticTo(size.width*.59f,size.height*.53f,size.width*.63f,size.height*.56f)
   lineTo(size.width*.76f,size.height*.73f);lineTo(size.width*.80f,size.height*.68f)
   quadraticTo(size.width*.86f,size.height*.65f,size.width*.91f,size.height*.76f)
   quadraticTo(size.width*1.04f,size.height*.96f,size.width*.89f,size.height*.98f)
   quadraticTo(size.width*.78f,size.height*1.02f,size.width*.69f,size.height*.87f)
   lineTo(size.width*.61f,size.height*.81f);quadraticTo(size.width*.57f,size.height*.75f,size.width*.64f,size.height*.78f);close()
  }
  drawPath(hand,Color.White)
  drawPath(hand,Color(0xFF005450),style=androidx.compose.ui.graphics.drawscope.Stroke(4f))
 }
}
