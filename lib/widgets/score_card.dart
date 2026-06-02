import 'package:flutter/material.dart';

import '../core/constants/app_colors.dart';
import '../models/score.dart';
import 'glass_container.dart';

/// 乐谱卡片组件
class ScoreCard extends StatelessWidget {
  final Score score;
  final bool isSelected;
  final VoidCallback? onTap;
  final VoidCallback? onDelete;

  const ScoreCard({
    super.key,
    required this.score,
    this.isSelected = false,
    this.onTap,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return GlassContainer(
      borderRadius: 14,
      border: Border.all(
        color: isSelected
            ? AppColors.primary.withOpacity(0.5)
            : AppColors.glassBorder,
        width: isSelected ? 1.5 : 1,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                // 音符图标
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    gradient: isSelected
                        ? AppColors.gradientPrimary
                        : LinearGradient(
                            colors: [
                              AppColors.surface,
                              AppColors.card,
                            ],
                          ),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    Icons.music_note_rounded,
                    color: isSelected ? Colors.white : AppColors.textTertiary,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),

                // 乐谱信息
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        score.name,
                        style: TextStyle(
                          color: isSelected
                              ? AppColors.textPrimary
                              : AppColors.textSecondary,
                          fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                          fontSize: 15,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '${score.noteCount} 个音符 · ${score.bpm} BPM',
                        style: const TextStyle(
                          color: AppColors.textTertiary,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),

                // 选中指示
                if (isSelected)
                  Container(
                    width: 24,
                    height: 24,
                    decoration: BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.check,
                      color: Colors.white,
                      size: 16,
                    ),
                  ),

                // 删除按钮
                if (onDelete != null)
                  IconButton(
                    icon: const Icon(
                      Icons.delete_outline_rounded,
                      color: AppColors.textTertiary,
                      size: 20,
                    ),
                    onPressed: onDelete,
                    tooltip: '删除',
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
