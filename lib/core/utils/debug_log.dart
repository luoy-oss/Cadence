import 'dart:developer' as developer;

/// 调试日志工具
class DebugLog {
  static bool enabled = false;

  static void d(String message) {
    if (enabled) {
      developer.log(message, name: 'Cadence');
    }
  }

  static void i(String message) {
    if (enabled) {
      developer.log('[INFO] $message', name: 'Cadence');
    }
  }

  static void w(String message) {
    if (enabled) {
      developer.log('[WARN] $message', name: 'Cadence');
    }
  }

  static void e(String message, [Object? error, StackTrace? stack]) {
    if (enabled) {
      developer.log('[ERROR] $message', name: 'Cadence', error: error, stackTrace: stack);
    }
  }

  static void divider([String label = '']) {
    if (enabled) {
      final line = label.isEmpty ? '─' * 40 : '─── $label ${'─' * (36 - label.length)}';
      developer.log(line, name: 'Cadence');
    }
  }

  static void block(String title, String content) {
    if (enabled) {
      developer.log('[$title]\n$content', name: 'Cadence');
    }
  }

  /// 始终输出的日志（不受开关控制）
  static void logAlways(String message) {
    developer.log(message, name: 'Cadence');
  }
}
