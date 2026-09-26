<p align="center">
  <img src="docs/banner.png" alt="省钱工具箱" width="720" />
</p>

<h1 align="center">💰 省钱工具箱 (Money Toolbox)</h1>

<p align="center">
  一款专注「帮你省下每一笔不必要开支」的安卓小工具合集。<br/>
  工具包括<b>停车收费提醒</b>与<b>续费提醒</b> —— 免费时长不浪费,各类缴费不错过。
</p>

<p align="center">
  <a href="https://github.com/andyfanybo/money-toolbox/releases/latest"><img src="https://img.shields.io/github/v/release/andyfanybo/money-toolbox?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=10B981" alt="Release" /></a>
  <a href="https://github.com/andyfanybo/money-toolbox/actions/workflows/release.yml"><img src="https://github.com/andyfanybo/money-toolbox/actions/workflows/release.yml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/%E6%9D%83%E9%99%90-%E4%B8%8D%E8%81%94%E7%BD%91-10B981" alt="无网络权限" />
  <img src="https://img.shields.io/badge/UI-Material%203-10B981" alt="Material 3" />
</p>

---

## ✨ 停车收费提醒:它帮你省什么钱?

绝大多数停车场都是这么收费的:

- 前 N 分钟(常见 30~60 分钟)**免费**;
- 超出后**按小时计费**,而且「超 1 分钟按整小时收」—— 停 1 小时零 1 分钟,收 2 小时的钱;
- 缴费后通常还有 **10~15 分钟**的出场宽限。

这两个规则背后全是省钱空间,App 帮你把每一分都抠出来:

| 提醒 | 触发时机 | 帮你省 |
|---|---|---|
| 🅿️ **首次提醒(免费到期)** | 免费时长截止前 X 分钟 | 及时驶出,一分钱不花 |
| 💰 **收费提醒** | 每个计费周期截止前 B 分钟(B = 缴费缓冲,默认 2 分钟) | 不被「超 1 分钟多付一小时」;缴费后另有 G 分钟出场时间 |
| ⏰ **宽限结束提醒** | 缴费后 G 分钟(出场宽限到期) | 若仍未驶出,从该时刻重新按周期计费前心中有数 |

**举例**:免费 60 分钟、6 元/小时、宽限 15 分钟、缴费缓冲 2 分钟,08:00 入场:

| 时间 | 状态 |
|---|---|
| 08:50 | 🅿️ **首次提醒**:免费 09:00 结束,及时驶出不花钱 |
| 09:58 | 💰 **首次收费提醒**:10:00 周期截止前缴费,只付 **¥6**;缴费后还有 15 分钟出场时间(到 10:13) |
| 10:13 | ⏰ 若仍未驶出,从现在起按下一周期计费 |
| 10:58 | 💰 第二次收费提醒(11:00 周期截止前),以此类推 |

在通知或弹窗上点「**已缴费**」,App 会把出场宽限结束时刻作为新的周期锚点,后续提醒自动顺延。

### 功能细节

- ⏱ **入场时间免输入**:默认当前时间(精确到分钟),支持 ±5 分钟微调、自定义修改日期时间(过夜停车场景)
- 🔢 **规则全可配**:免费时长(5/10/15/30 分钟或自定义,填 0 表示无免费)、计费单元(15/30/60 分钟或自定义)、收费单价、缴费与出场宽限、缴费缓冲(1/2/3/5 分钟)、提前提醒量
- 🔔 **三种提醒方式**:「通知」/「闹钟和提醒」(锁屏全屏亮起并响铃,像闹钟一样)/「弹出窗口提醒」(在其他应用上方弹窗,像来电一样),自由切换,带权限引导
- 📳 **后台准时提醒**:基于系统精确闹钟(AlarmManager),不常驻后台、不费电,手机重启后自动恢复提醒
- 📊 **实时面板**:免费剩余倒计时、当前计费周期、预计费用、省钱窗口高亮、提醒时间轴
- 🔒 **纯本地**:无网络权限,不收集任何数据;配置自动记忆,下次直接开停
- 🎨 **现代 UI**:Jetpack Compose + Material 3,深色模式自适应,Android 12+ 支持主题图标

