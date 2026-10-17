package tg.tataroyale

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tg.tataroyale.core.Cards

class MainActivity : Activity() {
    private lateinit var profile: Profile
    private lateinit var statsView: TextView
    private lateinit var arenaView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = Profile(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(Palette.BACKGROUND)
        }
        val kenteHeight = Ui.dp(this, 18f)
        root.addView(KenteStripView(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, kenteHeight))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setGravity(Gravity.CENTER_HORIZONTAL)
            val p = Ui.dp(this@MainActivity, 16f)
            setPadding(p, p, p, p)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(KenteStripView(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, kenteHeight))

        content.addView(Ui.text(this, "🛖  ⚔️  🛖", 44f), Ui.matchWidth(this, 4f))
        content.addView(Ui.text(this, "TATA ROYALE", 40f, Palette.TOGO_YELLOW, bold = true), Ui.matchWidth(this, 2f))
        content.addView(Ui.text(this, "La bataille des takienta", 18f, Palette.KOLA_LIGHT), Ui.matchWidth(this, 2f))

        arenaView = Ui.text(this, "", 16f, Palette.WHITE)
        statsView = Ui.text(this, "", 20f, Palette.TOGO_YELLOW, bold = true)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Palette.PANEL, Ui.dp(this@MainActivity, 16f).toFloat(), Palette.TOGO_GREEN, Ui.dp(this@MainActivity, 3f))
            val p = Ui.dp(this@MainActivity, 12f)
            setPadding(p, p, p, p)
            addView(statsView, Ui.matchWidth(this@MainActivity, 4f))
            addView(arenaView, Ui.matchWidth(this@MainActivity, 4f))
        }
        content.addView(card, Ui.matchWidth(this, 12f))

        content.addView(Ui.button(this, "⚔️  Combattre", Palette.TOGO_GREEN) {
            startActivity(Intent(this, BattleActivity::class.java))
        }, Ui.matchWidth(this))
        content.addView(Ui.button(this, "🃏  Mon deck", Palette.TOGO_RED) {
            startActivity(Intent(this, DeckActivity::class.java))
        }, Ui.matchWidth(this))
        content.addView(Ui.button(this, "📜  Lexique & culture", Palette.PANEL_LIGHT) { showLexicon() }, Ui.matchWidth(this))

        content.addView(
            Ui.text(this, "Un jeu de stratégie en temps réel inspiré du Togo et de l'Afrique de l'Ouest.", 13f, Palette.STRAW),
            Ui.matchWidth(this, 16f),
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val arena = profile.arena
        statsView.text = "🏆 ${profile.trophies}     🐚 ${profile.cauris} cauris"
        arenaView.text = "Arène : ${arena.name} (${arena.place})\nVictoires ${profile.wins} · Défaites ${profile.losses}"
    }

    private fun showLexicon() {
        val terms = listOf(
            "Takienta" to "Maisons-forteresses en terre des Batammariba (Koutammakou, nord du Togo). Ce sont vos tours latérales.",
            "Chefferie" to "Siège du chef traditionnel, abritée sous le parasol royal. C'est votre tour principale : si elle tombe, la partie est perdue.",
            "Kola" to "La noix de kola, offerte en signe d'hospitalité et de respect. Elle remplace l'élixir : chaque carte coûte de la kola.",
            "Cauris" to "Coquillages qui servaient autrefois de monnaie dans toute l'Afrique de l'Ouest. Vous en gagnez à chaque combat.",
            "Mono" to "Le fleuve qui marque la frontière entre le Togo et le Bénin. Il coupe l'arène en deux.",
        )
        val cards = Cards.ALL.map { "${it.emoji} ${it.name}" to it.culture }
        val message = (terms + cards).joinToString("\n\n") { (title, body) -> "• $title\n$body" }
        AlertDialog.Builder(this)
            .setTitle("Lexique & culture")
            .setMessage(message)
            .setPositiveButton("Akpé ! (Merci)", null)
            .show()
    }
}
