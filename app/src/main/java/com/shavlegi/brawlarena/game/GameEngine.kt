package com.shavlegi.brawlarena.game

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Arena size in world units. Fighter radius is 26. */
const val ARENA_W = 1700f
const val ARENA_H = 1100f
const val FIGHTER_RADIUS = 26f

/** Poison starts after this many ms; it ends stalemates. */
const val POISON_START_MS = 150_000L

/** Damage dealt for 1% of super charge. */
const val SUPER_CHARGE_PER_DAMAGE = 25f

data class Wall(val x: Float, val y: Float, val w: Float, val h: Float)
data class Bush(val x: Float, val y: Float, val w: Float, val h: Float)
data class Cube(val id: Int, val x: Float, val y: Float)

enum class AiMode { WANDER, CHASE, ATTACK, FLEE, FETCH_CUBE }

class Fighter(
    val id: Int,
    val name: String,
    val def: BrawlerDef,
    val isPlayer: Boolean,
    var x: Float,
    var y: Float
) {
    /** Aim direction, radians. */
    var angle: Float = 0f
    /** Desired move direction (unit vector, set by input or bot AI). */
    var moveX: Float = 0f
    var moveY: Float = 0f
    var hp: Float = def.hp.toFloat()
    var maxHp: Float = def.hp.toFloat()
    var cubes: Int = 0
    /** 0..100. */
    var superCharge: Float = 0f
    var cooldownUntil: Long = 0L
    var alive: Boolean = true
    var kills: Int = 0
    var inBush: Boolean = false

    // Bot AI state (unused for the player).
    var aiThinkUntil: Long = 0L
    var aiTargetX: Float = 0f
    var aiTargetY: Float = 0f
    var aiMode: AiMode = AiMode.WANDER
    var aiStrafeSign: Float = 1f
    var aiStrafeUntil: Long = 0L

    fun damageMultiplier(): Float = 1f + 0.08f * cubes
}

