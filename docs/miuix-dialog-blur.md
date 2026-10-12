# 用 MIUIX 实现弹窗毛玻璃（背景模糊）效果

> 适用库：`top.yukonga.miuix.kmp` 的 `miuix-blur` 模块（本文基于 `0.9.2`）。
> 项目参考实现：`ui/component/BlurToast.kt`（玻璃卡片范本）、`ui/screen/SleepInputDialog.kt`（同窗口弹窗）、`ui/screen/MainScreen.kt`（backdrop 源）。

---

## 1. 原理与前置条件

MIUIX 的模糊是 **backdrop blur（背景模糊）**：先把"背景内容"录制到一个 `GraphicsLayer`，再让前景组件从该 layer 取像素做高斯模糊后画在自己背后。

- **底层技术**：可分离高斯模糊 `RuntimeShader` 包裹 `RenderEffect`。
- **系统要求**：`RuntimeShader` 需 **Android API 33+**。低于 33 时，`drawBackdrop` 内部 `effectiveEnabled = enabled && isRuntimeShaderSupported()` 为 `false`，**静默降级为不模糊**（不会崩溃，只是没有毛玻璃）。务必在真机 33+ 上验证视觉效果。
- **单位**：模糊半径以 **dp** 传入，内部按屏幕密度换算为像素。

---

## 2. 三个核心 API

```kotlin
// 1) 创建（并 remember）一个 backdrop 句柄，用于承载被录制的背景
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw,
): LayerBackdrop

// 2) 在"背景内容容器"上录制其渲染结果到上述 backdrop
fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier

// 3) 在前景组件上，从 backdrop 取内容做模糊（画在组件自身背景）
fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (BackdropEffectScope.() -> Highlight?)? = null,
    enabled: Boolean = true,
): Modifier
```

模糊效果在 `effects` lambda 内声明（作用域为 `BackdropEffectScope`）：

```kotlin
// 横向/纵向独立半径；blurRadiusY 默认等于 blurRadiusX；半径被 clamp 到 [0, MaxBlurRadius]
fun BackdropEffectScope.textureBlurEffect(
    blurRadiusX: Float,
    blurRadiusY: Float = blurRadiusX,
    noiseCoefficient: Float = BlurDefaults.NoiseCoefficient,
    colors: BlurColors = BlurColors(),
)
```

模糊半径默认值（来自 `BlurDefaults`）：

| 常量 | 值 | 说明 |
|------|-----|------|
| `BlurDefaults.BlurRadius` | `20f` | 默认模糊半径（dp） |
| `BlurDefaults.MaxBlurRadius` | `150f` | 半径上限（dp），超出被截断 |
| `BlurDefaults.NoiseCoefficient` | `0.0045f` | 抗色带噪声，0 关闭 |

经验取值：轻磨砂 `5f`、默认 `20f`、明显玻璃感 `30f+`（均不超过 150）。

---

## 3. 最关键的架构约束（决定成败）

> **模糊消费方（Toast / 弹窗 / 导航栏）必须放在 `.layerBackdrop(backdrop)` 子树的【兄弟节点】、且【同窗口】渲染。**

原因：

1. **不能放进被录制的子树内部**：若把 `drawBackdrop` 的组件也包在 `layerBackdrop` 容器里，它会去模糊"包含自己"的层 → **自捕获（递归 backdrop）**，通常表现为黑屏/渲染异常。
2. **不能用独立窗口（`Dialog`）**：`androidx.compose.ui.window.Dialog` 是独立 `Window`，跨窗口读取 `GraphicsLayer` 行为不确定，模糊经常取不到背景。

因此：**弹窗不要再用 `Dialog`**，而是做成与背景内容同处一个 `Window` 的浮层（例如在 `MainScreen` 的根 `Box` 内、`layerBackdrop` 容器之外叠加）。

```
Root Box (fillMaxSize)
├── Column( .layerBackdrop(backdrop) )   ← 背景内容，被录制
│        └── ... 页面 UI ...
│
└── Box(fillMaxSize)                     ← 浮层（与上面的 Column 是兄弟节点）
     ├── Box(透明点击层)                 ← 点外部关闭（indication=null）
     └── Box(玻璃卡片: drawBackdrop 模糊自身背景)   ← 复用同一个 backdrop
```

---

## 4. 实现步骤（以首页睡眠输入弹窗为例）

### 4.1 在内容容器上录制 backdrop

`MainScreen.kt`：

```kotlin
val backdrop = rememberLayerBackdrop()

Box(modifier = Modifier.fillMaxSize()) {
    // 背景内容：录制进 backdrop，供浮层复用
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .layerBackdrop(backdrop)
    ) { /* 页面 UI */ }

    // 其它浮层（Toast / 导航栏）也在此之后、作为兄弟节点叠加
}
```

### 4.2 弹窗作为同窗口浮层，仅模糊卡片本身

要点（对应 `SleepInputDialog.kt`）：

- **只模糊卡片自身**：`drawBackdrop(...)` 画在卡片 `Modifier` 链上，**不要**铺到整屏遮罩层（否则变成全屏模糊 + 暗化）。
- **整屏仅一个透明点击层**用于"点卡片外部关闭"，不模糊、不暗化。
- **宽度约束**：用 `BoxWithConstraints` 读全屏宽，按比例设卡片宽度（竖屏 80%、横屏 60%），居中。
- **关闭交互**：返回键用 `BackHandler(enabled = showSleepDialog)`；点外部用透明点击层，但必须 `indication = null`（见第 6 节"闪一下"）。

