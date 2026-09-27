package com.mortargoblin.shipka

class Player {
    var x = 0f
    var y = 0f
    var hp = MAX_HP
    var angle = 0f
    var fireCooldown = 0f
    var invulnerable = 0f
    var kebabPower = 0f
    var walkPhase = 0f

    fun reset(x: Float, y: Float) {
        this.x = x
        this.y = y
        hp = MAX_HP
        angle = -Math.PI.toFloat() / 2f
        fireCooldown = 0f
        invulnerable = 1.5f
        kebabPower = 0f
        walkPhase = 0f
    }

    companion object {
        const val MAX_HP = 100
    }
}

enum class EnemyType(
    val hp: Int,
    val baseSpeed: Float,
    val points: Int,
) {
    /** Regular Ottoman line infantry: navy tunic, red fez, keeps distance and fires. */
    NIZAM(hp = 2, baseSpeed = 95f, points = 100),

    /** Irregular cavalryman on foot: white turban, curved sword, charges in. */
    BASHI_BAZOUK(hp = 1, baseSpeed = 165f, points = 150),
}

class Enemy(val type: EnemyType, var x: Float, var y: Float) {
    var hp = type.hp
    var angle = 0f
    var fireCooldown = 1.5f
    var walkPhase = 0f
    var hitFlash = 0f
    var strafeDir = 1f
    var attackCooldown = 0f
}

class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val fromPlayer: Boolean,
) {
    var life = 2f
}

class Kebab(val x: Float, val y: Float) {
    var life = 14f
    var age = 0f
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val size: Float,
    /** Non-null for the fez / turban that flies off a fallen enemy. */
    val hat: EnemyType? = null,
) {
    val maxLife = life
    var spin = 0f
}

class FloatText(val text: String, var x: Float, var y: Float, val color: Int) {
    var life = 1.3f
}

/** Floating virtual joystick: the base appears where the finger lands. */
class Stick {
    var active = false
    var pointerId = -1
    var originX = 0f
    var originY = 0f
    var knobX = 0f
    var knobY = 0f
    var dx = 0f
    var dy = 0f
    var radius = 70f

    val magnitude: Float get() = kotlin.math.sqrt(dx * dx + dy * dy)

    fun start(id: Int, x: Float, y: Float) {
        active = true
        pointerId = id
        originX = x
        originY = y
        knobX = x
        knobY = y
        dx = 0f
        dy = 0f
    }

    fun move(x: Float, y: Float) {
        var vx = x - originX
        var vy = y - originY
        val len = kotlin.math.sqrt(vx * vx + vy * vy)
        if (len > radius) {
            // Drag the base along so the stick never feels "stuck" at the rim.
            originX = x - vx / len * radius
            originY = y - vy / len * radius
            vx = x - originX
            vy = y - originY
        }
        knobX = x
        knobY = y
        dx = vx / radius
        dy = vy / radius
    }

    fun release() {
        active = false
        pointerId = -1
        dx = 0f
        dy = 0f
    }
}
