import 'package:flutter/material.dart';

import '../core/constants/app_colors.dart';

/// 网格覆盖层绘制器 - 用于校准和游戏时显示按键位置
class GridOverlayPainter extends CustomPainter {
  final double baseX;
  final double baseY;
  final double colSpacing;
  final double rowSpacing;
  final bool showLabels;
  final bool showGrid;
  final double opacity;

  static const _noteNames = [
    ['-1', '-2', '-3', '-4', '-5'],
    ['-6', '-7', '1', '2', '3'],
    ['4', '5', '6', '7', '+1'],
  ];

  GridOverlayPainter({
    required this.baseX,
    required this.baseY,
    required this.colSpacing,
    required this.rowSpacing,
    this.showLabels = true,
    this.showGrid = true,
    this.opacity = 0.5,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;

    final fillPaint = Paint()..style = PaintingStyle.fill;

    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 5; col++) {
        final x = baseX + col * colSpacing;
        final y = baseY + row * rowSpacing;
        final radius = 20.0;

        // 绘制圆形琴键标记
        fillPaint.color = AppColors.primary.withOpacity(0.2 * opacity);
        paint.color = AppColors.primary.withOpacity(0.6 * opacity);

        canvas.drawCircle(Offset(x, y), radius, fillPaint);
        canvas.drawCircle(Offset(x, y), radius, paint);

        // 绘制音符名称
        if (showLabels) {
          final textPainter = TextPainter(
            text: TextSpan(
              text: _noteNames[row][col],
              style: TextStyle(
                color: AppColors.textPrimary.withOpacity(opacity),
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
            textDirection: TextDirection.ltr,
          );
          textPainter.layout();
          textPainter.paint(
            canvas,
            Offset(x - textPainter.width / 2, y - textPainter.height / 2),
          );
        }
      }
    }

    // 绘制网格线
    if (showGrid) {
      paint.color = AppColors.primary.withOpacity(0.15 * opacity);
      paint.strokeWidth = 0.5;

      // 水平线
      for (int row = 0; row < 3; row++) {
        final y = baseY + row * rowSpacing;
        canvas.drawLine(
          Offset(baseX - colSpacing * 0.5, y),
          Offset(baseX + colSpacing * 4.5, y),
          paint,
        );
      }

      // 垂直线
      for (int col = 0; col < 5; col++) {
        final x = baseX + col * colSpacing;
        canvas.drawLine(
          Offset(x, baseY - rowSpacing * 0.5),
          Offset(x, baseY + rowSpacing * 2.5),
          paint,
        );
      }
    }

    // 绘制基准点十字标记
    final crossPaint = Paint()
      ..color = Colors.red.withOpacity(opacity)
      ..strokeWidth = 2;
    canvas.drawLine(
      Offset(baseX - 12, baseY - 12),
      Offset(baseX + 12, baseY + 12),
      crossPaint,
    );
    canvas.drawLine(
      Offset(baseX + 12, baseY - 12),
      Offset(baseX - 12, baseY + 12),
      crossPaint,
    );
  }

  @override
  bool shouldRepaint(covariant GridOverlayPainter oldDelegate) =>
      baseX != oldDelegate.baseX ||
      baseY != oldDelegate.baseY ||
      colSpacing != oldDelegate.colSpacing ||
      rowSpacing != oldDelegate.rowSpacing ||
      opacity != oldDelegate.opacity;
}
