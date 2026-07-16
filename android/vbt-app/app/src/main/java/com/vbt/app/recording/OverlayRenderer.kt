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
 * wideo. To jedyne źródło wyglądu wypalanej grafiki - używa go i podgląd (pośrednio,
 * przez ten sam układ w Compose), i [MetricsOverlay] podczas eksportu przez Media3.
 *
 * Wszystkie rozmiary skalują się względem krótszego boku klatki ([scale]), więc
 * nakładka wygląda tak samo niezależnie od rozdzielczości nagrania (HD/FHD, pion/poziom).
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

    /**
     * Renderuje nakładkę dla [snapshot] na nowej Bitmapie [width] x [height].
     * Bitmapa jest w pełni przezroczysta poza narysowanymi elementami.
     */
    fun render(snapshot: OverlaySnapshot?, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (snapshot == null) return bmp

        val scale = min(width, height) / 1080f
        val pad = 40f * scale

        // ---- Górny pasek: ćwiczenie • ciężar • zawodnik ----
        labelPaint.textSize = 34f * scale
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

        // ---- Dolny panel z metrykami ----
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

        val col1 = pad + 48f * scale
        // Duża prędkość ostatniego powtórzenia (albo live, gdy brak powtórzeń)
        val bigVel = if (snapshot.lastRepMeanVelocityMs > 0f) snapshot.lastRepMeanVelocityMs
        else snapshot.liveVelocityMs
        textPaint.color = zoneColor
        textPaint.textSize = 150f * scale
        canvas.drawText(fmt2(bigVel), col1, panelTop + 180f * scale, textPaint)
        labelPaint.color = 0xFFAAAAAA.toInt()
        labelPaint.textSize = 40f * scale
        val velW = textPaint.measureText(fmt2(bigVel))
        canvas.drawText("m/s", col1 + velW + 16f * scale, panelTop + 180f * scale, labelPaint)

        // Etykieta strefy pod prędkością
        labelPaint.color = zoneColor
        labelPaint.textSize = 42f * scale
        canvas.drawText(snapshot.zone.label.uppercase(), col1, panelTop + 250f * scale, labelPaint)

        // Prawa kolumna: licznik powtórzeń, moc, peak
        val colR = width - pad - 340f * scale
        textPaint.color = teal
        textPaint.textSize = 96f * scale
        canvas.drawText("REP ${snapshot.repCount}", colR, panelTop + 110f * scale, textPaint)

        labelPaint.color = Color.WHITE
        labelPaint.textSize = 46f * scale
        canvas.drawText("Moc: ${snapshot.lastRepPowerW.toInt()} W", colR, panelTop + 180f * scale, labelPaint)
        canvas.drawText("Peak: ${fmt2(snapshot.lastRepPeakVelocityMs)} m/s", colR, panelTop + 240f * scale, labelPaint)

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

    private fun fmt2(v: Float): String = String.format("%.2f", v)
    private fun fmtKg(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)
}
