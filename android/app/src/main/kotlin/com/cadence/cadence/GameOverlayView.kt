package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import android.view.View

/**
 * 游戏覆盖层 - osu! 风格音符指示器 + 进度条 + Combo + 编号 + 和弦连线
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
        private const val TAG = "GameOverlay"
        private const val APPROACH_TIME_MS = 1200L  // 圆圈从出现到目标的时长
        private const val TARGET_RADIUS = 30f        // 目标圆半径
        private const val START_RADIUS = 80f         // 起始圆半径
    }

    // 游戏事件数据
    data class NoteEvent(
        val row: Int,
        val col: Int,
        val targetTimeMs: Long,
        val sequenceIndex: Int = 0  // 在时间轴中的序号（用于编号显示）
    )

    // 时间分组（用于和弦连线）
    data class TimeGroup(
        val timeMs: Long,
        val notes: List<NoteEvent>
    )

    private var noteEvents = listOf<NoteEvent>()
    private var timeGroups = listOf<TimeGroup>()
    private var gameStartTimeMs = 0L
    private var isPlaying = false
    private var totalDurationMs = 0L
    private var sequenceCounter = 0  // 全局序号计数器

    // 命中效果
    data class HitEffect(
        val x: Float, val y: Float,
        val startTime: Long,
        val color: Int,
        val label: String,
        val sequenceNum: Int
    )
    private val hitEffects = mutableListOf<HitEffect>()

    var onGameEnd: (() -> Unit)? = null

    // ===== 颜色方案（暖色→冷色渐变，更醒目）=====
    private val noteColors = intArrayOf(
        Color.parseColor("#FF6B6B"),  // 红
        Color.parseColor("#FF8E53"),  // 橙
        Color.parseColor("#FFD93D"),  // 黄
        Color.parseColor("#6BCB77"),  // 绿
        Color.parseColor("#4D96FF"),  // 蓝
        Color.parseColor("#9B59B6"),  // 紫
    )

    private fun getNoteColor(sequenceIndex: Int): Int {
        return noteColors[sequenceIndex % noteColors.size]
    }

    // ===== 画笔 =====
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 18f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val comboTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700"); textSize = 52f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(20f, 0f, 0f, Color.parseColor("#80FFD700"))
    }
    private val comboLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8"); textSize = 16f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
    }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hitEffectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val hitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val chordLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val noteNames = arrayOf(
        arrayOf("-1", "-2", "-3", "-4", "-5"),
        arrayOf("-6", "-7", "1", "2", "3"),
        arrayOf("4", "5", "6", "7", "+1")
    )

    /** 设置游戏数据，自动分配序号和时间分组 */
    fun setGameData(events: List<NoteEvent>, durationMs: Long) {
        noteEvents = events
        totalDurationMs = durationMs

        // 按时间分组（用于和弦连线）
        val grouped = events.groupBy { it.targetTimeMs }
            .toSortedMap()
            .map { (time, notes) -> TimeGroup(time, notes) }
        timeGroups = grouped

        // 分配序号：按时间顺序，同时刻的音符共享同一序号
        var seq = 0
        val mutableEvents = events.toMutableList()
        var lastTime = -1L
        for (group in grouped) {
            if (group.timeMs != lastTime) {
                seq++
                lastTime = group.timeMs
            }
            for (note in group.notes) {
                val idx = mutableEvents.indexOfFirst {
                    it.row == note.row && it.col == note.col && it.targetTimeMs == note.targetTimeMs
                }
                if (idx >= 0) {
                    mutableEvents[idx] = note.copy(sequenceIndex = seq)
                }
            }
        }
        noteEvents = mutableEvents
        sequenceCounter = seq
    }

    fun startGame() {
        gameStartTimeMs = SystemClock.elapsedRealtime()
        isPlaying = true
        invalidate()
    }

    fun stopGame() {
        isPlaying = false
        hitEffects.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying) return

        val now = SystemClock.elapsedRealtime()
        val gameTimeMs = now - gameStartTimeMs

        if (totalDurationMs > 0 && gameTimeMs > totalDurationMs + 2000) {
            isPlaying = false
            onGameEnd?.invoke()
            return
        }

        drawChordLines(canvas, gameTimeMs)
        drawNoteIndicators(canvas, gameTimeMs)
        drawHitEffects(canvas, now)
        drawCombo(canvas)
        drawProgressBar(canvas, gameTimeMs)

        postInvalidateDelayed(16)
    }

    /** 绘制和弦连线（同时刻的音符之间画虚线）*/
    private fun drawChordLines(canvas: Canvas, gameTimeMs: Long) {
        for (group in timeGroups) {
            if (group.notes.size < 2) continue
            val timeUntilHit = group.timeMs - gameTimeMs
            if (timeUntilHit > APPROACH_TIME_MS || timeUntilHit < -200) continue

            val progress = (1.0 - timeUntilHit.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val alpha = (progress * 180).toInt().coerceIn(0, 255)
            val color = getNoteColor(group.notes.first().sequenceIndex)
            chordLinePaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            chordLinePaint.strokeWidth = 2f + progress * 2f

            // 连接所有同时刻音符
            for (i in 0 until group.notes.size - 1) {
                val n1 = group.notes[i]
                val n2 = group.notes[i + 1]
                val x1 = baseX + n1.col * colSpacing
                val y1 = baseY + n1.row * rowSpacing
                val x2 = baseX + n2.col * colSpacing
                val y2 = baseY + n2.row * rowSpacing
                canvas.drawLine(x1, y1, x2, y2, chordLinePaint)
            }
        }
    }

    /** 绘制 osu! 风格音符指示器（带编号和颜色）*/
    private fun drawNoteIndicators(canvas: Canvas, gameTimeMs: Long) {
        for (event in noteEvents) {
            val x = baseX + event.col * colSpacing
            val y = baseY + event.row * rowSpacing
            val timeUntilHit = event.targetTimeMs - gameTimeMs

            if (timeUntilHit > APPROACH_TIME_MS || timeUntilHit < -200) continue

            val progress = (1.0 - timeUntilHit.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val currentRadius = START_RADIUS + (TARGET_RADIUS - START_RADIUS) * progress
            val color = getNoteColor(event.sequenceIndex)

            // 外圈（approach circle）- 彩色
            val alpha = (progress * 220).toInt().coerceIn(0, 255)
            approachPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            approachPaint.strokeWidth = 3f * (1f - progress * 0.3f)
            canvas.drawCircle(x, y, currentRadius, approachPaint)

            // 目标圆填充（半透明彩色）
            val fillAlpha = (50 + progress * 100).toInt().coerceIn(0, 255)
            targetFillPaint.color = Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(x, y, TARGET_RADIUS, targetFillPaint)

            // 目标圆边框（实色）
            val borderAlpha = (120 + progress * 135).toInt().coerceIn(0, 255)
            targetBorderPaint.color = Color.argb(borderAlpha, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(x, y, TARGET_RADIUS, targetBorderPaint)

            // 中心小点
            dotPaint.color = Color.WHITE
            canvas.drawCircle(x, y, 4f, dotPaint)

            // 编号显示（圆圈上方）
            numberPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            numberPaint.textSize = 16f + progress * 4f
            canvas.drawText("${event.sequenceIndex}", x, y - TARGET_RADIUS - 12f, numberPaint)

            // 音符名称（圆圈内）
            val nameAlpha = (progress * 200).toInt().coerceIn(0, 255)
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(nameAlpha, 255, 255, 255)
                textSize = 11f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(noteNames[event.row][event.col], x, y + 4f, namePaint)

            // 接近完美时的发光效果
            if (progress > 0.85f) {
                val glowAlpha = ((progress - 0.85f) / 0.15f * 120).toInt().coerceIn(0, 255)
                glowPaint.color = Color.argb(glowAlpha, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawCircle(x, y, TARGET_RADIUS + 3, glowPaint)
            }
        }
    }

    private var combo = 0
    private var score = 0

    fun updateScore(newScore: Int, newCombo: Int) {
        score = newScore; combo = newCombo
    }

    fun addHitEffect(row: Int, col: Int, grade: String) {
        val x = baseX + col * colSpacing
        val y = baseY + row * rowSpacing
        val color = when (grade) {
            "perfect" -> Color.parseColor("#FFD700")
            "great" -> Color.parseColor("#22C55E")
            "good" -> Color.parseColor("#3B82F6")
            else -> Color.parseColor("#EF4444")
        }
        hitEffects.add(HitEffect(x, y, SystemClock.elapsedRealtime(), color, grade.uppercase(), 0))
    }

    private fun drawHitEffects(canvas: Canvas, now: Long) {
        val iterator = hitEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val age = (now - effect.startTime).toFloat() / 600f
            if (age >= 1f) { iterator.remove(); continue }

            val opacity = ((1f - age) * 255).toInt().coerceIn(0, 255)
            val expandRadius = TARGET_RADIUS + age * 50

            hitEffectPaint.color = Color.argb(opacity, Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
            hitEffectPaint.strokeWidth = 4f * (1f - age * 0.5f)
            canvas.drawCircle(effect.x, effect.y, expandRadius, hitEffectPaint)

            // 内圈
            val innerAlpha = ((1f - age) * 100).toInt().coerceIn(0, 255)
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(innerAlpha, Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
            }
            canvas.drawCircle(effect.x, effect.y, TARGET_RADIUS * (1f - age * 0.5f), innerPaint)

            if (age < 0.5f) {
                val textAlpha = if (age < 0.2f) 255 else ((0.5f - age) / 0.3f * 255).toInt().coerceIn(0, 255)
                hitTextPaint.color = Color.argb(textAlpha, Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
                hitTextPaint.textSize = 18f + age * 6f
                canvas.drawText(effect.label, effect.x, effect.y - TARGET_RADIUS - 30f - age * 20f, hitTextPaint)
            }
        }
    }

    private fun drawCombo(canvas: Canvas) {
        if (combo <= 1) return
        val cx = width / 2f
        val cy = height * 0.25f

        comboTextPaint.textSize = 52f + if (combo > 50) 10f else 0f
        canvas.drawText("$combo", cx, cy, comboTextPaint)
        canvas.drawText("COMBO", cx, cy + 28f, comboLabelPaint)
    }

    private fun drawProgressBar(canvas: Canvas, gameTimeMs: Long) {
        if (totalDurationMs <= 0) return
        val barHeight = 5f
        val y = height - barHeight
        val progress = (gameTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)

        canvas.drawRect(0f, y, width.toFloat(), height.toFloat(), progressBgPaint)

        progressFillPaint.shader = LinearGradient(
            0f, y, width * progress, y,
            Color.parseColor("#6366F1"), Color.parseColor("#A855F7"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, y, width * progress, height.toFloat(), progressFillPaint)
    }

    fun updateKeyConfig(bx: Float, by: Float, cs: Float, rs: Float) {
        baseX = bx; baseY = by; colSpacing = cs; rowSpacing = rs
    }
}
