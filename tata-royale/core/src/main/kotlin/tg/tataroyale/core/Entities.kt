package tg.tataroyale.core

import kotlin.random.Random

enum class Team {
    PLAYER, ENEMY;

    val opponent: Team get() = if (this == PLAYER) ENEMY else PLAYER
}

abstract class Combatant(
    val id: Int,
    val team: Team,
    var pos: Vec2,
    val maxHp: Int,
    val radius: Float,
) {
    var hp: Float = maxHp.toFloat()
    /** Temps restant du flash blanc affiché quand l'entité est touchée. */
    var hitFlash = 0f
    val alive: Boolean get() = hp > 0f
    val hpRatio: Float get() = (hp / maxHp).coerceIn(0f, 1f)
    abstract val isFlying: Boolean
    abstract val isBuilding: Boolean
}

enum class TowerKind { PRINCESS, KING }

/** Tours : les « takienta » (tours latérales) et la chefferie (tour du roi). */
class Tower(id: Int, team: Team, pos: Vec2, val kind: TowerKind, val lane: Int) :
    Combatant(
        id, team, pos,
        maxHp = if (kind == TowerKind.KING) 4200 else 2500,
        radius = if (kind == TowerKind.KING) 1.6f else 1.3f,
    ) {
    val damage = if (kind == TowerKind.KING) 110f else 90f
    val hitSpeed = if (kind == TowerKind.KING) 1.0f else 0.8f
    val range = if (kind == TowerKind.KING) 7f else 7.5f
    /** La chefferie dort tant qu'elle n'est pas attaquée ou qu'une takienta n'est pas tombée. */
    var active = kind == TowerKind.PRINCESS
    var cooldown = 0f
    var target: Combatant? = null
    override val isFlying = false
    override val isBuilding = true
}

class Troop(id: Int, team: Team, pos: Vec2, val card: CardDef) :
    Combatant(id, team, pos, card.hp, card.radius) {
    var deployTimer = card.deployTime
    var cooldown = card.hitSpeed * 0.5f
    var target: Combatant? = null
    /** Angle de déplacement (radians), utile pour l'animation. */
    var facing = 0f
    var attackAnim = 0f
    var walkPhase = 0f
    override val isFlying get() = card.flying
    override val isBuilding get() = card.kind == CardKind.BUILDING
    val deploying get() = deployTimer > 0f
}

enum class ProjectileStyle { ARROW, MAGIC, STONE, CANNON }

class Projectile(
    var pos: Vec2,
    val target: Combatant,
    val team: Team,
    val damage: Float,
    val speed: Float,
    val splash: Float,
    val targets: Targets,
    val style: ProjectileStyle,
) {
    var done = false
}

class PendingSpell(val team: Team, val card: CardDef, val pos: Vec2, var timer: Float)

enum class EffectKind { HIT, SPLASH, LIGHTNING, FIRE, DEATH, DEPLOY, TOWER_DOWN }

class Effect(val kind: EffectKind, val pos: Vec2, val radius: Float, val duration: Float) {
    var age = 0f
    val progress: Float get() = (age / duration).coerceIn(0f, 1f)
    val finished: Boolean get() = age >= duration
}

/** Main de 4 cartes + file d'attente, comme dans les jeux du genre. */
class Hand(deck: List<CardDef>, rng: Random) {
    private val queue = ArrayDeque(deck.shuffled(rng))
    val cards: MutableList<CardDef>

    init {
        require(deck.size >= HAND_SIZE + 1) { "Un deck doit contenir au moins ${HAND_SIZE + 1} cartes" }
        cards = MutableList(HAND_SIZE) { queue.removeFirst() }
    }

    val next: CardDef get() = queue.first()

    fun play(index: Int): CardDef {
        val played = cards[index]
        cards[index] = queue.removeFirst()
        queue.addLast(played)
        return played
    }

    companion object {
        const val HAND_SIZE = 4
    }
}

class PlayerState(val team: Team, deck: List<CardDef>, rng: Random) {
    val hand = Hand(deck, rng)
    /** Énergie « kola » : l'équivalent de l'élixir. */
    var kola = 5f
    var crowns = 0
}

enum class Outcome { VICTORY, DEFEAT, DRAW }

data class BattleResult(val outcome: Outcome, val playerCrowns: Int, val enemyCrowns: Int)
