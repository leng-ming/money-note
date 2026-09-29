# 小鲸鱼记账

一个跑在安卓手机上、**完全离线**的记账 App。

所有数据存在手机本地的 SQLite 文件里，App **没有申请任何网络权限**——所以在飞行模式、地铁隧道、手机欠费断网的时候，记账、查账、看报表全都照常工作。不会有"转圈等服务器"这回事。


## 一、已经做好的功能

| 模块 | 说明 |
|---|---|
| 记一笔 | 支出/收入、自研数字键盘、二级分类、**自动带出该分类上次用的账户**、日期与备注 |
| 账单明细 | **年 / 月 / 日 三种粒度**、按粒度分组、每组小计、底部大卡片、点击改、长按删 |
| 图表报表 | 环形占比图 + 趋势柱状图（年看 12 个月 / 月看每天）+ 分类排行，全部 Canvas 手绘 |
| 预算 | 月度总预算 + 分类预算、进度条、接近超支变橙、超支变红 |
| 周期账单 | 房租/话费/订阅这类固定账，设一次，每次打开 App 自动补记到期账单 |
| 搜索筛选 | 关键词 + 时间范围 + 收支类型 + 分类 + 账户，多维组合筛选 |
| 账户管理 | 现金/微信/支付宝/银行卡/信用卡，余额实时计算 |
| 分类管理 | **两级分类**（餐饮 → 奶茶/咖啡/零食…，预置 41 个细分）、16 种图标、12 种颜色 |
| 备份恢复 | 导出完整 JSON 备份、导出 Excel 可读的 CSV、从备份恢复 |

---

## 二、开发环境（本机已经装好了）

这套工具链是从零装起来的，路径如下：

| 组件 | 版本 | 路径 |
|---|---|---|
| JDK | Temurin 17.0.20.1 | `H:\Android\jdk-17` |
| Android SDK | cmdline-tools 12.0 | `H:\Android\Sdk` |
| ├ platform-tools | adb 37.0.1 | `H:\Android\Sdk\platform-tools` |
| ├ 编译用平台 | android-35 | `H:\Android\Sdk\platforms\android-35` |
| └ 构建工具 | build-tools 35.0.0 | `H:\Android\Sdk\build-tools\35.0.0` |
| Gradle | 8.13 | `H:\Android\gradle-8.13` |
| 项目源码 | — | `H:\python\money-note` |

> **为什么单独装 JDK 17？** 系统里原本有 JDK 25，但 AGP 8.x 对 JDK 25 太新会报错。
> 项目里 `gradle.properties` 用 `org.gradle.java.home` 锁定了 JDK 17，不改系统环境变量。

### 依赖下载走的镜像

国内直连 Maven Central / Google 会非常慢，所以 `settings.gradle.kts` 里配了阿里云镜像：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

实测 `dl.google.com`（SDK 官方源）国内直连能跑到 12 MB/s，所以 SDK 是从官方源装的，没走镜像。

---

## 三、日常使用：一键脚本

不用记命令，直接双击项目根目录里的这三个 `.bat`：

| 脚本 | 作用 |
|---|---|
| `build-debug.bat` | 编译调试版（快，用于自己测试） |
| `build-release.bat` | 编译正式版 APK（用于装到手机） |
| `install-to-phone.bat` | 把正式版直接装到 USB 连接着的手机 |

或者手动敲命令：

```powershell
$env:JAVA_HOME='H:\Android\jdk-17'
cd H:\python\money-note
& 'H:\Android\gradle-8.13\bin\gradle.bat' assembleRelease
```

产物位置：
- 正式版：`app\build\outputs\apk\release\app-release.apk`
- 调试版：`app\build\outputs\apk\debug\app-debug.apk`

安装到手机：

```powershell
& 'H:\Android\Sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\release\app-release.apk
```

---

## 四、代码结构

```
app/src/main/java/com/local/moneynote/
├── MainActivity.kt              App 入口、底部导航、路由
├── AppViewModel.kt              全局状态，把 Room 的 Flow 转成 Compose 的 State
├── core/
│   ├── Util.kt                  金额(分↔元)、日期区间、周期推进
│   └── Backup.kt                JSON 备份 / CSV 导出 / 恢复解析
├── data/
│   ├── Entities.kt              5 张表的实体 + 枚举转换器
│   ├── Daos.kt                  查询接口（含各类统计聚合 SQL）
│   ├── AppDatabase.kt           Room 数据库 + 首次建库的默认账户/分类
│   └── MoneyRepository.kt       唯一的本地数据出入口
└── ui/
    ├── Icons.kt                 分类图标映射、颜色解析
    ├── theme/Theme.kt           配色
    ├── components/              环形图、柱状图、账单行、通用卡片
    └── screens/                 7 个页面
```

