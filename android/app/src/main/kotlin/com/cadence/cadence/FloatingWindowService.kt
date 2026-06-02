package com.cadence.cadence

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 悬浮窗服务 - 控制球 + 控制面板 + 曲目选择 + 游戏覆盖层
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "CadenceFloating"
        private const val CHANNEL_ID = "cadence_floating"
        private const val NOTIFICATION_ID = 1

        var instance: FloatingWindowService? = null
            private set

        @Volatile
        var isRunning = false
            private set

        // Flutter 通信回调
        var onPlay: (() -> Unit)? = null
        var onPause: (() -> Unit)? = null
        var onStop: (() -> Unit)? = null
        var onSelectScore: ((String) -> Unit)? = null
        var onCalibrationChanged: ((Float, Float, Float, Float) -> Unit)? = null
        var onPanelOpened: (() -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var floatingBall: View? = null
    private var mainPanel: View? = null
    private var scoreSelectorPanel: View? = null
    private var calibrationView: CalibrationOverlayView? = null
    private var countdownOverlay: View? = null
    private var gameOverlay: GameOverlayView? = null

    private var isBallShowing = false
    private var isMainPanelShowing = false
    private var isCalibrating = false
    private var isGameShowing = false

    // 曲目列表
    private var scoreList: List<Pair<String, String>> = emptyList()
    private var selectedScoreName = "未选择"
    private var selectedScoreId: String? = null

    // 校准参数
    private var baseX = 0f
    private var baseY = 0f
    private var colSpacing = 150f
    private var rowSpacing = 120f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = resources.displayMetrics
        baseX = dm.widthPixels / 2f - 2 * colSpacing
        baseY = dm.heightPixels * 0.45f

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingBall()
        Log.i(TAG, "Floating window service created")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Cadence 悬浮窗", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Cadence 音游覆盖层" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("Cadence 运行中")
            .setContentText("点击悬浮球打开控制面板")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    // ========== 悬浮球 ==========

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (isBallShowing) return

        val size = dpToPx(50)
        val ball = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.parseColor("#6366F1"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
        }

        var lastX = 0f; var lastY = 0f; var isDragging = false

        ball.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.rawX; lastY = event.rawY; isDragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX; val dy = event.rawY - lastY
                    if (!isDragging && dx * dx + dy * dy > 100) isDragging = true
                    if (isDragging) {
                        val params = floatingBall?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener true
                        params.x += dx.toInt(); params.y += dy.toInt()
                        windowManager?.updateViewLayout(floatingBall, params)
                        lastX = event.rawX; lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!isDragging) showMainPanel() else snapToEdge(); true }
                else -> false
            }
        }

        floatingBall = ball
        val params = WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        windowManager?.addView(floatingBall, params)
        isBallShowing = true
    }

    private fun hideFloatingBall() {
        try { floatingBall?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        floatingBall = null; isBallShowing = false
    }

    private fun snapToEdge() {
        val params = floatingBall?.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = if (params.x < resources.displayMetrics.widthPixels / 2) 0 else resources.displayMetrics.widthPixels - dpToPx(50)
        windowManager?.updateViewLayout(floatingBall, params)
    }

    // ========== 主控制面板 ==========

    private fun showMainPanel() {
        if (isMainPanelShowing) return
        hideFloatingBall()

        val panelW = dpToPx(280)
        val maxPanelH = (resources.displayMetrics.heightPixels * 0.7).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F00F0F14"))
        }

        // 标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12))
        }
        titleBar.addView(TextView(this).apply {
            text = "Cadence"; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleBar.addView(TextView(this).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 18f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { hideMainPanel(); showFloatingBall() }
        })
        container.addView(titleBar)
        container.addView(createDivider())

        val scrollView = ScrollView(this)
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8))
        }

        // 当前曲目 + 选择按钮
        val scoreRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }
        scoreRow.addView(TextView(this).apply { text = "♪"; textSize = 18f })
        scoreRow.addView(TextView(this).apply {
            text = selectedScoreName; setTextColor(Color.parseColor("#BBBBBB")); textSize = 13f
            setPadding(dpToPx(8), 0, 0, 0); tag = "scoreName"
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        scoreRow.addView(TextView(this).apply {
            text = "选择 ▸"; setTextColor(Color.parseColor("#6366F1")); textSize = 13f
            setOnClickListener { showScoreSelector() }
        })
        contentLayout.addView(scoreRow)
        contentLayout.addView(createDivider())

        // 播放控制按钮
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(12))
        }
        controlRow.addView(createCircleButton("▶", "#22C55E") {
            hideMainPanel(); showFloatingBall()
            onPlay?.invoke()
        }, LinearLayout.LayoutParams(dpToPx(52), dpToPx(52)).apply { marginEnd = dpToPx(12) })
        controlRow.addView(createCircleButton("⏸", "#F59E0B") {
            onPause?.invoke()
        }, LinearLayout.LayoutParams(dpToPx(52), dpToPx(52)).apply { marginEnd = dpToPx(12) })
        controlRow.addView(createCircleButton("⏹", "#EF4444") {
            onStop?.invoke(); stopGameOverlay()
        }, LinearLayout.LayoutParams(dpToPx(52), dpToPx(52)))
        contentLayout.addView(controlRow)
        contentLayout.addView(createDivider())

        // 校准按钮
        val calibrateBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable(); bg.setColor(Color.parseColor("#1A1A24")); bg.cornerRadius = dpToPx(8).toFloat(); background = bg
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setOnClickListener { startCalibrationMode() }
        }
        calibrateBtn.addView(TextView(this).apply { text = "◎"; textSize = 18f; setTextColor(Color.parseColor("#EF4444")) })
        calibrateBtn.addView(TextView(this).apply { text = "  校准琴键位置"; setTextColor(Color.WHITE); textSize = 14f })
        calibrateBtn.addView(TextView(this).apply {
            text = "▸"; setTextColor(Color.parseColor("#6366F1")); textSize = 14f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.END })
        contentLayout.addView(calibrateBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(8) })

        scrollView.addView(contentLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        mainPanel = container
        val params = WindowManager.LayoutParams(panelW, maxPanelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.CENTER }

        // 标题栏可拖拽
        var dragStartX = 0f; var dragStartY = 0f; var paramStartX = 0; var paramStartY = 0
        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dragStartX = event.rawX; dragStartY = event.rawY; paramStartX = params.x; paramStartY = params.y; true }
                MotionEvent.ACTION_MOVE -> { params.x = paramStartX + (event.rawX - dragStartX).toInt(); params.y = paramStartY + (event.rawY - dragStartY).toInt(); windowManager?.updateViewLayout(mainPanel, params); true }
                else -> false
            }
        }

        windowManager?.addView(mainPanel, params)
        isMainPanelShowing = true
        onPanelOpened?.invoke()
    }

    private fun hideMainPanel() {
        try { mainPanel?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        mainPanel = null; isMainPanelShowing = false
    }

    // ========== 曲目选择器 ==========

    fun updateScoreList(scores: List<Pair<String, String>>) {
        scoreList = scores
    }

    private fun showScoreSelector() {
        hideMainPanel()

        val panelW = dpToPx(260)
        val panelH = dpToPx(400)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F00F0F14"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        // 标题
        val titleBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleBar.addView(TextView(this).apply {
            text = "◂ 返回"; setTextColor(Color.parseColor("#6366F1")); textSize = 14f
            setOnClickListener { hideScoreSelector(); showMainPanel() }
        })
        titleBar.addView(TextView(this).apply {
            text = "  选择曲目"; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(titleBar)
        panel.addView(createDivider())

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        if (scoreList.isEmpty()) {
            listLayout.addView(TextView(this).apply {
                text = "暂无曲目\n请在应用中导入琴谱"
                setTextColor(Color.parseColor("#888888")); textSize = 14f; gravity = Gravity.CENTER
                setPadding(0, dpToPx(40), 0, 0)
            })
        } else {
            for ((id, name) in scoreList) {
                val isSelected = id == selectedScoreId
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                    val bg = GradientDrawable()
                    bg.setColor(if (isSelected) Color.parseColor("#2A2A3A") else Color.parseColor("#1A1A24"))
                    bg.cornerRadius = dpToPx(6).toFloat(); background = bg
                    setOnClickListener {
                        selectedScoreId = id; selectedScoreName = name
                        onSelectScore?.invoke(id)
                        hideScoreSelector(); showMainPanel()
                    }
                }
                item.addView(TextView(this).apply {
                    text = if (isSelected) "●" else "♪"; textSize = 16f
                    setTextColor(if (isSelected) Color.parseColor("#6366F1") else Color.parseColor("#888888"))
                })
                item.addView(TextView(this).apply {
                    text = "  $name"; setTextColor(Color.WHITE); textSize = 14f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                listLayout.addView(item, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(4) })
            }
        }

        scrollView.addView(listLayout)
        panel.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        scoreSelectorPanel = panel
        val params = WindowManager.LayoutParams(panelW, panelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.CENTER }

        windowManager?.addView(scoreSelectorPanel, params)
    }

    private fun hideScoreSelector() {
        try { scoreSelectorPanel?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        scoreSelectorPanel = null
    }

    // ========== 游戏覆盖层 ==========

    fun showGameOverlay(events: List<GameOverlayView.NoteEvent>, durationMs: Long) {
        val handler = android.os.Handler(mainLooper)
        handler.post {
            stopGameOverlay()

            gameOverlay = GameOverlayView(this, baseX, baseY, colSpacing, rowSpacing).apply {
                setGameData(events, durationMs)
                onGameEnd = {
                    val handler2 = android.os.Handler(mainLooper)
                    handler2.post { stopGameOverlay() }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT
            )

            try {
                windowManager?.addView(gameOverlay, params)
                isGameShowing = true
            } catch (e: Exception) {
                Log.e(TAG, "Error showing game overlay", e)
            }
        }
    }

    fun startGameOverlay() {
        gameOverlay?.startGame()
    }

    fun stopGameOverlay() {
        gameOverlay?.stopGame()
        try { gameOverlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        gameOverlay = null; isGameShowing = false
    }

    fun updateGameScore(score: Int, combo: Int) {
        gameOverlay?.updateScore(score, combo)
    }

    fun addGameHitEffect(row: Int, col: Int, grade: String) {
        gameOverlay?.addHitEffect(row, col, grade)
    }

    // ========== 倒计时覆盖层 ==========

    fun showCountdown(seconds: Int, onFinished: (() -> Unit)? = null) {
        val handler = android.os.Handler(mainLooper)
        handler.post {
            hideCountdown()
            showCountdownOverlay(seconds, onFinished)
        }
    }

    fun updateCountdown(seconds: Int) {
        val handler = android.os.Handler(mainLooper)
        handler.post {
            if (seconds <= 0) hideCountdown()
            else countdownOverlay?.findViewWithTag<TextView>("countdownText")?.text = "$seconds"
        }
    }

    fun hideCountdown() {
        try { countdownOverlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        countdownOverlay = null
    }

    private fun showCountdownOverlay(seconds: Int, onFinished: (() -> Unit)? = null) {
        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.argb(160, 0, 0, 0)) }
        val textContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }

        val countdownText = TextView(this).apply {
            text = "$seconds"; setTextColor(Color.WHITE); textSize = 120f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; tag = "countdownText"
        }
        textContainer.addView(countdownText)
        textContainer.addView(TextView(this).apply {
            text = "秒后开始"; setTextColor(Color.parseColor("#BBBBBB")); textSize = 20f
            gravity = Gravity.CENTER; setPadding(0, dpToPx(8), 0, 0)
        })
        overlay.addView(textContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        countdownOverlay = overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT)

        try { windowManager?.addView(countdownOverlay, params) } catch (e: Exception) { Log.e(TAG, "Error showing countdown", e) }

        // 自动倒计时
        var remaining = seconds
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    hideCountdown()
                    onFinished?.invoke()
                } else {
                    countdownText.text = "$remaining"
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    // ========== 校准模式 ==========

    private fun startCalibrationMode() {
        hideMainPanel()
        calibrationView = CalibrationOverlayView(this, baseX, baseY, colSpacing, rowSpacing).apply {
            onConfirm = { bx, by, cs, rs ->
                baseX = bx; baseY = by; colSpacing = cs; rowSpacing = rs
                onCalibrationChanged?.invoke(bx, by, cs, rs)
                stopCalibrationMode(); showMainPanel()
            }
            onCancel = { stopCalibrationMode(); showMainPanel() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSPARENT)
        windowManager?.addView(calibrationView, params)
        isCalibrating = true
    }

    private fun stopCalibrationMode() {
        try { calibrationView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        calibrationView = null; isCalibrating = false
    }

    // ========== 状态更新 ==========

    fun updateSelectedScore(name: String) {
        selectedScoreName = name
        mainPanel?.findViewWithTag<TextView>("scoreName")?.text = name
    }

    fun updateConfig(bx: Float, by: Float, cs: Float, rs: Float) {
        baseX = bx; baseY = by; colSpacing = cs; rowSpacing = rs
    }

    // ========== 辅助方法 ==========

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#33333333"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                .apply { topMargin = dpToPx(8); bottomMargin = dpToPx(8) }
        }
    }

    private fun createCircleButton(text: String, color: String, onClick: () -> Unit): FrameLayout {
        val container = FrameLayout(this)
        val bg = GradientDrawable(); bg.setColor(Color.parseColor(color)); bg.cornerRadius = dpToPx(26).toFloat()
        container.background = bg
        container.addView(TextView(this).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.isClickable = true; container.isFocusable = true
        container.setOnClickListener { onClick() }
        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.7f; false }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1.0f; false }
                else -> false
            }
        }
        return container
    }

    override fun onDestroy() {
        isRunning = false
        stopCalibrationMode(); hideCountdown(); stopGameOverlay(); hideScoreSelector(); hideMainPanel(); hideFloatingBall()
        instance = null
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
