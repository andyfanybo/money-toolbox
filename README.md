<p align="center">
  <img src="docs/banner.png" alt="省钱工具箱" width="720" />
</p>

<h1 align="center">💰 省钱工具箱 (Money Toolbox)</h1>

<p align="center">
  一款专注「帮你省下每一笔不必要开支」的安卓小工具合集。<br/>
  第一个工具:<b>停车收费提醒</b> —— 免费时长不浪费,整点计费不多付。
</p>

<p align="center">
  <a href="https://github.com/YOUR_USERNAME/money-toolbox/releases/latest"><img src="https://img.shields.io/github/v/release/YOUR_USERNAME/money-toolbox?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=10B981" alt="Release" /></a>
  <a href="https://github.com/YOUR_USERNAME/money-toolbox/actions/workflows/release.yml"><img src="https://github.com/YOUR_USERNAME/money-toolbox/actions/workflows/release.yml/badge.svg" alt="CI" /></a>
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
| 🅿️ **免费到期提醒** | 免费时长到期前 X 分钟(默认 10 分钟) | 及时驶出,一分钱不花 |
| 💰 **省钱缴费提醒** | 每个计费周期截止前 =「计费周期 − 出场宽限」 | 不被「超 1 分钟多付一小时」 |

**举例**:免费 60 分钟、6 元/小时、宽限 15 分钟,08:00 入场:

| 时间 | 状态 |
|---|---|
| 09:40 | 🅿️ 免费还剩 20 分钟?不,免费 09:00 已结束 —— 09:00 前驶出则免费 |
| 09:45 | 💰 **第 1 次省钱提醒**:现在缴费并驶出(10:00 前),只付 **¥6** |
| 10:01 之后 | 停到 10:01 出场就是 1 小时零 1 分钟 → 被收 **¥12** |

省钱提醒若被忽略(你确实还想停),App 会在下个周期截止前(10:45、11:45……)**继续提醒**,以此类推。

### 功能细节

- ⏱ **入场时间免输入**:默认当前时间(精确到分钟),支持 ±5 分钟微调、精确改日期时间(过夜停车场景)
- 🔢 **规则全可配**:免费时长(含「无免费」)、计费单元(半小时/小时)、单价、出场宽限、提前提醒量
- 📳 **后台准时提醒**:基于系统精确闹钟(AlarmManager),不常驻后台、不费电,手机重启后自动恢复提醒
- 📊 **实时面板**:免费剩余倒计时、当前计费周期、预计费用、省钱窗口高亮、提醒时间轴
- 🔒 **纯本地**:无网络权限,不收集任何数据;配置自动记忆,下次直接开停
- 🎨 **现代 UI**:Jetpack Compose + Material 3,深色模式自适应,Android 12+ 支持主题图标

## 📲 下载安装

前往 [**GitHub Releases**](https://github.com/YOUR_USERNAME/money-toolbox/releases/latest) 下载对应版本:

| 文件 | 适用设备 |
|---|---|
| `MoneyToolbox-x.y.z-arm64-v8a.apk` | **推荐**,2016 年后绝大多数手机(64 位) |
| `MoneyToolbox-x.y.z-armeabi-v7a.apk` | 早期 32 位设备 |

> 安装时系统可能提示「未知来源应用」,允许即可。两个包签名一致,后续可放心覆盖升级。

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
| 推送到 `main` 分支 | 运行单元测试 + 编译,APK 存入 Workflow Artifacts |
| 推送 `v*` 标签(如 `v1.0.1`) | 同上,并把 **armv7 / armv8 两个 APK** 发布到 GitHub Releases |

发布新版本只需两步:

```bash
git tag v1.0.1
git push origin v1.0.1
```

版本号自动取自 tag(同时自动换算 `versionCode`),Release 说明自动生成。

<details>
<summary>本地构建(可选)</summary>

```bash
# 需要 JDK 17 和 Android SDK
./gradlew :app:testDebugUnitTest :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-{arm64-v8a,armeabi-v7a}-release.apk
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
│   ├── data/SettingsRepository.kt  # DataStore 本地持久化
│   ├── notify/ReminderScheduler.kt # 精确闹钟调度(单闹钟滚动排程)
│   ├── notify/AlarmReceiver.kt     # 闹钟触发 → 通知;支持通知栏直接结束停车
│   ├── notify/BootReceiver.kt      # 开机恢复提醒
│   ├── notify/Notifier.kt          # 通知渠道与文案
│   ├── ui/HomeScreen.kt            # 工具箱首页
│   ├── ui/ParkingScreen.kt         # 停车提醒: 设置表单 + 实时面板
│   └── ui/theme/Theme.kt           # Material 3 主题(翡翠绿品牌色)
├── app/src/test/                   # ParkingMathTest: 12 个计费边界用例
├── keystore/moneybox.keystore      # 便捷签名(可换成 Secrets 私密签名)
└── tools/gen_icons.py              # 图标生成脚本(Pillow)
```

## 🧮 提醒时间是怎么算的?

设入场时刻 `T`,免费时长 `F`,计费单元 `U`(分钟),出场宽限 `G`:

- **免费到期提醒**:`T + F − X`(X 为提前提醒分钟数)
- **第 k 次省钱缴费提醒**:`T + F + k·U − G`

在省钱提醒时刻缴费、并在 `G` 分钟内驶出,本周期费用保持 `k·U` 以内,不多付;忽略提醒则下个周期继续提醒。全部逻辑为纯函数实现,见 [`ParkingMath.kt`](app/src/main/java/com/fan/moneytoolbox/data/ParkingModels.kt) 与对应单元测试。

## 🗺 路线图

- [ ] 更多省钱工具:加油优惠提醒、会员自动续费提醒、外卖满减计算器
- [ ] 停车记录与「累计帮你省了多少」统计
- [ ] 桌面小组件(实时倒计时)
- [ ] 阶梯计费(首小时后递增)支持

欢迎提 Issue / PR。新增一个工具只需要在首页工具列表里加一张卡片,并实现对应页面。

## 开源许可

[MIT](LICENSE)