```kotlin
@Composable
fun SleepInputDialog(
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onSave: (minutes: Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明点击层：点卡片外部关闭。indication=null 关闭默认涟漪，避免"闪一下"
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // 卡片居中，宽度 = 屏幕宽 80%（横屏 60%）
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isLandscape =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthFraction = if (isLandscape) 0.6f else 0.8f

            val shape = RoundedCornerShape(GlobalCornerRadius)
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth * widthFraction)
                    // 1) 阴影
                    .dropShadow(
                        shape = shape,
                        shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = 0.2f)
                    )
                    // 2) 边框底色
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.dividerLine,
                        cornerRadius = GlobalCornerRadius
                    )
                    .padding(0.75.dp)
                    // 3) 模糊：只画在卡片自身背景（与 BlurToast 同款）
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            textureBlurEffect(blurRadiusX = 5f, blurRadiusY = 5f)
                        }
                    )
                    // 4) 半透明着色层，叠在模糊之上
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f),
                        cornerRadius = GlobalCornerRadius
                    )
            ) {
                // 卡片内容：标题、时间选择、取消/保存按钮……
            }
        }
    }
}
```

> 上述 1→4 的 `Modifier` 链顺序与 `BlurToast.kt` 完全一致，是本项目的"玻璃卡片"标准范式，建议统一复用。

### 4.3 在 MainScreen 中挂载弹窗浮层

```kotlin
// Layer 5：睡眠输入弹窗（同窗口浮层，复用首页 backdrop 做毛玻璃）
BackHandler(enabled = showSleepDialog) { showSleepDialog = false }
if (showSleepDialog) {
    SleepInputDialog(
        backdrop = backdrop,
        onDismiss = { showSleepDialog = false },
        onSave = { minutes -> timerViewModel.saveSleep(minutes); showSleepDialog = false }
    )
}
```

---

## 5. 完整最小示例（玻璃卡片）

不含业务逻辑的纯范式，可直接套用：

```kotlin
@Composable
fun GlassCard(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(GlobalCornerRadius)
    Box(
        modifier = modifier
            .dropShadow(shape = shape, shadow = Shadow(10.dp, Color.Black, 0.2f))
            .squircleBackground(MiuixTheme.colorScheme.dividerLine, GlobalCornerRadius)
            .padding(0.75.dp)
            .drawBackdrop(backdrop, { shape }) {
                textureBlurEffect(blurRadiusX = 20f, blurRadiusY = 20f)
            }
            .squircleBackground(
                MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f),
                GlobalCornerRadius
            )
    ) { content() }
}
```

---

## 6. 常见问题与坑

| 现象 | 根因 | 解法 |
|------|------|------|
| 全屏都模糊 + 变暗 | 把 `drawBackdrop` + 暗化层铺到了整屏遮罩 | `drawBackdrop` 只挂在卡片 `Modifier` 上，整屏仅留透明点击层 |
| 点外部关闭"闪一下" | `clickable` 默认带涟漪（ripple）指示 | `clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = ...)` |
| 弹窗占满全屏宽 | 卡片用无上限 `fillMaxWidth()` | 用 `BoxWithConstraints` + `widthIn(max = maxWidth * 0.8f)`（横屏 0.6f） |
| 模糊不显示（真机） | 设备 < API 33，`drawBackdrop` 静默降级 | 仅 API 33+ 有毛玻璃；低版本卡片仍是半透明面，不崩 |
| 模糊黑屏 / 异常 | 模糊节点在被录制的 `layerBackdrop` 子树内部（自捕获） | 把浮层移到子树**之外**的兄弟节点 |
| 用 `Dialog` 时模糊取不到背景 | 独立 `Window` 跨窗口读 `GraphicsLayer` 不确定 | 改用同窗口浮层（见第 3/4 节） |

> 与模糊无直接关系、但本项目同类的"比例缩放"坑：圆环中心文字若写死 `fontSize`，圆环缩小时字不缩。应让字号随组件尺寸等比计算（见 `ui/component/PieChart.kt` 的 `centerTextSize`）。

---

## 7. 验证

编译（仅快速校验，不出包）：

```bash
./gradlew :app:compileReleaseKotlin --no-daemon --offline
```

真机（**API 33+**）核验清单：

- 卡片内部呈现毛玻璃，整屏不模糊、不暗化；
- 返回键关闭、点卡片外部关闭（无涟漪闪烁）；
- 横竖屏切换时卡片宽度为 60% / 80%；
- 低版本（<33）不崩溃，卡片为半透明玻璃面。

---

## 8. 关键文件索引

| 文件 | 作用 |
|------|------|
| `ui/component/BlurToast.kt` | 玻璃卡片标准范式（阴影→边框→drawBackdrop→半透明面） |
| `ui/screen/MainScreen.kt` | `rememberLayerBackdrop()` + `.layerBackdrop()` 录制背景；挂载弹窗浮层（Layer 5） |
| `ui/screen/SleepInputDialog.kt` | 同窗口浮层弹窗，仅卡片模糊 + 按比例宽度 + 无涟漪关闭 |
| `ui/component/PieChart.kt` | 文字随尺寸等比缩放的参考（同类比例问题） |
| miuix-blur (`0.9.2`) | `LayerBackdrop` / `drawBackdrop` / `textureBlurEffect` / `BlurDefaults` |
