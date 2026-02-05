package com.example.vod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

// Suppressing at the top level to clean up the entire file
@Suppress("DEPRECATION")
class BlurTransformation(
    private val context: Context,
    private val radius: Float = 10f,
    private val sampling: Float = 1f
) : Transformation {

    init {
        require(radius in 0f..25f) { "radius must be > 0 and <= 25" }
        require(sampling > 0f) { "sampling must be > 0" }
    }

    override val cacheKey: String = "${javaClass.name}-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val scaledWidth = (input.width / sampling).toInt().coerceAtLeast(1)
        val scaledHeight = (input.height / sampling).toInt().coerceAtLeast(1)

        // Using KTX createBitmap as suggested by your IDE
        val output = createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.scale(1 / sampling, 1 / sampling)
        canvas.drawBitmap(input, 0f, 0f, paint)

        return try {
            blur(context, output, radius)
        } catch (error: Exception) {
            // "error" is now used via log or at least acknowledged
            android.util.Log.e("BlurTransform", "RenderScript failed", error)
            output
        }
    }

    private fun blur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        var rs: RenderScript? = null
        var inputAlloc: Allocation? = null
        var outputAlloc: Allocation? = null
        var blurScript: ScriptIntrinsicBlur? = null

        try {
            rs = RenderScript.create(context)
            inputAlloc = Allocation.createFromBitmap(
                rs,
                bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            outputAlloc = Allocation.createTyped(rs, inputAlloc.type)
            blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

            blurScript.setInput(inputAlloc)
            blurScript.setRadius(radius)
            blurScript.forEach(outputAlloc)
            outputAlloc.copyTo(bitmap)
        } finally {
            rs?.destroy()
            inputAlloc?.destroy()
            outputAlloc?.destroy()
            blurScript?.destroy()
        }
        return bitmap
    }
}