package com.kevcoder.carbcalculator.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File

object ImageProcessor {

    const val DEFAULT_MAX_EDGE_PX = 1280

    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxEdgePx: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0) return 1
        val longest = maxOf(srcWidth, srcHeight)
        var sampleSize = 1
        while (longest / (sampleSize * 2) >= maxEdgePx) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun targetDimensions(srcWidth: Int, srcHeight: Int, maxEdgePx: Int): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return srcWidth to srcHeight
        val longest = maxOf(srcWidth, srcHeight)
        if (longest <= maxEdgePx) return srcWidth to srcHeight
        val scale = maxEdgePx.toDouble() / longest.toDouble()
        val w = (srcWidth * scale).toInt().coerceAtLeast(1)
        val h = (srcHeight * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    fun processForUpload(
        file: File,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        quality: Int = 80,
    ): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return file.readBytes()
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: return file.readBytes()

        val rotation = readExifRotation(file)
        val oriented = if (rotation != 0) rotate(decoded, rotation) else decoded

        val (targetW, targetH) = targetDimensions(oriented.width, oriented.height, maxEdgePx)
        val scaled = if (targetW != oriented.width || targetH != oriented.height) {
            Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                if (it !== oriented) oriented.recycle()
            }
        } else oriented

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            scaled.recycle()
            out.toByteArray()
        }
    }

    private fun readExifRotation(file: File): Int =
        try {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