## 🔁 续费提醒

在首页打开「续费提醒」,添加停车月租、影音会员或其他账单。填写名称、下次到期日、续费周期(单次/每周/每月/每季/每年)、可选金额和提前提醒天数(0–365 天)。应用会在提前日期和到期当天的 09:00 安排本地通知;提前日期已过时仍保留到期当天提醒。

到期后项目会显示逾期状态。确认实际缴费后点「已续费」,周期性项目才会进入下一个到期日;单次项目变为已完成。月底和闰日的续费日期按最初日期计算,例如 1 月 31 日的月费在 2 月到期于 28 日,3 月恢复到 31 日。数据仅保存在本机,应用不会代扣款或自动续费。

手机重启或系统时区变化后会重新安排通知。请允许通知权限;若未授予精确闹钟权限,系统可能延迟当天的提醒时间。

## 📄 PDF 阅读与转图片

- 在首页打开「PDF 工具」阅读本地文件，支持翻页、跳页、手势缩放、文字搜索和记住上次页码。文件管理器中点开 PDF 时也可以选择「省钱工具箱 · PDF 阅读」。
- 可选择单个或多个 PDF，将每一页离线导出为 PNG 到指定文件夹；清晰度可选 1×、2×、3×、4×，阅读时也能单独导出当前页。
- 文件通过系统文件选择器授权，转换过程逐页渲染并显示进度，可取消。PDF 只在手机本地处理，不上传。

## 📲 下载安装

