import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/utils/debug_log.dart';
import '../models/game_result.dart';
import '../models/note.dart';
import '../models/score.dart';
import 'score_provider.dart';

/// 游戏状态
enum GameStatus {
  idle,       // 空闲
  countdown,  // 倒计时中
  playing,    // 游戏进行中
  finished,   // 游戏结束
}

/// 待显示的音符指示器
class NoteIndicator {
  final Note note;
  final int targetTimeMs;   // 目标时间（游戏时间轴上的毫秒数）
  final int eventIndex;     // 对应的事件索引

  const NoteIndicator({
    required this.note,
    required this.targetTimeMs,
    required this.eventIndex,
  });
}

/// 命中反馈效果
class HitEffect {
  final Note note;
  final HitGrade grade;
  final double x;
  final double y;
  final DateTime createdAt;

  HitEffect({
    required this.note,
    required this.grade,
    required this.x,
    required this.y,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();
}

/// 游戏引擎状态
class GameState {
  final GameStatus status;
  final int countdownRemaining;

  /// 当前游戏时间（毫秒，从游戏开始计时）
  final int gameTimeMs;

  /// 当前事件索引
  final int currentEventIndex;
  final int totalEvents;

  /// 分数
  final int score;
  final int combo;
  final int maxCombo;

  /// 各等级计数
  final int perfectCount;
  final int greatCount;
  final int goodCount;
  final int missCount;

  /// 当前活跃的音符指示器（需要显示的）
  final List<NoteIndicator> activeIndicators;

  /// 命中反馈效果列表
  final List<HitEffect> hitEffects;

  /// 最近一次命中的等级（用于 UI 显示）
  final HitGrade? lastHitGrade;

  /// 游戏是否暂停
  final bool isPaused;

  const GameState({
    this.status = GameStatus.idle,
    this.countdownRemaining = 0,
    this.gameTimeMs = 0,
    this.currentEventIndex = 0,
    this.totalEvents = 0,
    this.score = 0,
    this.combo = 0,
    this.maxCombo = 0,
    this.perfectCount = 0,
    this.greatCount = 0,
    this.goodCount = 0,
    this.missCount = 0,
    this.activeIndicators = const [],
    this.hitEffects = const [],
    this.lastHitGrade,
    this.isPaused = false,
  });

  double get progress =>
      totalEvents > 0 ? currentEventIndex / totalEvents : 0;

  int get totalHits => perfectCount + greatCount + goodCount + missCount;

  double get accuracy =>
      totalHits > 0
          ? (perfectCount * 300 + greatCount * 200 + goodCount * 100) /
              (totalHits * 300)
          : 0.0;

  GameState copyWith({
    GameStatus? status,
    int? countdownRemaining,
    int? gameTimeMs,
    int? currentEventIndex,
    int? totalEvents,
    int? score,
    int? combo,
    int? maxCombo,
    int? perfectCount,
    int? greatCount,
    int? goodCount,
    int? missCount,
    List<NoteIndicator>? activeIndicators,
    List<HitEffect>? hitEffects,
    HitGrade? lastHitGrade,
    bool? isPaused,
    bool clearLastHitGrade = false,
  }) {
    return GameState(
      status: status ?? this.status,
      countdownRemaining: countdownRemaining ?? this.countdownRemaining,
      gameTimeMs: gameTimeMs ?? this.gameTimeMs,
      currentEventIndex: currentEventIndex ?? this.currentEventIndex,
      totalEvents: totalEvents ?? this.totalEvents,
      score: score ?? this.score,
      combo: combo ?? this.combo,
      maxCombo: maxCombo ?? this.maxCombo,
      perfectCount: perfectCount ?? this.perfectCount,
      greatCount: greatCount ?? this.greatCount,
      goodCount: goodCount ?? this.goodCount,
      missCount: missCount ?? this.missCount,
      activeIndicators: activeIndicators ?? this.activeIndicators,
      hitEffects: hitEffects ?? this.hitEffects,
      lastHitGrade: clearLastHitGrade ? null : (lastHitGrade ?? this.lastHitGrade),
      isPaused: isPaused ?? this.isPaused,
    );
  }
}

/// 游戏引擎
class GameEngineNotifier extends StateNotifier<GameState> {
  final Ref ref;
  Timer? _gameTimer;
  Timer? _countdownTimer;
  final Stopwatch _stopwatch = Stopwatch();

  /// 事件时间轴（事件在游戏时间轴上的毫秒位置）
  List<int> _eventTimeline = [];

  GameEngineNotifier(this.ref) : super(const GameState());

  /// 开始游戏
  void startGame() {
    final scoreState = ref.read(scoreListProvider);
    final score = scoreState.selectedScore;
    if (score == null || score.events.isEmpty) return;

    // 重置状态
    _stopwatch.stop();
    _stopwatch.reset();
    _gameTimer?.cancel();
    _countdownTimer?.cancel();

    // 计算事件时间轴
    _buildTimeline(score);

    state = GameState(
      totalEvents: score.events.length,
    );

    // 开始倒计时
    state = state.copyWith(status: GameStatus.countdown, countdownRemaining: 3);
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final remaining = state.countdownRemaining - 1;
      if (remaining <= 0) {
        timer.cancel();
        _startPlaying();
      } else {
        state = state.copyWith(countdownRemaining: remaining);
      }
    });
  }

