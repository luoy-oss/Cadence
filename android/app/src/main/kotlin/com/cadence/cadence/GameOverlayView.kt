package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 游戏覆盖层 - 落下式音游风格
 * Row 0: 从上方下落
 * Row 1: 从两侧进入
 * Row 2: 从下方上浮
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

    // 命中效果
    data class HitBurst(val x: Float, val y: Float, val startTime: Long, val color: Int)
    private val hitBursts = mutableListOf<HitBurst>()

    var onGameEnd: (() -> Unit)? = null
    private val screenOffset = IntArray(2)
    private fun sx(screenX: Float) = screenX - screenOffset[0]
    private fun sy(screenY: Float) = screenY - screenOffset[1]

    // 简洁配色：单色（白色/浅蓝），不干扰判断
    private val noteColor = Color.parseColor("#E0E8FF")       // 淡蓝白
    private val noteColorBright = Color.parseColor("#FFFFFF")  // 纯白
    private val hitZoneColor = Color.parseColor("#6366F1")     // 紫蓝（命中区）
    private val hitZoneGlow = Color.parseColor("#818CF8")      // 亮紫蓝
    private val trailColor = Color.parseColor("#40E0E8FF")     // 半透明白

    // 画笔
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val noteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = noteColorBright
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hitZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = hitZoneColor
    }
    private val hitZoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(30, 99, 102, 241)
    }
    private val hitZoneGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 0f, Color.argb(200, 0, 0, 0))
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
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val burstFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
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
        // 序号循环 0-9
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

        // 先画命中区
        drawHitZones(canvas, gameTimeMs)
        // 和弦连线
        drawChordCurves(canvas, gameTimeMs)
        // 音符（后按的先画）
        val sortedNotes = noteEvents.filter { e ->
            val t = e.targetTimeMs - gameTimeMs
            t <= APPROACH_TIME_MS + 200 && t >= -300
        }.sortedByDescending { it.sequenceIndex }

        for (event in sortedNotes) {
            drawNote(canvas, event, gameTimeMs)
        }
        // 到达命中区时触发的命中效果
        drawHitBursts(canvas, now)
        drawCombo(canvas)
        drawProgressBar(canvas, gameTimeMs)
        postInvalidateDelayed(16)
    }

    /** 命中区（固定的半透明圆环 + 编号）*/
    private fun drawHitZones(canvas: Canvas, gameTimeMs: Long) {
        for (event in noteEvents) {
            val cx = sx(baseX + event.col * colSpacing)
            val cy = sy(baseY + event.row * rowSpacing)
            val t = event.targetTimeMs - gameTimeMs
            // 只画在窗口内的命中区
            if (t > APPROACH_TIME_MS + 200 || t < -300) continue

            // 命中区填充
            canvas.drawCircle(cx, cy, targetRadius, hitZoneFillPaint)
            // 命中区边框
            canvas.drawCircle(cx, cy, targetRadius, hitZonePaint)
            // 编号（小字，命中区内）
            numberPaint.color = Color.argb(120, 255, 255, 255)
            numberPaint.textSize = numberTextSize * 0.6f
            canvas.drawText("${event.sequenceIndex}", cx, cy + numberTextSize * 0.2f, numberPaint)
        }
    }

    /** 绘制单个音符（从远处飞向命中区）*/
    private fun drawNote(canvas: Canvas, event: NoteEvent, gameTimeMs: Long) {
        val hitX = sx(baseX + event.col * colSpacing)
        val hitY = sy(baseY + event.row * rowSpacing)
        val t = event.targetTimeMs - gameTimeMs
        val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()

        // 根据行决定飞入方向
        val travelDist = startRadius * 3f
        val noteX: Float
        val noteY: Float

        when (event.row) {
            0 -> {
                // Row 0: 从上方下落
                noteX = hitX
                noteY = hitY - travelDist + travelDist * progress
            }
            2 -> {
                // Row 2: 从下方上浮
                noteX = hitX
                noteY = hitY + travelDist - travelDist * progress
            }
            else -> {
                // Row 1: 从两侧进入（奇数列从左，偶数列从右）
                val fromLeft = event.col % 2 == 0
                noteX = if (fromLeft) hitX - travelDist + travelDist * progress
                        else hitX + travelDist - travelDist * progress
                noteY = hitY
            }
        }

        // 拖尾效果
        drawTrail(canvas, noteX, noteY, hitX, hitY, progress, event.row)

        // 音符圆圈
        val noteSize = targetRadius * 0.8f
        val alpha = (200 + progress * 55).toInt().coerceIn(0, 255)
        notePaint.color = Color.argb(alpha, Color.red(noteColor), Color.green(noteColor), Color.blue(noteColor))
        canvas.drawCircle(noteX, noteY, noteSize, notePaint)
        noteBorderPaint.color = Color.argb(alpha, Color.red(noteColorBright), Color.green(noteColorBright), Color.blue(noteColorBright))
        canvas.drawCircle(noteX, noteY, noteSize, noteBorderPaint)

        // 音符内编号
        numberPaint.color = Color.WHITE
        numberPaint.textSize = numberTextSize
        numberPaint.setShadowLayer(6f, 0f, 0f, Color.argb(200, 0, 0, 0))
        canvas.drawText("${event.sequenceIndex}", noteX, noteY + numberTextSize * 0.35f, numberPaint)

        // 接近命中区时发光
        if (progress > 0.85f) {
            val ga = ((progress - 0.85f) / 0.15f * 150).toInt().coerceIn(0, 255)
            hitZoneGlowPaint.color = Color.argb(ga, Color.red(hitZoneGlow), Color.green(hitZoneGlow), Color.blue(hitZoneGlow))
            canvas.drawCircle(hitX, hitY, targetRadius + 4, hitZoneGlowPaint)
        }

        // 到达命中区时触发爆炸
        if (t in -80..0) {
            hitBursts.add(HitBurst(hitX, hitY, SystemClock.elapsedRealtime(), hitZoneGlow))
        }
    }

    /** 拖尾效果 */
    private fun drawTrail(canvas: Canvas, nx: Float, ny: Float, hx: Float, hy: Float, progress: Float, row: Int) {
        val trailLen = (1f - progress) * 0.6f  // 越远拖尾越长
        val segments = 5
        for (i in 1..segments) {
            val frac = i.toFloat() / segments
            val tx = nx + (hx - nx) * frac * trailLen
            val ty = ny + (hy - ny) * frac * trailLen
            val ta = ((1f - frac) * 60 * progress).toInt().coerceIn(0, 255)
            trailPaint.color = Color.argb(ta, Color.red(trailColor), Color.green(trailColor), Color.blue(trailColor))
            val tSize = targetRadius * 0.4f * (1f - frac * 0.5f)
            canvas.drawCircle(tx, ty, tSize, trailPaint)
        }
    }

    /** 命中爆炸效果 */
    private fun drawHitBursts(canvas: Canvas, now: Long) {
        val it = hitBursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val age = (now - b.startTime).toFloat() / 500f
            if (age >= 1f) { it.remove(); continue }

            val opacity = (1f - age)

            // 扩散环
            val ringR = targetRadius + age * 50
            val ringA = (opacity * 200).toInt().coerceIn(0, 255)
            burstPaint.color = Color.argb(ringA, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            burstPaint.strokeWidth = 3f * (1f - age * 0.5f)
            canvas.drawCircle(b.x, b.y, ringR, burstPaint)

            // 闪光
            val flashA = (opacity * 80).toInt().coerceIn(0, 255)
            burstFillPaint.color = Color.argb(flashA, Color.red(b.color), Color.green(b.color), Color.blue(b.color))
            canvas.drawCircle(b.x, b.y, targetRadius * (1f + age * 0.3f), burstFillPaint)
        }
    }

    /** 和弦连线 */
    private fun drawChordCurves(canvas: Canvas, gameTimeMs: Long) {
        for (group in timeGroups) {
            if (group.notes.size < 2) continue
            val t = group.timeMs - gameTimeMs
            if (t > APPROACH_TIME_MS || t < -200) continue
            val progress = (1.0 - t.toDouble() / APPROACH_TIME_MS).coerceIn(0.0, 1.0).toFloat()
            val alpha = (progress * 120).toInt().coerceIn(0, 255)
            chordPaint.color = Color.argb(alpha, 99, 102, 241)

            val path = Path()
            for (i in 0 until group.notes.size) {
                val x = sx(baseX + group.notes[i].col * colSpacing)
                val y = sy(baseY + group.notes[i].row * rowSpacing)
                if (i == 0) path.moveTo(x, y)
                else {
                    val px = sx(baseX + group.notes[i - 1].col * colSpacing)
                    val py = sy(baseY + group.notes[i - 1].row * rowSpacing)
                    val midX = (px + x) / 2; val midY = (py + y) / 2
                    path.quadTo(midX + (y - py) * 0.15f, midY - (x - px) * 0.15f, x, y)
                }
            }
            canvas.drawPath(path, chordPaint)
        }
    }

    private var combo = 0; private var score = 0
    fun updateScore(s: Int, c: Int) { score = s; combo = c }

    fun addHitEffect(row: Int, col: Int, grade: String) {
        val x = sx(baseX + col * colSpacing); val y = sy(baseY + row * rowSpacing)
        hitBursts.add(HitBurst(x, y, SystemClock.elapsedRealtime(), hitZoneGlow))
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
