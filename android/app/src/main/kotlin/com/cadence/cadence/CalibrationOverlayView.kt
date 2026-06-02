package com.cadence.cadence

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * 校准覆盖层 - 两步校准流程
 *
 * 第一步：用户点击屏幕放置准心，然后拖动微调到「-1」琴键中心
 * 第二步：显示完整 3x5 网格，用滑条调整行/列间距
 */
@SuppressLint("ViewConstructor")
class CalibrationOverlayView(
    context: Context,
    private var baseX: Float,
    private var baseY: Float,
    private var colSpacing: Float,
    private var rowSpacing: Float
) : View(context) {

    var onConfirm: ((Float, Float, Float, Float) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val noteNames = arrayOf(
        arrayOf("-1", "-2", "-3", "-4", "-5"),
        arrayOf("-6", "-7", "1", "2", "3"),
        arrayOf("4", "5", "6", "7", "+1")
    )

    private var step = 1
    private var crosshairPlaced = false
    private var lockedX = baseX
    private var lockedY = baseY
    private val screenOffset = IntArray(2)

    // 画笔
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444"); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val crosshairCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#EF4444")
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(200, 30, 30, 50)
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#6366F1")
    }
    private val firstKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(220, 239, 68, 68)
    }
    private val firstKeyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#EF4444")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 32f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB"); textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B"); textSize = 22f; textAlign = Paint.Align.LEFT
        typeface = Typeface.MONOSPACE; setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E60F0F14") }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER
    }
    private val sliderTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val sliderFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1"); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val sliderThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val sliderValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    // 拖拽状态
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaleDetector: ScaleGestureDetector
    private var isScaling = false
    private var isDraggingColSlider = false
    private var isDraggingRowSlider = false

    private val panelHeight = 320f
    private var panelTop = 0f
    private val spacingMin = 40f
    private val spacingMax = 500f

    private var nextBtnRect = RectF()
    private var backBtnRect = RectF()
    private var confirmBtnRect = RectF()
    private var cancelBtnRect = RectF()

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (step != 2) return false
                val scaleFactor = detector.scaleFactor
                colSpacing = (colSpacing * scaleFactor).coerceIn(spacingMin, spacingMax)
                rowSpacing = (rowSpacing * scaleFactor).coerceIn(spacingMin, spacingMax)
                invalidate()
                return true
            }
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (step == 2) { isScaling = true; return true }
                return false
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) { isScaling = false }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        panelTop = h - panelHeight
        getLocationOnScreen(screenOffset)

        if (step == 1) drawStep1(canvas, w, h) else drawStep2(canvas, w, h)
    }

    private fun drawStep1(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, 150f, Paint().apply { color = Color.argb(180, 0, 0, 0) })
        titlePaint.color = Color.WHITE
        if (!crosshairPlaced) {
            canvas.drawText("第一步：定位「-1」琴键", w / 2f, 60f, titlePaint)
            canvas.drawText("点击游戏中的「-1」琴键位置", w / 2f, 100f, hintPaint)
        } else {
            canvas.drawText("拖动准心精确对齐「-1」琴键中心", w / 2f, 60f, titlePaint)
        }

        if (crosshairPlaced) {
            val cx = baseX; val cy = baseY
            val crossLen = 40f; val gap = 15f

            canvas.drawLine(cx - crossLen, cy, cx - gap, cy, crosshairPaint)
            canvas.drawLine(cx + gap, cy, cx + crossLen, cy, crosshairPaint)
            canvas.drawLine(cx, cy - crossLen, cx, cy - gap, crosshairPaint)
            canvas.drawLine(cx, cy + gap, cx, cy + crossLen, crosshairPaint)

            crosshairCirclePaint.strokeWidth = 3f
            canvas.drawCircle(cx, cy, 30f, crosshairCirclePaint)
            crosshairCirclePaint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, 60f, crosshairCirclePaint)

            val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EF4444"); style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 5f, centerPaint)

            titlePaint.color = Color.parseColor("#EF4444")
            canvas.drawText("-1", cx, cy - 70f, titlePaint)

            val screenX = (cx + screenOffset[0]).toInt()
            val screenY = (cy + screenOffset[1]).toInt()
            canvas.drawText("X: $screenX  Y: $screenY", 20f, 160f, coordPaint)
        }

        // 底部面板
        canvas.drawRect(0f, panelTop, w, h, panelPaint)
        val btnY = panelTop + panelHeight / 2f - 35f
        val btnWidth = (w - 80f) / 2

        cancelBtnRect.set(20f, btnY, 20f + btnWidth, btnY + 65f)
        nextBtnRect.set(w - 20f - btnWidth, btnY, w - 20f, btnY + 65f)

        btnPaint.color = Color.parseColor("#616161")
        canvas.drawRoundRect(cancelBtnRect, 14f, 14f, btnPaint)
        btnPaint.color = if (crosshairPlaced) Color.parseColor("#22C55E") else Color.parseColor("#333333")
        canvas.drawRoundRect(nextBtnRect, 14f, 14f, btnPaint)

        canvas.drawText("取消", cancelBtnRect.centerX(), cancelBtnRect.centerY() + 12f, btnTextPaint)
        btnTextPaint.color = if (crosshairPlaced) Color.WHITE else Color.parseColor("#666666")
        canvas.drawText("下一步 ▸", nextBtnRect.centerX(), nextBtnRect.centerY() + 12f, btnTextPaint)
        btnTextPaint.color = Color.WHITE
    }

    private fun drawStep2(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, 150f, Paint().apply { color = Color.argb(180, 0, 0, 0) })

        for (row in 0..2) {
            for (col in 0..4) {
                val x = lockedX + col * colSpacing
                val y = lockedY + row * rowSpacing
                val radius = 42f

                if (row == 0 && col == 0) {
                    canvas.drawCircle(x, y, radius + 4f, firstKeyPaint)
                    canvas.drawCircle(x, y, radius + 4f, firstKeyBorderPaint)
                }
                canvas.drawCircle(x, y, radius, keyPaint)
                canvas.drawCircle(x, y, radius, keyBorderPaint)
                canvas.drawText(noteNames[row][col], x, y + 10f, textPaint)
            }
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 99, 102, 241); strokeWidth = 2f
        }
        for (row in 0..2) {
            canvas.drawLine(lockedX, lockedY + row * rowSpacing,
                lockedX + 4 * colSpacing, lockedY + row * rowSpacing, linePaint)
        }
        for (col in 0..4) {
            canvas.drawLine(lockedX + col * colSpacing, lockedY,
                lockedX + col * colSpacing, lockedY + 2 * rowSpacing, linePaint)
        }

        titlePaint.color = Color.WHITE
        canvas.drawText("第二步：调整琴键间距", w / 2f, 60f, titlePaint)
        canvas.drawText("拖动滑条或双指缩放调整间距", w / 2f, 100f, hintPaint)

        val screenLX = (lockedX + screenOffset[0]).toInt()
        val screenLY = (lockedY + screenOffset[1]).toInt()
        canvas.drawText("-1: screen($screenLX, $screenLY)", 20f, 140f, coordPaint)

        // 底部控制面板
        canvas.drawRect(0f, panelTop, w, h, panelPaint)
        val padding = 40f
        val sliderLeft = padding + 80f
        val sliderRight = w - padding - 80f

        val colY = panelTop + 45f
        canvas.drawText("列间距", padding + 30f, colY + 6f, labelTextPaint)
        canvas.drawText("${colSpacing.toInt()}", sliderRight + 45f, colY + 6f, sliderValuePaint)
        drawSlider(canvas, sliderLeft, sliderRight, colY, colSpacing, spacingMin, spacingMax)

        val rowY = panelTop + 120f
        canvas.drawText("行间距", padding + 30f, rowY + 6f, labelTextPaint)
        canvas.drawText("${rowSpacing.toInt()}", sliderRight + 45f, rowY + 6f, sliderValuePaint)
        drawSlider(canvas, sliderLeft, sliderRight, rowY, rowSpacing, spacingMin, spacingMax)

        val btnY2 = panelTop + panelHeight - 85f
        val btnWidth = (w - 80f) / 2
        backBtnRect.set(20f, btnY2, 20f + btnWidth, btnY2 + 65f)
        confirmBtnRect.set(w - 20f - btnWidth, btnY2, w - 20f, btnY2 + 65f)

        btnPaint.color = Color.parseColor("#616161")
        canvas.drawRoundRect(backBtnRect, 14f, 14f, btnPaint)
        btnPaint.color = Color.parseColor("#22C55E")
        canvas.drawRoundRect(confirmBtnRect, 14f, 14f, btnPaint)

        canvas.drawText("◂ 返回", backBtnRect.centerX(), backBtnRect.centerY() + 12f, btnTextPaint)
        canvas.drawText("确认", confirmBtnRect.centerX(), confirmBtnRect.centerY() + 12f, btnTextPaint)
    }

    private fun drawSlider(canvas: Canvas, left: Float, right: Float, y: Float,
                           value: Float, min: Float, max: Float) {
        val ratio = (value - min) / (max - min)
        val thumbX = left + ratio * (right - left)
        canvas.drawLine(left, y, right, y, sliderTrackPaint)
        canvas.drawLine(left, y, thumbX, y, sliderFillPaint)
        canvas.drawCircle(thumbX, y, 16f, sliderThumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (isScaling) return true

        val x = event.x; val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (y >= panelTop) { handlePanelTouchDown(x, y); return true }
                if (step == 1) {
                    if (!crosshairPlaced) {
                        baseX = x; baseY = y; crosshairPlaced = true; invalidate()
                    } else {
                        isDragging = true; lastTouchX = x; lastTouchY = y
                    }
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingColSlider) {
                    val sliderLeft = 40f + 80f; val sliderRight = width.toFloat() - 40f - 80f
                    val ratio = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
                    colSpacing = spacingMin + ratio * (spacingMax - spacingMin); invalidate(); return true
                }
                if (isDraggingRowSlider) {
                    val sliderLeft = 40f + 80f; val sliderRight = width.toFloat() - 40f - 80f
                    val ratio = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
                    rowSpacing = spacingMin + ratio * (spacingMax - spacingMin); invalidate(); return true
                }
                if (isDragging && step == 1 && event.pointerCount == 1) {
                    baseX += event.x - lastTouchX; baseY += event.y - lastTouchY
                    lastTouchX = event.x; lastTouchY = event.y; invalidate(); return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false; isDraggingColSlider = false; isDraggingRowSlider = false
            }
        }
        return true
    }

    private fun handlePanelTouchDown(x: Float, y: Float) {
        if (step == 1) {
            when {
                cancelBtnRect.contains(x, y) -> onCancel?.invoke()
                nextBtnRect.contains(x, y) && crosshairPlaced -> {
                    lockedX = baseX; lockedY = baseY; step = 2; invalidate()
                }
            }
        } else {
            val sliderLeft = 40f + 80f; val sliderRight = width.toFloat() - 40f - 80f
            val colY = panelTop + 45f; val rowY = panelTop + 120f
            when {
                y in (colY - 30f)..(colY + 30f) && x in sliderLeft..sliderRight -> {
                    isDraggingColSlider = true
                    val ratio = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
                    colSpacing = spacingMin + ratio * (spacingMax - spacingMin); invalidate()
                }
                y in (rowY - 30f)..(rowY + 30f) && x in sliderLeft..sliderRight -> {
                    isDraggingRowSlider = true
                    val ratio = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
                    rowSpacing = spacingMin + ratio * (spacingMax - spacingMin); invalidate()
                }
                backBtnRect.contains(x, y) -> { step = 1; invalidate() }
                confirmBtnRect.contains(x, y) -> {
                    val screenX = lockedX + screenOffset[0]
                    val screenY = lockedY + screenOffset[1]
                    onConfirm?.invoke(screenX, screenY, colSpacing, rowSpacing)
                }
            }
        }
    }
}
