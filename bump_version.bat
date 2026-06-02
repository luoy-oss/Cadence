@echo off
REM 版本号更新脚本 (Windows)
REM 用法: bump_version.bat <版本号>
REM 示例: bump_version.bat 1.0.0-alpha.2
REM        bump_version.bat 1.0.0

if "%~1"=="" (
    echo 用法: %0 ^<版本号^>
    echo 示例: %0 1.0.0-alpha.2
    echo       %0 1.0.0
    exit /b 1
)

set VERSION=%~1

REM 去掉前缀v
set VERSION_CLEAN=%VERSION:v=%

REM 提取版本代码（最后一段数字）
for /f "tokens=*" %%a in ('echo %VERSION_CLEAN% ^| powershell -Command "$input -replace '^.*?(\d+)$','$1'"') do set VERSION_CODE=%%a

if "%VERSION_CODE%"=="" set VERSION_CODE=1

echo 版本号: %VERSION_CLEAN%
echo 版本代码: %VERSION_CODE%

REM 更新 pubspec.yaml
powershell -Command "(Get-Content pubspec.yaml) -replace '^version: .*', 'version: %VERSION_CLEAN%+%VERSION_CODE%' | Set-Content pubspec.yaml"

echo.
echo 已更新 pubspec.yaml: version: %VERSION_CLEAN%+%VERSION_CODE%
echo.
echo 下一步:
echo   git add pubspec.yaml
echo   git commit -m "chore: bump version to %VERSION_CLEAN%"
echo   git tag v%VERSION_CLEAN%
echo   git push origin main ^&^& git push origin v%VERSION_CLEAN%
