package net.johnbiz.countyline.notify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import net.johnbiz.countyline.core.OutlineProjection
import kotlin.math.max
import kotlin.math.min

/** Renders a county outline into a transparent bitmap for notifications. */
object OutlineBitmaps {
    const val ICON_PX = 256
    const val PICTURE_WIDTH_PX = 1024
    const val PICTURE_HEIGHT_PX = 512

    // A mid green that reads on both light and dark notification shades.
    private const val STROKE_COLOR = 0xFF2E7D32.toInt()
    private const val FILL_COLOR = 0x592E7D32

    fun render(rings: List<DoubleArray>, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val shortSide = min(width, height).toFloat()
        val projected = OutlineProjection.fit(rings, width.toFloat(), height.toFloat(), padding = shortSide * 0.08f)

        // Even-odd fill so holes (a county surrounding an independent city) stay empty.
        val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (ring in projected) {
            if (ring.size < 4) continue
            path.moveTo(ring[0], ring[1])
            var i = 2
            while (i + 1 < ring.size) {
                path.lineTo(ring[i], ring[i + 1])
                i += 2
            }
            path.close()
        }

        val canvas = Canvas(bitmap)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = FILL_COLOR
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, shortSide / 96f)
            strokeJoin = Paint.Join.ROUND
            color = STROKE_COLOR
        })
        return bitmap
    }
}
