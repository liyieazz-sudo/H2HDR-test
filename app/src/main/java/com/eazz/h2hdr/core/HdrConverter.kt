package com.eazz.h2hdr.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.ln
import kotlin.math.max

class HdrConverter(private val context: Context) {

    data class ConversionResult(
        val ultraHdrBitmap: Bitmap,
        val sdrBase: Bitmap,
        val gainmapBitmap: Bitmap
    )

    suspend fun loadHlgHeif(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            if (info.colorSpace?.isWideGamut == true) {
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.BT2020_HLG))
            }
        }
    }

    suspend fun convertToGainmapHdr(hlgBitmap: Bitmap): ConversionResult = withContext(Dispatchers.Default) {
        val width = hlgBitmap.width
        val height = hlgBitmap.height

        val sdrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val gainmapBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val hlgPixels = IntArray(width * height)
        hlgBitmap.getPixels(hlgPixels, 0, width, 0, 0, width, height)

        val sdrPixels = IntArray(width * height)
        val gainmapPixels = ByteArray(width * height)

        val maxHeadroom = 4.0f
        val minHeadroom = 1.0f
        val log2Max = ln(maxHeadroom) / ln(2.0f)
        val offsetSdr = 0.015625f
        val offsetHdr = 0.015625f

        for (i in hlgPixels.indices) {
            val pixel = hlgPixels[i]
            val r = Color.red(pixel) / 255.0f
            val g = Color.green(pixel) / 255.0f
            val b = Color.blue(pixel) / 255.0f

            val yHlg = 0.2627f * r + 0.6780f * g + 0.0593f * b

            val sdrFactor = if (yHlg > 0.75f) {
                0.75f + (yHlg - 0.75f) * 0.25f
            } else {
                yHlg
            }
            val ySdr = max(0.001f, sdrFactor / 0.8125f)

            val rSdr = (r * (ySdr / max(0.0001f, yHlg))).coerceIn(0f, 1f)
            val gSdr = (g * (ySdr / max(0.0001f, yHlg))).coerceIn(0f, 1f)
            val bSdr = (b * (ySdr / max(0.0001f, yHlg))).coerceIn(0f, 1f)

            sdrPixels[i] = Color.argb(
                255,
                (rSdr * 255).toInt(),
                (gSdr * 255).toInt(),
                (bSdr * 255).toInt()
            )

            val gain = (ln((yHlg + offsetHdr) / (ySdr + offsetSdr)) / ln(2.0f))
            val normGain = ((gain - 0f) / log2Max).coerceIn(0f, 1f)
            gainmapPixels[i] = (normGain * 255).toInt().toByte()
        }

        sdrBitmap.setPixels(sdrPixels, 0, width, 0, 0, width, height)
        gainmapBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(gainmapPixels))

        val gainmap = Gainmap(gainmapBitmap).apply {
            setDisplayRatioForFullHdr(maxHeadroom)
            setMinDisplayRatioForHdrTransition(minHeadroom)
            setGainmapRatioMin(1.0f, 1.0f, 1.0f)
            setGainmapRatioMax(maxHeadroom, maxHeadroom, maxHeadroom)
            setGamma(1.0f, 1.0f, 1.0f)
            setEpsilonSdr(offsetSdr, offsetSdr, offsetSdr)
            setEpsilonHdr(offsetHdr, offsetHdr, offsetHdr)
        }

        sdrBitmap.gainmap = gainmap

        ConversionResult(
            ultraHdrBitmap = sdrBitmap,
            sdrBase = sdrBitmap,
            gainmapBitmap = gainmapBitmap
        )
    }

    suspend fun saveUltraHdr(bitmap: Bitmap, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        outputStream.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
    }
}
