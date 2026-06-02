import 'package:flutter/material.dart';

import '../core/constants/app_colors.dart';
import '../models/game_result.dart';
import 'glass_container.dart';

/// 游戏中的悬浮分数/Combo 显示组件
class ScoreDisplay extends StatelessWidget {
  final int score;
  final int combo;
  final double accuracy;
  final HitGrade? lastGrade;

  const ScoreDisplay({
    super.key,
    required this.score,
    required this.combo,
    required this.accuracy,
    this.lastGrade,
  });

  @override
  Widget build(BuildContext context) {
    return GlassContainer(
      borderRadius: 14,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      blur: 12,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 分数
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'SCORE',
                style: TextStyle(
                  color: AppColors.textTertiary,
                  fontSize: 9,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Text(
                _formatScore(score),
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  fontFeatures: [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),

          Container(
            width: 1,
            height: 32,
            margin: const EdgeInsets.symmetric(horizontal: 14),
            color: AppColors.glassBorder,
          ),

          // Combo
          Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'COMBO',
                style: TextStyle(
                  color: AppColors.textTertiary,
                  fontSize: 9,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w600,
                ),
              ),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 150),
                child: Text(
                  '$combo',
                  key: ValueKey(combo),
                  style: TextStyle(
                    color: combo > 0 ? AppColors.hitPerfect : AppColors.textTertiary,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  ),
                ),
              ),
            ],
          ),

          Container(
            width: 1,
            height: 32,
            margin: const EdgeInsets.symmetric(horizontal: 14),
            color: AppColors.glassBorder,
          ),

          // 准确率
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'ACCURACY',
                style: TextStyle(
                  color: AppColors.textTertiary,
                  fontSize: 9,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Text(
                '${(accuracy * 100).toStringAsFixed(1)}%',
                style: TextStyle(
                  color: _accuracyColor(accuracy),
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _formatScore(int score) {
    if (score >= 1000000) {
      return '${(score / 1000000).toStringAsFixed(1)}M';
    } else if (score >= 1000) {
      return '${(score / 1000).toStringAsFixed(1)}K';
    }
    return '$score';
  }

  Color _accuracyColor(double accuracy) {
    if (accuracy >= 0.95) return AppColors.hitPerfect;
    if (accuracy >= 0.90) return AppColors.hitGreat;
    if (accuracy >= 0.80) return AppColors.hitGood;
    return AppColors.textSecondary;
  }
}
