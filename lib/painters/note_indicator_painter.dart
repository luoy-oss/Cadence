import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../core/constants/app_colors.dart';
import '../models/game_result.dart';

/// osu! 风格音符指示器绘制器
///
/// 在按键位置上绘制从大到小收缩的圆圈，
/// 当圆圈缩小到目标大小时即为最佳点击时机。
class NoteIndicatorPainter extends CustomPainter {
  /// 音符位置列表 {x, y}
  final List<Offset> notePositions;

  /// 每个音符的进度 (0.0 = 刚出现, 1.0 = 到达目标时刻)
  final List<double> progressValues;

  /// 命中效果列表 {x, y, grade, ageMs}
  final List<HitEffectData> hitEffects;

  /// 目标圆的半径
  final double targetRadius;

  /// 起始圆的半径（最大）
  final double startRadius;

  NoteIndicatorPainter({
    required this.notePositions,
    required this.progressValues,
    required this.hitEffects,
    this.targetRadius = 28.0,
    this.startRadius = 72.0,
  }) : assert(notePositions.length == progressValues.length);

  @override
  void paint(Canvas canvas, Size size) {
    _drawNoteIndicators(canvas);
    _drawHitEffects(canvas);
  }

  /// 绘制 osu! 风格的缩小圆圈
  void _drawNoteIndicators(Canvas canvas) {
    for (var i = 0; i < notePositions.length; i++) {
      final center = notePositions[i];
      final progress = progressValues[i].clamp(0.0, 1.0);

      final currentRadius = ui.lerpDouble(startRadius, targetRadius, progress)!;
      final opacity = ui.lerpDouble(0.0, 1.0, progress)!.clamp(0.0, 1.0);

      // 外圈（approach circle）- 从大到小
      final approachPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5
        ..color = Colors.white.withOpacity(opacity * 0.8);
      canvas.drawCircle(center, currentRadius, approachPaint);

      // 目标圆（固定大小，半透明填充）
      final targetFillPaint = Paint()
        ..style = PaintingStyle.fill
        ..color = Colors.white.withOpacity(0.15 + progress * 0.15);
      canvas.drawCircle(center, targetRadius, targetFillPaint);

      // 目标圆边框
      final targetBorderPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.0
        ..color = Colors.white.withOpacity(0.6 + progress * 0.4);
      canvas.drawCircle(center, targetRadius, targetBorderPaint);

      // 中心小点
      final dotPaint = Paint()
        ..style = PaintingStyle.fill
        ..color = Colors.white.withOpacity(0.8);
      canvas.drawCircle(center, 4.0, dotPaint);

      // 接近完美时的发光效果
      if (progress > 0.85) {
        final glowOpacity = ((progress - 0.85) / 0.15).clamp(0.0, 1.0);
        final glowPaint = Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 4.0
          ..color = AppColors.hitPerfect.withOpacity(glowOpacity * 0.6)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);
        canvas.drawCircle(center, targetRadius + 2, glowPaint);
      }
    }
  }

  /// 绘制命中爆炸效果
  void _drawHitEffects(Canvas canvas) {
    for (final effect in hitEffects) {
      final age = effect.ageMs / 500.0; // 500ms 动画周期
      if (age >= 1.0) continue;

      final opacity = (1.0 - age).clamp(0.0, 1.0);
      final expandRadius = targetRadius + age * 40;
      final center = Offset(effect.x, effect.y);

      Color effectColor;
      switch (effect.grade) {
        case HitGrade.perfect:
          effectColor = AppColors.hitPerfect;
          break;
        case HitGrade.great:
          effectColor = AppColors.hitGreat;
          break;
        case HitGrade.good:
          effectColor = AppColors.hitGood;
          break;
        case HitGrade.miss:
          effectColor = AppColors.hitMiss;
          break;
      }

      // 扩散环
      final ringPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = ui.lerpDouble(3.0, 0.5, age)!
        ..color = effectColor.withOpacity(opacity * 0.8);
      canvas.drawCircle(center, expandRadius, ringPaint);

      // 粒子效果（Perfect 额外有粒子）
      if (effect.grade == HitGrade.perfect) {
        final particlePaint = Paint()
          ..style = PaintingStyle.fill
          ..color = effectColor.withOpacity(opacity * 0.6);
        final random = Random(effect.hashCode);
        for (var i = 0; i < 8; i++) {
          final angle = random.nextDouble() * pi * 2;
          final dist = targetRadius + age * (30 + random.nextDouble() * 20);
          final px = center.dx + cos(angle) * dist;
          final py = center.dy + sin(angle) * dist;
          final particleSize = ui.lerpDouble(3.0, 0.5, age)!;
          canvas.drawCircle(Offset(px, py), particleSize, particlePaint);
        }
      }

      // 命中等级文字
      if (age < 0.6) {
        final textOpacity = age < 0.3 ? 1.0 : ((0.6 - age) / 0.3).clamp(0.0, 1.0);
        final textPainter = TextPainter(
          text: TextSpan(
            text: _gradeText(effect.grade),
            style: TextStyle(
              color: effectColor.withOpacity(textOpacity),
              fontSize: 16 - age * 8,
              fontWeight: FontWeight.bold,
            ),
          ),
          textDirection: TextDirection.ltr,
        );
        textPainter.layout();
        textPainter.paint(
          canvas,
          Offset(
            center.dx - textPainter.width / 2,
            center.dy - targetRadius - 24 - age * 15,
          ),
        );
      }
    }
  }

  String _gradeText(HitGrade grade) {
    switch (grade) {
      case HitGrade.perfect:
        return 'Perfect';
      case HitGrade.great:
        return 'Great';
      case HitGrade.good:
        return 'Good';
      case HitGrade.miss:
        return 'Miss';
    }
  }

  @override
  bool shouldRepaint(covariant NoteIndicatorPainter oldDelegate) => true;
}

/// 命中效果数据
class HitEffectData {
  final double x;
  final double y;
  final HitGrade grade;
  final int ageMs;

  const HitEffectData({
    required this.x,
    required this.y,
    required this.grade,
    required this.ageMs,
  });
}
