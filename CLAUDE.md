# Cadence 项目指南

## 发布流程

发布新版本前使用 `bump_version`：

```bash
# Linux/Mac
./bump_version.sh 1.0.0-beta.X

# Windows
bump_version.bat 1.0.0-beta.X

# 然后
git add -A
git commit -m "chore: bump version to 1.0.0-beta.X"
git tag v1.0.0-beta.X
git push origin main && git push origin v1.0.0-beta.X
```

## 架构

- Flutter 端负责：数据层（乐谱解析、设置持久化）、主页 UI
- Android 原生端负责：悬浮窗 UI、游戏动效绘制、校准覆盖层
- 通信：`MethodChannel('com.cadence/overlay')` 双向通信
- 所有游戏视觉效果（音符、进度、Combo、倒计时）由 Android 原生 View 绘制