  /// 构建事件时间轴
  void _buildTimeline(Score score) {
    _eventTimeline = [];
    for (final event in score.events) {
      _eventTimeline.add(event.time);
    }
  }

  /// 开始实际游戏
  void _startPlaying() {
    state = state.copyWith(
      status: GameStatus.playing,
      countdownRemaining: 0,
    );

    _stopwatch.start();

    // 60fps 游戏循环
    _gameTimer = Timer.periodic(const Duration(milliseconds: 16), (_) {
      _updateGameLoop();
    });
  }

  /// 游戏主循环（约60fps调用）
  void _updateGameLoop() {
    if (state.status != GameStatus.playing || state.isPaused) return;

    final gameTimeMs = _stopwatch.elapsedMilliseconds;
    final scoreState = ref.read(scoreListProvider);
    final score = scoreState.selectedScore;
    if (score == null) return;

    // 更新活跃指示器
    final indicators = _calculateActiveIndicators(score, gameTimeMs);

    // 检查是否有未命中的音符（已过判定窗口）
    _checkMissedNotes(score, gameTimeMs);

    // 清理过期的命中效果
    final now = DateTime.now();
    final effects = state.hitEffects.where((e) =>
        now.difference(e.createdAt).inMilliseconds < 500).toList();

    // 检查游戏是否结束
    if (state.currentEventIndex >= score.events.length && indicators.isEmpty) {
      _finishGame();
      return;
    }

    state = state.copyWith(
      gameTimeMs: gameTimeMs,
      activeIndicators: indicators,
      hitEffects: effects,
    );
  }

  /// 计算当前活跃的音符指示器
  List<NoteIndicator> _calculateActiveIndicators(Score score, int gameTimeMs) {
    final indicators = <NoteIndicator>[];
    const approachTime = HitConstants.approachTimeMs;

    // 遍历尚未处理的事件
    for (var i = state.currentEventIndex; i < score.events.length; i++) {
      final event = score.events[i];
      if (event.isRest) continue;

      final targetTime = _eventTimeline[i];
      final timeUntilHit = targetTime - gameTimeMs;

      // 只显示在 approach 窗口内的音符
      if (timeUntilHit > approachTime) break; // 还太早，后面的更不用看
      if (timeUntilHit < -HitConstants.goodWindow) continue; // 已经过了判定窗口

      for (final note in event.notes) {
        indicators.add(NoteIndicator(
          note: note,
          targetTimeMs: targetTime,
          eventIndex: i,
        ));
      }
    }

    return indicators;
  }

  /// 检查未命中的音符
  void _checkMissedNotes(Score score, int gameTimeMs) {
    var newIdx = state.currentEventIndex;

    while (newIdx < score.events.length) {
      final event = score.events[newIdx];
      if (event.isRest) {
        newIdx++;
        continue;
      }

      final targetTime = _eventTimeline[newIdx];
      final diff = gameTimeMs - targetTime;

      // 超过 Good 窗口 = Miss
      if (diff > HitConstants.goodWindow) {
        // 这个事件的所有音符都 Miss 了
        state = state.copyWith(
          combo: 0,
          missCount: state.missCount + event.notes.length,
          lastHitGrade: HitGrade.miss,
        );
        newIdx++;
      } else {
        break; // 还没到判定时间
      }
    }

    if (newIdx != state.currentEventIndex) {
      state = state.copyWith(currentEventIndex: newIdx);
    }
  }

