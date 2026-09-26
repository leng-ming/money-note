@echo off
chcp 65001 >nul
setlocal
set "ADB=H:\Android\Sdk\platform-tools\adb.exe"

echo ============================================================
echo                    adb 连接诊断
echo ============================================================
echo.
echo [1/3] 重启 adb 服务 ...
"%ADB%" kill-server >nul 2>&1
timeout /t 2 /nobreak >nul
"%ADB%" start-server >nul 2>&1
timeout /t 3 /nobreak >nul

echo [2/3] 当前能识别的调试设备:
echo ------------------------------------------------------------
"%ADB%" devices -l
echo ------------------------------------------------------------
echo.

echo [3/3] Windows 层面是否认到手机的调试接口:
powershell -NoProfile -Command "Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue | Where-Object { $_.FriendlyName -match 'ADB' } | Select-Object Status,FriendlyName | Format-Table -AutoSize"
echo.

echo ============================================================
echo                    怎么读上面的结果
echo ============================================================
echo.
echo  A) 显示   XXXXXXXX   device
echo     ==^> 完全正常！双击 install-to-phone.bat 就能装
echo.
echo  B) 显示   (no serial number)   offline
echo     ==^> 手机还没完成调试握手。请按顺序做:
echo          1. 解锁手机，保持屏幕亮着
echo          2. 下拉通知栏，点 "USB 正在为此设备充电"
echo             把 USB 用途改成 "传输文件"
echo          3. 这时手机会弹出 "是否允许 USB 调试"，点允许
echo.
echo  C) 显示   XXXXXXXX   unauthorized
echo     ==^> 手机上弹了授权框但你没点。
echo          解锁手机，点 "允许"，建议勾选 "一律允许"
echo.
echo  D) 列表完全是空的，但第 3 步能看到 ADB Interface
echo     ==^> 手机的 USB 调试没真正生效，依次试:
echo          1. 开发者选项里把 "USB 调试" 关掉，再打开
echo          2. 开发者选项里点 "撤销 USB 调试授权"
echo          3. 拔掉数据线，等 5 秒再插回去
echo          4. 换一根确定能传数据的线（很多线只能充电）
echo.
echo  E) 第 3 步也查不到 ADB Interface
echo     ==^> 物理连接问题: 换个 USB 口(优先机箱后面板)，或换线
echo.
echo  F) 上面都试过还不行 —— 换个思路:
echo     手机连上电脑能传文件的话，直接把 APK 复制到手机安装:
echo     C:\Users\LM\Desktop\小鲸鱼记账-v1.0.apk
echo.
echo ============================================================
pause
