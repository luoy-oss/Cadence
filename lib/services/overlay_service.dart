import 'package:flutter/services.dart';

/// Android 悬浮窗服务 - 通过 MethodChannel 与原生通信
class OverlayService {
  static const _channel = MethodChannel('com.cadence/overlay');

  // ========== 权限相关 ==========

  /// 检查悬浮窗权限
  static Future<bool> checkPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('checkOverlayPermission');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 请求悬浮窗权限
  static Future<void> requestPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } catch (_) {}
  }

  // ========== 悬浮窗控制 ==========

  /// 启动悬浮窗
  static Future<bool> start() async {
    try {
      final result = await _channel.invokeMethod<bool>('startOverlay');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  /// 停止悬浮窗
  static Future<void> stop() async {
    try {
      await _channel.invokeMethod('stopOverlay');
    } catch (_) {}
  }

  /// 检查悬浮窗是否运行中
  static Future<bool> isRunning() async {
    try {
      final result = await _channel.invokeMethod<bool>('isOverlayRunning');
      return result ?? false;
    } catch (_) {
      return false;
    }
  }

  // ========== 数据同步到悬浮窗 ==========

  /// 发送曲目列表到悬浮窗
  static Future<void> sendScoreList(List<Map<String, String>> scores) async {
    try {
      await _channel.invokeMethod('sendScoreList', scores);
    } catch (_) {}
  }

  /// 更新选中的曲目名称
  static Future<void> updateSelectedScore(String name) async {
    try {
      await _channel.invokeMethod('updateSelectedScore', name);
    } catch (_) {}
  }

  /// 发送按键位置配置到悬浮窗
  static Future<void> sendKeyConfig(Map<String, dynamic> config) async {
    try {
      await _channel.invokeMethod('sendKeyConfig', config);
    } catch (_) {}
  }

  // ========== 倒计时 ==========

  static Future<void> showCountdown(int seconds) async {
    try {
      await _channel.invokeMethod('showCountdown', seconds);
    } catch (_) {}
  }

  static Future<void> updateCountdown(int seconds) async {
    try {
      await _channel.invokeMethod('updateCountdown', seconds);
    } catch (_) {}
  }

  static Future<void> hideCountdown() async {
    try {
      await _channel.invokeMethod('hideCountdown');
    } catch (_) {}
  }

  // ========== 回调设置 ==========

  /// 设置悬浮窗到 Flutter 的回调
  static Future<void> setCallbacks({
    required Function() onPlay,
    required Function() onPause,
    required Function() onStop,
    required Function(double, double, double, double) onCalibrationChanged,
    Function()? onPanelOpened,
  }) async {
    try {
      await _channel.invokeMethod('setCallbacks');
    } catch (_) {}

    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onPlay':
          onPlay();
          break;
        case 'onPause':
          onPause();
          break;
        case 'onStop':
          onStop();
          break;
        case 'onCalibrationChanged':
          final args = call.arguments as Map<dynamic, dynamic>;
          onCalibrationChanged(
            (args['baseX'] as num).toDouble(),
            (args['baseY'] as num).toDouble(),
            (args['colSpacing'] as num).toDouble(),
            (args['rowSpacing'] as num).toDouble(),
          );
          break;
        case 'onPanelOpened':
          onPanelOpened?.call();
          break;
      }
    });
  }
}
