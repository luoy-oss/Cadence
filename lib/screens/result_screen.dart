import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/constants/app_colors.dart';
import '../core/constants/app_strings.dart';
import '../models/game_result.dart';
import '../providers/game_engine_provider.dart';
import '../widgets/glass_container.dart';

/// 游戏结算界面
class ResultScreen extends ConsumerWidget {
  const ResultScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final result = ref.read(gameEngineProvider.notifier).getResult();

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppColors.background, Color(0xFF1A1A2E)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                const SizedBox(height: 20),

                // 标题
                const Text(
                  AppStrings.gameResult,
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  result.scoreName,
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 16,
                  ),
                ),
                const SizedBox(height: 32),

                // 评级
                _buildGradeDisplay(result),
                const SizedBox(height: 32),

                // 总分
                _buildScoreDisplay(result),
                const SizedBox(height: 24),

                // 统计卡片
                _buildStatsGrid(result),
                const SizedBox(height: 24),

                // 命中分布
                _buildHitDistribution(result),
                const SizedBox(height: 32),

                // 操作按钮
                _buildActionButtons(context, ref),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildGradeDisplay(GameResult result) {
    final gradeColor = AppColors.forGrade(result.grade);
    return Container(
      width: 100,
      height: 100,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: gradeColor, width: 3),
        boxShadow: [
          BoxShadow(color: gradeColor.withOpacity(0.4), blurRadius: 20),
        ],
      ),
      child: Center(
        child: Text(
          result.grade,
          style: TextStyle(
            color: gradeColor,
            fontSize: 48,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
    );
  }

  Widget _buildScoreDisplay(GameResult result) {
    return GlassContainer(
      borderRadius: 16,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      child: Column(
        children: [
          const Text(
            AppStrings.totalScore,
            style: TextStyle(
              color: AppColors.textTertiary,
              fontSize: 12,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '${result.totalScore}',
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 36,
              fontWeight: FontWeight.bold,
              fontFeatures: [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatsGrid(GameResult result) {
    return Row(
      children: [
        Expanded(child: _buildStatCard(AppStrings.accuracy,
            '${(result.accuracy * 100).toStringAsFixed(1)}%',
            _accuracyColor(result.accuracy))),
        const SizedBox(width: 12),
        Expanded(child: _buildStatCard(AppStrings.maxCombo,
            '${result.maxCombo}', AppColors.hitPerfect)),
        const SizedBox(width: 12),
        Expanded(child: _buildStatCard('总音符',
            '${result.totalNotes}', AppColors.textSecondary)),
      ],
    );
  }

  Widget _buildStatCard(String label, String value, Color color) {
    return GlassContainer(
      borderRadius: 14,
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
      child: Column(
        children: [
          Text(
            label,
            style: const TextStyle(
              color: AppColors.textTertiary,
              fontSize: 11,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHitDistribution(GameResult result) {
    return GlassContainer(
      borderRadius: 14,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '命中分布',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          _buildHitRow('Perfect', result.perfectCount, result.totalHits, AppColors.hitPerfect),
          const SizedBox(height: 8),
          _buildHitRow('Great', result.greatCount, result.totalHits, AppColors.hitGreat),
          const SizedBox(height: 8),
          _buildHitRow('Good', result.goodCount, result.totalHits, AppColors.hitGood),
          const SizedBox(height: 8),
          _buildHitRow('Miss', result.missCount, result.totalHits, AppColors.hitMiss),
        ],
      ),
    );
  }

  Widget _buildHitRow(String label, int count, int total, Color color) {
    final ratio = total > 0 ? count / total : 0.0;
    return Row(
      children: [
        SizedBox(
          width: 60,
          child: Text(
            label,
            style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.w500),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: ratio,
              backgroundColor: AppColors.card,
              valueColor: AlwaysStoppedAnimation(color.withOpacity(0.7)),
              minHeight: 8,
            ),
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(
          width: 36,
          child: Text(
            '$count',
            textAlign: TextAlign.right,
            style: TextStyle(color: color, fontSize: 14, fontWeight: FontWeight.bold),
          ),
        ),
      ],
    );
  }

  Widget _buildActionButtons(BuildContext context, WidgetRef ref) {
    return Row(
      children: [
        Expanded(
          child: GlassButton(
            text: AppStrings.backToHome,
            icon: Icons.home_rounded,
            onPressed: () {
              ref.read(gameEngineProvider.notifier).reset();
              Navigator.popUntil(context, (route) => route.isFirst);
            },
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              gradient: AppColors.gradientPrimary,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: () {
                  ref.read(gameEngineProvider.notifier).reset();
                  Navigator.pushReplacementNamed(context, '/game');
                },
                borderRadius: BorderRadius.circular(12),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.replay_rounded, color: Colors.white, size: 18),
                      SizedBox(width: 8),
                      Text(
                        AppStrings.playAgain,
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Color _accuracyColor(double accuracy) {
    if (accuracy >= 0.95) return AppColors.hitPerfect;
    if (accuracy >= 0.90) return AppColors.hitGreat;
    if (accuracy >= 0.80) return AppColors.hitGood;
    return AppColors.textSecondary;
  }
}
