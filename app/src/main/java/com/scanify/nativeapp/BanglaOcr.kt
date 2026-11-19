package com.scanify.nativeapp

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.security.MessageDigest

internal enum class OcrLanguage(val label:String,val model:String) {
 MIXED("বাংলা + English","ben+eng"), BANGLA("বাংলা","ben"), ENGLISH("English","eng")
}

internal object BanglaOcr {
 private val hashes=mapOf(
  "ben" to "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964",
  "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
 )
 // Versioned private model directory. Publish each model only after checksum verification.
 @Synchronized private fun modelDirectory(context:Context):File {
  val root=File(context.noBackupFilesDir,"ocr-models-8741641")
  val directory=File(root,"tessdata").apply{check(isDirectory || mkdirs())}
  hashes.forEach { (language,expected) ->
   val target=File(directory,"$language.traineddata")
   if(!target.isFile || target.length()==0L) {
    val temp=File.createTempFile(language,".tmp",directory)
    try {
     context.assets.open("tessdata/$language.traineddata").use { input -> temp.outputStream().use{input.copyTo(it)} }
     val digest=MessageDigest.getInstance("SHA-256")
     temp.inputStream().use { input -> val bytes=ByteArray(8192);while(true){val count=input.read(bytes);if(count<0)break;digest.update(bytes,0,count)} }
     check(digest.digest().joinToString(""){"%02x".format(it)}==expected){"OCR model checksum mismatch"}
     check(temp.renameTo(target)){"Could not install OCR model"}
    } finally { temp.delete() }
   }
  }
  return root
 }
 fun recognize(context:Context,bitmap:Bitmap,language:OcrLanguage):String {
  if(language==OcrLanguage.ENGLISH)return NativeOcr.recognize(bitmap)
  val directory=modelDirectory(context)
  val engine=TessBaseAPI()
  return try {
   check(engine.init(directory.absolutePath,language.model,TessBaseAPI.OEM_LSTM_ONLY)){"Could not load Bangla OCR"}
   engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
   engine.setImage(bitmap)
   engine.getUTF8Text().orEmpty().trim()
  } finally { engine.recycle() }
 }
}
