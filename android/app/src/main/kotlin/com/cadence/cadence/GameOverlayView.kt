package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View

/**
 * 游戏覆盖层 - osu! 风格音符指示器
 * 坐标系：接收屏幕坐标，转换为视图坐标绘制
 */
@SuppressLint("ViewConstructor")
class GameOverlayView(
    context: Context,
    private var baseX: Float,   // 屏幕坐标
    private var baseY: Float,   // 屏幕坐标
    private var colSpacing: Float,
    private var rowSpacing: Float
) : View(context) {

    companion object {
        private const val APPROACH_TIME_MS = 1200L
    }

    // 可调参数（有默认值）
    private var targetRadius = 36f
    private var startRadius = 90f
    private var numberTextSize = 22f

    fun setDisplayParams(targetR: Float, startR: Float, numSize: Float) {
        targetRadius = targetR; startRadius = startR; numberTextSize = numSize
    }

    data class NoteEvent(
        val row: Int, val col: Int,
        val targetTimeMs: Long,
        val sequenceIndex: Int = 0
    )

    data class TimeGroup(val timeMs: Long, val notes: List<NoteEvent>)

    private var noteEvents = listOf<NoteEvent>()
    private var timeGroups = listOf<TimeGroup>()
    private var gameStartTimeMs = 0L
    private var isPlaying = false
    private var totalDurationMs = 0L
    private var gameSpeed = 1.0f

    fun setSpeed(speed: Float) { gameSpeed = speed.coerceIn(0.1f, 10.0f) }

    data class HitEffect(val x: Float, val y: Float, val startTime: Long, val color: Int, val label: String)
    private val hitEffects = mutableListOf<HitEffect>()

    var onGameEnd: (() -> Unit)? = null

    // 视图屏幕偏移
    private val screenOffset = IntArray(2)

    /** 屏幕坐标 → 视图坐标 */
    private fun sx(screenX: Float) = screenX - screenOffset[0]
    private fun sy(screenY: Float) = screenY - screenOffset[1]

    // ===== 颜色方案 =====
    private val noteColors = intArrayOf(
        Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E53"),
        Color.parseColor("#FFD93D"), Color.parseColor("#6BCB77"),
        Color.parseColor("#4D96FF"), Color.parseColor("#9B59B6"),
    )
    private fun getNoteColor(seq: Int) = noteColors[seq % noteColors.size]

    // ===== 画笔 =====
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3.5f
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
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    // 编号画笔（圆圈内，大号加粗）
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 0f, Color.argb(180, 0, 0, 0))
    }
    // 音符名称画笔（编号下方小字）
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255); textSize = 10f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
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
    private val hitEffectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val hitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val chordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }

    private val noteNames = arrayOf(
        arrayOf("-1", "-2", "-3", "-4", "-5"),
        arrayOf("-6", "-7", "1", "2", "3"),
        arrayOf("4", "5", "6", "7", "+1")
    )

    fun setGameData(events: List<NoteEvent>, durationMs: Long) {
        noteEvents = events; totalDurationMs = durationMs
        val grouped = events.groupBy { it.targetTimeMs }.toSortedMap()
            .map { (t, n) -> TimeGroup(t, n) }
        timeGroups = grouped
        var seq = 0; val mutable = events.toMutableList(); var lastT = -1L
        for (g in grouped) {
            if (g.timeMs != lastT) { seq++; lastT = g.timeMs }
            for (n in g.notes) {
                val idx = mutable.indexOfFirst { it.row == n.row && it.col == n.col && it.targetTimeMs == n.targetTimeMs }
                if (idx >= 0) mutable[idx] = n.copy(sequenceIndex = seq)
            }
        }
        noteEvents = mutable
    }

    fun startGame() { gameStartTimeMs = SystemClock.elapsedRealtime(); isPlaying = true; invalidate() }
    fun stopGame() { isPlaying = false; hitEffects.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying) return
        getLocationOnScreen(screenOffset)  // 关键：获取视图屏幕偏移

        val now = SystemClock.elapsedRealtime()
        val realTimeMs = now - gameStartTimeMs
        val gameTimeMs = (realTimeMs * gameSpeed).toLong()  // 速度缩放

        if (totalDurationMs > 0 && gameTimeMs > totalDurationMs + 2000) {
            isPlaying = false; onGameEnd?.invoke(); return
        }

        drawChordCurves(canvas, gameTimeMs)
        drawNoteIndicators(canvas, gameTimeMs)
        drawHitEffects(canvas, now)
        drawCombo(canvas)
        drawProgressBar(canvas, gameTimeMs)
        postInvalidateDelayed(16)
    }

    /** 和弦连线：贝塞尔曲线 */
    private fun drawChordCurves(canvas: Canvas, gameTimeMs: Long) {
        for (group in timeGroups) {
            if (group.notes.size < 2) continue
            val t = group.timeMs - gameTimeMs
            if (t > APPROACH_TIME_MS || t < -200) continue
            val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val alpha = (progress * 200).toInt().coerceIn(0, 255)
            val color = getNoteColor(group.notes.first().sequenceIndex)
            chordPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            chordPaint.strokeWidth = 2f + progress * 3f

            // 用贝塞尔曲线连接
            val path = Path()
            for (i in 0 until group.notes.size) {
                val x = sx(baseX + group.notes[i].col * colSpacing)
                val y = sy(baseY + group.notes[i].row * rowSpacing)
                if (i == 0) { path.moveTo(x, y) }
                else {
                    val px = sx(baseX + group.notes[i - 1].col * colSpacing)
                    val py = sy(baseY + group.notes[i - 1].row * rowSpacing)
                    val midX = (px + x) / 2; val midY = (py + y) / 2
                    // 控制点偏移，产生弧度
                    val cpX = midX + (y - py) * 0.2f
                    val cpY = midY - (x - px) * 0.2f
                    path.quadTo(cpX, cpY, x, y)
                }
            }
            canvas.drawPath(path, chordPaint)
        }
    }

    /** 音符指示器（大圆 + 圈内编号 + 圈下音名）*/
    private fun drawNoteIndicators(canvas: Canvas, gameTimeMs: Long) {
        for (event in noteEvents) {
            val cx = sx(baseX + event.col * colSpacing)
            val cy = sy(baseY + event.row * rowSpacing)
            val t = event.targetTimeMs - gameTimeMs
            if (t > APPROACH_TIME_MS || t < -200) continue

            val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val curRadius = startRadius + (targetRadius - startRadius) * progress
            val color = getNoteColor(event.sequenceIndex)

            // 外圈 approach circle
            val a1 = (progress * 230).toInt().coerceIn(0, 255)
            approachPaint.color = Color.argb(a1, Color.red(color), Color.green(color), Color.blue(color))
            approachPaint.strokeWidth = 3.5f * (1f - progress * 0.3f)
            canvas.drawCircle(cx, cy, curRadius, approachPaint)

            // 目标圆填充
            val a2 = (60 + progress * 120).toInt().coerceIn(0, 255)
            targetFillPaint.color = Color.argb(a2, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(cx, cy, targetRadius, targetFillPaint)

            // 目标圆边框
            val a3 = (130 + progress * 125).toInt().coerceIn(0, 255)
            targetBorderPaint.color = Color.argb(a3, Color.red(color), Color.green(color), Color.blue(color))
            targetBorderPaint.strokeWidth = 2.5f
            canvas.drawCircle(cx, cy, targetRadius, targetBorderPaint)

            // 中心白点
            dotPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, 4f, dotPaint)

            // 发光
            if (progress > 0.85f) {
                val ga = ((progress - 0.85f) / 0.15f * 140).toInt().coerceIn(0, 255)
                glowPaint.color = Color.argb(ga, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawCircle(cx, cy, targetRadius + 3, glowPaint)
            }

            // 圆圈内编号（大号加粗白色）
            numberPaint.color = Color.WHITE
            numberPaint.textSize = numberTextSize + progress * 4f
            numberPaint.setShadowLayer(6f, 0f, 0f, Color.argb(180, 0, 0, 0))
            canvas.drawText("${event.sequenceIndex}", cx, cy + 8f, numberPaint)
        }
    }

    private var combo = 0; private var score = 0
    fun updateScore(s: Int, c: Int) { score = s; combo = c }

    fun addHitEffect(row: Int, col: Int, grade: String) {
        val x = sx(baseX + col * colSpacing); val y = sy(baseY + row * rowSpacing)
        val c = when (grade) { "perfect" -> Color.parseColor("#FFD700"); "great" -> Color.parseColor("#22C55E"); "good" -> Color.parseColor("#3B82F6"); else -> Color.parseColor("#EF4444") }
        hitEffects.add(HitEffect(x, y, SystemClock.elapsedRealtime(), c, grade.uppercase()))
    }

    private fun drawHitEffects(canvas: Canvas, now: Long) {
        val it = hitEffects.iterator()
        while (it.hasNext()) {
            val e = it.next(); val age = (now - e.startTime).toFloat() / 600f
            if (age >= 1f) { it.remove(); continue }
            val op = ((1f - age) * 255).toInt().coerceIn(0, 255)
            val r = targetRadius + age * 50
            hitEffectPaint.color = Color.argb(op, Color.red(e.color), Color.green(e.color), Color.blue(e.color))
            hitEffectPaint.strokeWidth = 4f * (1f - age * 0.5f)
            canvas.drawCircle(e.x, e.y, r, hitEffectPaint)
            val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(((1f - age) * 100).toInt().coerceIn(0, 255), Color.red(e.color), Color.green(e.color), Color.blue(e.color)) }
            canvas.drawCircle(e.x, e.y, targetRadius * (1f - age * 0.5f), inner)
            if (age < 0.5f) {
                val ta = if (age < 0.2f) 255 else ((0.5f - age) / 0.3f * 255).toInt().coerceIn(0, 255)
                hitTextPaint.color = Color.argb(ta, Color.red(e.color), Color.green(e.color), Color.blue(e.color))
                hitTextPaint.textSize = 20f + age * 6f
                canvas.drawText(e.label, e.x, e.y - targetRadius - 30f - age * 20f, hitTextPaint)
            }
        }
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
