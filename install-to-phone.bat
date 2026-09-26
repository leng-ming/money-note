@echo off
chcp 65001 >nul
setlocal
set "ADB=H:\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\release\app-release.apk"

if not exist "%APK%" (
    echo [错误] 还没编译出正式版 APK
    echo        请先双击 build-release.bat
    pause
    exit /b 1
)

echo ============================================
echo   连接状态
echo ============================================
"%ADB%" devices -l
echo.
echo 如果上面没有显示你的手机(状态为 device)，请检查:
echo   1. 手机用 USB 线连着电脑
echo   2. 开发者选项里的 "USB 调试" 已打开
echo   3. 手机屏幕上弹出的 "允许 USB 调试" 点了允许
echo.
pause

echo.
echo ============================================
echo   安装中...
echo ============================================
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo [失败] 安装失败。常见原因:
    echo   - 手机没授权 USB 调试
    echo   - 手机上已有同包名但签名不同的版本，需要先卸载
    pause
    exit /b 1
)
echo.
echo [成功] 已装到手机，去桌面上找 "小鲸鱼记账"
pause
