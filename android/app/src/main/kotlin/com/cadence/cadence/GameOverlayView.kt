package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import android.view.View

/**
 * 游戏覆盖层 - osu! 风格音符指示器 + 进度条 + Combo
 * 全屏透明覆盖层，在游戏上层绘制
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
        private const val APPROACH_TIME_MS = 1000L  // 圆圈从出现到目标的时长
        private const val TARGET_RADIUS = 28f        // 目标圆半径
        private const val START_RADIUS = 72f         // 起始圆半径
    }

    // 游戏事件数据（从 Flutter 接收）
    data class NoteEvent(
        val row: Int,
        val col: Int,
        val targetTimeMs: Long  // 目标时间（相对于游戏开始的毫秒数）
    )

    private var noteEvents = listOf<NoteEvent>()
    private var gameStartTimeMs = 0L  // 游戏开始的 SystemClock 时间
    private var isPlaying = false
    private var totalDurationMs = 0L

    // 命中效果
    data class HitEffect(
        val x: Float, val y: Float,
        val startTime: Long,
        val color: Int,
        val label: String
    )
    private val hitEffects = mutableListOf<HitEffect>()

    // 游戏结束回调
    var onGameEnd: (() -> Unit)? = null

    // ===== 画笔 =====
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 255, 255)
    }
    private val targetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(150, 255, 255, 255)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 255, 255, 255)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }
    private val comboTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(20f, 0f, 0f, Color.parseColor("#80FFD700"))
    }
    private val comboLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 16f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
    }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1")
    }
    private val hitEffectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val noteNames = arrayOf(
        arrayOf("-1", "-2", "-3", "-4", "-5"),
        arrayOf("-6", "-7", "1", "2", "3"),
        arrayOf("4", "5", "6", "7", "+1")
    )

    /** 设置游戏数据 */
    fun setGameData(events: List<NoteEvent>, durationMs: Long) {
        noteEvents = events
        totalDurationMs = durationMs
    }

    /** 开始游戏 */
    fun startGame() {
        gameStartTimeMs = SystemClock.elapsedRealtime()
        isPlaying = true
        invalidate()
    }

    /** 停止游戏 */
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

        // 检查游戏是否结束
        if (totalDurationMs > 0 && gameTimeMs > totalDurationMs + 2000) {
            isPlaying = false
            onGameEnd?.invoke()
            return
        }

        // 绘制音符指示器
        drawNoteIndicators(canvas, gameTimeMs)

        // 绘制命中效果
        drawHitEffects(canvas, now)

        // 绘制 Combo
        drawCombo(canvas)

        // 绘制进度条
        drawProgressBar(canvas, gameTimeMs)

        // 60fps 刷新
        postInvalidateDelayed(16)
    }

    private fun drawNoteIndicators(canvas: Canvas, gameTimeMs: Long) {
        for (event in noteEvents) {
            val x = baseX + event.col * colSpacing
            val y = baseY + event.row * rowSpacing
            val timeUntilHit = event.targetTimeMs - gameTimeMs

            // 只显示在 approach 窗口内的音符
            if (timeUntilHit > APPROACH_TIME_MS || timeUntilHit < -200) continue

            val progress = (1.0 - timeUntilHit.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val currentRadius = START_RADIUS + (TARGET_RADIUS - START_RADIUS) * progress

            // 外圈（approach circle）
            val alpha = (progress * 200).toInt().coerceIn(0, 255)
            approachPaint.color = Color.argb(alpha, 255, 255, 255)
            approachPaint.strokeWidth = 2.5f * (1f - progress * 0.3f)
            canvas.drawCircle(x, y, currentRadius, approachPaint)

            // 目标圆填充
            val fillAlpha = (40 + progress * 80).toInt().coerceIn(0, 255)
            targetFillPaint.color = Color.argb(fillAlpha, 255, 255, 255)
            canvas.drawCircle(x, y, TARGET_RADIUS, targetFillPaint)

            // 目标圆边框
            val borderAlpha = (100 + progress * 155).toInt().coerceIn(0, 255)
            targetBorderPaint.color = Color.argb(borderAlpha, 255, 255, 255)
            canvas.drawCircle(x, y, TARGET_RADIUS, targetBorderPaint)

            // 中心小点
            canvas.drawCircle(x, y, 4f, dotPaint)

            // 接近完美时的发光效果
            if (progress > 0.85f) {
                val glowAlpha = ((progress - 0.85f) / 0.15f * 100).toInt().coerceIn(0, 255)
                glowPaint.color = Color.argb(glowAlpha, 255, 215, 0)
                canvas.drawCircle(x, y, TARGET_RADIUS + 2, glowPaint)
            }
        }
    }

    private fun drawHitEffects(canvas: Canvas, now: Long) {
        val iterator = hitEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val age = (now - effect.startTime).toFloat() / 500f
            if (age >= 1f) { iterator.remove(); continue }

            val opacity = ((1f - age) * 255).toInt().coerceIn(0, 255)
            val expandRadius = TARGET_RADIUS + age * 40

            // 扩散环
            hitEffectPaint.color = Color.argb(opacity, Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
            hitEffectPaint.strokeWidth = 3f * (1f - age * 0.5f)
            canvas.drawCircle(effect.x, effect.y, expandRadius, hitEffectPaint)

            // 等级文字
            if (age < 0.6f) {
                val textAlpha = if (age < 0.3f) 255 else ((0.6f - age) / 0.3f * 255).toInt().coerceIn(0, 255)
                hitTextPaint.color = Color.argb(textAlpha, Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
                hitTextPaint.textSize = 16f - age * 8f
                canvas.drawText(effect.label, effect.x, effect.y - TARGET_RADIUS - 24 - age * 15, hitTextPaint)
            }
        }
    }

    private var combo = 0
    private var score = 0

    fun updateScore(newScore: Int, newCombo: Int) {
        score = newScore
        combo = newCombo
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
        hitEffects.add(HitEffect(x, y, SystemClock.elapsedRealtime(), color, grade.uppercase()))
    }

    private fun drawCombo(canvas: Canvas) {
        if (combo <= 1) return
        val cx = width / 2f
        val cy = height * 0.3f

        comboTextPaint.textSize = 48f + if (combo > 50) 8f else 0f
        canvas.drawText("$combo", cx, cy, comboTextPaint)
        canvas.drawText("COMBO", cx, cy + 24f, comboLabelPaint)
    }

    private fun drawProgressBar(canvas: Canvas, gameTimeMs: Long) {
        if (totalDurationMs <= 0) return
        val barHeight = 4f
        val y = height - barHeight
        val progress = (gameTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)

        canvas.drawRect(0f, y, width.toFloat(), height.toFloat(), progressBgPaint)

        // 渐变填充
        progressFillPaint.shader = LinearGradient(
            0f, y, width * progress, y,
            Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, y, width * progress, height.toFloat(), progressFillPaint)
    }

    fun updateKeyConfig(bx: Float, by: Float, cs: Float, rs: Float) {
        baseX = bx; baseY = by; colSpacing = cs; rowSpacing = rs
    }
}
