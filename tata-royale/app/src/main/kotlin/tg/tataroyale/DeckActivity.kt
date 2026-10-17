package tg.tataroyale

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tg.tataroyale.core.CardDef
import tg.tataroyale.core.CardKind
import tg.tataroyale.core.Cards
import tg.tataroyale.core.Targets

/** Choix des 8 cartes du deck. Appui long : fiche détaillée de la carte. */
class DeckActivity : Activity() {
    private lateinit var profile: Profile
    private val selected = mutableListOf<CardDef>()
    private val tiles = mutableMapOf<String, LinearLayout>()
    private lateinit var header: TextView
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = Profile(this)
        selected += profile.deck

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(Palette.BACKGROUND)
        }
        root.addView(KenteStripView(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 14f)))
        header = Ui.text(this, "", 22f, Palette.TOGO_YELLOW, bold = true)
        root.addView(header, Ui.matchWidth(this, 10f))
        root.addView(
            Ui.text(this, "Touchez une carte pour l'ajouter ou la retirer. Appui long : détails.", 13f, Palette.STRAW),
            Ui.matchWidth(this, 4f),
        )

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Cards.ALL.chunked(3).forEach { rowCards ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowCards.forEach { card ->
                val tile = createTile(card)
                tiles[card.id] = tile
                row.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    val m = Ui.dp(this@DeckActivity, 5f)
                    setMargins(m, m, m, m)
                })
            }
            repeat(3 - rowCards.size) {
                row.addView(LinearLayout(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row)
        }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        saveButton = Ui.button(this, "✔  Enregistrer le deck", Palette.TOGO_GREEN) {
            profile.deck = selected.toList()
            Toast.makeText(this, "Deck enregistré !", Toast.LENGTH_SHORT).show()
            finish()
        }
        root.addView(saveButton, Ui.matchWidth(this, 10f))
        setContentView(root)
        refresh()
    }

    private fun createTile(card: CardDef): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setGravity(Gravity.CENTER)
        val p = Ui.dp(this@DeckActivity, 6f)
        setPadding(p, p, p, p)
        addView(Ui.text(this@DeckActivity, card.emoji, 34f))
        addView(Ui.text(this@DeckActivity, card.name, 12f, bold = true))
        addView(Ui.text(this@DeckActivity, "${card.cost} kola · ${card.rarity.label}", 11f, Palette.KOLA_LIGHT))
        setOnClickListener { toggle(card) }
        setOnLongClickListener {
            showDetails(card)
            true
        }
    }

    private fun toggle(card: CardDef) {
        if (card in selected) {
            selected.remove(card)
        } else if (selected.size < Cards.DECK_SIZE) {
            selected += card
        } else {
            Toast.makeText(this, "Le deck est complet : retirez d'abord une carte.", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    private fun refresh() {
        val radius = Ui.dp(this, 12f).toFloat()
        val stroke = Ui.dp(this, 3f)
        for (card in Cards.ALL) {
            val tile = tiles[card.id] ?: continue
            val inDeck = card in selected
            tile.background = if (inDeck) {
                Ui.rounded(Palette.PANEL_LIGHT, radius, Palette.TOGO_YELLOW, stroke)
            } else {
                Ui.rounded(Palette.PANEL, radius, Color.argb(80, 255, 255, 255), Ui.dp(this, 1f))
            }
            tile.alpha = if (inDeck) 1f else 0.6f
        }
        val avg = if (selected.isEmpty()) 0.0 else selected.map { it.cost }.average()
        header.text = "Mon deck : ${selected.size}/${Cards.DECK_SIZE}  ·  coût moyen ${"%.1f".format(avg)}"
        val complete = selected.size == Cards.DECK_SIZE
        saveButton.isEnabled = complete
        saveButton.alpha = if (complete) 1f else 0.5f
    }

    private fun showDetails(card: CardDef) {
        val stats = buildString {
            append(card.description).append("\n\n")
            append("Coût : ${card.cost} kola\n")
            when (card.kind) {
                CardKind.SPELL -> {
                    append("Dégâts : ${card.damage} (tours : ${(card.damage * card.towerDamageFactor).toInt()})\n")
                    append("Rayon : ${card.spellRadius} cases\n")
                }
                else -> {
                    append("Points de vie : ${card.hp}${if (card.count > 1) " × ${card.count}" else ""}\n")
                    append("Dégâts : ${card.damage} toutes les ${card.hitSpeed} s\n")
                    append("Portée : ${if (card.range < 2f) "mêlée" else "${card.range} cases"}\n")
                    if (card.splashRadius > 0f) append("Dégâts de zone\n")
                    if (card.flying) append("Unité volante\n")
                    append(
                        "Cibles : " + when (card.targets) {
                            Targets.GROUND -> "troupes au sol et bâtiments"
                            Targets.AIR_AND_GROUND -> "air et sol"
                            Targets.BUILDINGS -> "bâtiments uniquement"
                        } + "\n",
                    )
                    if (card.lifetime > 0f) append("Durée de vie : ${card.lifetime.toInt()} s\n")
                }
            }
            append("\n📜 ").append(card.culture)
        }
        AlertDialog.Builder(this)
            .setTitle("${card.emoji} ${card.name}")
            .setMessage(stats)
            .setPositiveButton("OK", null)
            .show()
    }
}
