package tg.tataroyale

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Petits utilitaires pour construire l'interface sans fichiers de layout. */
object Ui {
    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radius: Float, strokeColor: Int = Color.TRANSPARENT, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            setCornerRadius(radius)
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    fun text(context: Context, value: String, sizeSp: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }

    fun button(context: Context, label: String, color: Int, onClick: () -> Unit) = Button(context).apply {
        text = label
        textSize = 20f
        setAllCaps(false)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setShadowLayer(3f, 0f, 2f, Color.argb(160, 0, 0, 0))
        background = rounded(color, dp(context, 14f).toFloat(), Palette.TOGO_YELLOW, dp(context, 3f))
        val pad = dp(context, 14f)
        setPadding(pad, pad, pad, pad)
        setOnClickListener { onClick() }
    }

    fun matchWidth(context: Context, marginDp: Float = 8f) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(context, marginDp)
            setMargins(m, m, m, m)
        }
}

/** Bandeau décoratif aux motifs de pagne kente. */
class KenteStripView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        drawKente(canvas, paint, 0f, 0f, width.toFloat(), height.toFloat())
    }

    companion object {
        fun drawKente(canvas: Canvas, paint: Paint, left: Float, top: Float, right: Float, bottom: Float) {
            val h = bottom - top
            val block = h * 1.6f
            var x = left
            var i = 0
            paint.style = Paint.Style.FILL
            while (x < right) {
                val w = minOf(block, right - x)
                paint.color = Palette.KENTE[i % Palette.KENTE.size]
                canvas.drawRect(x, top, x + w, bottom, paint)
                // Petits motifs géométriques à l'intérieur de chaque bloc.
                paint.color = if (i % 2 == 0) Palette.BLACK else Palette.TOGO_YELLOW
                val inset = h * 0.3f
                if (i % 3 == 0) {
                    canvas.drawRect(x + inset, top + inset, x + w - inset, bottom - inset, paint)
                } else {
                    val stripe = h * 0.12f
                    canvas.drawRect(x, top + h / 2 - stripe, x + w, top + h / 2 + stripe, paint)
                }
                x += block
                i++
            }
        }
    }
}
