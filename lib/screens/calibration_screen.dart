import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/constants/app_colors.dart';
import '../core/constants/app_strings.dart';
import '../models/key_position.dart';
import '../painters/grid_overlay_painter.dart';
import '../providers/settings_provider.dart';

class CalibrationScreen extends ConsumerStatefulWidget {
  const CalibrationScreen({super.key});

  @override
  ConsumerState<CalibrationScreen> createState() => _CalibrationScreenState();
}

class _CalibrationScreenState extends ConsumerState<CalibrationScreen> {
  late double _baseX;
  late double _baseY;
  late double _colSpacing;
  late double _rowSpacing;

  @override
  void initState() {
    super.initState();
    final config = ref.read(settingsProvider);
    _baseX = config.baseX;
    _baseY = config.baseY;
    _colSpacing = config.columnSpacing;
    _rowSpacing = config.rowSpacing;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        title: const Text(AppStrings.calibrate,
            style: TextStyle(color: AppColors.textPrimary)),
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        actions: [
          TextButton(
            onPressed: _saveConfig,
            child: const Text(AppStrings.save,
                style: TextStyle(color: AppColors.primary)),
          ),
        ],
      ),
      body: Column(
        children: [
          // 说明文字
          Container(
            padding: const EdgeInsets.all(16),
            color: AppColors.surface,
            child: const Text(
              AppStrings.calibrateHint,
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
            ),
          ),

          // 可视化校准区域
          Expanded(
            child: GestureDetector(
              onPanUpdate: (details) {
                setState(() {
                  _baseX += details.delta.dx;
                  _baseY += details.delta.dy;
                });
              },
              child: CustomPaint(
                painter: GridOverlayPainter(
                  baseX: _baseX,
                  baseY: _baseY,
                  colSpacing: _colSpacing,
                  rowSpacing: _rowSpacing,
                  opacity: 1.0,
                ),
                size: Size.infinite,
              ),
            ),
          ),

          // 控制面板
          Container(
            padding: const EdgeInsets.all(16),
            color: AppColors.surface,
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildInfoChip('X: ${_baseX.round()}'),
                    _buildInfoChip('Y: ${_baseY.round()}'),
                  ],
                ),
                const SizedBox(height: 16),
                _buildAdjustRow(
                  label: AppStrings.rowSpacing,
                  value: _rowSpacing,
                  onChanged: (v) => setState(() => _rowSpacing = v),
                ),
                const SizedBox(height: 8),
                _buildAdjustRow(
                  label: AppStrings.columnSpacing,
                  value: _colSpacing,
                  onChanged: (v) => setState(() => _colSpacing = v),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoChip(String label) {
    return Chip(
      label: Text(label, style: const TextStyle(fontSize: 12, color: AppColors.textPrimary)),
      backgroundColor: AppColors.card,
    );
  }

  Widget _buildAdjustRow({
    required String label,
    required double value,
    required ValueChanged<double> onChanged,
  }) {
    return Row(
      children: [
        SizedBox(width: 60, child: Text(label, style: const TextStyle(color: AppColors.textSecondary))),
        IconButton(
          icon: const Icon(Icons.remove, size: 20, color: AppColors.textTertiary),
          onPressed: () => onChanged((value - 5).clamp(30, 300)),
        ),
        Expanded(
          child: Slider(
            value: value,
            min: 30,
            max: 300,
            divisions: 54,
            activeColor: AppColors.primary,
            inactiveColor: AppColors.card,
            onChanged: onChanged,
          ),
        ),
        IconButton(
          icon: const Icon(Icons.add, size: 20, color: AppColors.textTertiary),
          onPressed: () => onChanged((value + 5).clamp(30, 300)),
        ),
        SizedBox(
          width: 50,
          child: Text('${value.round()}px',
              style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
        ),
      ],
    );
  }

  void _saveConfig() {
    final config = KeyPositionConfig(
      baseX: _baseX,
      baseY: _baseY,
      columnSpacing: _colSpacing,
      rowSpacing: _rowSpacing,
    );
    ref.read(settingsProvider.notifier).updateConfig(config);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('配置已保存'),
        backgroundColor: AppColors.success,
      ),
    );
  }
}
