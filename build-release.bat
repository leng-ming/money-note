@echo off
chcp 65001 >nul
setlocal
set "JAVA_HOME=H:\Android\jdk-17"
set "ANDROID_HOME=H:\Android\Sdk"
set "ANDROID_SDK_ROOT=H:\Android\Sdk"
cd /d "%~dp0"

echo ============================================
echo   编译 正式版 (release)
echo ============================================
call "H:\Android\gradle-8.13\bin\gradle.bat" assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [失败] 编译出错，请把上面的红色错误发给小鲸鱼
    pause
    exit /b 1
)
echo.
echo [成功] 产物: app\build\outputs\apk\release\app-release.apk
for %%F in ("app\build\outputs\apk\release\app-release.apk") do echo        大小: %%~zF 字节
echo.
echo 接下来可以双击 install-to-phone.bat 装到手机
pause
