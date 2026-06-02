import 'dart:convert';

import '../../models/note.dart';
import '../../models/score.dart';
import 'debug_log.dart';

/// 琴谱解析器 - 支持 JSON 格式
///
/// JSON 格式：
/// ```json
/// [{
///   "name": "曲目名",
///   "bpm": 500,
///   "songNotes": [
///     {"time": 1440, "key": "1Key5"},
///     {"time": 1920, "key": "1Key0"}
///   ]
/// }]
/// ```
///
/// Key 映射：
/// - Key0-4 = -1, -2, -3, -4, -5
/// - Key5-9 = -6, -7, 1, 2, 3
/// - Key10-14 = 4, 5, 6, 7, +1
class ScoreParser {
  // key 编号到音符名称的映射
  static const List<String> _keyToNote = [
    '-1', '-2', '-3', '-4', '-5',  // Key0-4
    '-6', '-7', '1', '2', '3',     // Key5-9
    '4', '5', '6', '7', '+1',      // Key10-14
  ];

  /// 解析琴谱文本为事件列表
  static List<ScoreEvent> parse(String text) {
    DebugLog.divider('ScoreParser.parse');
    DebugLog.d('原始文本长度: ${text.length} 字符');

    // 检测 BOM
    if (text.isNotEmpty && text.codeUnitAt(0) == 0xFEFF) {
      DebugLog.w('检测到 BOM 头 (U+FEFF)，将在解析前移除');
    }

    // 尝试解析 JSON 格式
    if (text.trimLeft().startsWith('[') || text.trimLeft().startsWith('{')) {
      DebugLog.d('检测到 JSON 格式，开始解析');
      return _parseJson(text);
    }

    DebugLog.w('未识别的文本格式（非 JSON），返回空事件列表');
    return [];
  }

  /// 解析 JSON 格式琴谱
  static List<ScoreEvent> _parseJson(String text) {
    final events = <ScoreEvent>[];

    try {
      String cleanText = text.trim();

      // 移除 BOM (U+FEFF)
      cleanText = cleanText.replaceAll('﻿', '');

      // 清理控制字符
      cleanText = cleanText.replaceAll(RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]'), '');
      cleanText = cleanText.replaceAll('　', ' ');
      cleanText = cleanText.replaceAll(
        RegExp(r'[​‌‍‎‏⁠⁡⁢⁣⁤]'),
        '',
      );

      final dynamic jsonData = jsonDecode(cleanText);

      List<dynamic> songList;
      if (jsonData is List) {
        songList = jsonData;
      } else if (jsonData is Map) {
        songList = [jsonData];
      } else {
        return events;
      }

      if (songList.isEmpty) return events;

      // 取第一首歌
      final song = songList[0] as Map<String, dynamic>;
      final songNotes = song['songNotes'] as List<dynamic>?;
      if (songNotes == null || songNotes.isEmpty) return events;

      // 按时间分组，相同 time 的音符合并为和弦
      final Map<int, List<String>> timeGroups = {};
      for (final note in songNotes) {
        final noteMap = note as Map<String, dynamic>;
        final time = (noteMap['time'] as num?)?.toInt() ?? 0;
        final key = noteMap['key'] as String?;
        if (key == null) continue;
        timeGroups.putIfAbsent(time, () => []).add(key);
      }

      final sortedTimes = timeGroups.keys.toList()..sort();

      // 转换为事件列表
      for (final time in sortedTimes) {
        final keys = timeGroups[time]!;
        final notes = <Note>[];

        for (final key in keys) {
          final noteObj = _parseKey(key);
          if (noteObj != null) {
            notes.add(noteObj);
          }
        }

        if (notes.isNotEmpty) {
          events.add(ScoreEvent.note(notes, time: time));
        }
      }
    } catch (e, stackTrace) {
      DebugLog.e('JSON 解析失败', e, stackTrace);
    }

    return events;
  }

  /// 解析 key 字符串为音符
  static Note? _parseKey(String key) {
    final match = RegExp(r'Key(\d+)').firstMatch(key);
    if (match == null) return null;

    final keyIndex = int.tryParse(match.group(1)!);
    if (keyIndex == null || keyIndex < 0 || keyIndex >= _keyToNote.length) {
      return null;
    }

    return SkyNotes.findByName(_keyToNote[keyIndex]);
  }

  /// 从原始文本创建完整的Score对象
  static Score createScore({
    required String id,
    required String name,
    required String rawText,
  }) {
    final events = parse(rawText);

    String scoreName = name;
    int bpm = 500;
    try {
      String cleanText = rawText.trim().replaceAll('﻿', '');
      final jsonData = jsonDecode(cleanText);
      if (jsonData is List && jsonData.isNotEmpty) {
        final song = jsonData[0];
        if (scoreName.isEmpty) {
          scoreName = song['name']?.toString() ?? name;
        }
        bpm = (song['bpm'] as num?)?.toInt() ?? 500;
      }
    } catch (_) {}

    return Score(
      id: id,
      name: scoreName,
      rawText: rawText,
      events: events,
      bpm: bpm,
    );
  }
}
