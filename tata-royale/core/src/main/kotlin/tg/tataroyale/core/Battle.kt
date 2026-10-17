package tg.tataroyale.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulation complète d'un combat. Indépendante d'Android : l'application
 * appelle [update] à chaque image puis dessine l'état exposé.
 */
class Battle(
    playerDeck: List<CardDef>,
    enemyDeck: List<CardDef>,
    seed: Long = System.nanoTime(),
) {
    val rng = Random(seed)
    private var nextId = 1

    val player = PlayerState(Team.PLAYER, playerDeck, rng)
    val enemy = PlayerState(Team.ENEMY, enemyDeck, rng)

    val towers = mutableListOf<Tower>()
    val troops = mutableListOf<Troop>()
    val projectiles = mutableListOf<Projectile>()
    val spells = mutableListOf<PendingSpell>()
    val effects = mutableListOf<Effect>()

    var time = 0f
        private set
    var overtime = false
        private set
    var result: BattleResult? = null
        private set

    private var accumulator = 0f

    val isOver: Boolean get() = result != null
    val timeLeft: Float get() = max(0f, (if (overtime) REGULAR_TIME + OVERTIME else REGULAR_TIME) - time)
    val doubleKola: Boolean get() = time >= DOUBLE_KOLA_AT

    init {
        for (team in Team.entries) {
            towers += Tower(nextId++, team, Arena.toWorld(team, 3.5f, 25.5f), TowerKind.PRINCESS, 0)
            towers += Tower(nextId++, team, Arena.toWorld(team, 14.5f, 25.5f), TowerKind.PRINCESS, 1)
            towers += Tower(nextId++, team, Arena.toWorld(team, 9f, 29f), TowerKind.KING, -1)
        }
    }

    fun state(team: Team) = if (team == Team.PLAYER) player else enemy

    fun towersOf(team: Team) = towers.filter { it.team == team }

    // ------------------------------------------------------------------ Actions

    fun canDeploy(team: Team, card: CardDef, pos: Vec2): Boolean {
        if (isOver || state(team).kola < card.cost) return false
        if (pos.x < 0f || pos.x > Arena.WIDTH || pos.y < 0f || pos.y > Arena.HEIGHT) return false
        return card.isSpell || isInDeployZone(team, pos)
    }

    fun isInDeployZone(team: Team, pos: Vec2): Boolean {
        val y = Arena.localY(team, pos.y)
        if (pos.x < 0.5f || pos.x > Arena.WIDTH - 0.5f || y > Arena.HEIGHT - 0.5f) return false
        if (y >= Arena.RIVER_BOTTOM + 0.5f) return true
        if (y < Arena.POCKET_Y || y > Arena.RIVER_TOP - 0.3f) return false
        return pocketOpen(team, if (pos.x < Arena.WIDTH / 2f) 0 else 1)
    }

    /** Vrai si la takienta adverse de ce couloir est détruite. */
    fun pocketOpen(team: Team, lane: Int): Boolean =
        towers.none { it.team == team.opponent && it.kind == TowerKind.PRINCESS && it.lane == lane && it.alive }

    fun playCard(team: Team, handIndex: Int, pos: Vec2): Boolean {
        val st = state(team)
        val card = st.hand.cards.getOrNull(handIndex) ?: return false
        if (!canDeploy(team, card, pos)) return false
        st.kola -= card.cost
        st.hand.play(handIndex)
        deploy(team, card, pos)
        return true
    }

    private fun deploy(team: Team, card: CardDef, pos: Vec2) {
        if (card.isSpell) {
            spells += PendingSpell(team, card, pos, card.spellDelay)
            return
        }
        for (offset in formation(card.count)) {
            val troop = Troop(nextId++, team, pos + offset, card)
            troop.facing = if (team == Team.PLAYER) -HALF_PI else HALF_PI
            constrain(troop)
            troops += troop
        }
        effects += Effect(EffectKind.DEPLOY, pos, 1.2f, 0.5f)
    }

    private fun formation(count: Int): List<Vec2> = when (count) {
        1 -> listOf(Vec2.ZERO)
        2 -> listOf(Vec2(-0.5f, 0f), Vec2(0.5f, 0f))
        3 -> listOf(Vec2(0f, -0.5f), Vec2(-0.6f, 0.4f), Vec2(0.6f, 0.4f))
        else -> List(count) { i ->
            val a = i * 2f * PI / count
            Vec2(cos(a) * 0.8f, sin(a) * 0.8f)
        }
    }

    // ------------------------------------------------------------------ Boucle

    fun update(dt: Float) {
        for (e in effects) e.age += dt
        effects.removeAll { it.finished }
        if (isOver) return
        accumulator += min(dt, 0.25f)
        while (accumulator >= STEP && !isOver) {
            step(STEP)
            accumulator -= STEP
        }
    }

    private fun step(dt: Float) {
        time += dt
        val rate = KOLA_PER_SECOND * (if (doubleKola) 2f else 1f) * dt
        player.kola = min(MAX_KOLA, player.kola + rate)
        enemy.kola = min(MAX_KOLA, enemy.kola + rate)

        updateSpells(dt)
        for (t in troops.toList()) if (t.alive) updateTroop(t, dt)
        for (t in towers) updateTower(t, dt)
        updateProjectiles(dt)
        separate()

        val dead = troops.filter { !it.alive }
        for (d in dead) effects += Effect(EffectKind.DEATH, d.pos, d.radius + 0.4f, 0.45f)
        troops.removeAll(dead)
        projectiles.removeAll { it.done }

        checkTime()
    }

    // ------------------------------------------------------------------ Troupes

    private fun updateTroop(t: Troop, dt: Float) {
        if (t.hitFlash > 0f) t.hitFlash -= dt
        if (t.attackAnim > 0f) t.attackAnim -= dt
        if (t.deploying) {
            t.deployTimer -= dt
            return
        }
        if (t.isBuilding && t.card.lifetime > 0f) {
            t.hp -= t.maxHp / t.card.lifetime * dt
            if (!t.alive) return
        }
        if (t.cooldown > 0f) t.cooldown -= dt

        val current = t.target
        if (current == null || !current.alive || !inRange(t, current)) t.target = findTarget(t)
        val target = t.target

        if (target != null && inRange(t, target)) {
            t.facing = atan2(target.pos.y - t.pos.y, target.pos.x - t.pos.x)
            if (t.cooldown <= 0f) {
                attack(t, target)
                t.cooldown = t.card.hitSpeed
            }
            return
        }
        if (t.card.speed <= 0f) return

        val dest = target?.pos ?: nearestEnemyTower(t)?.pos ?: return
        val wp = if (t.isFlying) dest else Arena.waypoint(t.pos, dest)
        val dir = wp - t.pos
        val d = dir.length
        if (d > 0.001f) {
            val len = min(t.card.speed * dt, d)
            t.pos = t.pos + dir * (len / d)
            t.facing = atan2(dir.y, dir.x)
            t.walkPhase += len * 6f
        }
        constrain(t)
    }

    private fun canTarget(card: CardDef, other: Combatant): Boolean = when (card.targets) {
        Targets.BUILDINGS -> other.isBuilding
        Targets.GROUND -> !other.isFlying
        Targets.AIR_AND_GROUND -> true
    }

    private fun edgeDist(a: Combatant, b: Combatant) = a.pos.dist(b.pos) - a.radius - b.radius

    private fun inRange(t: Troop, target: Combatant) = edgeDist(t, target) <= t.card.range + 0.05f

    private fun findTarget(t: Troop): Combatant? {
        var best: Combatant? = null
        var bestDist = Float.MAX_VALUE
        val sight = max(t.card.sightRange, t.card.range)
        val candidates: Sequence<Combatant> =
            towers.asSequence().filter { it.team != t.team } + troops.asSequence().filter { it.team != t.team }
        for (c in candidates) {
            if (!c.alive || !canTarget(t.card, c)) continue
            val d = edgeDist(t, c)
            if (d <= sight && d < bestDist) {
                best = c
                bestDist = d
            }
        }
        return best
    }

    private fun nearestEnemyTower(t: Troop): Tower? =
        towers.filter { it.team != t.team && it.alive }.minByOrNull { it.pos.dist(t.pos) }

    private fun attack(t: Troop, target: Combatant) {
        t.attackAnim = 0.25f
        val card = t.card
        if (card.projectileSpeed > 0f) {
            val style = when (card.id) {
                "pretre" -> ProjectileStyle.MAGIC
                "calaos" -> ProjectileStyle.STONE
                else -> ProjectileStyle.ARROW
            }
            projectiles += Projectile(
                t.pos, target, t.team, card.damage.toFloat(), card.projectileSpeed,
                card.splashRadius, card.targets, style,
            )
        } else {
            applyHit(t.team, target, card.damage.toFloat(), card.splashRadius, card.targets)
        }
    }

    private fun applyHit(team: Team, primary: Combatant, damage: Float, splash: Float, targets: Targets) {
        if (splash <= 0f) {
            dealDamage(primary, damage)
            effects += Effect(EffectKind.HIT, primary.pos, 0.5f, 0.2f)
            return
        }
        val center = primary.pos
        for (c in enemiesOf(team)) {
            if (!c.alive) continue
            if (targets == Targets.GROUND && c.isFlying) continue
            if (c.pos.dist(center) <= splash + c.radius) dealDamage(c, damage)
        }
        effects += Effect(EffectKind.SPLASH, center, splash, 0.35f)
    }

    private fun enemiesOf(team: Team): List<Combatant> =
        towers.filter { it.team != team } + troops.filter { it.team != team }

    private fun dealDamage(target: Combatant, amount: Float) {
        if (!target.alive) return
        target.hp -= amount
        target.hitFlash = 0.12f
        if (target is Tower) {
            if (target.kind == TowerKind.KING) target.active = true
            if (!target.alive) onTowerDestroyed(target)
        }
    }

    private fun onTowerDestroyed(t: Tower) {
        effects += Effect(EffectKind.TOWER_DOWN, t.pos, 3f, 1.2f)
        val scorer = state(t.team.opponent)
        if (t.kind == TowerKind.KING) {
            scorer.crowns = 3
            finish(scorer.team)
            return
        }
        scorer.crowns = min(3, scorer.crowns + 1)
        towers.filter { it.team == t.team && it.kind == TowerKind.KING }.forEach { it.active = true }
        if (overtime) finish(scorer.team)
    }

    /** Empêche les troupes au sol de traverser le fleuve ailleurs que sur un pont. */
    private fun constrain(t: Troop) {
        var x = t.pos.x.coerceIn(0.4f, Arena.WIDTH - 0.4f)
        var y = t.pos.y.coerceIn(0.4f, Arena.HEIGHT - 0.4f)
        if (!t.isFlying && y > Arena.RIVER_TOP && y < Arena.RIVER_BOTTOM) {
            val b = Arena.nearestBridge(x)
            if (abs(x - b) > Arena.BRIDGE_HALF_WIDTH) {
                y = if (y < Arena.RIVER_MID) Arena.RIVER_TOP else Arena.RIVER_BOTTOM
            } else {
                x = x.coerceIn(b - Arena.BRIDGE_HALF_WIDTH + 0.2f, b + Arena.BRIDGE_HALF_WIDTH - 0.2f)
            }
        }
        t.pos = Vec2(x, y)
    }

    private fun separate() {
        val list = troops
        for (i in list.indices) {
            val a = list[i]
            for (j in i + 1 until list.size) {
                val b = list[j]
                if (a.isFlying != b.isFlying) continue
                val delta = b.pos - a.pos
                val d = delta.length
                val minD = a.radius + b.radius
                if (d >= minD) continue
                val dir = if (d > 0.0001f) delta * (1f / d) else Vec2(rng.nextFloat() - 0.5f, rng.nextFloat() - 0.5f)
                val overlap = (minD - d) * 0.5f
                val ma = if (a.isBuilding) 1e6f else a.card.mass
                val mb = if (b.isBuilding) 1e6f else b.card.mass
                val total = ma + mb
                a.pos = a.pos - dir * (overlap * 2f * mb / total)
                b.pos = b.pos + dir * (overlap * 2f * ma / total)
            }
        }
        for (t in list) {
            if (t.isFlying || t.isBuilding) continue
            for (tower in towers) {
                if (!tower.alive) continue
                val delta = t.pos - tower.pos
                val d = delta.length
                val minD = t.radius + tower.radius
                if (d < minD && d > 0.0001f) t.pos = tower.pos + delta * (minD / d)
            }
            constrain(t)
        }
    }

    // ------------------------------------------------------------------ Tours

    private fun updateTower(t: Tower, dt: Float) {
        if (t.hitFlash > 0f) t.hitFlash -= dt
        if (!t.alive || !t.active) return
        if (t.cooldown > 0f) t.cooldown -= dt
        val current = t.target
        if (current == null || !current.alive || current.pos.dist(t.pos) - current.radius > t.range) {
            t.target = troops.filter { it.team != t.team && it.alive && it.pos.dist(t.pos) - it.radius <= t.range }
                .minByOrNull { it.pos.dist(t.pos) }
        }
        val target = t.target ?: return
        if (t.cooldown <= 0f) {
            projectiles += Projectile(
                t.pos, target, t.team, t.damage, 12f, 0f, Targets.AIR_AND_GROUND,
                if (t.kind == TowerKind.KING) ProjectileStyle.CANNON else ProjectileStyle.ARROW,
            )
            t.cooldown = t.hitSpeed
        }
    }

    // ------------------------------------------------------------------ Projectiles & sorts

    private fun updateProjectiles(dt: Float) {
        for (p in projectiles) {
            if (p.done) continue
            if (!p.target.alive) {
                p.done = true
                continue
            }
            val delta = p.target.pos - p.pos
            val d = delta.length
            val stepLen = p.speed * dt
            if (d <= stepLen + p.target.radius * 0.5f) {
                p.pos = p.target.pos
                applyHit(p.team, p.target, p.damage, p.splash, p.targets)
                p.done = true
            } else {
                p.pos = p.pos + delta * (stepLen / d)
            }
        }
    }

    private fun updateSpells(dt: Float) {
        val it = spells.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.timer -= dt
            if (s.timer > 0f) continue
            it.remove()
            val card = s.card
            for (c in enemiesOf(s.team)) {
                if (!c.alive || c.pos.dist(s.pos) > card.spellRadius + c.radius) continue
                val factor = if (c is Tower) card.towerDamageFactor else 1f
                dealDamage(c, card.damage * factor)
            }
            val kind = if (card.id == Cards.XEVIOSO.id) EffectKind.LIGHTNING else EffectKind.FIRE
            effects += Effect(kind, s.pos, card.spellRadius, if (kind == EffectKind.FIRE) 0.9f else 0.6f)
        }
    }

    // ------------------------------------------------------------------ Fin de partie

    private fun checkTime() {
        if (isOver) return
        if (!overtime && time >= REGULAR_TIME) {
            if (player.crowns != enemy.crowns) {
                finish(if (player.crowns > enemy.crowns) Team.PLAYER else Team.ENEMY)
            } else {
                overtime = true
            }
        } else if (overtime && time >= REGULAR_TIME + OVERTIME) {
            // Départage : l'équipe dont la tour la plus abîmée est la plus faible perd.
            val pMin = towersOf(Team.PLAYER).filter { it.alive }.minOf { it.hpRatio }
            val eMin = towersOf(Team.ENEMY).filter { it.alive }.minOf { it.hpRatio }
            finish(
                when {
                    abs(pMin - eMin) < 0.001f -> null
                    pMin > eMin -> Team.PLAYER
                    else -> Team.ENEMY
                },
            )
        }
    }

    private fun finish(winner: Team?) {
        if (isOver) return
        val outcome = when (winner) {
            Team.PLAYER -> Outcome.VICTORY
            Team.ENEMY -> Outcome.DEFEAT
            null -> Outcome.DRAW
        }
        result = BattleResult(outcome, player.crowns, enemy.crowns)
    }

    companion object {
        const val REGULAR_TIME = 180f
        const val OVERTIME = 60f
        const val DOUBLE_KOLA_AT = 120f
        const val KOLA_PER_SECOND = 1f / 2.8f
        const val MAX_KOLA = 10f
        const val STEP = 1f / 30f
        private const val PI = Math.PI.toFloat()
        private const val HALF_PI = PI / 2f
    }
}
