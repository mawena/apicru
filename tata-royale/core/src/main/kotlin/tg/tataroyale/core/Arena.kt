package tg.tataroyale.core

import kotlin.math.abs

/**
 * Géométrie de l'arène : 18 x 32 cases. Le joueur occupe la moitié basse,
 * l'adversaire la moitié haute ; le fleuve Mono les sépare, franchi par deux ponts.
 */
object Arena {
    const val WIDTH = 18f
    const val HEIGHT = 32f
    const val RIVER_TOP = 15f
    const val RIVER_BOTTOM = 17f
    const val RIVER_MID = (RIVER_TOP + RIVER_BOTTOM) / 2f
    const val BRIDGE_HALF_WIDTH = 1f
    val BRIDGES_X = floatArrayOf(3.5f, 14.5f)

    /** Limite (dans le repère du joueur) de la « poche » ouverte quand une tour adverse tombe. */
    const val POCKET_Y = 10f

    /** -1 = côté nord (adversaire), 1 = côté sud (joueur), 0 = dans le lit du fleuve. */
    fun side(y: Float): Int = when {
        y < RIVER_TOP -> -1
        y > RIVER_BOTTOM -> 1
        else -> 0
    }

    fun nearestBridge(x: Float): Float =
        if (abs(x - BRIDGES_X[0]) <= abs(x - BRIDGES_X[1])) BRIDGES_X[0] else BRIDGES_X[1]

    fun onBridge(x: Float): Boolean = abs(x - nearestBridge(x)) <= BRIDGE_HALF_WIDTH

    /** Pont minimisant le détour pour aller de [fromX] à [toX]. */
    fun bestBridge(fromX: Float, toX: Float): Float =
        BRIDGES_X.minBy { abs(fromX - it) + abs(it - toX) }

    /**
     * Prochain point de passage d'une troupe au sol : si la destination est de
     * l'autre côté du fleuve, la troupe passe d'abord par un pont.
     */
    fun waypoint(pos: Vec2, dest: Vec2): Vec2 {
        val sidePos = side(pos.y)
        val sideDest = side(dest.y)
        if (sidePos == sideDest) return dest
        if (sidePos == 0) {
            if (sideDest == 0) return dest
            val b = nearestBridge(pos.x)
            return Vec2(b, if (sideDest < 0) RIVER_TOP - 0.4f else RIVER_BOTTOM + 0.4f)
        }
        val b = bestBridge(pos.x, dest.x)
        val entryY = if (sidePos > 0) RIVER_BOTTOM + 0.2f else RIVER_TOP - 0.2f
        if (abs(pos.x - b) > 0.3f) return Vec2(b, entryY)
        val exitY = if (sidePos > 0) RIVER_TOP - 0.4f else RIVER_BOTTOM + 0.4f
        return Vec2(b, exitY)
    }

    /** Convertit une ordonnée vers le repère d'une équipe (son camp toujours en bas). */
    fun localY(team: Team, y: Float): Float = if (team == Team.PLAYER) y else HEIGHT - y

    fun toWorld(team: Team, x: Float, localY: Float): Vec2 = Vec2(x, localY(team, localY))
}