class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val ownerId: Int,
    val radius: Float,
    val maxDist: Float,
    val pierce: Boolean,
    val kind: WeaponKind,
    val targetX: Float = 0f,
    val targetY: Float = 0f,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val flightMs: Long = 0L,
    val aoeRadius: Float = 0f
) {
    var traveled: Float = 0f
    var flightElapsedMs: Long = 0L
    val hitIds: MutableSet<Int> = mutableSetOf()

    /** 0..1 progress for lobbed bombs. */
    fun flightProgress(): Float =
        if (flightMs <= 0L) 1f else (flightElapsedMs.toFloat() / flightMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Pure-Kotlin game logic for one Showdown match: player + 5 bots,
 * last one standing wins. No Android dependencies.
 */
class GameEngine(playerDef: BrawlerDef, seed: Long = System.currentTimeMillis()) {

    private val random = Random(seed)

    val walls: List<Wall> = listOf(
        Wall(800f, 470f, 100f, 160f), // center block
        Wall(280f, 180f, 240f, 60f),
        Wall(1140f, 180f, 240f, 60f),
        Wall(280f, 860f, 240f, 60f),
        Wall(1140f, 860f, 240f, 60f),
        Wall(620f, 90f, 70f, 170f),
        Wall(1010f, 90f, 70f, 170f),
        Wall(620f, 840f, 70f, 170f),
        Wall(1010f, 840f, 70f, 170f)
    )

    val bushes: List<Bush> = listOf(
        Bush(80f, 480f, 180f, 140f),
        Bush(1440f, 480f, 180f, 140f),
        Bush(700f, 40f, 300f, 90f),
        Bush(700f, 970f, 300f, 90f),
        Bush(350f, 480f, 150f, 140f),
        Bush(1200f, 480f, 150f, 140f)
    )

    val fighters = mutableListOf<Fighter>()
    val projectiles = mutableListOf<Projectile>()
    val cubes = mutableListOf<Cube>()

    /** fighters[0] is always the player. */
    val player: Fighter get() = fighters[0]

    /** Match clock in ms (also drives cooldowns and AI timers). */
    var nowMs: Long = 0L
        private set
    var over: Boolean = false
        private set
    var winner: Fighter? = null
        private set
    var playerPlacement: Int = 0
        private set
    var poisonActive: Boolean = false
        private set
    var poisonDps: Float = 0f
        private set

    private var nextCubeId = 1
    private var cubeSpawnTimerMs: Long = 0L

    init {
        val botNames = listOf("რობო", "მაქსი", "ლეო", "ნიკა", "ზუკა")
        val spawnPoints = listOf(
            850f to 950f, // player, bottom center
            150f to 150f,
            1550f to 150f,
            150f to 950f,
            1550f to 950f,
            850f to 180f
        )
        // Bots: one of each brawler, plus two random extras.
        val botDefs = (BrawlerDef.ALL + List(2) { BrawlerDef.ALL.random(random) }).shuffled(random)
        spawnPoints.forEachIndexed { index, point ->
            val (sx, sy) = point
            if (index == 0) {
                fighters += Fighter(0, "", playerDef, true, sx, sy)
            } else {
                fighters += Fighter(index, botNames[index - 1], botDefs[index - 1], false, sx, sy)
            }
        }
        repeat(6) { spawnCube() }
    }

    fun aliveCount(): Int = fighters.count { it.alive }

    // ------------------------------------------------------------------ input

    fun setPlayerMove(dx: Float, dy: Float) {
        val len = hypot(dx, dy)
        if (len > 1f) {
            player.moveX = dx / len
            player.moveY = dy / len
        } else {
            player.moveX = dx
            player.moveY = dy
        }
    }

    fun tryFirePlayer(): Boolean = tryFire(player)

    fun trySuperPlayer(): Boolean = trySuper(player)

    // ------------------------------------------------------------------ update

    fun update(dtMs: Long) {
        if (over) return
        val dt = dtMs.coerceIn(0L, 50L)
        nowMs += dt
        val dtSec = dt / 1000f

        // Poison endgame.
        if (nowMs > POISON_START_MS) {
            poisonActive = true
            val poisonSeconds = (nowMs - POISON_START_MS) / 1000f
            poisonDps = min(400f, 120f + poisonSeconds * 4f)
            for (f in fighters) {
                if (over) break
                if (f.alive) applyDamage(f, poisonDps * dtSec, null)
            }
            if (over) return
        }

        // Bots think.
        for (f in fighters) {
            if (!f.isPlayer && f.alive && nowMs >= f.aiThinkUntil) {
                f.aiThinkUntil = nowMs + 150L
                think(f)
            }
        }

        // Movement.
        for (f in fighters) {
            if (f.alive) moveFighter(f, dtSec)
        }
        separateFighters()

        // Bush status.
        for (f in fighters) {
            f.inBush = f.alive && bushes.any { pointInRect(f.x, f.y, it) }
        }

        // Cube pickups.
        val picked = mutableListOf<Cube>()
        for (cube in cubes) {
            val taker = fighters.firstOrNull { it.alive && dist(it.x, it.y, cube.x, cube.y) < 44f }
            if (taker != null) {
                taker.cubes += 1
                taker.maxHp += 400f
                taker.hp = min(taker.maxHp, taker.hp + 400f)
                picked += cube
            }
        }
        cubes.removeAll(picked)

        // Cube respawn.
        cubeSpawnTimerMs += dt
        if (cubeSpawnTimerMs >= 6000L) {
            cubeSpawnTimerMs = 0L
            if (cubes.size < 8) spawnCube()
        }

        updateProjectiles(dt, dtSec)
    }

    private fun moveFighter(f: Fighter, dtSec: Float) {
        val len = hypot(f.moveX, f.moveY)
        if (len < 0.01f) return
        val nx = f.moveX / len
        val ny = f.moveY / len
        val step = f.def.speed * dtSec
        // Axis-separated movement so fighters slide along walls.
        val tryX = f.x + nx * step
        if (!hitsWall(tryX, f.y, FIGHTER_RADIUS) &&
            tryX in FIGHTER_RADIUS..(ARENA_W - FIGHTER_RADIUS)
        ) {
            f.x = tryX
        }
        val tryY = f.y + ny * step
        if (!hitsWall(f.x, tryY, FIGHTER_RADIUS) &&
            tryY in FIGHTER_RADIUS..(ARENA_H - FIGHTER_RADIUS)
        ) {
            f.y = tryY
        }
        // Face the movement direction.
        f.angle = atan2(ny, nx)
    }

    private fun separateFighters() {
        for (i in fighters.indices) {
            val a = fighters[i]
            if (!a.alive) continue
            for (j in i + 1 until fighters.size) {
                val b = fighters[j]
                if (!b.alive) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d = hypot(dx, dy)
                val minDist = FIGHTER_RADIUS * 2f
                if (d < minDist && d > 0.01f) {
                    val push = (minDist - d) / 2f
                    val ux = dx / d
                    val uy = dy / d
                    a.x = (a.x - ux * push).coerceIn(FIGHTER_RADIUS, ARENA_W - FIGHTER_RADIUS)
                    a.y = (a.y - uy * push).coerceIn(FIGHTER_RADIUS, ARENA_H - FIGHTER_RADIUS)
                    b.x = (b.x + ux * push).coerceIn(FIGHTER_RADIUS, ARENA_W - FIGHTER_RADIUS)
                    b.y = (b.y + uy * push).coerceIn(FIGHTER_RADIUS, ARENA_H - FIGHTER_RADIUS)
                }
            }
        }
    }

    // ------------------------------------------------------------------ combat

    fun tryFire(f: Fighter): Boolean {
        if (!f.alive || over) return false
        if (nowMs < f.cooldownUntil) return false
        val target = nearestVisibleEnemy(f, f.def.range)
        val aim = if (target != null) atan2(target.y - f.y, target.x - f.x) else f.angle
        f.angle = aim
        val mult = f.damageMultiplier()
        when (f.def.weapon) {
            WeaponKind.SHOTGUN -> {
                val count = f.def.pellets
                val spread = Math.toRadians(f.def.spreadDeg.toDouble()).toFloat()
                for (i in 0 until count) {
                    val offset = if (count == 1) 0f else (i.toFloat() / (count - 1) - 0.5f) * spread
                    val a = aim + offset
                    projectiles += Projectile(
                        x = f.x + cos(a) * (FIGHTER_RADIUS + 8f),
                        y = f.y + sin(a) * (FIGHTER_RADIUS + 8f),
                        vx = cos(a) * f.def.projectileSpeed,
                        vy = sin(a) * f.def.projectileSpeed,
                        damage = f.def.damage * mult,
                        ownerId = f.id,
                        radius = 6f,
                        maxDist = f.def.range,
                        pierce = false,
                        kind = WeaponKind.SHOTGUN
                    )
                }
            }
            WeaponKind.RIFLE -> {
                projectiles += Projectile(
                    x = f.x + cos(aim) * (FIGHTER_RADIUS + 8f),
                    y = f.y + sin(aim) * (FIGHTER_RADIUS + 8f),
                    vx = cos(aim) * f.def.projectileSpeed,
                    vy = sin(aim) * f.def.projectileSpeed,
                    damage = f.def.damage * mult,
                    ownerId = f.id,
                    radius = 8f,
                    maxDist = f.def.range,
                    pierce = false,
                    kind = WeaponKind.RIFLE
                )
            }
            WeaponKind.LOBBER -> {
                val (tx, ty) = if (target != null) {
                    target.x to target.y
                } else {
                    (f.x + cos(aim) * f.def.range) to (f.y + sin(aim) * f.def.range)
                }
                projectiles += Projectile(
                    x = f.x,
                    y = f.y,
                    vx = 0f,
                    vy = 0f,
                    damage = f.def.damage * mult,
                    ownerId = f.id,
                    radius = 10f,
                    maxDist = 0f,
                    pierce = false,
                    kind = WeaponKind.LOBBER,
                    targetX = tx.coerceIn(0f, ARENA_W),
                    targetY = ty.coerceIn(0f, ARENA_H),
                    startX = f.x,
                    startY = f.y,
                    flightMs = f.def.flightMs,
                    aoeRadius = 130f
                )
            }
        }
        f.cooldownUntil = nowMs + f.def.cooldownMs
        return true
    }

    fun trySuper(f: Fighter): Boolean {
        if (!f.alive || over) return false
        if (f.superCharge < 100f) return false
        val superRange = when (f.def.weapon) {
            WeaponKind.SHOTGUN -> 320f
            WeaponKind.RIFLE -> 1000f
            WeaponKind.LOBBER -> f.def.range
        }
        val target = nearestVisibleEnemy(f, superRange)
        val aim = if (target != null) atan2(target.y - f.y, target.x - f.x) else f.angle
        f.angle = aim
        val mult = f.damageMultiplier()
        when (f.def.weapon) {
            WeaponKind.SHOTGUN -> {
                val count = 12
                val spread = Math.toRadians(60.0).toFloat()
                for (i in 0 until count) {
                    val offset = (i.toFloat() / (count - 1) - 0.5f) * spread
                    val a = aim + offset
                    projectiles += Projectile(
                        x = f.x + cos(a) * (FIGHTER_RADIUS + 8f),
                        y = f.y + sin(a) * (FIGHTER_RADIUS + 8f),
                        vx = cos(a) * 1000f,
                        vy = sin(a) * 1000f,
                        damage = f.def.superDamage * mult,
                        ownerId = f.id,
                        radius = 7f,
                        maxDist = 320f,
                        pierce = false,
                        kind = WeaponKind.SHOTGUN
                    )
                }
            }
            WeaponKind.RIFLE -> {
                projectiles += Projectile(
                    x = f.x + cos(aim) * (FIGHTER_RADIUS + 8f),
                    y = f.y + sin(aim) * (FIGHTER_RADIUS + 8f),
                    vx = cos(aim) * 1600f,
                    vy = sin(aim) * 1600f,
                    damage = f.def.superDamage * mult,
                    ownerId = f.id,
                    radius = 12f,
                    maxDist = 1000f,
                    pierce = true,
                    kind = WeaponKind.RIFLE
                )
            }
            WeaponKind.LOBBER -> {
                val (tx, ty) = if (target != null) {
                    target.x to target.y
                } else {
                    (f.x + cos(aim) * f.def.range) to (f.y + sin(aim) * f.def.range)
                }
                projectiles += Projectile(
                    x = f.x,
                    y = f.y,
                    vx = 0f,
                    vy = 0f,
                    damage = f.def.superDamage * mult,
                    ownerId = f.id,
                    radius = 14f,
                    maxDist = 0f,
                    pierce = false,
                    kind = WeaponKind.LOBBER,
                    targetX = tx.coerceIn(0f, ARENA_W),
                    targetY = ty.coerceIn(0f, ARENA_H),
                    startX = f.x,
                    startY = f.y,
                    flightMs = 700L,
                    aoeRadius = 240f
                )
            }
        }
        f.superCharge = 0f
        return true
    }

    private fun updateProjectiles(dtMs: Long, dtSec: Float) {
        val dead = mutableListOf<Projectile>()
        for (p in projectiles) {
            if (p.kind == WeaponKind.LOBBER) {
                p.flightElapsedMs += dtMs
                val t = p.flightProgress()
                p.x = p.startX + (p.targetX - p.startX) * t
                p.y = p.startY + (p.targetY - p.startY) * t
                if (p.flightElapsedMs >= p.flightMs) {
                    explode(p)
                    dead += p
                }
                continue
            }
            val stepX = p.vx * dtSec
            val stepY = p.vy * dtSec
            p.x += stepX
            p.y += stepY
            p.traveled += hypot(stepX, stepY)
            var consumed = false
            if (p.x < 0f || p.x > ARENA_W || p.y < 0f || p.y > ARENA_H) {
                consumed = true
            } else if (walls.any { pointInRect(p.x, p.y, it) }) {
                consumed = true
            } else if (p.traveled >= p.maxDist) {
                consumed = true
            } else {
                val owner = fighters.firstOrNull { it.id == p.ownerId }
                for (f in fighters) {
                    if (!f.alive || f.id == p.ownerId) continue
                    if (p.hitIds.contains(f.id)) continue
                    if (dist(p.x, p.y, f.x, f.y) < p.radius + FIGHTER_RADIUS) {
                        applyDamage(f, p.damage, owner)
                        if (p.pierce) {
                            p.hitIds += f.id
                        } else {
                            consumed = true
                        }
                        break
                    }
                }
            }
            if (consumed) dead += p
            if (over) return
        }
        projectiles.removeAll(dead)
    }

    private fun explode(p: Projectile) {
        val owner = fighters.firstOrNull { it.id == p.ownerId }
        for (f in fighters) {
            if (!f.alive || f.id == p.ownerId) continue
            if (dist(p.targetX, p.targetY, f.x, f.y) < p.aoeRadius + FIGHTER_RADIUS) {
                applyDamage(f, p.damage, owner)
                if (over) return
            }
        }
    }

    fun applyDamage(target: Fighter, damage: Float, attacker: Fighter?) {
        if (!target.alive || over) return
        target.hp -= damage
        if (attacker != null && attacker != target && attacker.alive) {
            attacker.superCharge =
                min(100f, attacker.superCharge + damage / SUPER_CHARGE_PER_DAMAGE)
        }
        if (target.hp <= 0f) {
            target.hp = 0f
            target.alive = false
            target.moveX = 0f
            target.moveY = 0f
            if (attacker != null && attacker != target) attacker.kills += 1
            dropCubes(target)
            if (target.isPlayer) {
                playerPlacement = aliveCount() + 1
                over = true
                winner = null
            } else {
                checkEnd()
            }
        }
    }

    private fun checkEnd() {
        if (over) return
        if (aliveCount() <= 1) {
            over = true
            winner = fighters.firstOrNull { it.alive }
            if (winner?.isPlayer == true) playerPlacement = 1
        }
    }

    private fun dropCubes(f: Fighter) {
        val dropCount = (f.cubes + 1) / 2
        repeat(dropCount) {
            val angle = random.nextFloat() * (2f * PI.toFloat())
            val r = 40f + random.nextFloat() * 60f
            val x = (f.x + cos(angle) * r).coerceIn(40f, ARENA_W - 40f)
            val y = (f.y + sin(angle) * r).coerceIn(40f, ARENA_H - 40f)
            if (walls.none { pointInRect(x, y, it) }) {
                cubes += Cube(nextCubeId++, x, y)
            }
        }
        f.cubes = 0
    }

    private fun spawnCube() {
        repeat(20) {
            val x = random.nextFloat() * (ARENA_W - 160f) + 80f
            val y = random.nextFloat() * (ARENA_H - 160f) + 80f
            if (walls.none { pointInRect(x, y, it) }) {
                cubes += Cube(nextCubeId++, x, y)
                return
            }
        }
    }

    // ------------------------------------------------------------------ vision

    /** Nearest enemy this fighter can see, within [maxDist]. */
    fun nearestVisibleEnemy(f: Fighter, maxDist: Float = 950f): Fighter? {
        var best: Fighter? = null
        var bestDist = Float.MAX_VALUE
        for (other in fighters) {
            if (other === f || !other.alive) continue
            val d = dist(f.x, f.y, other.x, other.y)
            if (d < bestDist && d <= maxDist && isVisibleTo(f, other)) {
                best = other
                bestDist = d
            }
        }
        return best
    }

    /**
     * Visibility rules: max sight 950; a fighter in a bush is hidden unless
     * the observer shares that bush or is within 90 units; walls block sight
     * — except for lobbers, whose bombs fly over walls.
     */
    fun isVisibleTo(observer: Fighter, target: Fighter): Boolean {
        if (!target.alive) return false
        val d = dist(observer.x, observer.y, target.x, target.y)
        if (d > 950f) return false
        if (target.inBush && d > 90f) {
            val targetBush = bushes.firstOrNull { pointInRect(target.x, target.y, it) }
            val observerBush = bushes.firstOrNull { pointInRect(observer.x, observer.y, it) }
            if (targetBush == null || targetBush != observerBush) return false
        }
        if (observer.def.weapon == WeaponKind.LOBBER) return true
        return hasLineOfSight(observer.x, observer.y, target.x, target.y)
    }

    private fun hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        walls.none { segmentIntersectsRect(x1, y1, x2, y2, it) }

    // ------------------------------------------------------------------ bot AI

    /**
     * One decision tick (every 150 ms) for a bot: flee at low HP, attack a
     * visible enemy in range, chase one out of range, fetch a nearby cube,
     * or wander. Bots never see through bushes and never shoot at enemies
     * they cannot see.
     */
    private fun think(bot: Fighter) {
        val enemy = nearestVisibleEnemy(bot, bot.def.range)
        val nearestCube = cubes.minByOrNull { dist(bot.x, bot.y, it.x, it.y) }
        val cubeDist = nearestCube?.let { dist(bot.x, bot.y, it.x, it.y) } ?: Float.MAX_VALUE

        // Flee at low HP when an enemy is close.
        if (bot.hp < bot.maxHp * 0.3f && enemy != null &&
            dist(bot.x, bot.y, enemy.x, enemy.y) < 350f
        ) {
            bot.aiMode = AiMode.FLEE
            val d = dist(bot.x, bot.y, enemy.x, enemy.y).coerceAtLeast(1f)
            bot.moveX = (bot.x - enemy.x) / d
            bot.moveY = (bot.y - enemy.y) / d
            return
        }

        if (enemy != null) {
            val d = dist(bot.x, bot.y, enemy.x, enemy.y)
            val preferred = when (bot.def.weapon) {
                WeaponKind.SHOTGUN -> 180f
                WeaponKind.RIFLE -> 550f
                WeaponKind.LOBBER -> 380f
            }
            if (d <= bot.def.range * 0.95f) {
                bot.aiMode = AiMode.ATTACK
                if (nowMs >= bot.aiStrafeUntil) {
                    bot.aiStrafeUntil = nowMs + 1200L
                    bot.aiStrafeSign = -bot.aiStrafeSign
                }
                val ex = (enemy.x - bot.x) / d.coerceAtLeast(1f)
                val ey = (enemy.y - bot.y) / d.coerceAtLeast(1f)
                val radial = when {
                    d > preferred + 60f -> 0.7f
                    d < preferred - 60f -> -0.7f
                    else -> 0f
                }
                val mx = ex * radial + -ey * bot.aiStrafeSign * 0.8f
                val my = ey * radial + ex * bot.aiStrafeSign * 0.8f
                val len = hypot(mx, my).coerceAtLeast(0.001f)
                bot.moveX = mx / len
                bot.moveY = my / len
                if (bot.superCharge >= 100f) trySuper(bot) else tryFire(bot)
            } else {
                bot.aiMode = AiMode.CHASE
                val dd = d.coerceAtLeast(1f)
                bot.moveX = (enemy.x - bot.x) / dd
                bot.moveY = (enemy.y - bot.y) / dd
            }
            return
        }

        // No visible enemy: fetch a nearby cube.
        if (nearestCube != null && cubeDist < 500f) {
            bot.aiMode = AiMode.FETCH_CUBE
            val dd = cubeDist.coerceAtLeast(1f)
            bot.moveX = (nearestCube.x - bot.x) / dd
            bot.moveY = (nearestCube.y - bot.y) / dd
            return
        }

        // Wander to a random free point.
        bot.aiMode = AiMode.WANDER
        val td = dist(bot.x, bot.y, bot.aiTargetX, bot.aiTargetY)
        if (td < 40f || (bot.aiTargetX == 0f && bot.aiTargetY == 0f)) {
            var tx: Float
            var ty: Float
            do {
                tx = random.nextFloat() * (ARENA_W - 160f) + 80f
                ty = random.nextFloat() * (ARENA_H - 160f) + 80f
            } while (walls.any { pointInRect(tx, ty, it) })
            bot.aiTargetX = tx
            bot.aiTargetY = ty
        }
        val dd = dist(bot.x, bot.y, bot.aiTargetX, bot.aiTargetY).coerceAtLeast(1f)
        bot.moveX = (bot.aiTargetX - bot.x) / dd
        bot.moveY = (bot.aiTargetY - bot.y) / dd
    }

    private fun hitsWall(cx: Float, cy: Float, radius: Float): Boolean =
        walls.any { circleHitsRect(cx, cy, radius, it) }
}

// ------------------------------------------------------------------ geometry

fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

private fun pointInRect(x: Float, y: Float, r: Wall): Boolean =
    x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h

private fun pointInRect(x: Float, y: Float, r: Bush): Boolean =
    x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h

private fun circleHitsRect(cx: Float, cy: Float, radius: Float, r: Wall): Boolean {
    val nx = cx.coerceIn(r.x, r.x + r.w)
    val ny = cy.coerceIn(r.y, r.y + r.h)
    val dx = cx - nx
    val dy = cy - ny
    return dx * dx + dy * dy < radius * radius
}

private fun direction(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float =
    (x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)

private fun segmentsIntersect(
    x1: Float, y1: Float, x2: Float, y2: Float,
    x3: Float, y3: Float, x4: Float, y4: Float
): Boolean {
    val d1 = direction(x3, y3, x4, y4, x1, y1)
    val d2 = direction(x3, y3, x4, y4, x2, y2)
    val d3 = direction(x1, y1, x2, y2, x3, y3)
    val d4 = direction(x1, y1, x2, y2, x4, y4)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
        ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
}

private fun segmentIntersectsRect(x1: Float, y1: Float, x2: Float, y2: Float, r: Wall): Boolean {
    if (pointInRect(x1, y1, r) || pointInRect(x2, y2, r)) return true
    return segmentsIntersect(x1, y1, x2, y2, r.x, r.y, r.x + r.w, r.y) ||
        segmentsIntersect(x1, y1, x2, y2, r.x + r.w, r.y, r.x + r.w, r.y + r.h) ||
        segmentsIntersect(x1, y1, x2, y2, r.x + r.w, r.y + r.h, r.x, r.y + r.h) ||
        segmentsIntersect(x1, y1, x2, y2, r.x, r.y + r.h, r.x, r.y)
}
