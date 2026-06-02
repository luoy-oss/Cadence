package com.cadence.cadence

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.cadence/overlay"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkOverlayPermission" -> {
                    result.success(Settings.canDrawOverlays(this))
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(null)
                }
                "startOverlay" -> {
                    if (Settings.canDrawOverlays(this)) {
                        startOverlayService()
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                "stopOverlay" -> {
                    stopOverlayService()
                    result.success(null)
                }
                "isOverlayRunning" -> {
                    result.success(OverlayService.isRunning)
                }
                "sendScoreData" -> {
                    val data = call.arguments as? Map<String, Any>
                    OverlayService.sendScoreData(data)
                    result.success(null)
                }
                "sendKeyConfig" -> {
                    val config = call.arguments as? Map<String, Any>
                    OverlayService.sendKeyConfig(config)
                    result.success(null)
                }
                "startGame" -> {
                    OverlayService.startGame()
                    result.success(null)
                }
                "pauseGame" -> {
                    OverlayService.pauseGame()
                    result.success(null)
                }
                "resumeGame" -> {
                    OverlayService.resumeGame()
                    result.success(null)
                }
                "stopGame" -> {
                    OverlayService.stopGame()
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }
}
