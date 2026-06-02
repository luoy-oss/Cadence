package com.cadence.cadence

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.cadence/overlay"
    private var methodChannel: MethodChannel? = null

    companion object {
        private const val TAG = "CadenceMain"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                // ===== 权限 =====
                "checkOverlayPermission" -> result.success(canDrawOverlays())
                "requestOverlayPermission" -> { requestOverlayPermission(); result.success(null) }

                // ===== 悬浮窗控制 =====
                "startOverlay" -> {
                    if (canDrawOverlays()) { startFloatingWindowService(); result.success(true) }
                    else { requestOverlayPermission(); result.success(false) }
                }
                "stopOverlay" -> { stopFloatingWindowService(); result.success(null) }
                "isOverlayRunning" -> result.success(FloatingWindowService.isRunning)
                "setCallbacks" -> { setupFloatingCallbacks(); result.success(null) }

                // ===== 数据同步 =====
                "sendScoreList" -> {
                    val scores = call.arguments as? List<Map<String, String>> ?: emptyList()
                    val pairs = scores.map { (it["id"] ?: "") to (it["name"] ?: "") }
                    FloatingWindowService.instance?.updateScoreList(pairs)
                    result.success(null)
                }
                "updateSelectedScore" -> {
                    val name = call.arguments as? String ?: "未选择"
                    FloatingWindowService.instance?.updateSelectedScore(name)
                    result.success(null)
                }
                "sendKeyConfig" -> {
                    val config = call.arguments as? Map<String, Any>
                    if (config != null) {
                        // 注意：Dart KeyPositionConfig.toJson() 用的是 columnSpacing/rowSpacing
                        val cs = (config["columnSpacing"] as? Number)?.toFloat()
                            ?: (config["colSpacing"] as? Number)?.toFloat() ?: 150f
                        val rs = (config["rowSpacing"] as? Number)?.toFloat() ?: 120f
                        FloatingWindowService.instance?.updateConfig(
                            (config["baseX"] as? Number)?.toFloat() ?: 0f,
                            (config["baseY"] as? Number)?.toFloat() ?: 0f,
                            cs, rs,
                        )
                    }
                    result.success(null)
                }

                // ===== 游戏控制 =====
                "startGameWithData" -> {
                    val args = call.arguments as? Map<String, Any>
                    if (args != null) {
                        val notesRaw = args["notes"] as? List<Map<String, Any>> ?: emptyList()
                        val durationMs = (args["durationMs"] as? Number)?.toLong() ?: 0L
                        val countdownSec = (args["countdownSeconds"] as? Number)?.toInt() ?: 3

                        val events = notesRaw.map { note ->
                            GameOverlayView.NoteEvent(
                                row = (note["row"] as? Number)?.toInt() ?: 0,
                                col = (note["col"] as? Number)?.toInt() ?: 0,
                                targetTimeMs = (note["timeMs"] as? Number)?.toLong() ?: 0L,
                            )
                        }

                        val service = FloatingWindowService.instance
                        if (service != null) {
                            // 先显示游戏覆盖层（不播放）
                            service.showGameOverlay(events, durationMs)
                            // 倒计时结束后开始播放
                            service.showCountdown(countdownSec) {
                                service.startGameOverlay()
                            }
                        }
                    }
                    result.success(null)
                }
                "stopGame" -> {
                    FloatingWindowService.instance?.stopGameOverlay()
                    result.success(null)
                }
                "updateGameScore" -> {
                    val args = call.arguments as? Map<String, Any>
                    if (args != null) {
                        val score = (args["score"] as? Number)?.toInt() ?: 0
                        val combo = (args["combo"] as? Number)?.toInt() ?: 0
                        FloatingWindowService.instance?.updateGameScore(score, combo)
                    }
                    result.success(null)
                }
                "addHitEffect" -> {
                    val args = call.arguments as? Map<String, Any>
                    if (args != null) {
                        val row = (args["row"] as? Number)?.toInt() ?: 0
                        val col = (args["col"] as? Number)?.toInt() ?: 0
                        val grade = args["grade"] as? String ?: "miss"
                        FloatingWindowService.instance?.addGameHitEffect(row, col, grade)
                    }
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun setupFloatingCallbacks() {
        Log.d(TAG, "Setting up floating callbacks")

        FloatingWindowService.onPlay = {
            Log.d(TAG, "onPlay triggered")
            runOnUiThread { methodChannel?.invokeMethod("onPlay", null) }
        }
        FloatingWindowService.onPause = {
            Log.d(TAG, "onPause triggered")
            runOnUiThread { methodChannel?.invokeMethod("onPause", null) }
        }
        FloatingWindowService.onStop = {
            Log.d(TAG, "onStop triggered")
            runOnUiThread { methodChannel?.invokeMethod("onStop", null) }
        }
        FloatingWindowService.onSelectScore = { id ->
            Log.d(TAG, "onSelectScore: $id")
            runOnUiThread { methodChannel?.invokeMethod("onSelectScore", id) }
        }
        FloatingWindowService.onCalibrationChanged = { bx, by, cs, rs ->
            Log.d(TAG, "onCalibrationChanged: $bx, $by, $cs, $rs")
            runOnUiThread {
                methodChannel?.invokeMethod("onCalibrationChanged", mapOf(
                    "baseX" to bx, "baseY" to by, "colSpacing" to cs, "rowSpacing" to rs
                ))
            }
        }
        FloatingWindowService.onPanelOpened = {
            runOnUiThread { methodChannel?.invokeMethod("onPanelOpened", null) }
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e: Exception) { Log.e(TAG, "Error requesting overlay permission", e) }
        }
    }

    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopFloatingWindowService() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }
}
