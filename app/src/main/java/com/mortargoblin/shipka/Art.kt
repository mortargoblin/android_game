package com.mortargoblin.shipka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.random.Random

/** All the procedural (no image assets) drawing: soldiers, kebabs, battlefield. */
object Art {
    // Russian Imperial infantry, 1877: dark green tunic, white summer cap, red shoulder straps,
    // rolled greatcoat over the shoulder, Berdan rifle with a long bayonet.
    const val RUS_COAT = 0xFF2E4A2B.toInt()
    const val RUS_COAT_DARK = 0xFF1C3019.toInt()
    const val RUS_CAP = 0xFFF2F0E6.toInt()
    const val RUS_STRAP = 0xFFC62828.toInt()
    const val RUS_ROLL = 0xFF8C8577.toInt()

    // Ottoman Nizam infantry: navy tunic with red trim, red fez with black tassel.
    const val TUR_COAT = 0xFF1F2D5C.toInt()
    const val TUR_COAT_DARK = 0xFF121B3A.toInt()
    const val FEZ = 0xFFC8102E.toInt()
    const val FEZ_TOP = 0xFF93091F.toInt()

    // Bashi-bazouk irregulars: brown/crimson clothes, green sash, big white turban.
    const val BASHI_COAT = 0xFF7B3F2A.toInt()
    const val BASHI_COAT_DARK = 0xFF4E2718.toInt()
    const val BASHI_SASH = 0xFF2E7D32.toInt()
    const val TURBAN = 0xFFF5F1E4.toInt()

    const val SKIN = 0xFFE0B48C.toInt()
    const val WOOD = 0xFF6D4526.toInt()
    const val STEEL = 0xFFC9CED6.toInt()
    const val BOOT = 0xFF1A1A1A.toInt()

