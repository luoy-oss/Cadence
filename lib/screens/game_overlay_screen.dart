import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/constants/app_colors.dart';
import '../models/game_result.dart';
import '../models/key_position.dart';
import '../painters/note_indicator_painter.dart';
import '../providers/game_engine_provider.dart';
import '../providers/score_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/glass_container.dart';
import '../widgets/score_display.dart';

/// 游戏覆盖层界面 - 全屏透明，显示 osu! 风格的音符指示器
class GameOverlayScreen extends ConsumerStatefulWidget {
  const GameOverlayScreen({super.key});

  @override
  ConsumerState<GameOverlayScreen> createState() => _GameOverlayScreenState();
}

class _GameOverlayScreenState extends ConsumerState<GameOverlayScreen>
    with TickerProviderStateMixin {
  late AnimationController _comboAnimController;
  int _lastCombo = 0;

  @override
  void initState() {
    super.initState();
    _comboAnimController = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );

    // 自动开始游戏
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(gameEngineProvider.notifier).startGame();
    });
  }

  @override
  void dispose() {
    _comboAnimController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final gameState = ref.watch(gameEngineProvider);
    final config = ref.watch(settingsProvider);
    final scoreState = ref.watch(scoreListProvider);

    // Combo 变化动画
    if (gameState.combo != _lastCombo && gameState.combo > 0) {
      _lastCombo = gameState.combo;
      _comboAnimController.forward(from: 0);
    }

    // 游戏结束时跳转结果页
    ref.listen(gameEngineProvider, (prev, next) {
      if (next.status == GameStatus.finished &&
          prev?.status != GameStatus.finished) {
        Navigator.pushReplacementNamed(context, '/result');
      }
    });

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          // 音符指示器层
          _buildNoteIndicators(gameState, config),

          // 顶部信息栏
          Positioned(
            top: MediaQuery.of(context).padding.top + 8,
            left: 16,
            right: 16,
            child: _buildTopBar(gameState, scoreState.selectedScore?.name ?? ''),
          ),

          // Combo 显示
          if (gameState.combo > 1)
            Positioned(
              top: MediaQuery.of(context).padding.top + 80,
              left: 0,
              right: 0,
              child: _buildComboDisplay(gameState.combo),
            ),

          // 命中等级提示
          if (gameState.lastHitGrade != null)
            Positioned(
              top: MediaQuery.of(context).padding.top + 130,
              left: 0,
              right: 0,
              child: _buildGradeIndicator(gameState.lastHitGrade!),
            ),

          // 倒计时
          if (gameState.status == GameStatus.countdown)
            _buildCountdown(gameState.countdownRemaining),

          // 暂停按钮
          Positioned(
            bottom: 32,
            right: 16,
            child: _buildPauseButton(gameState),
          ),

          // 进度条
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: _buildProgressBar(gameState.progress),
          ),
        ],
      ),
    );
  }

  /// 构建音符指示器层
  Widget _buildNoteIndicators(GameState gameState, KeyPositionConfig config) {
    final positions = <Offset>[];
    final progressValues = <double>[];
    final now = gameState.gameTimeMs;

    for (final indicator in gameState.activeIndicators) {
      final x = config.getX(indicator.note.col);
      final y = config.getY(indicator.note.row);
      positions.add(Offset(x, y));

      // 计算进度：0.0 = 刚出现，1.0 = 到达目标时刻
      final timeUntilHit = indicator.targetTimeMs - now;
      final progress = 1.0 - (timeUntilHit / HitConstants.approachTimeMs);
      progressValues.add(progress.clamp(0.0, 1.0));
    }

    // 转换命中效果
    final hitEffectData = gameState.hitEffects.map((e) {
      final ageMs = DateTime.now().difference(e.createdAt).inMilliseconds;
      return HitEffectData(
        x: config.getX(e.note.col),
        y: config.getY(e.note.row),
        grade: e.grade,
        ageMs: ageMs,
      );
    }).toList();

    return CustomPaint(
      painter: NoteIndicatorPainter(
        notePositions: positions,
        progressValues: progressValues,
        hitEffects: hitEffectData,
      ),
      size: Size.infinite,
    );
  }

  /// 顶部信息栏
  Widget _buildTopBar(GameState gameState, String scoreName) {
    return GlassContainer(
      borderRadius: 14,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      blur: 15,
      child: Row(
        children: [
          // 返回按钮
          GestureDetector(
            onTap: () {
              ref.read(gameEngineProvider.notifier).reset();
              Navigator.pop(context);
            },
            child: const Icon(Icons.close_rounded, color: AppColors.textPrimary, size: 22),
          ),
          const SizedBox(width: 12),

          // 曲名
          Expanded(
            child: Text(
              scoreName,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 14,
                fontWeight: FontWeight.w500,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),

          // 分数和准确率
          ScoreDisplay(
            score: gameState.score,
            combo: gameState.combo,
            accuracy: gameState.accuracy,
          ),
        ],
      ),
    );
  }

  /// Combo 显示
  Widget _buildComboDisplay(int combo) {
    return Center(
      child: AnimatedBuilder(
        animation: _comboAnimController,
        builder: (context, child) {
          final scale = 1.0 + _comboAnimController.value * 0.3;
          return Transform.scale(
            scale: scale,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '$combo',
                  style: TextStyle(
                    color: AppColors.hitPerfect.withOpacity(0.9),
                    fontSize: 48 + (combo > 50 ? 8 : 0),
                    fontWeight: FontWeight.bold,
                    shadows: [
                      Shadow(
                        color: AppColors.hitPerfect.withOpacity(0.5),
                        blurRadius: 20,
                      ),
                    ],
                  ),
                ),
                const Text(
                  'COMBO',
                  style: TextStyle(
                    color: AppColors.textTertiary,
                    fontSize: 12,
                    letterSpacing: 3,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  /// 命中等级指示
  Widget _buildGradeIndicator(HitGrade grade) {
    Color color;
    String text;
    switch (grade) {
      case HitGrade.perfect:
        color = AppColors.hitPerfect;
        text = 'PERFECT';
        break;
      case HitGrade.great:
        color = AppColors.hitGreat;
        text = 'GREAT';
        break;
      case HitGrade.good:
        color = AppColors.hitGood;
        text = 'GOOD';
        break;
      case HitGrade.miss:
        color = AppColors.hitMiss;
        text = 'MISS';
        break;
    }

    return Center(
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 150),
        child: Text(
          text,
          key: ValueKey('${grade}_${DateTime.now().millisecondsSinceEpoch ~/ 200}'),
          style: TextStyle(
            color: color.withOpacity(0.8),
            fontSize: 20,
            fontWeight: FontWeight.bold,
            letterSpacing: 4,
          ),
        ),
      ),
    );
  }

  /// 倒计时
  Widget _buildCountdown(int remaining) {
    return Center(
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 300),
        child: Text(
          '$remaining',
          key: ValueKey(remaining),
          style: const TextStyle(
            color: AppColors.textPrimary,
            fontSize: 96,
            fontWeight: FontWeight.bold,
            shadows: [
              Shadow(color: AppColors.primary, blurRadius: 40),
            ],
          ),
        ),
      ),
    );
  }

  /// 暂停按钮
  Widget _buildPauseButton(GameState gameState) {
    return GlassContainer(
      borderRadius: 12,
      padding: const EdgeInsets.all(10),
      blur: 10,
      child: GestureDetector(
        onTap: () {
          final engine = ref.read(gameEngineProvider.notifier);
          if (gameState.isPaused) {
            engine.resume();
          } else {
            engine.pause();
          }
        },
        child: Icon(
          gameState.isPaused
              ? Icons.play_arrow_rounded
              : Icons.pause_rounded,
          color: AppColors.textPrimary,
          size: 24,
        ),
      ),
    );
  }

  /// 进度条
  Widget _buildProgressBar(double progress) {
    return Container(
      height: 3,
      color: AppColors.glassBg,
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: progress.clamp(0.0, 1.0),
        child: Container(
          decoration: const BoxDecoration(
            gradient: AppColors.gradientPrimary,
          ),
        ),
      ),
    );
  }
}
