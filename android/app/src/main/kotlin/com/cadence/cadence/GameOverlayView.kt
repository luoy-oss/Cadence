package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 游戏覆盖层 - 光点跳跃式
 *
 * 一个光点从当前琴键跳到下一个琴键。
 * 同一琴键连续按：光点不动，外围生成渐进圈。
 * 和弦（一→多）：光点一分为多。
 * 合并（多→一）：多光点合而为一。
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
    data class Dot(
        var x: Float, var y: Float,         // 当前位置
        val targetX: Float, val targetY: Float, // 目标位置
        val startTimeMs: Long,               // 开始移动的时间
        val arriveTimeMs: Long,              // 到达目标的时间
        val color: Int
    )

    private val activeDots = mutableListOf<Dot>()

    // 命中爆炸
    data class HitBurst(val x: Float, val y: Float, val startTime: Long, val color: Int)
    private val hitBursts = mutableListOf<HitBurst>()

    // 渐进圈（同一琴键连续按时）
    data class ApproachRing(
        val x: Float, val y: Float,
        val startTimeMs: Long,
        val hitTimeMs: Long,
        val color: Int
    )
    private val approachRings = mutableListOf<ApproachRing>()

    var onGameEnd: (() -> Unit)? = null
    private val screenOffset = IntArray(2)
    private fun sx(screenX: Float) = screenX - screenOffset[0]
    private fun sy(screenY: Float) = screenY - screenOffset[1]

    // 配色
    private val dotColor = Color.parseColor("#FFD700")          // 金色光点
    private val dotGlowColor = Color.parseColor("#FFA500")      // 橙色发光
    private val ringColor = Color.parseColor("#6366F1")         // 紫蓝渐进圈
    private val hitZoneColor = Color.parseColor("#303050")      // 命中区底色
    private val hitZoneBorder = Color.parseColor("#4A4A6A")     // 命中区边框

    // 画笔
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    // 预览指示器（下一个目标位置的脉冲光圈）
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val previewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // 方向箭头画笔
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE
    }
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val hitZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = hitZoneColor
    }
    private val hitZoneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = hitZoneBorder
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val burstFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

    fun stopGame() { isPlaying = false; activeDots.clear(); hitBursts.clear(); approachRings.clear(); invalidate() }

    /** 初始化：为第一个时间组创建光点 */
    private fun initDots() {
        if (timeGroups.isEmpty()) return
        val firstGroup = timeGroups[0]
        activeDots.clear()
        approachRings.clear()
        for (note in firstGroup.notes) {
            val tx = sx(baseX + note.col * colSpacing)
            val ty = sy(baseY + note.row * rowSpacing)
            activeDots.add(Dot(tx, ty, tx, ty, 0L, firstGroup.timeMs, dotColor))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying) return
        getLocationOnScreen(screenOffset)

        val now = SystemClock.elapsedRealtime()
        val realTimeMs = now - gameStartTimeMs
        val gameTimeMs = (realTimeMs * gameSpeed).toLong()

        if (totalDurationMs > 0 && gameTimeMs > totalDurationMs + 2000) {
            isPlaying = false; onGameEnd?.invoke(); return
        }

        // 更新光点状态
        updateDots(gameTimeMs)

        // 绘制
        drawHitZones(canvas)
        drawPreviews(canvas, gameTimeMs)     // 下一步目标预览
        drawApproachRings(canvas, gameTimeMs)
        drawDotTrails(canvas)
        drawDots(canvas)
        drawHitBursts(canvas, now)
        drawCombo(canvas)
        drawProgressBar(canvas, gameTimeMs)
        postInvalidateDelayed(16)
    }

    /** 下一步目标预览（脉冲光圈 + 方向虚线）*/
    private fun drawPreviews(canvas: Canvas, gameTimeMs: Long) {
        // 找到当前和下一个时间组
        var currentGroupIdx = -1
        for (i in timeGroups.indices) {
            if (timeGroups[i].timeMs <= gameTimeMs) currentGroupIdx = i
        }
        if (currentGroupIdx < 0 || currentGroupIdx + 1 >= timeGroups.size) return

        val currentGroup = timeGroups[currentGroupIdx]
        val nextGroup = timeGroups[currentGroupIdx + 1]
        val timeUntilNext = nextGroup.timeMs - gameTimeMs

        // 只在下一个目标进入提前窗口时显示预览
        val previewWindow = APPROACH_TIME_MS
        if (timeUntilNext > previewWindow || timeUntilNext < 0) return

        // 预览强度：越接近越明显
        val previewProgress = (1.0 - timeUntilNext.toDouble() / previewWindow).coerceIn(0.0, 1.0).toFloat()
        val pulse = (sin(SystemClock.elapsedRealtime() * 0.008f) * 0.3f + 0.7f)  // 脉冲效果

        for (note in nextGroup.notes) {
            val tx = sx(baseX + note.col * colSpacing)
            val ty = sy(baseY + note.row * rowSpacing)

            // 预览光圈（脉冲大小）
            val previewRadius = targetRadius * (0.8f + pulse * 0.4f) * previewProgress
            val previewAlpha = (previewProgress * pulse * 160).toInt().coerceIn(0, 255)
            previewPaint.color = Color.argb(previewAlpha, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
            previewPaint.strokeWidth = 2f + previewProgress * 2f
            canvas.drawCircle(tx, ty, previewRadius, previewPaint)

            // 预览填充（微弱发光）
            val fillAlpha = (previewProgress * pulse * 40).toInt().coerceIn(0, 255)
            previewFillPaint.color = Color.argb(fillAlpha, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
            canvas.drawCircle(tx, ty, targetRadius * 0.5f * previewProgress, previewFillPaint)

            // 方向虚线（从当前光点位置指向预览位置）
            if (activeDots.isNotEmpty()) {
                val dot = activeDots[0]
                val arrowAlpha = (previewProgress * 100).toInt().coerceIn(0, 255)
                arrowPaint.color = Color.argb(arrowAlpha, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
                // 只在不同位置时画箭头
                val dx = tx - dot.x; val dy = ty - dot.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > targetRadius * 2) {
                    // 从光点边缘到预览边缘
                    val startX = dot.x + dx / dist * targetRadius * 0.6f
                    val startY = dot.y + dy / dist * targetRadius * 0.6f
                    val endX = tx - dx / dist * targetRadius * 0.8f
                    val endY = ty - dy / dist * targetRadius * 0.8f
                    canvas.drawLine(startX, startY, endX, endY, arrowPaint)
                }
            }
        }
    }

    /** 命中区（所有琴键位置的固定圆圈）*/
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

    /** 更新光点位置和状态 */
    private fun updateDots(gameTimeMs: Long) {
        // 找到当前和下一个时间组
        var currentGroupIdx = -1
        for (i in timeGroups.indices) {
            if (timeGroups[i].timeMs <= gameTimeMs) currentGroupIdx = i
        }

        if (currentGroupIdx < 0) return

        val currentGroup = timeGroups[currentGroupIdx]
        val nextGroupIdx = currentGroupIdx + 1
        val hasNext = nextGroupIdx < timeGroups.size

        // 检查当前组是否已命中
        val hitWindow = 80L
        if (gameTimeMs >= currentGroup.timeMs + hitWindow && !currentGroup.hit) {
            currentGroup.hit = true
            // 触发命中爆炸
            for (note in currentGroup.notes) {
                val cx = sx(baseX + note.col * colSpacing)
                val cy = sy(baseY + note.row * rowSpacing)
                hitBursts.add(HitBurst(cx, cy, SystemClock.elapsedRealtime(), dotColor))
            }
        }

        if (!hasNext) return

        val nextGroup = timeGroups[nextGroupIdx]
        val moveStartTime = currentGroup.timeMs
        val moveEndTime = nextGroup.timeMs

        if (gameTimeMs < moveStartTime || moveEndTime <= moveStartTime) return

        val progress = ((gameTimeMs - moveStartTime).toFloat() / (moveEndTime - moveStartTime)).coerceIn(0f, 1f)

        // 更新每个光点
        val currentNotes = currentGroup.notes
        val nextNotes = nextGroup.notes

        // 重新构建光点列表
        activeDots.clear()

        if (nextNotes.size == 1 && currentNotes.size == 1) {
            // 一对一：一个光点移动
            val from = currentNotes[0]; val to = nextNotes[0]
            val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
            val toX = sx(baseX + to.col * colSpacing); val toY = sy(baseY + to.row * rowSpacing)

            if (from.col == to.col && from.row == to.row) {
                // 同一琴键：添加渐进圈
                if (approachRings.none { it.hitTimeMs == moveEndTime }) {
                    approachRings.add(ApproachRing(toX, toY, gameTimeMs, moveEndTime, ringColor))
                }
                activeDots.add(Dot(toX, toY, toX, toY, moveStartTime, moveEndTime, dotColor))
            } else {
                // 不同琴键：光点移动
                val cx = fromX + (toX - fromX) * easeOutCubic(progress)
                val cy = fromY + (toY - fromY) * easeOutCubic(progress)
                activeDots.add(Dot(cx, cy, toX, toY, moveStartTime, moveEndTime, dotColor))
            }
        } else if (nextNotes.size > currentNotes.size) {
            // 一分为多：原光点 + 新光点从原位置出发
            for (note in nextNotes) {
                val toX = sx(baseX + note.col * colSpacing); val toY = sy(baseY + note.row * rowSpacing)
                val fromNote = currentNotes.firstOrNull() ?: note
                val fromX = sx(baseX + fromNote.col * colSpacing); val fromY = sy(baseY + fromNote.row * rowSpacing)
                val cx = fromX + (toX - fromX) * easeOutCubic(progress)
                val cy = fromY + (toY - fromY) * easeOutCubic(progress)
                activeDots.add(Dot(cx, cy, toX, toY, moveStartTime, moveEndTime, dotColor))
            }
        } else if (nextNotes.size < currentNotes.size) {
            // 多合为一：所有光点向目标汇聚
            val to = nextNotes.first()
            val toX = sx(baseX + to.col * colSpacing); val toY = sy(baseY + to.row * rowSpacing)
            for (from in currentNotes) {
                val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
                val cx = fromX + (toX - fromX) * easeOutCubic(progress)
                val cy = fromY + (toY - fromY) * easeOutCubic(progress)
                activeDots.add(Dot(cx, cy, toX, toY, moveStartTime, moveEndTime, dotColor))
            }
        } else {
            // 等量：一对一移动
            for (i in currentNotes.indices) {
                val from = currentNotes[i]; val to = nextNotes.getOrElse(i) { from }
                val fromX = sx(baseX + from.col * colSpacing); val fromY = sy(baseY + from.row * rowSpacing)
                val toX = sx(baseX + to.col * colSpacing); val toY = sy(baseY + to.row * rowSpacing)
                val cx = fromX + (toX - fromX) * easeOutCubic(progress)
                val cy = fromY + (toY - fromY) * easeOutCubic(progress)
                activeDots.add(Dot(cx, cy, toX, toY, moveStartTime, moveEndTime, dotColor))
            }
        }
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

    /** 绘制渐进圈 */
    private fun drawApproachRings(canvas: Canvas, gameTimeMs: Long) {
        val it = approachRings.iterator()
        while (it.hasNext()) {
            val ring = it.next()
            val remaining = ring.hitTimeMs - gameTimeMs
            if (remaining < -200) { it.remove(); continue }
            if (remaining > APPROACH_TIME_MS) continue

            val progress = (1.0 - remaining.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val radius = startRadius + (targetRadius * 0.7f - startRadius) * progress
            val alpha = (progress * progress * 220).toInt().coerceIn(0, 255)

            approachPaint.color = Color.argb(alpha, Color.red(ring.color), Color.green(ring.color), Color.blue(ring.color))
            approachPaint.strokeWidth = 3f + progress * 2f
            canvas.drawCircle(ring.x, ring.y, radius, approachPaint)
        }
    }

    /** 绘制光点拖尾 */
    private fun drawDotTrails(canvas: Canvas) {
        for (dot in activeDots) {
            val trailLen = 8
            for (i in 1..trailLen) {
                val frac = i.toFloat() / trailLen
                val ta = ((1f - frac) * 40).toInt().coerceIn(0, 255)
                trailPaint.color = Color.argb(ta, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
                val tSize = targetRadius * 0.3f * (1f - frac)
                canvas.drawCircle(dot.x - (dot.x - dot.targetX) * frac * 0.1f,
                    dot.y - (dot.y - dot.targetY) * frac * 0.1f, tSize, trailPaint)
            }
        }
    }

    /** 绘制光点 */
    private fun drawDots(canvas: Canvas) {
        for (dot in activeDots) {
            val dotSize = targetRadius * 0.5f

            // 外发光
            dotGlowPaint.color = Color.argb(80, Color.red(dotGlowColor), Color.green(dotGlowColor), Color.blue(dotGlowColor))
            canvas.drawCircle(dot.x, dot.y, dotSize * 2.5f, dotGlowPaint)

            // 光点主体
            dotPaint.color = dot.color
            canvas.drawCircle(dot.x, dot.y, dotSize, dotPaint)

            // 白色高光
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = Color.argb(180, 255, 255, 255)
            }
            canvas.drawCircle(dot.x - dotSize * 0.2f, dot.y - dotSize * 0.2f, dotSize * 0.4f, highlightPaint)

            // 边框
            canvas.drawCircle(dot.x, dot.y, dotSize, dotBorderPaint)
        }
    }

    /** 命中爆炸 */
    private fun drawHitBursts(canvas: Canvas, now: Long) {
        val it = hitBursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val age = (now - b.startTime).toFloat() / 500f
            if (age >= 1f) { it.remove(); continue }

            val opacity = (1f - age)
            val ringR = targetRadius + age * 50
            val ringA = (opacity * 180).toInt().coerceIn(0, 255)
            burstPaint.color = Color.argb(ringA, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            burstPaint.strokeWidth = 3f * (1f - age * 0.5f)
            canvas.drawCircle(b.x, b.y, ringR, burstPaint)

            val flashA = (opacity * 60).toInt().coerceIn(0, 255)
            burstFillPaint.color = Color.argb(flashA, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            canvas.drawCircle(b.x, b.y, targetRadius * (1f + age * 0.2f), burstFillPaint)
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
