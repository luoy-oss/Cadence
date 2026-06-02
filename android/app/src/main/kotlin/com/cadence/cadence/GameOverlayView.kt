package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.sqrt

/**
 * 游戏覆盖层 - 光点 + osu! 缩圈混合式
 *
 * - 当前目标：金色光点在命中区
 * - 下一步目标：O→o 缩圈 + 编号（1在2上面）
 * - 同键连续：光点不动，外围缩圈
 * - 和弦：光点分裂/合并
 * - 立即显示前几个待按琴键
 * - 倒计时在角落，不遮挡
 */
@SuppressLint("ViewConstructor")
class GameOverlayView(
    context: Context,
    private var baseX: Float,
    private var baseY: Float,
    private var colSpacing: Float,
    private var rowSpacing: Float
) : View(context) {

    companion object {
        private const val APPROACH_TIME_MS = 1200L
    }

    private var targetRadius = 36f
    private var startRadius = 90f
    private var numberTextSize = 22f
    private var gameSpeed = 1.0f

    data class NoteEvent(val row: Int, val col: Int, val targetTimeMs: Long, val sequenceIndex: Int = 0)
    class TimeGroup(val timeMs: Long, val notes: List<NoteEvent>, var hit: Boolean = false)

    private var noteEvents = listOf<NoteEvent>()
    private var timeGroups = listOf<TimeGroup>()
    private var gameStartTimeMs = 0L
    private var isPlaying = false
    private var totalDurationMs = 0L

    // 光点
    data class Dot(var x: Float, var y: Float, val targetX: Float, val targetY: Float, val color: Int)
    private val activeDots = mutableListOf<Dot>()

    // 命中爆炸
    data class HitBurst(val x: Float, val y: Float, val startTime: Long, val color: Int)
    private val hitBursts = mutableListOf<HitBurst>()

    var onGameEnd: (() -> Unit)? = null
    var onProgressUpdate: ((elapsedMs: Long, totalMs: Long) -> Unit)? = null
    private var lastProgressCallbackMs = 0L
    private val screenOffset = IntArray(2)
    private fun sx(screenX: Float) = screenX - screenOffset[0]
    private fun sy(screenY: Float) = screenY - screenOffset[1]

    // 配色
    private val dotColor = Color.parseColor("#FFD700")
    private val dotGlowColor = Color.parseColor("#FFA500")
    private val ringColor1 = Color.parseColor("#FF6B6B")   // 1号：红
    private val ringColor2 = Color.parseColor("#FFD93D")   // 2号：黄
    private val ringColor3 = Color.parseColor("#6BCB77")   // 3号：绿
    private val hitZoneFill = Color.parseColor("#202040")
    private val hitZoneBorder = Color.parseColor("#404060")

    // 画笔
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE
    }
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val hitZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = hitZoneFill
    }
    private val hitZoneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = hitZoneBorder
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 0f, Color.argb(200, 0, 0, 0))
    }
    private val burstFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val comboTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700"); textSize = 56f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(20f, 0f, 0f, Color.parseColor("#80FFD700"))
    }
    private val comboLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8"); textSize = 16f; textAlign = Paint.Align.CENTER
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
    }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    // 方向虚线
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    fun setSpeed(speed: Float) { gameSpeed = speed.coerceIn(0.1f, 10.0f) }
    fun setDisplayParams(targetR: Float, startR: Float, numSize: Float) {
        targetRadius = targetR; startRadius = startR; numberTextSize = numSize
    }

    fun setGameData(events: List<NoteEvent>, durationMs: Long) {
        noteEvents = events; totalDurationMs = durationMs
        val grouped = events.groupBy { it.targetTimeMs }.toSortedMap()
            .map { (t, n) -> TimeGroup(t, n) }
        timeGroups = grouped
        var seq = 0; val mutable = events.toMutableList(); var lastT = -1L
        for (g in grouped) {
            if (g.timeMs != lastT) { seq = (seq + 1) % 10; lastT = g.timeMs }
            for (n in g.notes) {
                val idx = mutable.indexOfFirst { it.row == n.row && it.col == n.col && it.targetTimeMs == n.targetTimeMs }
                if (idx >= 0) mutable[idx] = n.copy(sequenceIndex = seq)
            }
        }
        noteEvents = mutable
    }

    fun startGame() {
        gameStartTimeMs = SystemClock.elapsedRealtime()
        isPlaying = true
        initDots()
        invalidate()
    }

    fun stopGame() {
        isPlaying = false
        activeDots.clear(); hitBursts.clear()
        invalidate()
    }

    private fun initDots() {
        if (timeGroups.isEmpty()) return
        val first = timeGroups[0]
        activeDots.clear()
        for (note in first.notes) {
            val tx = sx(baseX + note.col * colSpacing)
            val ty = sy(baseY + note.row * rowSpacing)
            activeDots.add(Dot(tx, ty, tx, ty, dotColor))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying) return
        getLocationOnScreen(screenOffset)

        val now = SystemClock.elapsedRealtime()
        val realTimeMs = now - gameStartTimeMs
        val gameTimeMs = (realTimeMs * gameSpeed).toLong()

        if (isPlaying && totalDurationMs > 0 && gameTimeMs > totalDurationMs + 2000) {
            isPlaying = false; onGameEnd?.invoke(); return
        }

        // 进度回调（节流 1次/秒）
        if (totalDurationMs > 0 && now - lastProgressCallbackMs > 1000) {
            lastProgressCallbackMs = now
            onProgressUpdate?.invoke(gameTimeMs, totalDurationMs)
        }

        // 命中区（始终显示）
        drawHitZones(canvas)

        // O→o 缩圈：显示接下来 3 个待按琴键
        drawUpcomingRings(canvas, gameTimeMs)

        // 方向虚线
        drawArrows(canvas, gameTimeMs)

        // 更新光点
        if (isPlaying) updateDots(gameTimeMs)

        // 光点
        drawDots(canvas)

        // 命中爆炸
        drawHitBursts(canvas, now)

        // Combo
        drawCombo(canvas)

        // 进度条
        drawProgressBar(canvas, gameTimeMs)

        postInvalidateDelayed(16)
    }

    /** 命中区 */
    private fun drawHitZones(canvas: Canvas) {
        for (row in 0..2) {
            for (col in 0..4) {
                val cx = sx(baseX + col * colSpacing)
                val cy = sy(baseY + row * rowSpacing)
                canvas.drawCircle(cx, cy, targetRadius * 0.7f, hitZonePaint)
                canvas.drawCircle(cx, cy, targetRadius * 0.7f, hitZoneBorderPaint)
            }
        }
    }

    /** O→o 缩圈：接下来 3 个待按琴键（1号在最上层）*/
    private fun drawUpcomingRings(canvas: Canvas, gameTimeMs: Long) {
        val upcoming = mutableListOf<TimeGroup>()
        for (g in timeGroups) {
            if (!g.hit && g.timeMs > gameTimeMs - 100) {
                upcoming.add(g)
                if (upcoming.size >= 3) break
            }
        }

        for (i in upcoming.indices.reversed()) {
            val group = upcoming[i]
            val rank = i + 1
            val t = group.timeMs - gameTimeMs
            if (t > APPROACH_TIME_MS) continue

            val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val color = when (rank) { 1 -> ringColor1; 2 -> ringColor2; else -> ringColor3 }
            val curRadius = startRadius + (targetRadius * 0.7f - startRadius) * progress

            for (note in group.notes) {
                val cx = sx(baseX + note.col * colSpacing)
                val cy = sy(baseY + note.row * rowSpacing)
                val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)

                // --- 1号目标：命中区脉冲发光 ---
                if (rank == 1 && progress > 0.2f) {
                    val pulse = (Math.sin(gameTimeMs * 0.01) * 0.3 + 0.7).toFloat()
                    val glowAlpha = (pulse * 160 * progress).toInt().coerceIn(0, 255)
                    val fillGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                        this.color = Color.argb(glowAlpha, r, g, b)
                    }
                    canvas.drawCircle(cx, cy, targetRadius * 1.1f, fillGlow)
                }

                // O→o 缩圈
                val alpha = (progress * progress * 230).toInt().coerceIn(0, 255)
                approachPaint.color = Color.argb(alpha, r, g, b)
                approachPaint.strokeWidth = when (rank) {
                    1 -> 5f + progress * 4f
                    2 -> 3.5f + progress * 2.5f
                    else -> 2.5f + progress * 1.5f
                }
                canvas.drawCircle(cx, cy, curRadius, approachPaint)

                // 命中区发光（接近时）
                if (progress > 0.7f) {
                    val ga = ((progress - 0.7f) / 0.3f * 120).toInt().coerceIn(0, 255)
                    val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 5f
                        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                        this.color = Color.argb(ga, r, g, b)
                    }
                    canvas.drawCircle(cx, cy, targetRadius * 0.7f + 4, glowP)
                }

                // 编号（rank 越大字号越小）
                val numAlpha = (progress * 220).toInt().coerceIn(0, 255)
                numberPaint.color = Color.argb(numAlpha, r, g, b)
                numberPaint.textSize = when (rank) {
                    1 -> numberTextSize * 1.3f + progress * 6f
                    2 -> numberTextSize * 1.1f + progress * 4f
                    else -> numberTextSize + progress * 3f
                }
                numberPaint.setShadowLayer(8f, 0f, 0f, Color.argb(200, 0, 0, 0))
                canvas.drawText("$rank", cx, cy + numberTextSize * 0.35f, numberPaint)

                // 1号目标中心脉冲指示点
                if (rank == 1 && progress > 0.3f) {
                    val dotPulse = (Math.sin(gameTimeMs * 0.012) * 0.3 + 0.7).toFloat()
                    val dotAlpha = (progress * 200 * dotPulse).toInt().coerceIn(0, 255)
                    val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        this.color = Color.argb(dotAlpha, r, g, b)
                    }
                    canvas.drawCircle(cx, cy, 5f + progress * 3f, centerDot)
                }
            }
        }
    }

    /** 方向指示（从当前光点到 1 号目标）*/
    private fun drawArrows(canvas: Canvas, gameTimeMs: Long) {
        if (activeDots.isEmpty()) return
        val dot = activeDots[0]

        val nextGroup = timeGroups.firstOrNull { !it.hit && it.timeMs > gameTimeMs - 100 } ?: return
        val t = nextGroup.timeMs - gameTimeMs
        if (t > APPROACH_TIME_MS || t < 0) return

        val note = nextGroup.notes.firstOrNull() ?: return
        val tx = sx(baseX + note.col * colSpacing)
        val ty = sy(baseY + note.row * rowSpacing)

        val dx = tx - dot.x; val dy = ty - dot.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < targetRadius * 2) return

        val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
        val pulse = (Math.sin(gameTimeMs * 0.008) * 0.2 + 0.8).toFloat()
        val arrowAlpha = (progress * 160 * pulse).toInt().coerceIn(0, 255)

        // 更粗更亮的方向线
        arrowPaint.color = Color.argb(arrowAlpha, 255, 215, 0)
        arrowPaint.strokeWidth = 2.5f + progress * 2f

        val startX = dot.x + dx / dist * targetRadius * 0.6f
        val startY = dot.y + dy / dist * targetRadius * 0.6f
        val endX = tx - dx / dist * targetRadius * 0.8f
        val endY = ty - dy / dist * targetRadius * 0.8f
        canvas.drawLine(startX, startY, endX, endY, arrowPaint)

        // 箭头端点发光圆
        val headAlpha = (progress * 200 * pulse).toInt().coerceIn(0, 255)
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.argb(headAlpha, 255, 215, 0)
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(endX, endY, 5f + progress * 3f, headPaint)
    }

    /** 更新光点位置 */
    private fun updateDots(gameTimeMs: Long) {
        var currentIdx = -1
        for (i in timeGroups.indices) {
            if (timeGroups[i].timeMs <= gameTimeMs) currentIdx = i
        }
        if (currentIdx < 0) return

        val current = timeGroups[currentIdx]

        // 标记已命中
        val hitWindow = 80L
        if (!current.hit && gameTimeMs >= current.timeMs + hitWindow) {
            current.hit = true
            for (note in current.notes) {
                val cx = sx(baseX + note.col * colSpacing)
                val cy = sy(baseY + note.row * rowSpacing)
                hitBursts.add(HitBurst(cx, cy, SystemClock.elapsedRealtime(), dotColor))
            }
        }

        if (currentIdx + 1 >= timeGroups.size) return
        val next = timeGroups[currentIdx + 1]

        val moveStart = current.timeMs
        val moveEnd = next.timeMs
        if (gameTimeMs < moveStart || moveEnd <= moveStart) return

        val progress = ((gameTimeMs - moveStart).toFloat() / (moveEnd - moveStart)).coerceIn(0f, 1f)
        val eased = easeOutCubic(progress)

        activeDots.clear()
        val currentNotes = current.notes
        val nextNotes = next.notes

        when {
            nextNotes.size > currentNotes.size -> {
                // 一分为多
                for (note in nextNotes) {
                    val toX = sx(baseX + note.col * colSpacing); val toY = sy(baseY + note.row * rowSpacing)
                    val from = currentNotes.firstOrNull() ?: note
                    val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
                    activeDots.add(Dot(fromX + (toX - fromX) * eased, fromY + (toY - fromY) * eased, toX, toY, dotColor))
                }
            }
            nextNotes.size < currentNotes.size -> {
                // 多合为一
                val to = nextNotes.first()
                val toX = sx(baseX + to.col * colSpacing); val toY = sy(baseY + to.row * rowSpacing)
                for (from in currentNotes) {
                    val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
                    activeDots.add(Dot(fromX + (toX - fromX) * eased, fromY + (toY - fromY) * eased, toX, toY, dotColor))
                }
            }
            else -> {
                // 等量
                for (i in currentNotes.indices) {
                    val from = currentNotes[i]; val to = nextNotes.getOrElse(i) { from }
                    val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
                    val toX = sx(baseX + to.col * colSpacing); val toY = sy(baseY + to.row * rowSpacing)
                    activeDots.add(Dot(fromX + (toX - fromX) * eased, fromY + (toY - fromY) * eased, toX, toY, dotColor))
                }
            }
        }
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

    /** 绘制光点 */
    private fun drawDots(canvas: Canvas) {
        for (dot in activeDots) {
            val dotSize = targetRadius * 0.5f
            // 外发光
            dotGlowPaint.color = Color.argb(80, Color.red(dotGlowColor), Color.green(dotGlowColor), Color.blue(dotGlowColor))
            canvas.drawCircle(dot.x, dot.y, dotSize * 2.5f, dotGlowPaint)
            // 主体
            dotPaint.color = dot.color
            canvas.drawCircle(dot.x, dot.y, dotSize, dotPaint)
            // 高光
            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = Color.argb(180, 255, 255, 255)
            }
            canvas.drawCircle(dot.x - dotSize * 0.2f, dot.y - dotSize * 0.2f, dotSize * 0.35f, highlight)
            // 边框
            canvas.drawCircle(dot.x, dot.y, dotSize, dotBorderPaint)
        }
    }

    /** 命中闪光（无扩圈） */
    private fun drawHitBursts(canvas: Canvas, now: Long) {
        val it = hitBursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val age = (now - b.startTime).toFloat() / 300f
            if (age >= 1f) { it.remove(); continue }
            val opacity = (1f - age)
            burstFillPaint.color = Color.argb((opacity * 80).toInt().coerceIn(0, 255), Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            canvas.drawCircle(b.x, b.y, targetRadius * 0.7f * (1f + age * 0.15f), burstFillPaint)
        }
    }

    private var combo = 0; private var score = 0
    fun updateScore(s: Int, c: Int) { score = s; combo = c }

    fun addHitEffect(row: Int, col: Int, grade: String) {
        val x = sx(baseX + col * colSpacing); val y = sy(baseY + row * rowSpacing)
        hitBursts.add(HitBurst(x, y, SystemClock.elapsedRealtime(), dotColor))
    }

    private fun drawCombo(canvas: Canvas) {
        if (combo <= 1) return
        comboTextPaint.textSize = 56f + if (combo > 50) 10f else 0f
        canvas.drawText("$combo", width / 2f, height * 0.25f, comboTextPaint)
        canvas.drawText("COMBO", width / 2f, height * 0.25f + 30f, comboLabelPaint)
    }

    private fun drawProgressBar(canvas: Canvas, gameTimeMs: Long) {
        if (totalDurationMs <= 0) return
        val h = 5f; val y = height - h; val p = (gameTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        canvas.drawRect(0f, y, width.toFloat(), height.toFloat(), progressBgPaint)
        progressFillPaint.shader = LinearGradient(0f, y, width * p, y, Color.parseColor("#6366F1"), Color.parseColor("#A855F7"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, y, width * p, height.toFloat(), progressFillPaint)
    }

    fun updateKeyConfig(bx: Float, by: Float, cs: Float, rs: Float) {
        baseX = bx; baseY = by; colSpacing = cs; rowSpacing = rs
    }
}
