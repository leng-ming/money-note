@echo off
chcp 65001 >nul
setlocal
set "JAVA_HOME=H:\Android\jdk-17"
set "ANDROID_HOME=H:\Android\Sdk"
set "ANDROID_SDK_ROOT=H:\Android\Sdk"
cd /d "%~dp0"

echo ============================================
echo   编译 调试版 (debug)
echo ============================================
call "H:\Android\gradle-8.13\bin\gradle.bat" assembleDebug --console=plain
if errorlevel 1 (
    echo.
    echo [失败] 编译出错，请把上面的红色错误发给小鲸鱼
    pause
    exit /b 1
)
echo.
echo [成功] 产物: app\build\outputs\apk\debug\app-debug.apk
pause