---

## 五、几个刻意的设计决定

这些是踩过坑之后的选择，改代码时建议保留：

1. **金额一律用 `Long` 存"分"**，绝不用 `Float`/`Double` 存钱。
   浮点数算钱会出现 `0.1 + 0.2 = 0.30000000000000004` 这类问题，账目对不上。

2. **账户余额不落库**，永远由「初始余额 + 流水汇总」实时算出来。
   如果存一个余额字段，一旦某次删账/改账没同步好，余额就和账单永久对不上了。

3. **时间区间一律「左闭右开」** `[start, end)`。
   查 9 月是 `9月1日00:00 <= t < 10月1日00:00`，避免"当天最后一毫秒算不算"的边界 bug。

4. **周期账单补记有 guard 上限**。
   万一某条规则的"下次时间"因为数据异常停在很久以前，没有 `guard` 会一次性生成几万笔把 App 卡死。

5. **图表全部手绘 Canvas**。
   引入 MPAndroidChart 这类库要多几 MB 体积，还要处理它的 View 互操作，不划算。

6. **首次建库用原始 SQL 而不是 DAO**。
   Room 的 `onCreate` 回调里调 DAO 会触发数据库重复打开而卡死，这是个经典陷阱。

7. **凡是碰 `clearAllTables()` 的地方，必须 `withContext(Dispatchers.IO)`**。
   `clearAllTables()` 是阻塞方法，Room 内部有 `assertNotMainThread()`，在主线程调用会抛
   `IllegalStateException`。而 Compose 的 `rememberCoroutineScope()` 默认就是主线程调度器，
   所以极容易踩中。后果是「点了恢复但数据没变」——异常被 `runCatching` 吞掉变成一句 Toast，
   **静默失效，最难排查**。这个 bug 单元测试抓不到（不涉及 Room 与线程），
   是靠真机上「先删数据、再恢复」的端到端验证才暴露出来的。

8. **文件 IO 也要 `withContext(Dispatchers.IO)`**。
   `rememberCoroutineScope()` 是主线程调度器，直接在里面读写文件，账单多了以后会卡界面甚至 ANR。
   导出/导入备份属于这一类。

---

## 六、数据备份（重要）

数据只在这一台手机上，**手机丢了/刷机了就没了**。所以：

> 进入「我的」→「数据与备份」→「导出完整备份」。

导出的 `.json` 文件建议存到手机之外（电脑、网盘、微信传给自己）。
换手机时用「从备份恢复」一键还原。

---

## 七、装不上？按这个排查

双击 `诊断adb连接.bat`，它会打印设备状态并直接告诉你该怎么做。速查表：

| `adb devices` 显示 | 含义 | 怎么做 |
|---|---|---|
| `XXXXXXXX  device` | 正常 | 双击 `install-to-phone.bat` |
| `(no serial number)  offline` | 调试握手没完成 | **解锁手机 → 下拉通知栏 → 点「USB 正在为此设备充电」→ 改成「传输文件」** → 弹出的授权框点允许 |
| `XXXXXXXX  unauthorized` | 授权框没点 | 解锁手机，点「允许」（勾选「一律允许」） |
| 完全空白 | 手机的 USB 调试没生效 | 开发者选项里关掉再打开「USB 调试」；再点「撤销 USB 调试授权」；拔插数据线 |
| 空白且查不到 ADB Interface | 物理连接问题 | 换 USB 口（优先机箱后面板）、换数据线（很多线只能充电不能传数据） |

**兜底方案**：如果 adb 怎么都连不上，直接把 `C:\Users\LM\Desktop\小鲸鱼记账-v1.1.apk`
发到手机上（微信/QQ 传给自己）点开安装，效果完全一样。

---

## 八、App 信息

- 包名：`com.local.moneynote`
- 版本：**1.1**（versionCode 2）
- 最低支持：Android 8.0（API 26）
- 目标版本：Android 15（API 35）
- 权限：**无**（不需要网络、存储、定位任何权限；导出文件走系统文件选择器）

### 版本历史

**v1.1** —— 根据实际使用两天的反馈改进：

- 明细/图表新增 **年 / 月 / 日** 三种浏览粒度（原来只能按月）
- 记账时**自动带出「该分类上次使用的账户」**，不用每笔重选
- 分类支持**二级细分**，预置 41 个（餐饮 9、交通 6、购物 5、居住 5、娱乐 5、
  通讯 2、医疗 3、教育 3、人情 3、理财 3）
- 修复键盘上「保存」和「完成」两个按钮功能重复的问题
- 数据库 v1 → v2 无损迁移，老数据一条不丢

**v1.0** —— 首个版本：记一笔、明细、图表、预算、周期账单、搜索、账户分类管理、备份恢复
