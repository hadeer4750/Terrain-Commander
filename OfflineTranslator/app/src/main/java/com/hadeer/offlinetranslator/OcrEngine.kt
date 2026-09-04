package com.hadeer.offlinetranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        var enhanced: Bitmap? = null
        return try {
            if (!tess.init(dataPath, "fas+ara+eng")) return Result("", 0)

            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setImage(bitmap)
            val first = Result(
                tess.getUTF8Text()?.trim().orEmpty(),
                try { tess.meanConfidence() } catch (_: Exception) { 0 }
            )

            enhanced = enhanceForPrice(bitmap)
            tess.clear()
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            tess.setImage(enhanced)
            val second = Result(
                tess.getUTF8Text()?.trim().orEmpty(),
                try { tess.meanConfidence() } catch (_: Exception) { 0 }
            )

            when {
                second.text.isBlank() -> first
                first.text.isBlank() -> second
                MoneyUtils.parsePriceAmount(second.text) != null && MoneyUtils.parsePriceAmount(first.text) == null -> second
                MoneyUtils.parsePriceAmount(first.text) != null && MoneyUtils.parsePriceAmount(second.text) == null -> first
                second.confidence >= first.confidence -> second
                else -> first
            }
        } finally {
            if (enhanced != null && enhanced !== bitmap && !enhanced!!.isRecycled) enhanced!!.recycle()
            tess.recycle()
        }
    }

    private fun enhanceForPrice(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        val minSide = minOf(source.width, source.height)
        val scale = when {
            maxSide > 2400 -> 2400f / maxSide.toFloat()
            minSide < 1000 -> 1000f / minSide.toFloat()
            else -> 1f
        }
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (width != source.width || height != source.height) {
            Bitmap.createScaledBitmap(source, width, height, true)
        } else source

        val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val saturation = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        saturation.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(saturation)
        }
        Canvas(output).drawBitmap(scaled, 0f, 0f, paint)
        if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        return output
    }

    private fun copyAsset(context: Context, asset: String, dest: File) {
        if (dest.exists() && dest.length() > 100_000L) return
        dest.parentFile?.mkdirs()
        context.assets.open(asset).use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        }
    }
}