    const val GRASS = 0xFF7F8F4E.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        fill.color = color
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rect, fill)
    }

    private fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, r, fill)
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun outlineCircle(c: Canvas, cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawCircle(cx, cy, r, stroke)
    }

    private fun shadow(c: Canvas, x: Float, y: Float, r: Float) {
        oval(c, x + r * 0.15f, y + r * 0.3f, r * 1.05f, r * 0.8f, 0x44000000)
    }

    /** Two boots that swing back and forth while walking. Drawn in body space (facing +x). */
    private fun feet(c: Canvas, r: Float, walkPhase: Float) {
        val swing = sin(walkPhase) * r * 0.45f
        oval(c, swing, -r * 0.38f, r * 0.32f, r * 0.2f, BOOT)
        oval(c, -swing, r * 0.38f, r * 0.32f, r * 0.2f, BOOT)
    }

    private fun rifle(c: Canvas, r: Float, bayonet: Boolean) {
        val y = r * 0.42f
        line(c, -r * 0.3f, y, r * 1.55f, y, r * 0.2f, WOOD)
        line(c, r * 1.2f, y, r * 1.95f, y, r * 0.12f, 0xFF333333.toInt())
        if (bayonet) line(c, r * 1.95f, y, r * 2.75f, y, r * 0.07f, STEEL)
        circle(c, r * 0.55f, y, r * 0.17f, SKIN)
        circle(c, r * 1.15f, y, r * 0.17f, SKIN)
    }

    private fun scimitar(c: Canvas, r: Float, walkPhase: Float) {
        c.save()
        c.rotate(sin(walkPhase * 0.5f) * 12f, r * 0.5f, r * 0.5f)
        stroke.color = STEEL
        stroke.strokeWidth = r * 0.14f
        path.reset()
        path.moveTo(r * 0.5f, r * 0.55f)
        path.quadTo(r * 1.5f, r * 0.9f, r * 2.2f, r * 0.1f)
        c.drawPath(path, stroke)
        line(c, r * 0.35f, r * 0.55f, r * 0.62f, r * 0.55f, r * 0.2f, 0xFFB8860B.toInt())
        circle(c, r * 0.5f, r * 0.55f, r * 0.17f, SKIN)
        c.restore()
        // Off hand
        circle(c, r * 0.35f, -r * 0.75f, r * 0.16f, SKIN)
    }

    fun drawRussian(c: Canvas, x: Float, y: Float, angle: Float, r: Float, walkPhase: Float, blink: Boolean) {
        shadow(c, x, y, r)
        if (blink) return
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        feet(c, r, walkPhase)
        rifle(c, r, bayonet = true)
        // Shoulders / tunic
        oval(c, 0f, 0f, r * 0.6f, r, RUS_COAT)
        stroke.color = RUS_COAT_DARK
        stroke.strokeWidth = r * 0.08f
        rect.set(-r * 0.6f, -r, r * 0.6f, r)
        c.drawOval(rect, stroke)
        // Rolled greatcoat slung across the body (the famous "skatka")
        line(c, -r * 0.35f, -r * 0.8f, r * 0.25f, r * 0.75f, r * 0.3f, RUS_ROLL)
        // Red shoulder straps
        line(c, -r * 0.15f, -r * 0.72f, r * 0.15f, -r * 0.72f, r * 0.16f, RUS_STRAP)
        line(c, -r * 0.15f, r * 0.72f, r * 0.15f, r * 0.72f, r * 0.16f, RUS_STRAP)
        // White peaked cap seen from above, black visor at the front
        oval(c, r * 0.5f, 0f, r * 0.16f, r * 0.34f, 0xFF111111.toInt())
        circle(c, 0f, 0f, r * 0.5f, RUS_CAP)
        outlineCircle(c, 0f, 0f, r * 0.5f, r * 0.06f, 0xFF9E9A8E.toInt())
        c.restore()
    }

    fun drawTurk(c: Canvas, type: EnemyType, x: Float, y: Float, angle: Float, r: Float, walkPhase: Float, flash: Float) {
        shadow(c, x, y, r)
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        feet(c, r, walkPhase)
        if (type == EnemyType.NIZAM) {
            rifle(c, r, bayonet = false)
            oval(c, 0f, 0f, r * 0.6f, r, TUR_COAT)
            stroke.color = FEZ
            stroke.strokeWidth = r * 0.08f
            rect.set(-r * 0.6f, -r, r * 0.6f, r)
            c.drawOval(rect, stroke)
            // Leather cartridge belt over one shoulder
            line(c, -r * 0.3f, -r * 0.75f, r * 0.3f, r * 0.75f, r * 0.13f, 0xFF4E342E.toInt())
            drawFez(c, 0f, 0f, r * 0.55f, walkPhase)
        } else {
            scimitar(c, r, walkPhase)
            oval(c, 0f, 0f, r * 0.62f, r, BASHI_COAT)
            stroke.color = BASHI_COAT_DARK
            stroke.strokeWidth = r * 0.08f
            rect.set(-r * 0.62f, -r, r * 0.62f, r)
            c.drawOval(rect, stroke)
            line(c, 0f, -r * 0.85f, 0f, r * 0.85f, r * 0.22f, BASHI_SASH)
            drawTurban(c, 0f, 0f, r * 0.56f)
        }
        if (flash > 0f) {
            circle(c, 0f, 0f, r * 1.05f, Color.argb((flash * 200).toInt().coerceIn(0, 255), 255, 255, 255))
        }
        c.restore()
    }

    /** Fez seen from above: red cylinder, darker crown and a swinging black tassel. */
    fun drawFez(c: Canvas, x: Float, y: Float, r: Float, sway: Float) {
        circle(c, x, y, r, FEZ)
        circle(c, x, y, r * 0.66f, FEZ_TOP)
        val tx = x - r * 1.05f
        val ty = y + sin(sway * 0.7f) * r * 0.5f
        line(c, x, y, tx, ty, r * 0.16f, 0xFF111111.toInt())
        circle(c, tx, ty, r * 0.2f, 0xFF111111.toInt())
    }

    fun drawTurban(c: Canvas, x: Float, y: Float, r: Float) {
        circle(c, x, y, r, TURBAN)
        stroke.color = 0xFFBDB6A2.toInt()
        stroke.strokeWidth = r * 0.1f
        rect.set(x - r * 0.75f, y - r * 0.75f, x + r * 0.75f, y + r * 0.75f)
        c.drawArc(rect, 20f, 230f, false, stroke)
        rect.set(x - r * 0.45f, y - r * 0.45f, x + r * 0.45f, y + r * 0.45f)
        c.drawArc(rect, 200f, 220f, false, stroke)
        circle(c, x, y, r * 0.25f, FEZ)
    }

    /** A proper şiş kebab: meat, peppers, tomato and onion on a steel skewer. */
    fun drawKebab(c: Canvas, x: Float, y: Float, s: Float, glow: Float) {
        if (glow > 0f) {
            circle(c, x, y, s * 1.3f, Color.argb((glow * 90).toInt().coerceIn(0, 255), 255, 214, 64))
        }
        c.save()
        c.translate(x, y)
        c.rotate(-30f)
        oval(c, s * 0.1f, s * 0.35f, s * 1.1f, s * 0.25f, 0x33000000)
        line(c, -s * 1.15f, 0f, s * 1.05f, 0f, s * 0.07f, 0xFF9E9E9E.toInt())
        outlineCircle(c, -s * 1.25f, 0f, s * 0.12f, s * 0.06f, 0xFF9E9E9E.toInt())
        val step = s * 0.33f
        var px = -s * 0.8f
        for (i in 0 until 6) {
            when (i % 6) {
                0, 2, 4 -> {
                    fill.color = 0xFF7A3B1A.toInt()
                    rect.set(px - step * 0.48f, -s * 0.27f, px + step * 0.48f, s * 0.27f)
                    c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                    fill.color = 0xFFA0582D.toInt()
                    rect.set(px - step * 0.3f, -s * 0.2f, px + step * 0.15f, -s * 0.05f)
                    c.drawRoundRect(rect, s * 0.05f, s * 0.05f, fill)
                }
                1 -> oval(c, px, 0f, step * 0.38f, s * 0.26f, 0xFF43A047.toInt())
                3 -> circle(c, px, 0f, s * 0.24f, 0xFFE53935.toInt())
                5 -> oval(c, px, 0f, step * 0.35f, s * 0.24f, 0xFFF3E5D0.toInt())
            }
            px += step
        }
        c.restore()
    }

    /** Pre-rendered battlefield near Shipka: trampled grass, dirt, rocks and tufts. */
    fun makeBackground(w: Int, h: Int, u: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(GRASS)
        val rnd = Random(1877)
        // Light and dark patches
        repeat(60) {
            val color = if (rnd.nextBoolean()) 0x22FFF3C4 else 0x22203010
            oval(c, rnd.nextFloat() * w, rnd.nextFloat() * h, (40 + rnd.nextFloat() * 120) * u, (30 + rnd.nextFloat() * 80) * u, color)
        }
        // A dusty road running across the field
        stroke.color = 0x55A1887F
        stroke.strokeWidth = 90 * u
        path.reset()
        path.moveTo(-50f, h * 0.7f)
        path.cubicTo(w * 0.3f, h * 0.45f, w * 0.6f, h * 0.95f, w + 50f, h * 0.35f)
        c.drawPath(path, stroke)
        stroke.color = 0x33795548
        stroke.strokeWidth = 60 * u
        c.drawPath(path, stroke)
        // Rocks
        repeat(14) {
            val rx = rnd.nextFloat() * w
            val ry = rnd.nextFloat() * h
            val rr = (6 + rnd.nextFloat() * 14) * u
            oval(c, rx + rr * 0.2f, ry + rr * 0.3f, rr, rr * 0.75f, 0x33000000)
            oval(c, rx, ry, rr, rr * 0.75f, 0xFF8D8A80.toInt())
            oval(c, rx - rr * 0.25f, ry - rr * 0.2f, rr * 0.45f, rr * 0.3f, 0xFFB0ADA3.toInt())
        }
        // Grass tufts
        repeat(260) {
            val tx = rnd.nextFloat() * w
            val ty = rnd.nextFloat() * h
            val col = if (rnd.nextBoolean()) 0xFF5E6E36.toInt() else 0xFF9AA862.toInt()
            for (k in -1..1) line(c, tx, ty, tx + k * 4 * u, ty - 8 * u, 1.6f * u, col)
        }
        // A few wild poppies
        repeat(25) {
            circle(c, rnd.nextFloat() * w, rnd.nextFloat() * h, 2.6f * u, 0xFFD32F2F.toInt())
        }
        return bmp
    }
}
