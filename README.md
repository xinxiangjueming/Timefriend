# 爱时间 Timefriend

**爱时间（Timefriend）** 是一款运行在 Android 平台上的时间记录与番茄钟应用，采用 Jetpack Compose + MIUI 风格（miuix）界面，帮助你记录每天的时间开销、用番茄钟保持专注，并通过日历与统计图表回顾自己的时间分配。

- 包名：`com.example.timefriend`
- 当前版本：1.0.11（versionCode 2）
- 最低支持：Android 13（API 33）

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 时间记录 | 今日页按分类快速记录时间消耗，支持睡眠时长输入弹窗 |
| 番茄钟 | 专注 / 休息计时，自定义时长，本周每日专注柱状图 |
| 历史统计 | 日历视图 + 「日 / 周 / 月」分段切换的饼图 + 番茄钟周汇总卡片 |
| 云备份 | 基于 WebDAV 的备份：Room 数据库 WAL 检查点合并 → zstd 压缩 → 上传；恢复时按行键合并，**只补缺、不覆盖本地**；兼容旧版 JSON 备份 |
| 手表联动 | 小米手表同步服务（Vela interconnect / xms-wearable），支持监听注册、断连重绑与开关唤起 |
| 界面 | 悬浮底栏与弹窗的液态玻璃（Kyant Backdrop 折射 / 色散）、MIUI 风格毛玻璃（miuix-blur + Haze 同窗口采样）、深浅色主题、横竖屏双布局 |
| 多语言 | 简体中文（默认）、繁体中文、English、日本語、한국어，跟随系统语言 |

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose、MIUIX（miuix-ui / miuix-blur / miuix-icons 0.9.2）、Haze 1.7.2、Kyant Backdrop 2.0.1 + Shapes 1.2.1（液态玻璃）
- **数据库**：Room（SQLite）
- **网络**：OkHttp（WebDAV 协议）
- **压缩**：zstd-jni（Zstandard）
- **穿戴**：小米 Vela interconnect 手机端 SDK（`xms-wearable-lib_1.4_release.aar`）
- **构建**：Gradle Kotlin DSL + KSP，`compileSdk 37` / `minSdk 33` / `targetSdk 36`

## 构建要求

1. Android Studio（较新稳定版）与 Android SDK 37
2. JDK 17
3. 将小米穿戴 SDK 的 `xms-wearable-lib_1.4_release.aar` 放入 `app/libs/` 目录（获取方式见 `app/libs/README.md`）

## 构建

```bash
# Debug 包
./gradlew assembleDebug

# Release 包（R8 + 资源压缩，仅保留 5 种语言资源）
./gradlew assembleRelease
```

## 目录结构

```
Timefriend/
├── app/
│   ├── libs/                       # 小米穿戴 xms-wearable AAR（需自行放置）
│   └── src/main/
│       ├── java/com/example/timefriend/
│       │   ├── data/               # Room 实体与数据库、TimeRepository、WebDavHelper、ZstdUtil
│       │   ├── mvi/                # MVI 意向与状态
│       │   ├── service/            # 前台服务（计时等）
│       │   ├── ui/
│       │   │   ├── component/      # 毛玻璃弹窗、按钮行、饼图、番茄钟周图、Toast
│       │   │   └── screen/         # 今日 / 番茄钟 / 历史 / 设置页与输入弹窗
│       │   ├── util/               # 工具类
│       │   ├── viewmodel/          # ViewModel
│       │   └── wearable/           # 小米手表同步（WearableLink）
│       └── res/
│           ├── values*/            # 五套语言资源（默认简体中文 + en / zh-rTW / ja / ko）
│           └── values-night/       # 深色主题资源
├── docs/                           # 设计与实现文档（分段控件、毛玻璃弹窗等）
├── build.gradle.kts                # 根构建脚本
└── settings.gradle.kts
```

## 相关文档

- `docs/history-segmented-toggle.md` — 历史页「日 / 周 / 月」滑动胶囊分段控件解析
- `docs/miuix-dialog-blur.md` — 基于 MIUIX 的弹窗毛玻璃（背景模糊）实现说明
- `docs/segmented-toggle-spec.md` — 分段切换控件设计规格

## 开源致谢

本项目的界面效果建立在以下开源工作之上，谨向作者与贡献者致谢：

| 项目 | 作者 / 维护者 | 在本项目中的作用 |
| --- | --- | --- |
| [Backdrop · Liquid Glass](https://github.com/Kyant0/AndroidLiquidGlass) | Kyant（Kyant0） | 悬浮底栏与弹窗的液态玻璃：背景采样、`lens` 折射与色散、按动放大与拖拽切 tab、交互高光；实现参照其官方文档与 catalog 的 `LiquidBottomTabs` / `DialogContent` 示例 |
| [Shapes](https://github.com/Kyant0/Shapes) | Kyant（Kyant0） | G2 连续圆角 `Capsule` / `RoundedRectangle`，为 `lens` 提供可识别的折射形状 |
| [Miuix](https://github.com/YuKongA/miuix) | YuKongA 及贡献者 | MIUI 风格组件与主题（miuix-ui / miuix-icons）；`miuix-blur` 的 `textureBlur` 用于低版本弹窗玻璃卡、Toast 与导航栏的同窗口采样模糊 |
| [Haze](https://github.com/chrisbanes/haze) | Chris Banes | 低版本弹窗外部 scrim 的硬件加速模糊（20dp 模糊 + 压暗） |

特别感谢 Kyant 开源的液态玻璃库及其开发文档、示例组件——底栏滑块的双层折射与色散、按动形变、弹窗玻璃卡的官方样式均直接参照其实现；同样感谢 Miuix 提供的模糊能力，使低版本设备仍能保持一致的毛玻璃观感。

其余依赖（Jetpack Compose / AndroidX / Room、OkHttp、zstd-jni、Kotlin 协程、小米 Vela interconnect SDK 等）一并致谢。

## 开源协议

本项目基于 **Apache License 2.0** 开源，完整条款见 [LICENSE](LICENSE) 文件。

```
Copyright 2026 xinxiangjueming

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