  /// 用户按下某个按键位置（由覆盖层 UI 调用）
  void onKeyPress(int row, int col) {
    if (state.status != GameStatus.playing || state.isPaused) return;

    final gameTimeMs = _stopwatch.elapsedMilliseconds;
    final scoreState = ref.read(scoreListProvider);
    final score = scoreState.selectedScore;
    if (score == null) return;

    // 查找最近的匹配音符
    int bestEventIdx = -1;
    int bestTimeDiff = 999999;
    Note? bestNote;

    for (var i = state.currentEventIndex; i < score.events.length; i++) {
      final event = score.events[i];
      if (event.isRest) continue;

      final targetTime = _eventTimeline[i];
      final diff = (gameTimeMs - targetTime).abs();

      // 只在判定窗口内查找
      if (diff > HitConstants.goodWindow) {
        if (gameTimeMs > targetTime) continue; // 已经过了，看下一个
        break; // 还没到，后面的更远
      }

      for (final note in event.notes) {
        if (note.row == row && note.col == col) {
          if (diff < bestTimeDiff) {
            bestTimeDiff = diff;
            bestEventIdx = i;
            bestNote = note;
          }
        }
      }
    }

    if (bestNote == null || bestEventIdx == -1) return;

    // 判定等级
    HitGrade grade;
    int scoreAdd;
    if (bestTimeDiff <= HitConstants.perfectWindow) {
      grade = HitGrade.perfect;
      scoreAdd = HitConstants.perfectScore;
    } else if (bestTimeDiff <= HitConstants.greatWindow) {
      grade = HitGrade.great;
      scoreAdd = HitConstants.greatScore;
    } else {
      grade = HitGrade.good;
      scoreAdd = HitConstants.goodScore;
    }

    // 应用 combo 加成
    final newCombo = state.combo + 1;
    final comboMultiplier = 1.0 + (newCombo - 1) * 0.1; // 每 combo +10%
    final finalScore = (scoreAdd * comboMultiplier).round();

    // 更新计数
    int perfC = state.perfectCount;
    int greatC = state.greatCount;
    int goodC = state.goodCount;
    switch (grade) {
      case HitGrade.perfect:
        perfC++;
        break;
      case HitGrade.great:
        greatC++;
        break;
      case HitGrade.good:
        goodC++;
        break;
      case HitGrade.miss:
        break;
    }

    // 添加命中效果
    final effects = List<HitEffect>.from(state.hitEffects);
    effects.add(HitEffect(
      note: bestNote,
      grade: grade,
      x: 0, // 由 UI 层根据按键位置计算
      y: 0,
    ));

    // 推进事件索引（如果这个事件的所有音符都已命中）
    int newEventIdx = bestEventIdx;
    // 简化处理：任何一个音符命中就推进整个事件
    // （对于和弦，需要更复杂的处理，但这里简化）
    if (bestEventIdx == state.currentEventIndex) {
      newEventIdx = state.currentEventIndex + 1;
    }

    state = state.copyWith(
      score: state.score + finalScore,
      combo: newCombo,
      maxCombo: newCombo > state.maxCombo ? newCombo : state.maxCombo,
      perfectCount: perfC,
      greatCount: greatC,
      goodCount: goodC,
      lastHitGrade: grade,
      hitEffects: effects,
      currentEventIndex: newEventIdx,
    );

    DebugLog.d('按键命中: ${bestNote.name} $grade +$finalScore combo=$newCombo');
  }

  /// 暂停
  void pause() {
    if (state.status != GameStatus.playing) return;
    _stopwatch.stop();
    _gameTimer?.cancel();
    state = state.copyWith(isPaused: true);
  }

  /// 恢复
  void resume() {
    if (state.status != GameStatus.playing || !state.isPaused) return;
    _stopwatch.start();
    state = state.copyWith(isPaused: false);
    _gameTimer = Timer.periodic(const Duration(milliseconds: 16), (_) {
      _updateGameLoop();
    });
  }

  /// 结束游戏
  void _finishGame() {
    _stopwatch.stop();
    _gameTimer?.cancel();

    state = state.copyWith(
      status: GameStatus.finished,
    );

    DebugLog.i('游戏结束: 分数=${state.score} combo=${state.maxCombo} '
        '准确率=${(state.accuracy * 100).toStringAsFixed(1)}%');
  }

  /// 获取游戏结果
  GameResult getResult() {
    final scoreState = ref.read(scoreListProvider);
    final scoreName = scoreState.selectedScore?.name ?? '';

    return GameResult(
      scoreName: scoreName,
      totalNotes: state.totalEvents,
      perfectCount: state.perfectCount,
      greatCount: state.greatCount,
      goodCount: state.goodCount,
      missCount: state.missCount,
      maxCombo: state.maxCombo,
      totalScore: state.score,
      accuracy: state.accuracy,
      hitRecords: [],
      duration: Duration(milliseconds: _stopwatch.elapsedMilliseconds),
    );
  }

  /// 重置为初始状态
  void reset() {
    _stopwatch.stop();
    _stopwatch.reset();
    _gameTimer?.cancel();
    _countdownTimer?.cancel();
    state = const GameState();
  }

  @override
  void dispose() {
    _stopwatch.stop();
    _gameTimer?.cancel();
    _countdownTimer?.cancel();
    super.dispose();
  }
}

/// 游戏引擎 Provider
final gameEngineProvider =
    StateNotifierProvider<GameEngineNotifier, GameState>((ref) {
  return GameEngineNotifier(ref);
});
