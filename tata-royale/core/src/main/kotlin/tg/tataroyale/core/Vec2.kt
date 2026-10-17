package tg.tataroyale.core

import kotlin.math.sqrt

/** Vecteur 2D immuable, exprimé en cases de l'arène. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)

    val length: Float get() = sqrt(x * x + y * y)

    fun dist(o: Vec2): Float {
        val dx = x - o.x
        val dy = y - o.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}
