package com.shavlegi.brawlarena.game

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shavlegi.brawlarena.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The match screen: Canvas arena, HUD, floating joystick (left),
 * FIRE (hold) and SUPER buttons (right). The game loop runs on
 * `withFrameNanos`; every frame bumps [frame], which drives recomposition
 * and Canvas redraws.
 */
@Composable
fun GameScreen(
    engine: GameEngine,
    onEnd: (GameEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    var frame by remember { mutableLongStateOf(0L) }
    var firing by remember { mutableStateOf(false) }
    var endHandled by remember { mutableStateOf(false) }
    val latestOnEnd by rememberUpdatedState(onEnd)

    var joyBase by remember { mutableStateOf(Offset.Zero) }
    var joyKnob by remember { mutableStateOf(Offset.Zero) }
    var joyActive by remember { mutableStateOf(false) }

    val player = engine.player
    val playerName = stringResource(R.string.you)

    LaunchedEffect(Unit) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos < 0L) lastNanos = nanos
                val dtMs = ((nanos - lastNanos) / 1_000_000L).coerceIn(0L, 50L)
                lastNanos = nanos
                engine.update(dtMs)
                if (firing) engine.tryFirePlayer()
                frame++
                if (engine.over && !endHandled) {
                    endHandled = true
                    latestOnEnd(engine)
                }
            }
            if (engine.over) break
        }
    }

    // Reading frame here subscribes this composable (and the HUD) to frame updates.
    val tick = frame

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGame(engine, size, tick, playerName)
        }

        // ---- HUD: fighters left, timer, cubes ----
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.alive, engine.aliveCount()),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Text(
                formatTime(engine.nowMs),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Text(
                stringResource(R.string.cubes_label, player.cubes),
                color = Color(0xFF35C5FF),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (engine.poisonActive) {
            Text(
                stringResource(R.string.poison_warning),
                color = Color(0xFFFF6B6B),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // ---- Bottom-center: player HP bar + super bar ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hpFrac = (player.hp / player.maxHp).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(220.dp, 12.dp)
                    .background(Color(0xFF222222), RoundedCornerShape(6.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(hpFrac)
                        .background(hpColor(hpFrac), RoundedCornerShape(6.dp))
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .size(220.dp, 8.dp)
                    .background(Color(0xFF222222), RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((player.superCharge / 100f).coerceIn(0f, 1f))
                        .background(Color(0xFFFFC93D), RoundedCornerShape(4.dp))
                )
            }
        }

        // ---- Floating joystick zone (left 45%) ----
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            joyBase = offset
                            joyKnob = offset
                            joyActive = true
                        },
                        onDragEnd = {
                            joyActive = false
                            joyBase = Offset.Zero
                            joyKnob = Offset.Zero
                            engine.setPlayerMove(0f, 0f)
                        },
                        onDragCancel = {
                            joyActive = false
                            joyBase = Offset.Zero
                            joyKnob = Offset.Zero
                            engine.setPlayerMove(0f, 0f)
                        },
                        onDrag = { _, dragAmount ->
                            joyKnob += dragAmount
                            val v = joyKnob - joyBase
                            val len = v.getDistance()
                            val maxR = 110f
                            val clamped = if (len > maxR) v * (maxR / len) else v
                            joyKnob = joyBase + clamped
                            engine.setPlayerMove(clamped.x / maxR, clamped.y / maxR)
                        }
                    )
                }
        ) {
            if (joyActive) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.12f), radius = 110f, center = joyBase)
                    drawCircle(
                        Color.White.copy(alpha = 0.35f),
                        radius = 110f,
                        center = joyBase,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(Color(0xFF4C8DFF), radius = 48f, center = joyKnob)
                }
            }
        }

        // ---- SUPER button ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 152.dp)
                .size(76.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { engine.trySuperPlayer() })
                }
                .background(
                    if (player.superCharge >= 100f) Color(0xFFFFC93D) else Color(0xFF6B5B1E),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.super_label),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "${player.superCharge.toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // ---- FIRE button (hold to keep shooting) ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 28.dp)
                .size(104.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        firing = true
                        tryAwaitRelease()
                        firing = false
                    })
                }
                .background(
                    if (firing) Color(0xFFFF8A3D) else Color(0xFFE85D2B),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.fire),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000L
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun hpColor(frac: Float): Color = when {
    frac > 0.5f -> Color(0xFF4CAF50)
    frac > 0.25f -> Color(0xFFFFC93D)
    else -> Color(0xFFFF6B6B)
}

