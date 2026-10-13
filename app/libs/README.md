# app/libs — 小米 Vela interconnect 手机端 SDK（xms-wearable）

手表→手机数据同步依赖小米官方 SDK `xms-wearable`。

## 获取 AAR

1. 打开官方 Vela 文档站 interconnect 页面：<https://iot.mi.com/vela>（interconnect / 小米互联）。
2. 下载 `interconnect_dev_test_demo.zip`，内含：
   - `xms-wearable-lib_1.4_release.aar`（当前有效版本 SDK）
   - 测试流程 PDF + demo APK + demo rpk（用于**先跑通 demo 双端**再查自己的代码）
3. 把 `xms-wearable-lib_1.4_release.aar` 放进本目录（`app/libs/`）。

## 为什么是本地 AAR

`xms-wearable` 不发布到公共 Maven 仓库，只能随官方 demo 包分发，故用 `fileTree(dir: "libs", ...)`
本地引入（`build.gradle.kts` 已配置）。AAR 自带 consumer proguard 规则，开启 minify 无需额外 keep。

## 放好后

- 根目录 `gradlew` 同步即可；`WearableLink` 会在 `AppContainer` 初始化时自动启动并监听手表。
- 注意：rpk 必须与本 App **同签名**（包名 `com.example.timefriend`、用根目录 `timefriend` 密钥库）。
  手表端 `Timefriend for vela` 工程的 `sign/release/` 证书即由该密钥库提取，已对齐。
