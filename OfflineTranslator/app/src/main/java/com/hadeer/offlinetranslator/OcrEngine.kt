package com.hadeer.offlinetranslator

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

object OcrEngine {
    data class Result(val text: String, val confidence: Int)

    fun prepare(context: Context) {
        val base = File(context.filesDir, "tesseract")
        val tessdata = File(base, "tessdata")
        tessdata.mkdirs()
        copyAsset(context, "tessdata/fas.traineddata", File(tessdata, "fas.traineddata"))
        copyAsset(context, "tessdata/ara.traineddata", File(tessdata, "ara.traineddata"))
        copyAsset(context, "tessdata/eng.traineddata", File(tessdata, "eng.traineddata"))
    }

    fun recognize(context: Context, bitmap: Bitmap): Result {
        val dataPath = File(context.filesDir, "tesseract").absolutePath
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(dataPath, "fas+ara+eng")) Result("", 0)
            else {
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                tess.setImage(bitmap)
                val text = tess.getUTF8Text()?.trim().orEmpty()
                val confidence = try { tess.meanConfidence() } catch (_: Exception) { 0 }
                Result(text, confidence)
            }
        } finally {
            tess.recycle()
        }
    }

    private fun copyAsset(context: Context, asset: String, dest: File) {
        if (dest.exists() && dest.length() > 100_000L) return
        dest.parentFile?.mkdirs()
        context.assets.open(asset).use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        }
    }
}