// ------------------------------------------------------------------ drawing

private fun DrawScope.drawGame(engine: GameEngine, canvasSize: Size, tick: Long, playerName: String) {
    val scale = min(canvasSize.width / 1000f, canvasSize.height / 620f)
    val viewW = canvasSize.width / scale
    val viewH = canvasSize.height / scale
    val player = engine.player
    val camLeft = (player.x - viewW / 2f).coerceIn(0f, (ARENA_W - viewW).coerceAtLeast(0f))
    val camTop = (player.y - viewH / 2f).coerceIn(0f, (ARENA_H - viewH).coerceAtLeast(0f))
    fun sx(wx: Float) = (wx - camLeft) * scale
    fun sy(wy: Float) = (wy - camTop) * scale
    fun sr(wr: Float) = wr * scale

    // Ground.
    drawRect(Color(0xFF0F1F1A), size = canvasSize)
    val gridStep = 100f
    var gx = (camLeft / gridStep).toInt() * gridStep
    while (gx < camLeft + viewW) {
        var gy = (camTop / gridStep).toInt() * gridStep
        while (gy < camTop + viewH) {
            drawCircle(Color(0xFF1B3227), radius = 2.5f * scale, center = Offset(sx(gx), sy(gy)))
            gy += gridStep
        }
        gx += gridStep
    }
    // Arena border.
    drawRect(
        Color(0xFF4C8DFF),
        topLeft = Offset(sx(0f), sy(0f)),
        size = Size(sr(ARENA_W), sr(ARENA_H)),
        style = Stroke(width = 4f * scale)
    )

    // Bushes.
    for (bush in engine.bushes) {
        drawRoundRect(
            Color(0xFF1E6B2E).copy(alpha = 0.85f),
            topLeft = Offset(sx(bush.x), sy(bush.y)),
            size = Size(sr(bush.w), sr(bush.h)),
            cornerRadius = CornerRadius(18f * scale, 18f * scale)
        )
        drawCircle(
            Color(0xFF2E8B3E),
            radius = 10f * scale,
            center = Offset(sx(bush.x + bush.w * 0.25f), sy(bush.y + bush.h * 0.4f))
        )
        drawCircle(
            Color(0xFF2E8B3E),
            radius = 8f * scale,
            center = Offset(sx(bush.x + bush.w * 0.6f), sy(bush.y + bush.h * 0.65f))
        )
        drawCircle(
            Color(0xFF2E8B3E),
            radius = 9f * scale,
            center = Offset(sx(bush.x + bush.w * 0.8f), sy(bush.y + bush.h * 0.3f))
        )
    }

    // Power cubes (pulse with the frame counter).
    val pulse = 1f + 0.08f * sin(tick / 20f)
    for (cube in engine.cubes) {
        val half = 13f * pulse * scale
        drawRoundRect(
            Color(0xFF35C5FF),
            topLeft = Offset(sx(cube.x) - half, sy(cube.y) - half),
            size = Size(half * 2f, half * 2f),
            cornerRadius = CornerRadius(4f * scale, 4f * scale)
        )
        val inner = 6f * scale
        drawRect(
            Color.White,
            topLeft = Offset(sx(cube.x) - inner, sy(cube.y) - inner),
            size = Size(inner * 2f, inner * 2f)
        )
    }

    // Walls.
    for (wall in engine.walls) {
        drawRoundRect(
            Color(0xFF3A4A63),
            topLeft = Offset(sx(wall.x), sy(wall.y)),
            size = Size(sr(wall.w), sr(wall.h)),
            cornerRadius = CornerRadius(8f * scale, 8f * scale)
        )
        drawRoundRect(
            Color(0xFF22304A),
            topLeft = Offset(sx(wall.x), sy(wall.y)),
            size = Size(sr(wall.w), sr(wall.h)),
            cornerRadius = CornerRadius(8f * scale, 8f * scale),
            style = Stroke(width = 3f * scale)
        )
        drawLine(
            Color(0xFF5A6E92),
            start = Offset(sx(wall.x) + 6f * scale, sy(wall.y) + 5f * scale),
            end = Offset(sx(wall.x + wall.w) - 6f * scale, sy(wall.y) + 5f * scale),
            strokeWidth = 3f * scale
        )
    }

    // Projectiles.
    for (p in engine.projectiles) {
        if (p.kind == WeaponKind.LOBBER) {
            drawCircle(
                Color(0xFFFF6B6B).copy(alpha = 0.6f),
                radius = sr(p.aoeRadius),
                center = Offset(sx(p.targetX), sy(p.targetY)),
                style = Stroke(width = 2.5f * scale)
            )
            val t = p.flightProgress()
            val lift = sin(PI.toFloat() * t) * 34f
            drawCircle(
                Color.Black.copy(alpha = 0.3f),
                radius = sr(10f),
                center = Offset(sx(p.x), sy(p.y))
            )
            drawCircle(
                Color(0xFF222222),
                radius = sr(p.radius),
                center = Offset(sx(p.x), sy(p.y) - sr(lift))
            )
            drawCircle(
                Color(0xFFFF6B6B),
                radius = sr(p.radius * 0.55f),
                center = Offset(sx(p.x), sy(p.y) - sr(lift))
            )
        } else {
            val col = when (p.kind) {
                WeaponKind.SHOTGUN -> Color(0xFFFFB347)
                WeaponKind.RIFLE -> Color(0xFF7FB0FF)
                WeaponKind.LOBBER -> Color(0xFFFF6B6B)
            }
            drawCircle(col.copy(alpha = 0.3f), radius = sr(p.radius * 2f), center = Offset(sx(p.x), sy(p.y)))
            drawCircle(col, radius = sr(p.radius), center = Offset(sx(p.x), sy(p.y)))
            drawCircle(Color.White, radius = sr(p.radius * 0.4f), center = Offset(sx(p.x), sy(p.y)))
        }
    }

    // Fighters.
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 26f * scale
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    for (f in engine.fighters) {
        if (!f.alive) continue
        // Enemies hidden in bushes are not drawn (unless the player can see them).
        if (!f.isPlayer && f.inBush && !engine.isVisibleTo(player, f)) continue
        val alpha = if (f.inBush) 0.55f else 1f
        val body = Color(f.def.color)
        val cx = sx(f.x)
        val cy = sy(f.y)
        val r = sr(FIGHTER_RADIUS)
        drawCircle(
            Color.Black.copy(alpha = 0.35f * alpha),
            radius = r,
            center = Offset(cx + 3f * scale, cy + 4f * scale)
        )
        drawCircle(body.copy(alpha = alpha), radius = r, center = Offset(cx, cy))
        drawCircle(
            Color.White.copy(alpha = 0.9f * alpha),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f * scale)
        )
        if (f.isPlayer) {
            drawCircle(
                Color.White.copy(alpha = 0.9f),
                radius = r + 6f * scale,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f * scale)
            )
        }
        // Gun barrel toward the aim direction.
        val bx = cx + cos(f.angle) * (r + 10f * scale)
        val by = cy + sin(f.angle) * (r + 10f * scale)
        drawLine(
            Color(0xFF222222).copy(alpha = alpha),
            start = Offset(cx, cy),
            end = Offset(bx, by),
            strokeWidth = 8f * scale
        )
        // Eyes.
        val ex = cos(f.angle)
        val ey = sin(f.angle)
        val px = -ey
        val py = ex
        drawCircle(
            Color.White.copy(alpha = alpha),
            radius = 4.5f * scale,
            center = Offset(cx + ex * 9f * scale + px * 8f * scale, cy + ey * 9f * scale + py * 8f * scale)
        )
        drawCircle(
            Color.White.copy(alpha = alpha),
            radius = 4.5f * scale,
            center = Offset(cx + ex * 9f * scale - px * 8f * scale, cy + ey * 9f * scale - py * 8f * scale)
        )
        // HP bar.
        val barW = 56f * scale
        val barH = 8f * scale
        val barX = cx - barW / 2f
        val barY = cy - r - 16f * scale
        val hpFrac = (f.hp / f.maxHp).coerceIn(0f, 1f)
        drawRoundRect(
            Color(0xFF222222).copy(alpha = 0.9f),
            topLeft = Offset(barX, barY),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(4f * scale, 4f * scale)
        )
        drawRoundRect(
            hpColor(hpFrac),
            topLeft = Offset(barX, barY),
            size = Size(barW * hpFrac, barH),
            cornerRadius = CornerRadius(4f * scale, 4f * scale)
        )
        // Name.
        val name = if (f.isPlayer) playerName else f.name
        drawContext.canvas.nativeCanvas.drawText(name, cx, barY - 6f * scale, paint)
    }
}
