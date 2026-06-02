import 'package:flutter/services.dart';

/// Android 悬浮窗服务 - 通过 MethodChannel 与原生通信
class OverlayService {
  static const _channel = MethodChannel('com.cadence/overlay');

  // ========== 权限 ==========

  static Future<bool> checkPermission() async {
    try { return await _channel.invokeMethod<bool>('checkOverlayPermission') ?? false; } catch (_) { return false; }
  }

  static Future<void> requestPermission() async {
    try { await _channel.invokeMethod('requestOverlayPermission'); } catch (_) {}
  }

  // ========== 悬浮窗控制 ==========

  static Future<bool> start() async {
    try { return await _channel.invokeMethod<bool>('startOverlay') ?? false; } catch (_) { return false; }
  }

  static Future<void> stop() async {
    try { await _channel.invokeMethod('stopOverlay'); } catch (_) {}
  }

  static Future<bool> isRunning() async {
    try { return await _channel.invokeMethod<bool>('isOverlayRunning') ?? false; } catch (_) { return false; }
  }

  // ========== 数据同步 ==========

  static Future<void> sendScoreList(List<Map<String, String>> scores) async {
    try { await _channel.invokeMethod('sendScoreList', scores); } catch (_) {}
  }

  static Future<void> updateSelectedScore(String name) async {
    try { await _channel.invokeMethod('updateSelectedScore', name); } catch (_) {}
  }

  static Future<void> sendKeyConfig(Map<String, dynamic> config) async {
    try { await _channel.invokeMethod('sendKeyConfig', config); } catch (_) {}
  }

  // ========== 游戏控制 ==========

  /// 发送游戏数据并开始（含倒计时）
  static Future<void> startGameWithData({
    required List<Map<String, dynamic>> notes,
    required int durationMs,
    required int countdownSeconds,
  }) async {
    try {
      await _channel.invokeMethod('startGameWithData', {
        'notes': notes,
        'durationMs': durationMs,
        'countdownSeconds': countdownSeconds,
      });
    } catch (_) {}
  }

  static Future<void> stopGame() async {
    try { await _channel.invokeMethod('stopGame'); } catch (_) {}
  }

  static Future<void> updateGameScore(int score, int combo) async {
    try { await _channel.invokeMethod('updateGameScore', {'score': score, 'combo': combo}); } catch (_) {}
  }

  static Future<void> addHitEffect(int row, int col, String grade) async {
    try { await _channel.invokeMethod('addHitEffect', {'row': row, 'col': col, 'grade': grade}); } catch (_) {}
  }

  // ========== 回调设置 ==========

  static Future<void> setCallbacks({
    required Function() onPlay,
    required Function() onPause,
    required Function() onStop,
    required Function(String) onSelectScore,
    required Function(double, double, double, double) onCalibrationChanged,
    Function()? onPanelOpened,
  }) async {
    try { await _channel.invokeMethod('setCallbacks'); } catch (_) {}

    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onPlay': onPlay(); break;
        case 'onPause': onPause(); break;
        case 'onStop': onStop(); break;
        case 'onSelectScore': onSelectScore(call.arguments as String? ?? ''); break;
        case 'onCalibrationChanged':
          final args = call.arguments as Map<dynamic, dynamic>;
          onCalibrationChanged(
            (args['baseX'] as num).toDouble(), (args['baseY'] as num).toDouble(),
            (args['colSpacing'] as num).toDouble(), (args['rowSpacing'] as num).toDouble(),
          );
          break;
        case 'onPanelOpened': onPanelOpened?.call(); break;
      }
    });
  }
}
