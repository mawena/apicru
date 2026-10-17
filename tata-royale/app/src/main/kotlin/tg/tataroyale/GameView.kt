package tg.tataroyale

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import tg.tataroyale.core.Arena
import tg.tataroyale.core.Battle
import tg.tataroyale.core.BattleResult
import tg.tataroyale.core.BotAi
import tg.tataroyale.core.CardDef
import tg.tataroyale.core.CardKind
import tg.tataroyale.core.Combatant
import tg.tataroyale.core.Effect
import tg.tataroyale.core.EffectKind
import tg.tataroyale.core.Outcome
import tg.tataroyale.core.Projectile
import tg.tataroyale.core.ProjectileStyle
import tg.tataroyale.core.Team
import tg.tataroyale.core.Tower
import tg.tataroyale.core.TowerKind
import tg.tataroyale.core.Troop
import tg.tataroyale.core.Vec2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vue du combat : fait avancer la simulation à chaque image, dessine l'arène
 * au Canvas et gère le glisser-déposer des cartes.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val battle: Battle,
    private val bot: BotAi,
    private val opponentName: String,
    private val arenaName: String,
    /** Appelé une seule fois à la fin du combat ; renvoie le texte de récompense. */
    private val onBattleEnd: (BattleResult) -> String,
    private val onExit: () -> Unit,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()
    private val rect = RectF()

    // Mise en page (pixels)
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private var hudH = 0f
    private var panelTop = 0f
    private val cardRects = Array(4) { RectF() }
    private val nextRect = RectF()
    private val kolaRect = RectF()
    private val exitRect = RectF()
    private var background: Bitmap? = null

    // Entrées
    private var selected = -1
    private var dragging = false
    private var touchX = 0f
    private var touchY = 0f
    private var message: String? = null
    private var messageTimer = 0f

    private var lastNanos = 0L
    private var endHandled = false
    private var rewardText = ""
    var paused = false
        set(value) {
            field = value
            lastNanos = 0L
            if (!value) postInvalidateOnAnimation()
        }

    private fun sx(x: Float) = ox + x * scale
    private fun sy(y: Float) = oy + y * scale
    private fun toWorld(px: Float, py: Float) = Vec2((px - ox) / scale, (py - oy) / scale)

    // ------------------------------------------------------------------ Mise en page

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        hudH = h * 0.06f
        val panelH = h * 0.19f
        panelTop = h - panelH
        val availH = panelTop - hudH
        scale = min(w / Arena.WIDTH, availH / Arena.HEIGHT)
        ox = (w - Arena.WIDTH * scale) / 2f
        oy = hudH + (availH - Arena.HEIGHT * scale) / 2f

        val pad = w * 0.02f
        val kolaH = panelH * 0.17f
        kolaRect.set(w * 0.2f, h - pad - kolaH, w - pad, h - pad)
        val cardsTop = panelTop + panelH * 0.14f
        val cardsBottom = kolaRect.top - pad
        val nextW = w * 0.13f
        nextRect.set(pad, cardsTop + (cardsBottom - cardsTop) * 0.3f, pad + nextW, cardsBottom)
        val startX = nextRect.right + pad * 1.5f
        val cardW = (w - pad - startX - pad * 3) / 4f
        for (i in 0 until 4) {
            val left = startX + i * (cardW + pad)
            cardRects[i].set(left, cardsTop, left + cardW, cardsBottom)
        }
        exitRect.set(w * 0.2f, h * 0.62f, w * 0.8f, h * 0.70f)
        if (w > 0 && h > 0) background = buildBackground(w, h)
    }

    /** L'arène statique (sol, fleuve, ponts, décor) est dessinée une seule fois. */
    private fun buildBackground(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Palette.SAVANNA_DARK)

        for (i in 0 until Arena.WIDTH.toInt()) {
            for (j in 0 until Arena.HEIGHT.toInt()) {
                fill.color = if ((i + j) % 2 == 0) Palette.LATERITE else Palette.LATERITE_DARK
                c.drawRect(sx(i.toFloat()), sy(j.toFloat()), sx(i + 1f), sy(j + 1f), fill)
            }
        }
        // Chemins de terre battue.
        fill.color = Color.argb(150, Color.red(Palette.PATH), Color.green(Palette.PATH), Color.blue(Palette.PATH))
        for (b in Arena.BRIDGES_X) c.drawRect(sx(b - 0.7f), sy(5f), sx(b + 0.7f), sy(27f), fill)
        c.drawRect(sx(3.5f), sy(2.4f), sx(14.5f), sy(3.6f), fill)
        c.drawRect(sx(3.5f), sy(28.4f), sx(14.5f), sy(29.6f), fill)

        // Le fleuve Mono.
        fill.color = Palette.RIVER
        c.drawRect(sx(0f), sy(Arena.RIVER_TOP), sx(Arena.WIDTH), sy(Arena.RIVER_BOTTOM), fill)
        stroke.color = Palette.RIVER_LIGHT
        stroke.strokeWidth = scale * 0.08f
        for (k in 0 until 9) {
            val x = 0.6f + k * 2f
            val y = Arena.RIVER_TOP + 0.6f + (k % 2) * 0.8f
            rect.set(sx(x), sy(y - 0.2f), sx(x + 0.9f), sy(y + 0.2f))
            c.drawArc(rect, 200f, 140f, false, stroke)
        }
        text.textSize = scale * 0.55f
        text.color = Color.argb(170, 255, 255, 255)
        c.drawText("~ Mono ~", sx(9f), sy(Arena.RIVER_MID + 0.2f), text)

        // Ponts en bois.
        for (b in Arena.BRIDGES_X) {
            fill.color = Palette.WOOD
            c.drawRect(sx(b - 1f), sy(Arena.RIVER_TOP - 0.3f), sx(b + 1f), sy(Arena.RIVER_BOTTOM + 0.3f), fill)
            stroke.color = Palette.WOOD_DARK
            stroke.strokeWidth = scale * 0.05f
            var y = Arena.RIVER_TOP - 0.3f
            while (y < Arena.RIVER_BOTTOM + 0.3f) {
                c.drawLine(sx(b - 1f), sy(y), sx(b + 1f), sy(y), stroke)
                y += 0.35f
            }
            fill.color = Palette.WOOD_DARK
            c.drawRect(sx(b - 1.1f), sy(Arena.RIVER_TOP - 0.4f), sx(b - 0.9f), sy(Arena.RIVER_BOTTOM + 0.4f), fill)
            c.drawRect(sx(b + 0.9f), sy(Arena.RIVER_TOP - 0.4f), sx(b + 1.1f), sy(Arena.RIVER_BOTTOM + 0.4f), fill)
        }

        // Baobabs et termitières décoratifs.
        for ((x, y) in listOf(0.9f to 12.7f, 17.1f to 12.7f, 0.9f to 19.3f, 17.1f to 19.3f)) drawBaobab(c, x, y)
        for ((x, y) in listOf(9f to 13f, 9f to 19f)) drawTermiteMound(c, x, y)

        // Liseré jaune et bandeau kente au-dessus du panneau de cartes.
        stroke.color = Palette.TOGO_YELLOW
        stroke.strokeWidth = scale * 0.12f
        c.drawRect(sx(0f), sy(0f), sx(Arena.WIDTH), sy(Arena.HEIGHT), stroke)
        fill.color = Palette.PANEL
        c.drawRect(0f, panelTop, w.toFloat(), h.toFloat(), fill)
        c.drawRect(0f, 0f, w.toFloat(), hudH, fill)
        KenteStripView.drawKente(c, fill, 0f, panelTop, w.toFloat(), panelTop + h * 0.012f)
        KenteStripView.drawKente(c, fill, 0f, hudH - h * 0.008f, w.toFloat(), hudH)
        return bmp
    }

    private fun drawBaobab(c: Canvas, x: Float, y: Float) {
        fill.color = Color.argb(60, 0, 0, 0)
        rect.set(sx(x - 0.8f), sy(y + 0.3f), sx(x + 0.8f), sy(y + 0.8f))
        c.drawOval(rect, fill)
        fill.color = Color.rgb(110, 80, 60)
        c.drawRect(sx(x - 0.25f), sy(y - 0.4f), sx(x + 0.25f), sy(y + 0.6f), fill)
        fill.color = Color.rgb(70, 120, 50)
        c.drawCircle(sx(x - 0.4f), sy(y - 0.6f), scale * 0.5f, fill)
        c.drawCircle(sx(x + 0.4f), sy(y - 0.6f), scale * 0.5f, fill)
        c.drawCircle(sx(x), sy(y - 0.95f), scale * 0.55f, fill)
    }

    private fun drawTermiteMound(c: Canvas, x: Float, y: Float) {
        fill.color = Palette.EARTH_WALL_DARK
        path.reset()
        path.moveTo(sx(x - 0.6f), sy(y + 0.4f))
        path.lineTo(sx(x - 0.2f), sy(y - 0.9f))
        path.lineTo(sx(x + 0.1f), sy(y - 0.5f))
        path.lineTo(sx(x + 0.3f), sy(y - 1.1f))
        path.lineTo(sx(x + 0.6f), sy(y + 0.4f))
        path.close()
        c.drawPath(path, fill)
    }

    // ------------------------------------------------------------------ Boucle de rendu

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1e9f, 0.1f)
        lastNanos = now
        if (!paused) {
            battle.update(dt)
            bot.update(dt)
        }
        if (messageTimer > 0f) messageTimer -= dt

        val result = battle.result
        if (result != null && !endHandled) {
            endHandled = true
            selected = -1
            dragging = false
            rewardText = onBattleEnd(result)
        }

        background?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        drawDeployZone(canvas)
        drawEntities(canvas)
        for (p in battle.projectiles) drawProjectile(canvas, p)
        drawSpells(canvas)
        for (e in battle.effects) drawEffect(canvas, e)
        drawGhost(canvas)
        drawHud(canvas)
        drawPanel(canvas)
        drawMessage(canvas)
        if (result != null) drawResult(canvas, result)

        if (!paused) postInvalidateOnAnimation()
    }

    private fun selectedCard(): CardDef? = battle.player.hand.cards.getOrNull(selected)

    private fun drawDeployZone(c: Canvas) {
        val card = selectedCard() ?: return
        if (card.isSpell) return
        fill.color = Color.argb(70, 200, 20, 20)
        c.drawRect(sx(0f), sy(0f), sx(Arena.WIDTH), sy(Arena.RIVER_BOTTOM + 0.5f), fill)
        fill.color = Color.argb(45, 255, 255, 255)
        c.drawRect(sx(0.5f), sy(Arena.RIVER_BOTTOM + 0.5f), sx(Arena.WIDTH - 0.5f), sy(Arena.HEIGHT - 0.5f), fill)
        for (lane in 0..1) {
            if (!battle.pocketOpen(Team.PLAYER, lane)) continue
            val left = if (lane == 0) 0.5f else Arena.WIDTH / 2f
            val right = if (lane == 0) Arena.WIDTH / 2f else Arena.WIDTH - 0.5f
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawRect(sx(left), sy(Arena.POCKET_Y), sx(right), sy(Arena.RIVER_TOP - 0.3f), fill)
        }
    }

    private fun drawEntities(c: Canvas) {
        val all = ArrayList<Combatant>(battle.towers.size + battle.troops.size)
        all.addAll(battle.towers)
        all.addAll(battle.troops)
        // Les volants passent au-dessus, puis tri par profondeur.
        all.sortWith(compareBy<Combatant>({ it.isFlying }, { it.pos.y }))
        for (e in all) {
            when (e) {
                is Tower -> drawTower(c, e)
                is Troop -> drawTroop(c, e)
            }
        }
    }

    private fun teamColor(team: Team) = if (team == Team.PLAYER) Palette.PLAYER else Palette.ENEMY

    private fun drawTower(c: Canvas, t: Tower) {
        val cx = sx(t.pos.x)
        val cy = sy(t.pos.y)
        val r = t.radius * scale
        if (!t.alive) {
            fill.color = Palette.EARTH_WALL_DARK
            for (k in 0 until 6) {
                val a = k * 1.05f
                c.drawCircle(cx + cos(a) * r * 0.5f, cy + sin(a) * r * 0.35f, r * 0.3f, fill)
            }
            fill.color = Color.rgb(90, 80, 70)
            c.drawCircle(cx, cy, r * 0.3f, fill)
            return
        }
        val king = t.kind == TowerKind.KING
        val bodyW = if (king) r * 1.0f else r * 0.8f

        fill.color = Color.argb(80, 0, 0, 0)
        rect.set(cx - r * 1.1f, cy + r * 0.25f, cx + r * 1.1f, cy + r * 0.9f)
        c.drawOval(rect, fill)

        // Murs en terre crue (banco) et bandes décoratives.
        fill.color = Palette.EARTH_WALL
        rect.set(cx - bodyW, cy - r * 0.9f, cx + bodyW, cy + r * 0.6f)
        c.drawRoundRect(rect, r * 0.3f, r * 0.3f, fill)
        fill.color = Palette.EARTH_WALL_DARK
        c.drawRect(cx - bodyW, cy - r * 0.35f, cx + bodyW, cy - r * 0.2f, fill)
        c.drawRect(cx - bodyW, cy + r * 0.1f, cx + bodyW, cy + r * 0.2f, fill)
        rect.set(cx - r * 0.22f, cy + r * 0.05f, cx + r * 0.22f, cy + r * 0.6f)
        c.drawRoundRect(rect, r * 0.2f, r * 0.2f, fill)
        if (king) {
            // Deux tourelles latérales.
            for (side in listOf(-1f, 1f)) {
                fill.color = Palette.EARTH_WALL
                c.drawCircle(cx + side * bodyW, cy - r * 0.1f, r * 0.35f, fill)
            }
        }

        // Toit conique en paille.
        fill.color = Palette.STRAW
        path.reset()
        path.moveTo(cx - bodyW * 1.3f, cy - r * 0.75f)
        path.lineTo(cx + bodyW * 1.3f, cy - r * 0.75f)
        path.lineTo(cx, cy - r * 2.0f)
        path.close()
        c.drawPath(path, fill)
        stroke.color = Palette.STRAW_DARK
        stroke.strokeWidth = r * 0.06f
        for (k in -2..2) c.drawLine(cx, cy - r * 2f, cx + k * bodyW * 0.55f, cy - r * 0.75f, stroke)

        val color = teamColor(t.team)
        if (king) {
            // Parasol royal, symbole de la chefferie.
            stroke.color = Palette.WOOD_DARK
            stroke.strokeWidth = r * 0.08f
            c.drawLine(cx, cy - r * 2f, cx, cy - r * 2.5f, stroke)
            fill.color = color
            rect.set(cx - r * 0.75f, cy - r * 2.95f, cx + r * 0.75f, cy - r * 2.15f)
            c.drawArc(rect, 180f, 180f, true, fill)
            fill.color = Palette.TOGO_YELLOW
            for (k in 0..6) c.drawCircle(cx - r * 0.7f + k * r * 0.233f, cy - r * 2.55f, r * 0.07f, fill)
            if (!t.active) {
                text.textSize = r * 0.6f
                c.drawText("💤", cx + r * 1.1f, cy - r * 1.6f, text)
            }
        } else {
            stroke.color = Palette.WOOD_DARK
            stroke.strokeWidth = r * 0.07f
            c.drawLine(cx, cy - r * 2f, cx, cy - r * 2.6f, stroke)
            fill.color = color
            path.reset()
            path.moveTo(cx, cy - r * 2.6f)
            path.lineTo(cx + r * 0.6f, cy - r * 2.45f)
            path.lineTo(cx, cy - r * 2.3f)
            path.close()
            c.drawPath(path, fill)
        }

        if (t.hitFlash > 0f) {
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawCircle(cx, cy - r * 0.6f, r * 1.1f, fill)
        }
        drawHpBar(c, cx, cy - r * (if (king) 3.2f else 2.85f), r * 2f, t.hpRatio, color, t.hp.toInt())
    }

    private fun drawHpBar(c: Canvas, cx: Float, top: Float, width: Float, ratio: Float, color: Int, value: Int?) {
        val h = scale * 0.28f
        fill.color = Color.argb(200, 20, 10, 5)
        rect.set(cx - width / 2, top, cx + width / 2, top + h)
        c.drawRoundRect(rect, h / 2, h / 2, fill)
        fill.color = color
        rect.set(cx - width / 2, top, cx - width / 2 + width * ratio, top + h)
        c.drawRoundRect(rect, h / 2, h / 2, fill)
        if (value != null) {
            text.textSize = h * 0.95f
            text.color = Color.WHITE
            c.drawText(value.toString(), cx, top + h * 0.85f, text)
        }
    }

    private fun drawTroop(c: Canvas, t: Troop) {
        val r = t.radius * scale
        val lift = if (t.isFlying) scale * 0.7f else 0f
        val bob = if (t.isFlying) sin(battle.time * 6f + t.id) * scale * 0.08f else sin(t.walkPhase) * scale * 0.04f
        var cx = sx(t.pos.x)
        var cy = sy(t.pos.y)

        fill.color = Color.argb(if (t.isFlying) 50 else 80, 0, 0, 0)
        rect.set(cx - r * 0.9f, cy + r * 0.4f, cx + r * 0.9f, cy + r * 0.9f)
        c.drawOval(rect, fill)

        if (t.attackAnim > 0f) {
            val lunge = (t.attackAnim / 0.25f) * scale * 0.15f
            cx += cos(t.facing) * lunge
            cy += sin(t.facing) * lunge
        }
        cy -= lift + bob

        val color = teamColor(t.team)
        val alpha = if (t.deploying) 140 else 255
        fill.color = color
        fill.alpha = alpha
        if (t.isBuilding) {
            rect.set(cx - r, cy - r, cx + r, cy + r)
            c.drawRoundRect(rect, r * 0.35f, r * 0.35f, fill)
        } else {
            c.drawCircle(cx, cy, r, fill)
        }
        fill.color = Color.rgb(250, 236, 210)
        fill.alpha = alpha
        c.drawCircle(cx, cy, r * 0.78f, fill)
        fill.alpha = 255

        text.textSize = r * 1.15f
        text.color = Color.BLACK
        text.alpha = alpha
        c.drawText(t.card.emoji, cx, cy + text.textSize * 0.36f, text)
        text.alpha = 255

        if (t.deploying) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = scale * 0.08f
            rect.set(cx - r * 1.15f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 1.15f)
            c.drawArc(rect, -90f, 360f * (1f - t.deployTimer / t.card.deployTime), false, stroke)
        }
        if (t.hitFlash > 0f) {
            fill.color = Color.argb(120, 255, 255, 255)
            c.drawCircle(cx, cy, r, fill)
        }
        if (t.hp < t.maxHp) drawHpBar(c, cx, cy - r - scale * 0.42f, max(r * 2f, scale * 0.9f), t.hpRatio, color, null)
    }

    private fun drawProjectile(c: Canvas, p: Projectile) {
        val x = sx(p.pos.x)
        val y = sy(p.pos.y) - scale * 0.4f
        when (p.style) {
            ProjectileStyle.ARROW -> {
                val d = p.target.pos - p.pos
                val len = d.length.coerceAtLeast(0.001f)
                stroke.color = Palette.WOOD_DARK
                stroke.strokeWidth = scale * 0.08f
                c.drawLine(x, y, x - d.x / len * scale * 0.5f, y - d.y / len * scale * 0.5f, stroke)
            }
            ProjectileStyle.MAGIC -> {
                fill.color = Color.argb(120, 180, 80, 255)
                c.drawCircle(x, y, scale * 0.3f, fill)
                fill.color = Color.rgb(230, 200, 255)
                c.drawCircle(x, y, scale * 0.14f, fill)
            }
            ProjectileStyle.STONE -> {
                fill.color = Color.rgb(90, 90, 90)
                c.drawCircle(x, y, scale * 0.1f, fill)
            }
            ProjectileStyle.CANNON -> {
                fill.color = Palette.BLACK
                c.drawCircle(x, y, scale * 0.18f, fill)
            }
        }
    }

    private fun drawSpells(c: Canvas) {
        for (s in battle.spells) {
            val x = sx(s.pos.x)
            val y = sy(s.pos.y)
            stroke.color = teamColor(s.team)
            stroke.strokeWidth = scale * 0.1f
            c.drawCircle(x, y, s.card.spellRadius * scale, stroke)
            text.textSize = scale * 1.2f
            val fall = (s.timer / max(s.card.spellDelay, 0.01f)) * scale * 3f
            c.drawText(s.card.emoji, x, y - fall, text)
        }
    }

    private fun drawEffect(c: Canvas, e: Effect) {
        val x = sx(e.pos.x)
        val y = sy(e.pos.y)
        val p = e.progress
        val fade = ((1f - p) * 255).toInt().coerceIn(0, 255)
        when (e.kind) {
            EffectKind.HIT -> {
                fill.color = Color.argb(fade, 255, 255, 220)
                c.drawCircle(x, y, e.radius * scale * (0.4f + p), fill)
            }
            EffectKind.SPLASH -> {
                stroke.color = Color.argb(fade, 255, 150, 40)
                stroke.strokeWidth = scale * 0.15f
                c.drawCircle(x, y, e.radius * scale * (0.3f + 0.7f * p), stroke)
            }
            EffectKind.LIGHTNING -> {
                fill.color = Color.argb(fade / 2, 255, 255, 180)
                c.drawCircle(x, y, e.radius * scale, fill)
                path.reset()
                var px = x
                var py = y - scale * 9f
                path.moveTo(px, py)
                var k = 0
                while (py < y) {
                    py += scale * 1.2f
                    px += if (k++ % 2 == 0) scale * 0.6f else -scale * 0.8f
                    path.lineTo(px, min(py, y))
                }
                path.lineTo(x, y)
                stroke.color = Color.argb(fade, 255, 220, 40)
                stroke.strokeWidth = scale * 0.35f
                c.drawPath(path, stroke)
                stroke.color = Color.argb(fade, 255, 255, 255)
                stroke.strokeWidth = scale * 0.12f
                c.drawPath(path, stroke)
            }
            EffectKind.FIRE -> {
                fill.color = Color.argb(fade / 2, 255, 90, 20)
                c.drawCircle(x, y, e.radius * scale, fill)
                text.textSize = scale * 0.9f
                text.alpha = fade
                for (k in 0 until 7) {
                    val a = k * 0.9f + p * 2f
                    val d = e.radius * scale * (if (k == 0) 0f else 0.6f)
                    c.drawText("🔥", x + cos(a) * d, y + sin(a) * d + text.textSize * 0.3f, text)
                }
                text.alpha = 255
            }
            EffectKind.DEATH -> {
                fill.color = Color.argb(fade / 2, 200, 200, 200)
                c.drawCircle(x, y - p * scale * 0.6f, e.radius * scale * (0.5f + p * 0.5f), fill)
            }
            EffectKind.DEPLOY -> {
                stroke.color = Color.argb(fade, 255, 255, 255)
                stroke.strokeWidth = scale * 0.08f
                c.drawCircle(x, y, e.radius * scale * p, stroke)
            }
            EffectKind.TOWER_DOWN -> {
                fill.color = Color.argb(fade, 255, 140, 30)
                c.drawCircle(x, y, e.radius * scale * (0.3f + p * 0.7f), fill)
                fill.color = Color.argb(fade, 255, 230, 120)
                c.drawCircle(x, y, e.radius * scale * 0.4f * (1f - p), fill)
            }
        }
    }

    private fun ghostPosition(): Vec2? {
        if (!dragging || touchY >= panelTop) return null
        return toWorld(touchX, touchY)
    }

    private fun drawGhost(c: Canvas) {
        val card = selectedCard() ?: return
        val pos = ghostPosition() ?: return
        val ok = battle.canDeploy(Team.PLAYER, card, pos)
        val x = sx(pos.x)
        val y = sy(pos.y)
        val radius = if (card.isSpell) card.spellRadius else card.radius + (if (card.count > 1) 0.6f else 0f)
        fill.color = if (ok) Color.argb(80, 255, 255, 255) else Color.argb(90, 230, 30, 30)
        c.drawCircle(x, y, radius * scale, fill)
        stroke.color = if (ok) Color.WHITE else Palette.TOGO_RED
        stroke.strokeWidth = scale * 0.08f
        c.drawCircle(x, y, radius * scale, stroke)
        text.textSize = scale * 1.1f
        text.alpha = 200
        c.drawText(card.emoji, x, y + text.textSize * 0.35f, text)
        text.alpha = 255
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val baseline = hudH * 0.62f
        text.textSize = hudH * 0.36f
        text.textAlign = Paint.Align.LEFT
        text.color = Palette.KOLA_LIGHT
        c.drawText("🤖 $opponentName  👑 ${battle.enemy.crowns}", w * 0.03f, baseline, text)
        text.textAlign = Paint.Align.RIGHT
        text.color = Color.rgb(140, 190, 255)
        c.drawText("Vous 👑 ${battle.player.crowns}", w * 0.97f, baseline, text)
        text.textAlign = Paint.Align.CENTER

        val left = battle.timeLeft.toInt()
        text.textSize = hudH * 0.44f
        text.color = if (battle.overtime) Palette.TOGO_RED else Color.WHITE
        c.drawText("%d:%02d".format(left / 60, left % 60), w / 2f, hudH * 0.55f, text)
        text.textSize = hudH * 0.22f
        text.color = Palette.TOGO_YELLOW
        val sub = when {
            battle.overtime -> "Prolongation !"
            battle.doubleKola -> "Kola ×2"
            else -> arenaName
        }
        c.drawText(sub, w / 2f, hudH * 0.85f, text)
    }

    private fun drawPanel(c: Canvas) {
        val hand = battle.player.hand
        val kola = battle.player.kola
        drawCard(c, nextRect, hand.next, kola, selected = false, small = true)
        text.textSize = nextRect.width() * 0.2f
        text.color = Palette.STRAW
        c.drawText("Suivante", nextRect.centerX(), nextRect.top - text.textSize * 0.4f, text)
        for (i in 0 until 4) drawCard(c, cardRects[i], hand.cards[i], kola, selected = i == selected, small = false)

        // Jauge de kola.
        val r = kolaRect
        fill.color = Color.argb(220, 25, 12, 6)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        fill.color = Palette.KOLA
        rect.set(r.left, r.top, r.left + r.width() * (kola / Battle.MAX_KOLA), r.bottom)
        c.drawRoundRect(rect, r.height() / 2, r.height() / 2, fill)
        stroke.color = Color.argb(120, 0, 0, 0)
        stroke.strokeWidth = 2f
        for (k in 1 until 10) {
            val x = r.left + r.width() * k / 10f
            c.drawLine(x, r.top, x, r.bottom, stroke)
        }
        val bubbleR = r.height() * 0.85f
        val bx = r.left - bubbleR * 1.1f
        fill.color = Palette.KOLA
        c.drawCircle(bx, r.centerY(), bubbleR, fill)
        stroke.color = Palette.TOGO_YELLOW
        stroke.strokeWidth = bubbleR * 0.15f
        c.drawCircle(bx, r.centerY(), bubbleR, stroke)
        text.textSize = bubbleR * 1.1f
        text.color = Color.WHITE
        c.drawText(kola.toInt().toString(), bx, r.centerY() + text.textSize * 0.36f, text)
        text.textSize = r.height() * 0.7f
        c.drawText("Kola", r.centerX(), r.centerY() + text.textSize * 0.36f, text)
    }

    private fun drawCard(c: Canvas, r: RectF, card: CardDef, kola: Float, selected: Boolean, small: Boolean) {
        val lift = if (selected) r.height() * 0.08f else 0f
        rect.set(r.left, r.top - lift, r.right, r.bottom - lift)
        val corner = r.width() * 0.12f
        fill.color = when (card.kind) {
            CardKind.SPELL -> Color.rgb(70, 50, 110)
            CardKind.BUILDING -> Color.rgb(110, 70, 40)
            CardKind.TROOP -> Palette.PANEL_LIGHT
        }
        c.drawRoundRect(rect, corner, corner, fill)
        stroke.color = if (selected) Palette.TOGO_YELLOW else Color.argb(150, 0, 0, 0)
        stroke.strokeWidth = if (selected) r.width() * 0.05f else r.width() * 0.02f
        c.drawRoundRect(rect, corner, corner, stroke)

        text.textSize = rect.width() * 0.48f
        c.drawText(card.emoji, rect.centerX(), rect.top + rect.height() * 0.55f, text)
        if (!small) {
            text.color = Color.WHITE
            text.textSize = rect.width() * 0.13f
            val maxW = rect.width() * 0.92f
            while (text.measureText(card.name) > maxW && text.textSize > 8f) text.textSize *= 0.92f
            c.drawText(card.name, rect.centerX(), rect.bottom - rect.height() * 0.08f, text)
        }

        // Pastille de coût.
        val br = rect.width() * (if (small) 0.2f else 0.17f)
        val bx = rect.left + br * 0.9f
        val by = rect.top + br * 0.9f
        fill.color = Palette.KOLA
        c.drawCircle(bx, by, br, fill)
        text.textSize = br * 1.3f
        text.color = Color.WHITE
        c.drawText(card.cost.toString(), bx, by + text.textSize * 0.36f, text)

        if (kola < card.cost) {
            // Assombrit la carte et montre la progression de la recharge.
            fill.color = Color.argb(150, 0, 0, 0)
            c.drawRoundRect(rect, corner, corner, fill)
            fill.color = Color.argb(70, 232, 120, 160)
            val h = rect.height() * (kola / card.cost)
            c.drawRect(rect.left, rect.bottom - h, rect.right, rect.bottom, fill)
        }
    }

    private fun drawMessage(c: Canvas) {
        val msg = message ?: return
        if (messageTimer <= 0f) return
        text.textSize = scale * 0.8f
        text.color = Color.argb((min(1f, messageTimer) * 255).toInt(), 255, 255, 255)
        c.drawText(msg, width / 2f, panelTop - scale * 1.2f, text)
    }

    private fun drawResult(c: Canvas, result: BattleResult) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.color = Color.argb(170, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, fill)

        val (title, color) = when (result.outcome) {
            Outcome.VICTORY -> "VICTOIRE !" to Palette.TOGO_YELLOW
            Outcome.DEFEAT -> "DÉFAITE" to Palette.TOGO_RED
            Outcome.DRAW -> "ÉGALITÉ" to Color.WHITE
        }
        text.textSize = w * 0.13f
        text.color = color
        c.drawText(title, w / 2f, h * 0.3f, text)

        text.textSize = w * 0.06f
        text.color = Color.WHITE
        c.drawText("Vous ${result.playerCrowns} 👑  –  👑 ${result.enemyCrowns} $opponentName", w / 2f, h * 0.4f, text)
        text.textSize = w * 0.055f
        text.color = Palette.TOGO_YELLOW
        c.drawText(rewardText, w / 2f, h * 0.5f, text)
        val proverb = when (result.outcome) {
            Outcome.VICTORY -> "« L'union fait la force. »"
            Outcome.DEFEAT -> "« Petit à petit, l'oiseau fait son nid. »"
            Outcome.DRAW -> "« Un seul doigt ne peut ramasser la farine. »"
        }
        text.textSize = w * 0.04f
        text.color = Palette.STRAW
        c.drawText(proverb, w / 2f, h * 0.56f, text)

        fill.color = Palette.TOGO_GREEN
        c.drawRoundRect(exitRect, exitRect.height() / 3, exitRect.height() / 3, fill)
        stroke.color = Palette.TOGO_YELLOW
        stroke.strokeWidth = exitRect.height() * 0.06f
        c.drawRoundRect(exitRect, exitRect.height() / 3, exitRect.height() / 3, stroke)
        text.textSize = exitRect.height() * 0.4f
        text.color = Color.WHITE
        c.drawText("Retour au village", exitRect.centerX(), exitRect.centerY() + text.textSize * 0.36f, text)
    }

    // ------------------------------------------------------------------ Entrées

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        if (battle.isOver) {
            if (event.actionMasked == MotionEvent.ACTION_UP && exitRect.contains(x, y)) onExit()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = cardRects.indexOfFirst { it.contains(x, y) }
                if (hit >= 0) {
                    selected = hit
                    dragging = true
                    touchX = x
                    touchY = y
                } else if (selected >= 0 && y < panelTop && y > hudH) {
                    tryPlay(toWorld(x, y))
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                touchX = x
                touchY = y
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                dragging = false
                if (y < panelTop && y > hudH) tryPlay(toWorld(x, y))
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun tryPlay(pos: Vec2) {
        val card = selectedCard() ?: return
        when {
            battle.player.kola < card.cost -> showMessage("Pas assez de kola !")
            !battle.canDeploy(Team.PLAYER, card, pos) -> showMessage("Déployez dans votre camp")
            battle.playCard(Team.PLAYER, selected, pos) -> selected = -1
        }
    }

    private fun showMessage(msg: String) {
        message = msg
        messageTimer = 1.5f
    }
}
