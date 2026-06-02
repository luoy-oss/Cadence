import 'dart:convert';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';

import '../core/constants/app_colors.dart';
import '../core/constants/app_strings.dart';
import '../providers/score_provider.dart';
import '../providers/settings_provider.dart';
import '../services/overlay_service.dart';
import '../widgets/score_card.dart';
import '../widgets/glass_container.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> with WidgetsBindingObserver {
  bool _hasOverlay = false;
  bool _isOverlayRunning = false;
  bool _callbacksSet = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermissions();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPermissions();
    }
  }

  Future<void> _checkPermissions() async {
    final overlay = await OverlayService.checkPermission();
    final running = await OverlayService.isRunning();
    if (mounted) {
      setState(() {
        _hasOverlay = overlay;
        _isOverlayRunning = running;
      });
      if (running && !_callbacksSet) {
        _setupFloatingCallbacks();
      }
    }
  }

  /// 启动后延迟验证 Service 是否真的在运行
  Future<void> _verifyRunning() async {
    await Future.delayed(const Duration(milliseconds: 800));
    if (!mounted) return;
    final running = await OverlayService.isRunning();
    if (!running && _isOverlayRunning) {
      // Service 没有真正启动，回滚状态
      setState(() {
        _isOverlayRunning = false;
        _callbacksSet = false;
      });
    }
  }

  void _setupFloatingCallbacks() {
    OverlayService.setCallbacks(
      onPlay: () {
        // 悬浮窗点击播放 - 先同步校准配置，再发送游戏数据
        final selected = ref.read(scoreListProvider).selectedScore;
        if (selected == null || selected.events.isEmpty) return;

        // 确保校准配置同步到原生端
        final config = ref.read(settingsProvider);
        OverlayService.sendKeyConfig(config.toJson());

        // 构建音符事件列表
        final notes = <Map<String, dynamic>>[];
        for (final event in selected.events) {
          if (event.isRest) continue;
          for (final note in event.notes) {
            notes.add({
              'row': note.row,
              'col': note.col,
              'timeMs': event.time,
            });
          }
        }

        // 计算总时长
        final lastTime = selected.events.last.time;

        // 发送到原生覆盖层（含倒计时 + 游戏数据）
        OverlayService.startGameWithData(
          notes: notes,
          durationMs: lastTime + 2000,
          countdownSeconds: 3,
        );
      },
      onPause: () {},
      onStop: () {
        OverlayService.stopGame();
      },
      onSelectScore: (id) {
        ref.read(scoreListProvider.notifier).selectScore(id);
      },
      onCalibrationChanged: (baseX, baseY, colSpacing, rowSpacing) {
        ref.read(settingsProvider.notifier).updateConfig(
          ref.read(settingsProvider).copyWith(
            baseX: baseX, baseY: baseY,
            columnSpacing: colSpacing, rowSpacing: rowSpacing,
          ),
        );
      },
      onPanelOpened: () {
        // 面板打开时同步数据到悬浮窗
        _syncDataToFloating();
      },
    );
    _callbacksSet = true;
    _syncDataToFloating();
  }

  void _syncDataToFloating() {
    final scores = ref.read(scoreListProvider).scores;
    OverlayService.sendScoreList(
      scores.map((s) => {'id': s.id, 'name': s.name}).toList(),
    );
    final selectedScore = ref.read(scoreListProvider).selectedScore;
    if (selectedScore != null) {
      OverlayService.updateSelectedScore(selectedScore.name);
    }
    final config = ref.read(settingsProvider);
    if (config.baseX != 0 || config.baseY != 0) {
      OverlayService.sendKeyConfig(config.toJson());
    }
  }

  @override
  Widget build(BuildContext context) {
    final scoreState = ref.watch(scoreListProvider);
    final filteredScores = ref.watch(filteredScoresProvider);

    // 监听乐谱变化，同步到悬浮窗
    ref.listen(scoreListProvider, (prev, next) {
      if (_isOverlayRunning) {
        OverlayService.sendScoreList(
          next.scores.map((s) => {'id': s.id, 'name': s.name}).toList(),
        );
        if (next.selectedScore != null) {
          OverlayService.updateSelectedScore(next.selectedScore!.name);
        }
      }
    });

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
          child: Column(
            children: [
              _buildAppBar(),
              Expanded(
                child: CustomScrollView(
                  slivers: [
                    SliverToBoxAdapter(child: _buildOverlayToggleCard()),
                    if (!_hasOverlay)
                      SliverToBoxAdapter(child: _buildPermissionBanner()),
                    if (_isOverlayRunning)
                      SliverToBoxAdapter(child: _buildInfoCard()),
                    SliverToBoxAdapter(child: _buildSearchBar()),
                    const SliverToBoxAdapter(
                      child: Padding(
                        padding: EdgeInsets.fromLTRB(20, 16, 20, 8),
                        child: Text(
                          AppStrings.scoreList,
                          style: TextStyle(
                            color: AppColors.textPrimary,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                    if (scoreState.isLoading)
                      const SliverFillRemaining(
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (filteredScores.isEmpty)
                      SliverFillRemaining(child: _buildEmptyState())
                    else
                      SliverList(
                        delegate: SliverChildBuilderDelegate(
                          (context, index) {
                            final score = filteredScores[index];
                            final isSelected = score.id == scoreState.selectedId;
                            return Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                              child: ScoreCard(
                                score: score,
                                isSelected: isSelected,
                                onTap: () {
                                  ref.read(scoreListProvider.notifier).selectScore(score.id);
                                  if (_isOverlayRunning) {
                                    OverlayService.updateSelectedScore(score.name);
                                  }
                                },
                                onDelete: () => _confirmDelete(score.id, score.name),
                              ),
                            );
                          },
                          childCount: filteredScores.length,
                        ),
                      ),
                    const SliverToBoxAdapter(child: SizedBox(height: 80)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      floatingActionButton: _buildFab(),
    );
  }

  Widget _buildAppBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              gradient: AppColors.gradientPrimary,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.music_note, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppStrings.appName,
                style: TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1,
                ),
              ),
              Text(
                AppStrings.appSubtitle,
                style: TextStyle(
                  color: AppColors.textTertiary,
                  fontSize: 11,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          const Spacer(),
          if (!_hasOverlay)
            IconButton(
              icon: const Icon(Icons.warning_rounded, color: AppColors.warning),
              onPressed: _showPermissionDialog,
              tooltip: '权限不足',
            ),
        ],
      ),
    );
  }

  Widget _buildOverlayToggleCard() {
    final canToggle = _hasOverlay;
    return GlassContainer(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(
              color: _isOverlayRunning ? AppColors.success : AppColors.textTertiary,
              shape: BoxShape.circle,
              boxShadow: _isOverlayRunning
                  ? [BoxShadow(color: AppColors.success.withOpacity(0.5), blurRadius: 8)]
                  : null,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  AppStrings.overlayControl,
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  canToggle
                      ? (_isOverlayRunning ? AppStrings.overlayRunning : AppStrings.overlayClosed)
                      : AppStrings.overlayNeedPermission,
                  style: const TextStyle(
                    color: AppColors.textTertiary,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          Transform.scale(
            scale: 0.85,
            child: Switch(
              value: _isOverlayRunning,
              activeColor: AppColors.success,
              activeTrackColor: AppColors.success.withOpacity(0.3),
              inactiveThumbColor: AppColors.textTertiary,
              inactiveTrackColor: AppColors.glassBg,
              onChanged: canToggle
                  ? (value) async {
                      if (value) {
                        final started = await OverlayService.start();
                        if (started) {
                          // 乐观更新：先信任 start() 返回值
                          setState(() => _isOverlayRunning = true);
                          _setupFloatingCallbacks();
                          // 延迟验证 Service 是否真正在运行
                          _verifyRunning();
                        }
                      } else {
                        await OverlayService.stop();
                        setState(() {
                          _isOverlayRunning = false;
                          _callbacksSet = false;
                        });
                      }
                    }
                  : null,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionBanner() {
    return GlassContainer(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.all(16),
      color: AppColors.warning.withOpacity(0.1),
      border: Border.all(color: AppColors.warning.withOpacity(0.3)),
      child: InkWell(
        onTap: () => OverlayService.requestPermission(),
        borderRadius: BorderRadius.circular(16),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded, color: AppColors.warning, size: 20),
            const SizedBox(width: 12),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppStrings.needPermission,
                    style: TextStyle(
                      color: AppColors.warning,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Text(
                    '点击前往设置授予悬浮窗权限',
                    style: TextStyle(color: AppColors.textTertiary, fontSize: 12),
                  ),
                ],
              ),
            ),
            const Icon(Icons.arrow_forward_ios_rounded, size: 14, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoCard() {
    return GlassContainer(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: AppColors.primary.withOpacity(0.2),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.info_outline_rounded, color: AppColors.primary, size: 18),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              '悬浮窗已开启，点击悬浮球打开控制面板',
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.glassBg,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.glassBorder),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
            child: TextField(
              style: const TextStyle(color: AppColors.textPrimary),
              decoration: InputDecoration(
                hintText: AppStrings.searchHint,
                hintStyle: const TextStyle(color: AppColors.textTertiary),
                prefixIcon: const Icon(Icons.search_rounded, color: AppColors.textTertiary, size: 20),
                filled: true,
                fillColor: Colors.transparent,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              ),
              onChanged: (value) {
                ref.read(searchQueryProvider.notifier).state = value;
              },
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.music_note_rounded, size: 64, color: AppColors.textTertiary),
          SizedBox(height: 16),
          Text(
            AppStrings.noScores,
            style: TextStyle(color: AppColors.textTertiary, fontSize: 16),
          ),
        ],
      ),
    );
  }

  Widget _buildFab() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // 测试曲目按钮
        Container(
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.glassBorder),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.3),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: FloatingActionButton.small(
            onPressed: _addTestSong,
            backgroundColor: Colors.transparent,
            elevation: 0,
            heroTag: 'test',
            child: const Icon(Icons.science_rounded, color: AppColors.accent, size: 22),
          ),
        ),
        const SizedBox(height: 12),
        // 导入按钮
        Container(
          decoration: BoxDecoration(
            gradient: AppColors.gradientPrimary,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withOpacity(0.4),
                blurRadius: 12,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: FloatingActionButton(
            onPressed: _importScore,
            backgroundColor: Colors.transparent,
            elevation: 0,
            heroTag: 'import',
            child: const Icon(Icons.add_rounded, color: Colors.white, size: 28),
          ),
        ),
      ],
    );
  }

  Future<void> _importScore() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['txt', 'json'],
      );
      if (result != null && result.files.isNotEmpty) {
        final file = result.files.first;
        List<int> bytes;
        if (file.bytes != null) {
          bytes = file.bytes!;
        } else if (file.path != null) {
          bytes = await File(file.path!).readAsBytes();
        } else {
          return;
        }
        final content = _decodeText(bytes);
        final name = file.name.replaceAll(RegExp(r'\.(txt|json)$'), '');
        await ref.read(scoreListProvider.notifier).importScore(name, content);
        _showSnackBar('已导入: $name');
      }
    } catch (e) {
      _showSnackBar('导入失败: $e', isError: true);
    }
  }

  Future<void> _addTestSong() async {
    try {
      await ref.read(scoreListProvider.notifier).addTestSong();
      _showSnackBar('已添加测试曲目：小星星');
    } catch (e) {
      _showSnackBar('添加失败: $e', isError: true);
    }
  }

  void _confirmDelete(String id, String name) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(AppStrings.deleteScore, style: TextStyle(color: AppColors.textPrimary)),
        content: Text('确定要删除「$name」吗？', style: const TextStyle(color: AppColors.textSecondary)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消', style: TextStyle(color: AppColors.textTertiary)),
          ),
          TextButton(
            onPressed: () {
              ref.read(scoreListProvider.notifier).deleteScore(id);
              Navigator.pop(context);
            },
            child: const Text('删除', style: TextStyle(color: AppColors.error)),
          ),
        ],
      ),
    );
  }

  void _showPermissionDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(AppStrings.needPermission, style: TextStyle(color: AppColors.textPrimary)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Cadence 需要悬浮窗权限才能在游戏上层显示：',
                style: TextStyle(color: AppColors.textSecondary)),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.layers_rounded, color: AppColors.primary),
              title: const Text(AppStrings.overlayPermission,
                  style: TextStyle(color: AppColors.textPrimary)),
              subtitle: const Text(AppStrings.overlayPermissionDesc,
                  style: TextStyle(color: AppColors.textTertiary)),
              trailing: const Icon(Icons.arrow_forward_ios_rounded,
                  size: 16, color: AppColors.textTertiary),
              onTap: () {
                OverlayService.requestPermission();
                Navigator.pop(context);
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('关闭', style: TextStyle(color: AppColors.textTertiary)),
          ),
        ],
      ),
    );
  }

  void _showSnackBar(String message, {bool isError = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? AppColors.error : AppColors.success,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  String _decodeText(List<int> bytes) {
    if (bytes.length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE) {
      final codeUnits = <int>[];
      for (var i = 2; i < bytes.length - 1; i += 2) {
        codeUnits.add(bytes[i] | (bytes[i + 1] << 8));
      }
      return String.fromCharCodes(codeUnits);
    }
    if (bytes.length >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF) {
      final codeUnits = <int>[];
      for (var i = 2; i < bytes.length - 1; i += 2) {
        codeUnits.add((bytes[i] << 8) | bytes[i + 1]);
      }
      return String.fromCharCodes(codeUnits);
    }
    return utf8.decode(bytes, allowMalformed: true);
  }
}