前往 [**GitHub Releases**](https://github.com/andyfanybo/money-toolbox/releases/latest) 下载对应版本:

| 文件 | 适用设备 |
|---|---|
| `MoneyToolbox-x.y.z-arm64-v8a.apk` | ARMv8 / 64 位 Android 手机 |

> 安装时系统可能提示「未知来源应用」,允许即可。同一签名的旧版可以覆盖升级。

**系统要求**:Android 8.0(API 26)及以上。

**建议**:首次使用时允许通知权限,并在系统设置中给 App 开启「闹钟和提醒」权限(Android 12+),保证锁屏状态下提醒准时弹出。

## 🚀 快速上手

1. 打开 App,进入「停车收费提醒」;
2. 入场时间默认就是现在,直接下一步;
3. 按停车场价目牌设置:免费时长、每小时单价、出场宽限;
4. 点「开始停车提醒」—— 然后该干嘛干嘛去,到点手机会叫你。

## 🏗 使用 GitHub Actions 云编译

仓库已配置好 CI(`.github/workflows/release.yml`),**无需本地安装任何 Android 开发环境**:

| 操作 | 结果 |
|---|---|
| 推送到 `main` 分支或手动运行 | 运行单元测试 + 编译 ARMv8 APK，直接创建 GitHub 预发布版 |
| 推送 `v*` 标签(如 `v1.2.0`) | 同上,并创建 GitHub 正式 Release |

发布新版本只需两步:

```bash
git tag v1.2.0
git push origin v1.2.0
```

正式版版本名取自 tag;普通构建显示 `1.2.0-dev.<运行序号>.<尝试序号>`。每次 CI 构建的内部 `versionCode` 随运行序号递增,便于直接覆盖安装旧构建;Release 说明自动生成。本地构建默认显示 `1.2.0`。

<details>
<summary>本地构建(可选)</summary>

```bash
# 需要 JDK 17 和 Android SDK
./gradlew :app:testDebugUnitTest :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

</details>

## 🔑 签名配置

仓库内置了一枚**便捷签名密钥** `keystore/moneybox.keystore`(PKCS12,密码 `moneybox2026`,别名 `moneybox`),保证 CI 每次构建产出的 APK 签名一致、可直接覆盖安装 —— 对个人应用开箱即用。

> ⚠️ 公开仓库 + 内置密钥意味着任何人都能用同样的签名构建 APK。个人自用无妨;若有更高要求,改用私密密钥:
>
> 1. 本地生成自己的密钥:`keytool -genkeypair -v -keystore my.keystore -storetype PKCS12 -alias myalias -keyalg RSA -keysize 2048 -validity 10950`
> 2. Base64 编码:`base64 -w0 my.keystore`(Windows Git Bash 下可用 `base64 my.keystore`)
> 3. 在仓库 **Settings → Secrets and variables → Actions** 配置 4 个 Secrets:`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
> 4. CI 检测到 Secrets 后会自动优先使用你的密钥

## 📁 项目结构

```
money-toolbox/
├── .github/workflows/release.yml   # CI: 测试 + 双 ABI 构建 + 发布
├── app/src/main/java/com/fan/moneytoolbox/
│   ├── data/ParkingModels.kt       # 计费与提醒时间算法(纯函数,含单元测试)
│   ├── data/SettingsRepository.kt  # 停车配置与会话持久化
│   ├── data/RenewalModels.kt       # 续费周期与日期计算
│   ├── data/RenewalRepository.kt   # 续费项目本地持久化
│   ├── notify/ReminderScheduler.kt # 精确闹钟调度(单闹钟滚动排程)
│   ├── notify/AlarmReceiver.kt     # 闹钟触发 → 通知;支持通知栏直接结束停车
│   ├── notify/BootReceiver.kt      # 开机恢复提醒
│   ├── notify/Notifier.kt          # 通知渠道与文案
│   ├── ui/HomeScreen.kt            # 工具箱首页
│   ├── ui/RenewalScreen.kt         # 续费项目列表与编辑
│   ├── ui/ParkingScreen.kt         # 停车提醒: 设置表单 + 实时面板
│   └── ui/theme/Theme.kt           # Material 3 主题(翡翠绿品牌色)
├── app/src/test/                   # ParkingMathTest: 21 个计费与提醒边界用例
├── keystore/moneybox.keystore      # 便捷签名(可换成 Secrets 私密签名)
└── tools/gen_icons.py              # 图标生成脚本(Pillow)
```

## 🧮 提醒时间是怎么算的?

设入场时刻 `T`,免费时长 `F`,计费单元 `U`,缴费缓冲 `B`(默认 2 分钟),出场宽限 `G`,提前提醒量 `X`:

- **首次提醒(免费到期)**:通常为 `T + F − X`;若 `X ≥ F`,则在免费结束前 1 分钟提醒(免费时长仅 1 分钟时提前 30 秒)
- **第 k 次收费提醒**:`锚点 + k·U − B`(未缴费时锚点 = `T + F`)
- **标记已缴费后**:出场宽限结束时刻 `缴费时刻 + G` 会先提醒一次,之后的收费提醒以它为新周期锚点(对应「缴费后可再停 G 分钟,超时重新计费」的真实规则)

全部逻辑为纯函数实现,见 [`ParkingModels.kt`](app/src/main/java/com/fan/moneytoolbox/data/ParkingModels.kt) 与 21 个单元测试用例。

## 🗺 路线图

- [ ] 更多省钱工具:加油优惠提醒、外卖满减计算器
- [ ] 停车记录与「累计帮你省了多少」统计
- [ ] 桌面小组件(实时倒计时)
- [ ] 阶梯计费(首小时后递增)支持

欢迎提 Issue / PR。新增一个工具只需要在首页工具列表里加一张卡片,并实现对应页面。

## 开源许可

本仓库原创代码仍按 [MIT](LICENSE) 授权。PDF 功能使用 Artifex MuPDF，适用 [GNU AGPL v3](LICENSE-MUPDF-AGPL-3.0.txt)；包含 MuPDF 的 APK 作为组合程序按 AGPL v3 条件发布，完整应用源码就在本仓库。MuPDF 的版权归 Artifex Software, Inc. 所有。
