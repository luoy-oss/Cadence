import 'package:flutter/services.dart';

/// Android 悬浮窗服务 - 通过 MethodChannel 与原生通信
class OverlayService {
  static const _channel = MethodChannel('com.cadence/overlay');

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

  /// 启动悬浮窗覆盖层
  static Future<void> start() async {
    try {
      await _channel.invokeMethod('startOverlay');
    } catch (_) {}
  }

  /// 停止悬浮窗覆盖层
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

  /// 发送琴谱数据到悬浮窗
  static Future<void> sendScoreData(Map<String, dynamic> data) async {
    try {
      await _channel.invokeMethod('sendScoreData', data);
    } catch (_) {}
  }

  /// 发送按键位置配置到悬浮窗
  static Future<void> sendKeyConfig(Map<String, dynamic> config) async {
    try {
      await _channel.invokeMethod('sendKeyConfig', config);
    } catch (_) {}
  }

  /// 开始游戏
  static Future<void> startGame() async {
    try {
      await _channel.invokeMethod('startGame');
    } catch (_) {}
  }

  /// 暂停游戏
  static Future<void> pauseGame() async {
    try {
      await _channel.invokeMethod('pauseGame');
    } catch (_) {}
  }

  /// 恢复游戏
  static Future<void> resumeGame() async {
    try {
      await _channel.invokeMethod('resumeGame');
    } catch (_) {}
  }

  /// 停止游戏
  static Future<void> stopGame() async {
    try {
      await _channel.invokeMethod('stopGame');
    } catch (_) {}
  }
}
