import 'note.dart';

/// 命中判定等级
enum HitGrade {
  perfect,
  great,
  good,
  miss,
}

/// 单次命中记录
class HitRecord {
  final Note note;
  final HitGrade grade;
  final int timeDiffMs; // 与目标时间的差值（毫秒），正=晚按，负=早按
  final int score;
  final DateTime timestamp;

  const HitRecord({
    required this.note,
    required this.grade,
    required this.timeDiffMs,
    required this.score,
    required this.timestamp,
  });
}

/// 游戏结果
class GameResult {
  final String scoreName;
  final int totalNotes;
  final int perfectCount;
  final int greatCount;
  final int goodCount;
  final int missCount;
  final int maxCombo;
  final int totalScore;
  final double accuracy; // 0.0 ~ 1.0
  final List<HitRecord> hitRecords;
  final Duration duration;

  const GameResult({
    required this.scoreName,
    required this.totalNotes,
    required this.perfectCount,
    required this.greatCount,
    required this.goodCount,
    required this.missCount,
    required this.maxCombo,
    required this.totalScore,
    required this.accuracy,
    required this.hitRecords,
    required this.duration,
  });

  /// 总命中次数
  int get totalHits => perfectCount + greatCount + goodCount + missCount;

  /// 获取评级字母
  String get grade {
    if (accuracy >= 0.95) return 'S';
    if (accuracy >= 0.90) return 'A';
    if (accuracy >= 0.80) return 'B';
    if (accuracy >= 0.70) return 'C';
    return 'D';
  }
}

/// 命中判定常量
class HitConstants {
  /// Perfect 判定窗口（±毫秒）
  static const int perfectWindow = 50;

  /// Great 判定窗口（±毫秒）
  static const int greatWindow = 100;

  /// Good 判定窗口（±毫秒）
  static const int goodWindow = 150;

  /// 各等级得分
  static const int perfectScore = 300;
  static const int greatScore = 200;
  static const int goodScore = 100;

  /// 音符提前显示时间（毫秒）— 圆圈从出现到目标时刻的时长
  static const int approachTimeMs = 1000;
}
