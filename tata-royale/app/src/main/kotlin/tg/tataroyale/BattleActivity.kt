package tg.tataroyale

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import tg.tataroyale.core.Battle
import tg.tataroyale.core.BotAi
import tg.tataroyale.core.Cards
import tg.tataroyale.core.Outcome
import tg.tataroyale.core.Team
import kotlin.random.Random

class BattleActivity : Activity() {
    private lateinit var gameView: GameView
    private lateinit var profile: Profile
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        profile = Profile(this)

        val rng = Random(System.nanoTime())
        val arena = profile.arena
        val enemyDeck = Cards.ALL.shuffled(rng).take(Cards.DECK_SIZE)
        val battle = Battle(profile.deck, enemyDeck, rng.nextLong())
        val bot = BotAi(battle, Team.ENEMY, rng, arena.difficulty)
        val opponent = OPPONENTS.random(rng)

        gameView = GameView(
            this, battle, bot, opponent, arena.name,
            onBattleEnd = { result ->
                finished = true
                val reward = profile.record(result.outcome, result.playerCrowns)
                val sign = if (reward.trophies >= 0) "+" else ""
                "$sign${reward.trophies} 🏆    +${reward.cauris} 🐚"
            },
            onExit = { finish() },
        )
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Palette.BACKGROUND)
            addView(gameView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        gameView.paused = true
    }

    override fun onResume() {
        super.onResume()
        gameView.paused = false
    }

    @Deprecated("Remplacé par OnBackPressedDispatcher dans AndroidX, inutile ici.")
    override fun onBackPressed() {
        if (finished) {
            finish()
            return
        }
        gameView.paused = true
        AlertDialog.Builder(this)
            .setTitle("Abandonner le combat ?")
            .setMessage("Un abandon compte comme une défaite.")
            .setPositiveButton("Abandonner") { _, _ ->
                finished = true
                profile.record(Outcome.DEFEAT, 0)
                finish()
            }
            .setNegativeButton("Continuer") { _, _ -> gameView.paused = false }
            .setOnCancelListener { gameView.paused = false }
            .show()
    }

    companion object {
        private val OPPONENTS = listOf("Chef Adjo", "Reine Ablavi", "Togbui Kodjo", "Dame Afi", "Sage Komlan", "Nana Akossiwa")
    }
}
