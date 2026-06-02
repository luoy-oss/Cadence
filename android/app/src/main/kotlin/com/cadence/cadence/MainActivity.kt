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
                "checkOverlayPermission" -> {
                    result.success(canDrawOverlays())
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(null)
                }
                "startOverlay" -> {
                    if (canDrawOverlays()) {
                        startFloatingWindowService()
                        result.success(true)
                    } else {
                        requestOverlayPermission()
                        result.success(false)
                    }
                }
                "stopOverlay" -> {
                    stopFloatingWindowService()
                    result.success(null)
                }
                "isOverlayRunning" -> {
                    result.success(FloatingWindowService.isRunning)
                }
                "sendScoreList" -> {
                    val scores = call.arguments as? List<Map<String, String>> ?: emptyList()
                    // 暂存，悬浮窗启动后会读取
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
                        val bx = (config["baseX"] as? Number)?.toFloat() ?: 0f
                        val by = (config["baseY"] as? Number)?.toFloat() ?: 0f
                        val cs = (config["colSpacing"] as? Number)?.toFloat() ?: 150f
                        val rs = (config["rowSpacing"] as? Number)?.toFloat() ?: 120f
                        FloatingWindowService.instance?.updateConfig(bx, by, cs, rs)
                    }
                    result.success(null)
                }
                "startGame" -> {
                    result.success(null)
                }
                "pauseGame" -> {
                    result.success(null)
                }
                "resumeGame" -> {
                    result.success(null)
                }
                "stopGame" -> {
                    result.success(null)
                }
                "setCallbacks" -> {
                    setupFloatingCallbacks()
                    result.success(null)
                }
                "showCountdown" -> {
                    val seconds = (call.arguments as? Number)?.toInt() ?: 3
                    FloatingWindowService.instance?.showCountdown(seconds)
                    result.success(null)
                }
                "updateCountdown" -> {
                    val seconds = (call.arguments as? Number)?.toInt() ?: 0
                    FloatingWindowService.instance?.updateCountdown(seconds)
                    result.success(null)
                }
                "hideCountdown" -> {
                    FloatingWindowService.instance?.hideCountdown()
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun setupFloatingCallbacks() {
        Log.d(TAG, "Setting up floating callbacks")

        FloatingWindowService.onPlay = {
            Log.d(TAG, "onPlay callback triggered")
            runOnUiThread { methodChannel?.invokeMethod("onPlay", null) }
        }
        FloatingWindowService.onPause = {
            Log.d(TAG, "onPause callback triggered")
            runOnUiThread { methodChannel?.invokeMethod("onPause", null) }
        }
        FloatingWindowService.onStop = {
            Log.d(TAG, "onStop callback triggered")
            runOnUiThread { methodChannel?.invokeMethod("onStop", null) }
        }
        FloatingWindowService.onCalibrationChanged = { bx, by, cs, rs ->
            Log.d(TAG, "onCalibrationChanged: baseX=$bx, baseY=$by, colSpacing=$cs, rowSpacing=$rs")
            runOnUiThread {
                methodChannel?.invokeMethod("onCalibrationChanged", mapOf(
                    "baseX" to bx, "baseY" to by,
                    "colSpacing" to cs, "rowSpacing" to rs
                ))
            }
        }
        FloatingWindowService.onPanelOpened = {
            Log.d(TAG, "onPanelOpened callback triggered")
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
                    Uri.parse("package:$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting overlay permission", e)
            }
        }
    }

    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopFloatingWindowService() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }
}
