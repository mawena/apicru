package tg.tataroyale

import android.content.Context
import tg.tataroyale.core.CardDef
import tg.tataroyale.core.Cards
import tg.tataroyale.core.Outcome

/** Progression du joueur, sauvegardée dans les SharedPreferences. */
class Profile(context: Context) {
    private val prefs = context.getSharedPreferences("tata_royale", Context.MODE_PRIVATE)

    var trophies: Int
        get() = prefs.getInt(KEY_TROPHIES, 0)
        private set(v) { prefs.edit().putInt(KEY_TROPHIES, v).apply() }

    var cauris: Int
        get() = prefs.getInt(KEY_CAURIS, 100)
        private set(v) { prefs.edit().putInt(KEY_CAURIS, v).apply() }

    val wins: Int get() = prefs.getInt(KEY_WINS, 0)
    val losses: Int get() = prefs.getInt(KEY_LOSSES, 0)

    var deck: List<CardDef>
        get() {
            val ids = prefs.getString(KEY_DECK, null)?.split(",").orEmpty()
            val cards = ids.mapNotNull { Cards.byId(it) }.distinct()
            return if (cards.size == Cards.DECK_SIZE) cards else Cards.DEFAULT_DECK
        }
        set(value) {
            require(value.size == Cards.DECK_SIZE)
            prefs.edit().putString(KEY_DECK, value.joinToString(",") { it.id }).apply()
        }

    val arena: ArenaInfo get() = ARENAS.last { trophies >= it.minTrophies }

    data class Reward(val trophies: Int, val cauris: Int)

    fun rewardFor(outcome: Outcome, crowns: Int): Reward = when (outcome) {
        Outcome.VICTORY -> Reward(30, 40 + crowns * 20)
        Outcome.DEFEAT -> Reward(-15, 10)
        Outcome.DRAW -> Reward(0, 20)
    }

    fun record(outcome: Outcome, crowns: Int): Reward {
        val reward = rewardFor(outcome, crowns)
        trophies = (trophies + reward.trophies).coerceAtLeast(0)
        cauris += reward.cauris
        when (outcome) {
            Outcome.VICTORY -> prefs.edit().putInt(KEY_WINS, wins + 1).apply()
            Outcome.DEFEAT -> prefs.edit().putInt(KEY_LOSSES, losses + 1).apply()
            Outcome.DRAW -> Unit
        }
        return reward
    }

    data class ArenaInfo(val minTrophies: Int, val name: String, val place: String, val difficulty: Float)

    companion object {
        private const val KEY_TROPHIES = "trophies"
        private const val KEY_CAURIS = "cauris"
        private const val KEY_WINS = "wins"
        private const val KEY_LOSSES = "losses"
        private const val KEY_DECK = "deck"

        val ARENAS = listOf(
            ArenaInfo(0, "Marché d'Adawlato", "Lomé", 0.6f),
            ArenaInfo(150, "Rives du Mono", "Région des Plateaux", 0.8f),
            ArenaInfo(400, "Monts de la Kara", "Pays kabyè", 1.0f),
            ArenaInfo(700, "Koutammakou", "Pays des Batammariba", 1.2f),
            ArenaInfo(1000, "Cour royale de Notsé", "Berceau des Ewé", 1.5f),
        )
    }
}
