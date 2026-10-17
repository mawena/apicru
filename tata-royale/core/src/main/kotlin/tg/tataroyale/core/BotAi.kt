package tg.tataroyale.core

import kotlin.math.max
import kotlin.random.Random

/**
 * Adversaire contrôlé par l'ordinateur. Il raisonne dans son propre repère
 * (son camp en bas) grâce à [Arena.localY], ce qui le rend utilisable pour
 * les deux équipes.
 */
class BotAi(
    private val battle: Battle,
    val team: Team = Team.ENEMY,
    private val rng: Random = Random.Default,
    /** 0.5 = facile, 1 = normal, 1.5 = difficile. */
    private val difficulty: Float = 1f,
) {
    private var thinkTimer = 2f
    private var attackThreshold = 8f

    private val state get() = battle.state(team)

    fun update(dt: Float) {
        if (battle.isOver) return
        thinkTimer -= dt
        if (thinkTimer > 0f) return
        thinkTimer = 0.7f / difficulty + rng.nextFloat() * 0.6f

        if (trySpell()) return
        val threats = battle.troops.filter { it.team != team && it.alive && localY(it.pos) > Arena.RIVER_TOP - 1f }
        val threatValue = threats.sumOf { it.card.cost.toDouble() / it.card.count }
        val deepest = threats.maxOfOrNull { localY(it.pos) } ?: 0f
        if (threats.isNotEmpty() && (threatValue >= 2.5 || deepest > 21f)) {
            defend(threats)
            return
        }
        if (state.kola >= attackThreshold) {
            if (attack()) attackThreshold = 6.5f + rng.nextFloat() * 3.5f
        }
    }

    private fun localY(p: Vec2) = Arena.localY(team, p.y)

    private fun world(x: Float, localY: Float) = Arena.toWorld(team, x, localY)

    private fun affordable(): List<Pair<Int, CardDef>> =
        state.hand.cards.withIndex().filter { it.value.cost <= state.kola }.map { it.index to it.value }

    private fun play(index: Int, pos: Vec2): Boolean = battle.playCard(team, index, pos)

    /** Sort sur un groupe d'ennemis rentable, ou pour achever une tour. */
    private fun trySpell(): Boolean {
        val spells = affordable().filter { it.second.isSpell }
        if (spells.isEmpty()) return false
        val foes = battle.troops.filter { it.team != team && it.alive }
        for ((index, card) in spells) {
            val finishable = battle.towers.firstOrNull {
                it.team != team && it.alive && it.hp <= card.damage * card.towerDamageFactor
            }
            if (finishable != null) return play(index, finishable.pos)

            var bestValue = 0.0
            var bestPos: Vec2? = null
            for (f in foes) {
                val group = foes.filter { it.pos.dist(f.pos) <= card.spellRadius * 0.8f }
                val value = group.sumOf { it.card.cost.toDouble() / it.card.count }
                if (value > bestValue) {
                    bestValue = value
                    val cx = group.map { it.pos.x }.average().toFloat()
                    val cy = group.map { it.pos.y }.average().toFloat()
                    bestPos = Vec2(cx, cy)
                }
            }
            if (bestPos != null && bestValue >= card.cost + 1.0) return play(index, bestPos)
        }
        return false
    }

    private fun defend(threats: List<Troop>): Boolean {
        val main = threats.maxBy { localY(it.pos) }
        val needAir = main.isFlying
        val swarm = threats.count { it.pos.dist(main.pos) < 3f } >= 3
        val candidates = affordable().filter { (_, c) ->
            !c.isSpell && c.targets != Targets.BUILDINGS && (!needAir || c.hitsAir)
        }
        if (candidates.isEmpty()) return false
        val (index, card) = candidates.maxBy { (_, c) ->
            var score = c.hp * c.count / 400.0 + c.damage * c.count / c.hitSpeed / 60.0
            if (swarm && c.splashRadius > 0f) score += 6.0
            if (main.card.targets == Targets.BUILDINGS && c.kind == CardKind.BUILDING) score += 5.0
            score - c.cost * 1.5 + rng.nextDouble() * 2.0
        }
        val mx = main.pos.x.coerceIn(1f, Arena.WIDTH - 1f)
        val my = localY(main.pos)
        val pos = when {
            card.kind == CardKind.BUILDING -> world(Arena.WIDTH / 2f, 22f)
            card.range >= 3f -> world(mx, (my + 4f).coerceIn(Arena.RIVER_BOTTOM + 1f, 30f))
            else -> world(mx, max(my + 1.5f, Arena.RIVER_BOTTOM + 1f).coerceAtMost(30f))
        }
        return play(index, pos)
    }

    private fun attack(): Boolean {
        val troopsInHand = affordable().filter { !it.second.isSpell && it.second.kind == CardKind.TROOP }
        if (troopsInHand.isEmpty()) return false

        // Couloir où la takienta adverse est la plus fragile.
        val lane = battle.towers
            .filter { it.team != team && it.kind == TowerKind.PRINCESS }
            .minBy { it.hpRatio + rng.nextFloat() * 0.3f }
            .lane
        val laneX = Arena.BRIDGES_X[lane]

        val ownPush = battle.troops.filter {
            it.team == team && it.alive && it.card.isTank && kotlin.math.abs(it.pos.x - laneX) < 4f
        }
        val tank = troopsInHand.filter { it.second.isTank }.randomOrNull(rng)
        val (index, card) = if (ownPush.isEmpty() && tank != null) tank else troopsInHand.random(rng)

        val pos = when {
            card.isTank && card.speed < 1f -> world(laneX, 27.5f)
            card.isTank -> world(laneX, 19f)
            ownPush.isNotEmpty() -> {
                val lead = ownPush.minBy { localY(it.pos) }
                world(laneX, (localY(lead.pos) + 2f).coerceIn(Arena.RIVER_BOTTOM + 1f, 30f))
            }
            else -> world(laneX, 24f)
        }
        return play(index, pos)
    }
}
