package tg.tataroyale.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleTest {

    private fun battle(seed: Long = 42L) = Battle(Cards.DEFAULT_DECK, Cards.DEFAULT_DECK, seed)

    private fun Battle.run(seconds: Float, tick: (Float) -> Unit = {}) {
        var t = 0f
        while (t < seconds && !isOver) {
            update(Battle.STEP)
            tick(Battle.STEP)
            t += Battle.STEP
        }
    }

    /** Place la carte voulue dans la main (index 0) sans dépendre du mélange. */
    private fun Battle.forceCard(team: Team, card: CardDef) {
        state(team).hand.cards[0] = card
    }

    @Test
    fun handCyclesThroughDeck() {
        val hand = Hand(Cards.DEFAULT_DECK, Random(1))
        val seen = mutableSetOf<String>()
        repeat(16) {
            seen += hand.cards.map { it.id }
            hand.play(it % Hand.HAND_SIZE)
        }
        assertEquals(Cards.DEFAULT_DECK.map { it.id }.toSet(), seen)
        assertEquals(Hand.HAND_SIZE, hand.cards.distinctBy { it.id }.size)
    }

    @Test
    fun kolaRegeneratesAndCaps() {
        val b = battle()
        b.run(5.6f)
        assertEquals(7f, b.player.kola, 0.1f)
        b.run(30f)
        assertEquals(Battle.MAX_KOLA, b.player.kola)
    }

    @Test
    fun troopsOnlyDeployOnOwnSideButSpellsAnywhere() {
        val b = battle()
        b.player.kola = 10f
        assertTrue(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, Vec2(4f, 22f)))
        assertFalse(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, Vec2(4f, 8f)))
        assertFalse(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, Vec2(4f, 16f)))
        assertTrue(b.canDeploy(Team.PLAYER, Cards.XEVIOSO, Vec2(4f, 8f)))
        b.enemy.kola = 10f
        assertTrue(b.canDeploy(Team.ENEMY, Cards.LUTTEUR, Vec2(4f, 8f)))
        assertFalse(b.canDeploy(Team.ENEMY, Cards.LUTTEUR, Vec2(4f, 22f)))
    }

    @Test
    fun cannotPlayWithoutEnoughKola() {
        val b = battle()
        b.forceCard(Team.PLAYER, Cards.ZANGBETO)
        b.player.kola = 5f
        assertFalse(b.playCard(Team.PLAYER, 0, Vec2(4f, 22f)))
        b.player.kola = 6f
        assertTrue(b.playCard(Team.PLAYER, 0, Vec2(4f, 22f)))
        assertEquals(0f, b.player.kola, 0.001f)
        assertEquals(1, b.troops.size)
    }

    @Test
    fun zangbetoCrossesBridgeAndHitsTower() {
        val b = battle()
        b.forceCard(Team.PLAYER, Cards.ZANGBETO)
        b.player.kola = 10f
        assertTrue(b.playCard(Team.PLAYER, 0, Vec2(5f, 21f)))
        val tower = b.towers.first { it.team == Team.ENEMY && it.kind == TowerKind.PRINCESS && it.lane == 0 }
        b.run(40f)
        assertTrue(tower.hp < tower.maxHp, "la takienta adverse aurait dû être touchée")
    }

    @Test
    fun destroyingKingTowerGivesThreeCrownVictory() {
        val b = battle()
        val king = b.towers.first { it.team == Team.ENEMY && it.kind == TowerKind.KING }
        king.hp = 50f
        b.forceCard(Team.PLAYER, Cards.XEVIOSO)
        b.player.kola = 10f
        assertTrue(b.playCard(Team.PLAYER, 0, king.pos))
        b.run(2f)
        val result = assertNotNull(b.result)
        assertEquals(Outcome.VICTORY, result.outcome)
        assertEquals(3, result.playerCrowns)
    }

    @Test
    fun idleBattleEndsInDrawAfterOvertime() {
        val b = battle()
        b.run(Battle.REGULAR_TIME + Battle.OVERTIME + 1f)
        assertEquals(Outcome.DRAW, b.result?.outcome)
    }

    @Test
    fun pocketOpensWhenPrincessTowerFalls() {
        val b = battle()
        b.player.kola = 10f
        val pocket = Vec2(3f, 12f)
        assertFalse(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, pocket))
        b.towers.first { it.team == Team.ENEMY && it.lane == 0 }.hp = 0f
        assertTrue(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, pocket))
        assertFalse(b.canDeploy(Team.PLAYER, Cards.LUTTEUR, Vec2(15f, 12f)))
    }

    @Test
    fun botsPlayFullMatchesWithoutBreakingRules() {
        for (seed in 1L..6L) {
            val b = Battle(Cards.ALL.shuffled(Random(seed)).take(8), Cards.ALL.shuffled(Random(seed + 100)).take(8), seed)
            val bots = listOf(
                BotAi(b, Team.PLAYER, Random(seed * 7)),
                BotAi(b, Team.ENEMY, Random(seed * 13)),
            )
            var played = 0
            b.run(Battle.REGULAR_TIME + Battle.OVERTIME + 1f) { dt ->
                val before = b.troops.size + b.spells.size
                bots.forEach { it.update(dt) }
                if (b.troops.size + b.spells.size > before) played++
                for (t in b.troops) {
                    if (!t.isFlying && t.pos.y > Arena.RIVER_TOP + 0.01f && t.pos.y < Arena.RIVER_BOTTOM - 0.01f) {
                        assertTrue(abs(t.pos.x - Arena.nearestBridge(t.pos.x)) <= Arena.BRIDGE_HALF_WIDTH + 0.01f, "troupe dans le fleuve")
                    }
                    assertTrue(t.pos.x in 0f..Arena.WIDTH && t.pos.y in 0f..Arena.HEIGHT)
                }
                assertTrue(b.player.kola in 0f..Battle.MAX_KOLA && b.enemy.kola in 0f..Battle.MAX_KOLA)
            }
            assertNotNull(b.result, "le match $seed doit se terminer")
            assertTrue(played > 10, "les IA doivent jouer des cartes (seed $seed : $played)")
            val damaged = b.towers.count { it.hp < it.maxHp }
            println("seed=$seed result=${b.result} cartes=$played toursTouchées=$damaged t=${b.time}")
        }
    }
}
