package com.vbt.app.recording

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.vbt.app.ui.theme.VbtTeal
import kotlin.math.min

/**
 * Rysuje przezroczystą nakładkę z parametrami VBT na Bitmapie o wymiarach klatki
 * wideo. To jedyne źródło wyglądu wypalanej grafiki - używa go [MetricsOverlay]
 * podczas eksportu przez Media3.
 *
 * Zestaw pokazywanych metryk ([metrics]) jest konfigurowalny w Ustawieniach.
 * Wszystkie rozmiary skalują się względem krótszego boku klatki, więc nakładka
 * wygląda tak samo niezależnie od rozdzielczości (HD/FHD, pion/poziom).
 */
class OverlayRenderer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = 0xFFCCCCCC.toInt()
    }

    private val teal = VbtTeal.toArgb()

    fun render(
        snapshot: OverlaySnapshot?,
        metrics: List<OverlayMetric>,
        width: Int,
        height: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (snapshot == null) return bmp

        val scale = min(width, height) / 1080f
        val pad = 40f * scale

        // ---- Górny pasek: ćwiczenie • ciężar ----
        textPaint.textSize = 40f * scale
        textPaint.color = Color.WHITE
        val header = buildString {
            append(snapshot.exerciseName)
            if (snapshot.loadKg > 0f) append("  •  ${fmtKg(snapshot.loadKg)} kg")
        }
        drawPill(canvas, pad, pad, header, textPaint, scale)
        snapshot.athleteName?.takeIf { it.isNotBlank() && it != "You" }?.let { name ->
            labelPaint.color = 0xFFCCCCCC.toInt()
            labelPaint.textSize = 32f * scale
            canvas.drawText(name, pad + 12f * scale, pad + 90f * scale + labelPaint.textSize, labelPaint)
        }

        snapshot.heartRate?.let { hr ->
            textPaint.textSize = 40f * scale
            textPaint.color = 0xFFFF5252.toInt()
            val hrText = "♥ $hr"
            val w = textPaint.measureText(hrText)
            drawPill(canvas, width - pad - w - 48f * scale, pad, hrText, textPaint, scale)
        }

        // ---- Dolny panel ----
        val zoneColor = snapshot.zone.color.toArgb()
        val panelH = 300f * scale
        val panelTop = height - panelH - pad
        val panelRect = RectF(pad, panelTop, width - pad, height - pad)

        bgPaint.shader = LinearGradient(
            0f, panelTop, 0f, height - pad,
            0x00000000, 0xE6000000.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(panelRect, 32f * scale, 32f * scale, bgPaint)
        bgPaint.shader = null

        // Kolorowy pasek strefy po lewej
        accentPaint.color = zoneColor
        canvas.drawRoundRect(
            RectF(pad, panelTop, pad + 14f * scale, height - pad),
            8f * scale, 8f * scale, accentPaint
        )

        // Licznik powtórzeń + strefa (zawsze widoczne, u góry panelu)
        val innerLeft = pad + 48f * scale
        textPaint.color = teal
        textPaint.textSize = 60f * scale
        canvas.drawText("REP ${snapshot.repCount}", innerLeft, panelTop + 66f * scale, textPaint)
        labelPaint.color = zoneColor
        labelPaint.textSize = 40f * scale
        val repW = textPaint.measureText("REP ${snapshot.repCount}")
        canvas.drawText(snapshot.zone.label.uppercase(), innerLeft + repW + 30f * scale, panelTop + 60f * scale, labelPaint)

        // Kafelki wybranych metryk (równo rozłożone w szerokości panelu)
        if (metrics.isNotEmpty()) {
            val tilesLeft = innerLeft
            val tilesRight = width - pad - 30f * scale
            val tileW = (tilesRight - tilesLeft) / metrics.size
            val valueSize = (min(120f, 150f / metrics.size + 60f)) * scale
            val valueY = panelTop + 190f * scale
            val labelY = panelTop + 250f * scale

            metrics.forEachIndexed { i, metric ->
                val cx = tilesLeft + tileW * i + tileW / 2f
                val value = metric.format(metric.value(snapshot))
                textPaint.color = if (metric.isVelocity) zoneColor else teal
                textPaint.textSize = valueSize
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(value, cx, valueY, textPaint)

                labelPaint.color = 0xFFAAAAAA.toInt()
                labelPaint.textSize = 34f * scale
                labelPaint.textAlign = Paint.Align.CENTER
                val label = if (metric.unit.isEmpty()) metric.label else "${metric.label} (${metric.unit})"
                canvas.drawText(label, cx, labelY, labelPaint)
            }
            textPaint.textAlign = Paint.Align.LEFT
            labelPaint.textAlign = Paint.Align.LEFT
        }

        return bmp
    }

    private fun drawPill(canvas: Canvas, x: Float, y: Float, text: String, paint: Paint, scale: Float) {
        val padX = 24f * scale
        val padY = 14f * scale
        val w = paint.measureText(text)
        val h = paint.textSize
        bgPaint.color = 0xB3000000.toInt()
        canvas.drawRoundRect(
            RectF(x, y, x + w + padX * 2, y + h + padY * 2),
            18f * scale, 18f * scale, bgPaint
        )
        canvas.drawText(text, x + padX, y + padY + h * 0.85f, paint)
    }

    private fun fmtKg(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)
}
