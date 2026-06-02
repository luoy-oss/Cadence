package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View

/**
 * 游戏覆盖层 - osu! 风格
 * 图层顺序：后按的先画（底层），先按的后画（顶层）
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
    data class TimeGroup(val timeMs: Long, val notes: List<NoteEvent>)

    private var noteEvents = listOf<NoteEvent>()
    private var timeGroups = listOf<TimeGroup>()
    private var gameStartTimeMs = 0L
    private var isPlaying = false
    private var totalDurationMs = 0L

    // 命中效果（爆炸 + 判定文字）
    data class HitBurst(
        val x: Float, val y: Float,
        val startTime: Long,
        val color: Int,
        val grade: String
    )
    private val hitBursts = mutableListOf<HitBurst>()

    var onGameEnd: (() -> Unit)? = null
    private val screenOffset = IntArray(2)
    private fun sx(screenX: Float) = screenX - screenOffset[0]
    private fun sy(screenY: Float) = screenY - screenOffset[1]

    // ===== 颜色 =====
    private val noteColors = intArrayOf(
        Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E53"),
        Color.parseColor("#FFD93D"), Color.parseColor("#6BCB77"),
        Color.parseColor("#4D96FF"), Color.parseColor("#9B59B6"),
    )
    private fun getNoteColor(seq: Int) = noteColors[seq % noteColors.size]

    // ===== 画笔 =====
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f, 0f, 0f, Color.argb(200, 0, 0, 0))
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
    private val burstRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val burstFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gradeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val chordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
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
            if (g.timeMs != lastT) { seq++; lastT = g.timeMs }
            for (n in g.notes) {
                val idx = mutable.indexOfFirst { it.row == n.row && it.col == n.col && it.targetTimeMs == n.targetTimeMs }
                if (idx >= 0) mutable[idx] = n.copy(sequenceIndex = seq)
            }
        }
        noteEvents = mutable
    }

    fun startGame() { gameStartTimeMs = SystemClock.elapsedRealtime(); isPlaying = true; invalidate() }
    fun stopGame() { isPlaying = false; hitBursts.clear(); invalidate() }

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

        drawChordCurves(canvas, gameTimeMs)

        // 图层顺序：后按的先画（底层），先按的后画（顶层）
        // 所以按 sequenceIndex 降序绘制
        val sortedNotes = noteEvents.filter { e ->
            val t = e.targetTimeMs - gameTimeMs
            t <= APPROACH_TIME_MS && t >= -300
        }.sortedByDescending { it.sequenceIndex }

        for (event in sortedNotes) {
            drawSingleNote(canvas, event, gameTimeMs)
        }

        drawHitBursts(canvas, now)
        drawCombo(canvas)
        drawProgressBar(canvas, gameTimeMs)
        postInvalidateDelayed(16)
    }

    /** 绘制单个音符（osu! 风格）*/
    private fun drawSingleNote(canvas: Canvas, event: NoteEvent, gameTimeMs: Long) {
        val cx = sx(baseX + event.col * colSpacing)
        val cy = sy(baseY + event.row * rowSpacing)
        val t = event.targetTimeMs - gameTimeMs
        val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
        val color = getNoteColor(event.sequenceIndex)

        // 当 approach circle 与 target circle 重合时（progress ≈ 1.0），该按了
        val curRadius = startRadius + (targetRadius - startRadius) * progress

        // ===== 1. 目标圆（固定大小，随进度变亮）=====
        val fillAlpha = (80 + progress * 150).toInt().coerceIn(0, 255)
        targetFillPaint.color = Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, targetRadius, targetFillPaint)

        val borderAlpha = (150 + progress * 105).toInt().coerceIn(0, 255)
        targetBorderPaint.color = Color.argb(borderAlpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, targetRadius, targetBorderPaint)

        // ===== 2. Approach circle（从大到小，从透明到不透明）=====
        val approachAlpha = (progress * progress * 255).toInt().coerceIn(0, 255)  // 二次方曲线，后期更亮
        approachPaint.color = Color.argb(approachAlpha, Color.red(color), Color.green(color), Color.blue(color))
        approachPaint.strokeWidth = 4f + progress * 2f  // 越接近越粗
        canvas.drawCircle(cx, cy, curRadius, approachPaint)

        // ===== 3. 发光效果（接近时）=====
        if (progress > 0.8f) {
            val ga = ((progress - 0.8f) / 0.2f * 160).toInt().coerceIn(0, 255)
            glowPaint.color = Color.argb(ga, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(cx, cy, targetRadius + 4, glowPaint)
        }

        // ===== 4. 圆圈内编号（大号加粗）=====
        numberPaint.color = Color.WHITE
        numberPaint.textSize = numberTextSize + progress * 6f
        numberPaint.setShadowLayer(8f, 0f, 0f, Color.argb(200, 0, 0, 0))
        canvas.drawText("${event.sequenceIndex}", cx, cy + numberTextSize * 0.35f, numberPaint)

        // ===== 5. 自动命中判定（时间过了就触发爆炸）=====
        if (t < -50 && t > -300) {
            // 这个音符已经过了命中时间，触发爆炸效果
            triggerHitBurst(cx, cy, color, t)
        }
    }

    /** 触发命中爆炸（每个音符只触发一次）*/
    private var lastBurstIndex = -1
    private fun triggerHitBurst(cx: Float, cy: Float, color: Int, timeDiff: Long) {
        // 简化：根据时间差判定等级
        val absDiff = kotlin.math.abs(timeDiff)
        val grade = when {
            absDiff <= 50 -> "PERFECT"
            absDiff <= 100 -> "GREAT"
            absDiff <= 150 -> "GOOD"
            else -> "MISS"
        }
        val gradeColor = when (grade) {
            "PERFECT" -> Color.parseColor("#FFD700")
            "GREAT" -> Color.parseColor("#22C55E")
            "GOOD" -> Color.parseColor("#3B82F6")
            else -> Color.parseColor("#EF4444")
        }
        // 避免重复触发（通过时间戳去重）
        val burstKey = "${cx.toInt()}_${cy.toInt()}_${(timeDiff / 100)}"
        if (hitBursts.none { it.startTime == SystemClock.elapsedRealtime() && it.x == cx && it.y == cy }) {
            hitBursts.add(HitBurst(cx, cy, SystemClock.elapsedRealtime(), gradeColor, grade))
        }
    }

    /** 绘制命中爆炸效果（osu! 风格）*/
    private fun drawHitBursts(canvas: Canvas, now: Long) {
        val it = hitBursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val age = (now - b.startTime).toFloat() / 600f
            if (age >= 1f) { it.remove(); continue }

            val opacity = (1f - age)

            // 外圈扩散环
            val ringRadius = targetRadius + age * 60
            val ringAlpha = (opacity * 200).toInt().coerceIn(0, 255)
            burstRingPaint.color = Color.argb(ringAlpha, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            burstRingPaint.strokeWidth = 4f * (1f - age * 0.6f)
            canvas.drawCircle(b.x, b.y, ringRadius, burstRingPaint)

            // 内圈闪光
            val flashRadius = targetRadius * (1f + age * 0.3f)
            val flashAlpha = (opacity * 100).toInt().coerceIn(0, 255)
            burstFillPaint.color = Color.argb(flashAlpha, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            canvas.drawCircle(b.x, b.y, flashRadius, burstFillPaint)

            // 判定文字（从中心向上飘）
            if (age < 0.7f) {
                val textAlpha = if (age < 0.3f) 255 else ((0.7f - age) / 0.4f * 255).toInt().coerceIn(0, 255)
                gradeTextPaint.color = Color.argb(textAlpha, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
                gradeTextPaint.textSize = 20f + age * 8f
                canvas.drawText(b.grade, b.x, b.y - targetRadius - 20f - age * 30f, gradeTextPaint)
            }

            // 粒子效果（Perfect 额外粒子）
            if (b.grade == "PERFECT") {
                val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb((opacity * 180).toInt().coerceIn(0, 255), Color.red(b.color), Color.green(b.color), Color.blue(b.color))
                }
                for (i in 0 until 8) {
                    val angle = i * Math.PI.toFloat() / 4f + age * 2f
                    val dist = targetRadius + age * 45
                    val px = b.x + kotlin.math.cos(angle) * dist
                    val py = b.y + kotlin.math.sin(angle) * dist
                    val pSize = 3f * (1f - age)
                    canvas.drawCircle(px, py, pSize, particlePaint)
                }
            }
        }
    }

    /** 和弦贝塞尔曲线连线 */
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

            val path = Path()
            for (i in 0 until group.notes.size) {
                val x = sx(baseX + group.notes[i].col * colSpacing)
                val y = sy(baseY + group.notes[i].row * rowSpacing)
                if (i == 0) { path.moveTo(x, y) }
                else {
                    val px = sx(baseX + group.notes[i - 1].col * colSpacing)
                    val py = sy(baseY + group.notes[i - 1].row * rowSpacing)
                    val midX = (px + x) / 2; val midY = (py + y) / 2
                    val cpX = midX + (y - py) * 0.2f; val cpY = midY - (x - px) * 0.2f
                    path.quadTo(cpX, cpY, x, y)
                }
            }
            canvas.drawPath(path, chordPaint)
        }
    }

    private var combo = 0; private var score = 0
    fun updateScore(s: Int, c: Int) { score = s; combo = c }

    fun addHitEffect(row: Int, col: Int, grade: String) {
        val x = sx(baseX + col * colSpacing); val y = sy(baseY + row * rowSpacing)
        val c = when (grade) { "perfect" -> Color.parseColor("#FFD700"); "great" -> Color.parseColor("#22C55E"); "good" -> Color.parseColor("#3B82F6"); else -> Color.parseColor("#EF4444") }
        hitBursts.add(HitBurst(x, y, SystemClock.elapsedRealtime(), c, grade.uppercase()))
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
