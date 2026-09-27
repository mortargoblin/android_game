package com.mortargoblin.shipka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Game(context: Context) {
    private enum class State { TITLE, PLAYING, GAME_OVER }

    private val prefs = context.getSharedPreferences("shipka", Context.MODE_PRIVATE)

    private var w = 1280f
    private var h = 720f
    /** World unit: 1 at a 720px-tall surface. */
    private var u = 1f
    private var background: Bitmap? = null

    private var state = State.TITLE
    private var stateTime = 0f
    private var paused = false

    private val rnd = Random(System.nanoTime())
    private val player = Player()
    private val enemies = ArrayList<Enemy>()
    private val bullets = ArrayList<Bullet>()
    private val kebabs = ArrayList<Kebab>()
    private val particles = ArrayList<Particle>()
    private val texts = ArrayList<FloatText>()
    private val moveStick = Stick()
    private val aimStick = Stick()

    private var score = 0
    private var best = prefs.getInt("best", 0)
    private var wave = 0
    private var toSpawn = 0
    private var spawnTimer = 0f
    private var waveBanner = 0f
    private var kebabTimer = 0f
    private var kebabsEaten = 0
    private var kills = 0
    private var shake = 0f
    private var time = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    // ------------------------------------------------------------------ lifecycle

    fun resize(width: Int, height: Int) {
        val changed = width.toFloat() != w || height.toFloat() != h || background == null
        w = width.toFloat()
        h = height.toFloat()
        u = min(w, h) / 720f
        moveStick.radius = 70f * u
        aimStick.radius = 70f * u
        if (changed) {
            background?.recycle()
            background = Art.makeBackground(width, height, u)
            player.x = player.x.coerceIn(0f, w)
            player.y = player.y.coerceIn(0f, h)
            if (state != State.PLAYING) player.reset(w / 2, h / 2)
        }
    }

    fun onPause() {
        paused = state == State.PLAYING
        moveStick.release()
        aimStick.release()
    }

    private fun startGame() {
        enemies.clear()
        bullets.clear()
        kebabs.clear()
        particles.clear()
        texts.clear()
        player.reset(w / 2, h / 2)
        score = 0
        wave = 0
        kills = 0
        kebabsEaten = 0
        kebabTimer = 10f
        time = 0f
        paused = false
        setState(State.PLAYING)
        startWave()
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
    }

    private fun startWave() {
        wave++
        toSpawn = 4 + wave * 3
        spawnTimer = 1.5f
        waveBanner = 2.2f
        // A kebab at the start of each wave from the second on: the field kitchen caught up.
        if (wave > 1) spawnKebab()
    }

    // ------------------------------------------------------------------ input

    fun onTouch(e: MotionEvent, scale: Float) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i) * scale
                val y = e.getY(i) * scale
                when (state) {
                    State.TITLE -> if (stateTime > 0.3f) startGame()
                    State.GAME_OVER -> if (stateTime > 1.2f) startGame()
                    State.PLAYING -> {
                        if (paused) {
                            paused = false
                            return
                        }
                        val id = e.getPointerId(i)
                        if (x < w / 2) {
                            if (!moveStick.active) moveStick.start(id, x, y)
                        } else if (!aimStick.active) {
                            aimStick.start(id, x, y)
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val x = e.getX(i) * scale
                    val y = e.getY(i) * scale
                    if (moveStick.active && moveStick.pointerId == id) moveStick.move(x, y)
                    if (aimStick.active && aimStick.pointerId == id) aimStick.move(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (moveStick.pointerId == id) moveStick.release()
                if (aimStick.pointerId == id) aimStick.release()
            }
            MotionEvent.ACTION_CANCEL -> {
                moveStick.release()
                aimStick.release()
            }
        }
    }

    // ------------------------------------------------------------------ update

    fun update(dt: Float) {
        stateTime += dt
        if (state != State.PLAYING || paused) {
            updateParticles(dt)
            return
        }
        time += dt
        shake = max(0f, shake - dt * 30f * u)
        waveBanner = max(0f, waveBanner - dt)

        updatePlayer(dt)
        updateSpawning(dt)
        updateEnemies(dt)
        updateBullets(dt)
        updateKebabs(dt)
        updateParticles(dt)

        if (player.hp <= 0) {
            player.hp = 0
            burst(player.x, player.y, Art.RUS_COAT, 30, 260f)
            burst(player.x, player.y, Art.RUS_CAP, 10, 200f)
            if (score > best) {
                best = score
                prefs.edit().putInt("best", best).apply()
            }
            moveStick.release()
            aimStick.release()
            setState(State.GAME_OVER)
        }
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        val boosted = p.kebabPower > 0f
        p.kebabPower = max(0f, p.kebabPower - dt)
        p.invulnerable = max(0f, p.invulnerable - dt)
        p.fireCooldown -= dt

        var mx = moveStick.dx
        var my = moveStick.dy
        val mag = hypot(mx, my)
        if (mag > 1f) {
            mx /= mag
            my /= mag
        }
        val speed = 240f * u * (if (boosted) 1.35f else 1f)
        val r = PLAYER_R * u
        p.x = (p.x + mx * speed * dt).coerceIn(r, w - r)
        p.y = (p.y + my * speed * dt).coerceIn(r, h - r)
        if (mag > 0.1f) p.walkPhase += dt * 14f * min(1f, mag)

        val aiming = aimStick.active && aimStick.magnitude > 0.25f
        if (aiming) {
            p.angle = atan2(aimStick.dy, aimStick.dx)
        } else if (mag > 0.2f) {
            p.angle = atan2(my, mx)
        }

        if (aiming && p.fireCooldown <= 0f) {
            p.fireCooldown = if (boosted) 0.16f else 0.28f
            val a = p.angle + (rnd.nextFloat() - 0.5f) * 0.06f
            val muzzle = r * 2.0f
            val sx = p.x + cos(p.angle) * muzzle - sin(p.angle) * r * 0.42f
            val sy = p.y + sin(p.angle) * muzzle + cos(p.angle) * r * 0.42f
            val bs = 950f * u
            bullets.add(Bullet(sx, sy, cos(a) * bs, sin(a) * bs, fromPlayer = true))
            // Black-powder smoke
            repeat(4) {
                particles.add(
                    Particle(
                        sx, sy,
                        cos(a) * 60f * u + (rnd.nextFloat() - 0.5f) * 80f * u,
                        sin(a) * 60f * u + (rnd.nextFloat() - 0.5f) * 80f * u,
                        0.5f + rnd.nextFloat() * 0.4f, 0xAAE0E0E0.toInt(), (5f + rnd.nextFloat() * 5f) * u,
                    )
                )
            }
            particles.add(Particle(sx, sy, 0f, 0f, 0.06f, 0xFFFFD54F.toInt(), 9f * u))
        }
    }

    private fun updateSpawning(dt: Float) {
        if (toSpawn > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnEnemy()
                toSpawn--
                spawnTimer = max(0.3f, 1.5f - wave * 0.1f) * (0.6f + rnd.nextFloat() * 0.7f)
            }
        } else if (enemies.isEmpty()) {
            score += wave * 250
            texts.add(FloatText("Wave cleared! +${wave * 250}", w / 2, h * 0.4f, 0xFFFFE082.toInt()))
            startWave()
        }

        kebabTimer -= dt
        if (kebabTimer <= 0f) {
            kebabTimer = 14f + rnd.nextFloat() * 8f
            spawnKebab()
        }
    }

    private fun spawnEnemy() {
        val bashiChance = min(0.55f, 0.1f + wave * 0.06f)
        val type = if (rnd.nextFloat() < bashiChance) EnemyType.BASHI_BAZOUK else EnemyType.NIZAM
        val m = 40f * u
        val (x, y) = when (rnd.nextInt(4)) {
            0 -> rnd.nextFloat() * w to -m
            1 -> rnd.nextFloat() * w to h + m
            2 -> -m to rnd.nextFloat() * h
            else -> w + m to rnd.nextFloat() * h
        }
        enemies.add(Enemy(type, x, y).apply {
            strafeDir = if (rnd.nextBoolean()) 1f else -1f
            fireCooldown = 1.2f + rnd.nextFloat() * 1.5f
        })
    }

    private fun spawnKebab() {
        if (kebabs.size >= 3) return
        val m = 80f * u
        kebabs.add(Kebab(m + rnd.nextFloat() * (w - 2 * m), m + rnd.nextFloat() * (h - 2 * m)))
    }

    private fun updateEnemies(dt: Float) {
        val p = player
        val er = ENEMY_R * u
        for (e in enemies) {
            e.hitFlash = max(0f, e.hitFlash - dt * 5f)
            e.attackCooldown = max(0f, e.attackCooldown - dt)
            val dx = p.x - e.x
            val dy = p.y - e.y
            val dist = max(1f, hypot(dx, dy))
            val nx = dx / dist
            val ny = dy / dist
            e.angle = atan2(dy, dx)
            val speed = (e.type.baseSpeed + wave * 4f) * u
            var vx: Float
            var vy: Float
            when (e.type) {
                EnemyType.NIZAM -> {
                    // Advance to firing range, then strafe and keep some distance.
                    val onField = e.x > er && e.x < w - er && e.y > er && e.y < h - er
                    when {
                        dist > 330f * u || !onField -> { vx = nx; vy = ny }
                        dist < 200f * u -> { vx = -nx * 0.7f; vy = -ny * 0.7f }
                        else -> { vx = -ny * e.strafeDir * 0.6f; vy = nx * e.strafeDir * 0.6f }
                    }
                    if (rnd.nextFloat() < dt * 0.3f) e.strafeDir = -e.strafeDir
                    e.fireCooldown -= dt
                    if (onField && dist < 560f * u && e.fireCooldown <= 0f) {
                        e.fireCooldown = max(1.1f, 2.8f - wave * 0.12f) + rnd.nextFloat() * 1.2f
                        val a = e.angle + (rnd.nextFloat() - 0.5f) * 0.25f
                        val bs = (360f + wave * 8f) * u
                        val sx = e.x + cos(e.angle) * er * 1.9f - sin(e.angle) * er * 0.42f
                        val sy = e.y + sin(e.angle) * er * 1.9f + cos(e.angle) * er * 0.42f
                        bullets.add(Bullet(sx, sy, cos(a) * bs, sin(a) * bs, fromPlayer = false))
                        particles.add(Particle(sx, sy, 0f, 0f, 0.5f, 0x99E0E0E0.toInt(), 8f * u))
                    }
                }
                EnemyType.BASHI_BAZOUK -> {
                    // Charge, weaving slightly.
                    val weave = sin(time * 4f + e.strafeDir * 3f) * 0.35f
                    vx = nx - ny * weave
                    vy = ny + nx * weave
                    if (dist < (PLAYER_R + ENEMY_R) * u * 1.1f && e.attackCooldown <= 0f) {
                        e.attackCooldown = 0.9f
                        damagePlayer(18, nx, ny)
                    }
                }
            }
            e.x += vx * speed * dt
            e.y += vy * speed * dt
            if (vx != 0f || vy != 0f) e.walkPhase += dt * 12f
        }

        // Keep the crowd from stacking on top of each other.
        for (i in enemies.indices) {
            val a = enemies[i]
            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d = hypot(dx, dy)
                val minD = er * 2f
                if (d in 0.01f..minD) {
                    val push = (minD - d) * 0.5f
                    a.x -= dx / d * push
                    a.y -= dy / d * push
                    b.x += dx / d * push
                    b.y += dy / d * push
                }
            }
        }
    }

    private fun damagePlayer(amount: Int, nx: Float, ny: Float) {
        val p = player
        if (p.invulnerable > 0f) return
        p.hp -= amount
        p.invulnerable = 0.6f
        shake = 12f * u
        val r = PLAYER_R * u
        p.x = (p.x + nx * 25f * u).coerceIn(r, w - r)
        p.y = (p.y + ny * 25f * u).coerceIn(r, h - r)
        burst(p.x, p.y, 0xFFB71C1C.toInt(), 8, 160f)
    }

    private fun updateBullets(dt: Float) {
        val pr = PLAYER_R * u
        val er = ENEMY_R * u
        var i = bullets.size - 1
        while (i >= 0) {
            val b = bullets[i]
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.life -= dt
            var dead = b.life <= 0f || b.x < -50 || b.x > w + 50 || b.y < -50 || b.y > h + 50
            if (!dead && b.fromPlayer) {
                for (k in enemies.indices.reversed()) {
                    val e = enemies[k]
                    if (hypot(e.x - b.x, e.y - b.y) < er) {
                        dead = true
                        e.hp--
                        e.hitFlash = 1f
                        e.x += b.vx * 0.015f
                        e.y += b.vy * 0.015f
                        if (e.hp <= 0) killEnemy(k, b)
                        break
                    }
                }
            } else if (!dead && hypot(player.x - b.x, player.y - b.y) < pr * 0.9f) {
                dead = true
                val len = max(1f, hypot(b.vx, b.vy))
                damagePlayer(10, b.vx / len, b.vy / len)
            }
            if (dead) bullets.removeAt(i)
            i--
        }
    }

    private fun killEnemy(index: Int, bullet: Bullet) {
        val e = enemies.removeAt(index)
        kills++
        score += e.type.points
        texts.add(FloatText("+${e.type.points}", e.x, e.y - 30f * u, Color.WHITE))
        val coat = if (e.type == EnemyType.NIZAM) Art.TUR_COAT else Art.BASHI_COAT
        burst(e.x, e.y, coat, 14, 180f)
        burst(e.x, e.y, 0xFF8D6E63.toInt(), 8, 120f)
        // The hat goes flying.
        val len = max(1f, hypot(bullet.vx, bullet.vy))
        particles.add(
            Particle(
                e.x, e.y,
                bullet.vx / len * 220f * u + (rnd.nextFloat() - 0.5f) * 120f * u,
                bullet.vy / len * 220f * u + (rnd.nextFloat() - 0.5f) * 120f * u,
                1.4f, 0, ENEMY_R * u * 0.5f, hat = e.type,
            ).apply { spin = (rnd.nextFloat() - 0.5f) * 20f }
        )
        if (rnd.nextFloat() < 0.12f && kebabs.size < 3) kebabs.add(Kebab(e.x, e.y))
    }

    private fun updateKebabs(dt: Float) {
        val p = player
        val reach = (PLAYER_R + KEBAB_S) * u
        var i = kebabs.size - 1
        while (i >= 0) {
            val k = kebabs[i]
            k.age += dt
            k.life -= dt
            if (hypot(p.x - k.x, p.y - k.y) < reach) {
                val healed = min(KEBAB_HEAL, Player.MAX_HP - p.hp)
                p.hp += healed
                p.kebabPower = 6f
                kebabsEaten++
                score += 50
                texts.add(FloatText("KEBAB! +$healed HP", k.x, k.y - 30f * u, 0xFFFFD54F.toInt()))
                burst(k.x, k.y, 0xFFFFD54F.toInt(), 12, 150f)
                kebabs.removeAt(i)
            } else if (k.life <= 0f) {
                kebabs.removeAt(i)
            }
            i--
        }
    }

    private fun updateParticles(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 1f - min(1f, dt * 3f)
            p.vy *= 1f - min(1f, dt * 3f)
            p.spin *= 1f - min(1f, dt * 1.5f)
            if (p.life <= 0f) particles.removeAt(i)
            i--
        }
        i = texts.size - 1
        while (i >= 0) {
            val t = texts[i]
            t.life -= dt
            t.y -= 40f * u * dt
            if (t.life <= 0f) texts.removeAt(i)
            i--
        }
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        repeat(count) {
            val a = rnd.nextFloat() * 6.2832f
            val s = (0.3f + rnd.nextFloat()) * speed * u
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, 0.4f + rnd.nextFloat() * 0.5f, color, (2.5f + rnd.nextFloat() * 3f) * u))
        }
    }

    // ------------------------------------------------------------------ draw

    fun draw(c: Canvas) {
        c.save()
        if (shake > 0f) c.translate((rnd.nextFloat() - 0.5f) * shake, (rnd.nextFloat() - 0.5f) * shake)
        background?.let { c.drawBitmap(it, 0f, 0f, null) } ?: c.drawColor(Art.GRASS)

        for (k in kebabs) {
            val blinkOut = k.life < 3f && ((k.life * 6f).toInt() % 2 == 0)
            if (!blinkOut) {
                val bob = sin(k.age * 4f) * 3f * u
                Art.drawKebab(c, k.x, k.y + bob, KEBAB_S * u, 0.6f + 0.4f * sin(k.age * 5f))
            }
        }

        if (state == State.PLAYING) {
            val p = player
            val blink = p.invulnerable > 0f && ((p.invulnerable * 14f).toInt() % 2 == 0)
            if (p.kebabPower > 0f) {
                paint.color = Color.argb(70, 255, 200, 60)
                c.drawCircle(p.x, p.y, PLAYER_R * u * 1.6f, paint)
            }
            Art.drawRussian(c, p.x, p.y, p.angle, PLAYER_R * u, p.walkPhase, blink)
        }

        for (e in enemies) Art.drawTurk(c, e.type, e.x, e.y, e.angle, ENEMY_R * u, e.walkPhase, e.hitFlash)

        for (b in bullets) {
            paint.color = if (b.fromPlayer) 0xFFFFF59D.toInt() else 0xFFFF8A65.toInt()
            c.drawCircle(b.x, b.y, (if (b.fromPlayer) 3.5f else 5f) * u, paint)
        }

        for (p in particles) {
            val t = (p.life / p.maxLife).coerceIn(0f, 1f)
            if (p.hat != null) {
                c.save()
                c.rotate(p.spin * (p.maxLife - p.life) * 57.3f, p.x, p.y)
                if (p.hat == EnemyType.NIZAM) Art.drawFez(c, p.x, p.y, p.size, 0f) else Art.drawTurban(c, p.x, p.y, p.size)
                c.restore()
            } else {
                paint.color = p.color
                paint.alpha = (Color.alpha(p.color) * t).toInt()
                c.drawCircle(p.x, p.y, p.size * (0.5f + t * 0.5f), paint)
            }
        }
        c.restore()

        for (t in texts) {
            textPaint.textSize = 26f * u
            textPaint.color = t.color
            textPaint.alpha = (255 * (t.life / 1.3f).coerceIn(0f, 1f)).toInt()
            outlined(c, t.text, t.x, t.y)
        }

        when (state) {
            State.TITLE -> drawTitle(c)
            State.PLAYING -> {
                drawHud(c)
                drawSticks(c)
                if (paused) drawPaused(c)
            }
            State.GAME_OVER -> drawGameOver(c)
        }
    }

    private fun outlined(c: Canvas, text: String, x: Float, y: Float) {
        val color = textPaint.color
        val alpha = textPaint.alpha
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = textPaint.textSize * 0.14f
        textPaint.color = Color.BLACK
        textPaint.alpha = alpha
        c.drawText(text, x, y, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = color
        textPaint.alpha = alpha
        c.drawText(text, x, y, textPaint)
    }

    private fun drawHud(c: Canvas) {
        val pad = 20f * u
        // Health bar
        val bw = 240f * u
        val bh = 22f * u
        paint.color = 0x99000000.toInt()
        rect.set(pad - 3 * u, pad - 3 * u, pad + bw + 3 * u, pad + bh + 3 * u)
        c.drawRoundRect(rect, 6 * u, 6 * u, paint)
        val frac = player.hp / Player.MAX_HP.toFloat()
        paint.color = when {
            frac > 0.5f -> 0xFF43A047.toInt()
            frac > 0.25f -> 0xFFFBC02D.toInt()
            else -> 0xFFE53935.toInt()
        }
        rect.set(pad, pad, pad + bw * frac, pad + bh)
        c.drawRoundRect(rect, 4 * u, 4 * u, paint)
        textPaint.textSize = 18f * u
        textPaint.color = Color.WHITE
        textPaint.alpha = 255
        textPaint.textAlign = Paint.Align.LEFT
        c.drawText("${player.hp} / ${Player.MAX_HP}", pad + 8 * u, pad + bh * 0.78f, textPaint)

        // Kebab counter under the health bar
        Art.drawKebab(c, pad + 22 * u, pad + bh + 30 * u, 16f * u, 0f)
        textPaint.textSize = 24f * u
        outlined(c, "× $kebabsEaten", pad + 48 * u, pad + bh + 38 * u)
        if (player.kebabPower > 0f) {
            textPaint.color = 0xFFFFD54F.toInt()
            textPaint.textSize = 18f * u
            outlined(c, "KEBAB POWER ${"%.1f".format(player.kebabPower)}s", pad, pad + bh + 70 * u)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f * u
        textPaint.color = Color.WHITE
        outlined(c, "$score", w / 2, pad + 26 * u)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 24f * u
        outlined(c, "Wave $wave", w - pad, pad + 22 * u)
        textPaint.textSize = 18f * u
        textPaint.color = 0xFFE0E0E0.toInt()
        outlined(c, "Foes left ${enemies.size + toSpawn}", w - pad, pad + 48 * u)
        textPaint.textAlign = Paint.Align.CENTER

        if (waveBanner > 0f) {
            val a = min(1f, waveBanner * 2f)
            textPaint.textSize = 56f * u
            textPaint.color = 0xFFFFE0B2.toInt()
            textPaint.alpha = (255 * a).toInt()
            outlined(c, "WAVE $wave", w / 2, h * 0.3f)
            textPaint.textSize = 22f * u
            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * a).toInt()
            outlined(c, if (wave == 1) "The Ottomans advance on the pass!" else "More of them are coming up the slope!", w / 2, h * 0.3f + 36 * u)
        }
    }

    private fun drawSticks(c: Canvas) {
        drawStick(c, moveStick, 120f * u, h - 120f * u, "MOVE")
        drawStick(c, aimStick, w - 120f * u, h - 120f * u, "AIM & FIRE")
    }

    private fun drawStick(c: Canvas, s: Stick, hintX: Float, hintY: Float, label: String) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * u
        if (s.active) {
            paint.color = 0x66FFFFFF
            c.drawCircle(s.originX, s.originY, s.radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0x55FFFFFF
            val kx = s.originX + s.dx * s.radius
            val ky = s.originY + s.dy * s.radius
            c.drawCircle(kx, ky, s.radius * 0.45f, paint)
        } else if (time < 8f) {
            paint.color = 0x33FFFFFF
            c.drawCircle(hintX, hintY, s.radius, paint)
            paint.style = Paint.Style.FILL
            textPaint.textSize = 16f * u
            textPaint.color = Color.WHITE
            textPaint.alpha = 160
            c.drawText(label, hintX, hintY + 6f * u, textPaint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun dim(c: Canvas, alpha: Int) {
        paint.color = Color.argb(alpha, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawTitle(c: Canvas) {
        dim(c, 150)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 76f * u
        textPaint.color = 0xFFFFE0B2.toInt()
        textPaint.alpha = 255
        outlined(c, "SHIPKA 1877", w / 2, h * 0.24f)
        textPaint.textSize = 24f * u
        textPaint.color = Color.WHITE
        outlined(c, "Russo-Turkish War  ·  Hold the pass, soldier!", w / 2, h * 0.24f + 44 * u)

        // The cast
        val cy = h * 0.52f
        val r = 30f * u
        val bob = sin(stateTime * 3f) * 4f * u
        Art.drawRussian(c, w / 2 - 230 * u, cy + bob, 0f, r, stateTime * 6f, false)
        Art.drawTurk(c, EnemyType.NIZAM, w / 2 + 40 * u, cy - bob, Math.PI.toFloat(), r, stateTime * 6f, 0f)
        Art.drawTurk(c, EnemyType.BASHI_BAZOUK, w / 2 + 220 * u, cy + bob, Math.PI.toFloat(), r, stateTime * 6f, 0f)
        Art.drawKebab(c, w / 2 - 95 * u, cy + 5 * u, 26f * u, 0.5f + 0.5f * sin(stateTime * 4f))
        textPaint.textSize = 16f * u
        textPaint.color = 0xFFE0E0E0.toInt()
        outlined(c, "You", w / 2 - 230 * u, cy + 62 * u)
        outlined(c, "Kebab (+${KEBAB_HEAL} HP)", w / 2 - 95 * u, cy + 62 * u)
        outlined(c, "Nizam rifleman", w / 2 + 40 * u, cy + 62 * u)
        outlined(c, "Bashi-bazouk", w / 2 + 220 * u, cy + 62 * u)

        textPaint.textSize = 20f * u
        textPaint.color = Color.WHITE
        outlined(c, "Left thumb: move    ·    Right thumb: aim & fire", w / 2, h * 0.76f)
        if ((stateTime * 2f).toInt() % 2 == 0) {
            textPaint.textSize = 32f * u
            textPaint.color = 0xFFFFD54F.toInt()
            outlined(c, "TAP TO BEGIN", w / 2, h * 0.87f)
        }
        if (best > 0) {
            textPaint.textSize = 18f * u
            textPaint.color = 0xFFBDBDBD.toInt()
            outlined(c, "Best: $best", w / 2, h * 0.94f)
        }
    }

    private fun drawGameOver(c: Canvas) {
        dim(c, min(170, (stateTime * 300).toInt()))
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 64f * u
        textPaint.color = 0xFFEF9A9A.toInt()
        textPaint.alpha = 255
        outlined(c, "THE PASS HAS FALLEN", w / 2, h * 0.3f)
        textPaint.textSize = 28f * u
        textPaint.color = Color.WHITE
        outlined(c, "Score: $score", w / 2, h * 0.3f + 60 * u)
        textPaint.textSize = 22f * u
        outlined(c, "Reached wave $wave  ·  $kills foes defeated  ·  $kebabsEaten kebabs eaten", w / 2, h * 0.3f + 100 * u)
        textPaint.color = 0xFFFFE082.toInt()
        outlined(c, if (score >= best && score > 0) "New best!" else "Best: $best", w / 2, h * 0.3f + 136 * u)
        if (stateTime > 1.2f && (stateTime * 2f).toInt() % 2 == 0) {
            textPaint.textSize = 30f * u
            textPaint.color = 0xFFFFD54F.toInt()
            outlined(c, "TAP TO FIGHT AGAIN", w / 2, h * 0.8f)
        }
    }

    private fun drawPaused(c: Canvas) {
        dim(c, 120)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 48f * u
        textPaint.color = Color.WHITE
        textPaint.alpha = 255
        outlined(c, "PAUSED", w / 2, h * 0.45f)
        textPaint.textSize = 22f * u
        outlined(c, "Tap to resume", w / 2, h * 0.45f + 40 * u)
    }

    companion object {
        private const val PLAYER_R = 30f
        private const val ENEMY_R = 29f
        private const val KEBAB_S = 26f
        private const val KEBAB_HEAL = 25
    }
}
