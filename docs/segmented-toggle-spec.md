# 分段选择控件（Segmented Toggle，滑动胶囊）规格说明书

> 本文件为**自包含规格**：不依赖项目上下文即可被任何第三方 AI / 开发者理解并复现。  
> 参考实现所在工程：Android · Jetpack Compose · MIUIX 主题（`MiuixTheme.colorScheme`）。  
> 实际产品代码：`HistoryScreen.kt`（`pieContent` 内联实现）——本文件为标准答案，个别实现细节见文末「附录：与产品代码的差异」。

---

## 0. 控件是什么

一个**单选分段选择控件**：一条固定宽度的圆角浅色轨道上，有一个主色"胶囊"作为选中指示器；点击某个分段时，胶囊**带弹性动画水平滑动**到对应位置，同时该分段文字高亮（加粗、变为主色文字色）。

本规格描述的是 3 段版本（标签示例：`日 / 周 / 月`，用于切换统计范围），结构对 N 段同样成立。

---

## 1. 关键规格速查（两个问题的直接答案）

### 1.1 圆角

| 位置          | 圆角值       | 说明                          |
| ----------- | --------- | --------------------------- |
| 整条轨道（容器）背景  | **28 dp** | `RoundedCornerShape(28.dp)` |
| 滑动胶囊（指示器）背景 | **28 dp** | 与轨道一致；胶囊高仅 32 dp，视觉上接近药丸形   |

> 圆角取值来源：项目全局圆角常量 `GlobalCornerRadius = 28.dp`（`Shape.kt`）。注意：外层容器因有 `4.dp` 内边距，其圆角不会被内层遮挡；胶囊实际可视宽度约 66.7 dp、高 32 dp，28 dp 圆角使胶囊几乎呈"两端半圆"效果。

### 1.2 弹动（弹性滑动动画）

| 参数                    | 值                  | 含义                                    |
| --------------------- | ------------------ | ------------------------------------- |
| 动画类型                  | `spring()`（弹簧物理动画） | 非线性插值，带物理回弹                           |
| **dampingRatio（阻尼比）** | **0.7**            | **欠阻尼（< 1）→ 到位后会轻微过冲并回弹一次**，即"弹一下"的手感 |
| **stiffness（刚度）**     | **400**            | 弹簧刚度，决定滑动速度与响应感（越大越快）                 |

**弹动幅度估算**（基于该阻尼比的理论过冲量，单段步长 ≈ 66.7 dp）：

- 相邻段切换（滑动 66.7 dp）：过冲约 **2~3 dp**，随后回落稳定。
- 首段切末段（滑动 133.3 dp）：过冲约 **4~6 dp**，随后回落稳定。
- 数值参考：阻尼比 ζ=0.7 的理论峰值过冲 ≈ 4.6%（`exp(-π·ζ/√(1-ζ²))`），乘以位移距离即得过冲量。整体视觉为"干脆利落的轻微弹一下"，不是大幅晃动。

---

## 2. 完整视觉规格表

| 属性     | 值                                                                       | 备注             |
| ------ | ----------------------------------------------------------------------- | -------------- |
| 分段数    | 3（可扩展 N）                                                                | 布局按等分自动适配      |
| 轨道宽度   | 200 dp（写死）                                                              | 动画步长以此为准       |
| 轨道内边距  | 4 dp                                                                    | 胶囊与轨道边缘间距      |
| 轨道背景   | `colorScheme.surfaceContainerHigh`                                      | 浅色/深色主题自动适配    |
| 轨道圆角   | 28 dp                                                                   | 见 1.1          |
| 胶囊宽度   | 轨道内容区宽度 ÷ 分段数（`fillMaxWidth(1f/N)`）                                     | 3 段时 ≈ 66.7 dp |
| 胶囊高度   | 32 dp                                                                   | 固定             |
| 胶囊背景   | `colorScheme.primary`                                                   | 主题主色           |
| 胶囊圆角   | 28 dp                                                                   | 见 1.1          |
| 文字字号   | 13 sp                                                                   |                |
| 选中态文字  | `FontWeight.Bold` + `colorScheme.onPrimary`                             | 白色系（主色上的文字色）   |
| 未选中态文字 | `FontWeight.Normal` + `colorScheme.onSurfaceContainer` 且 `alpha = 0.4f` | 弱化             |
| 点击反馈   | 无涟漪（`indication = null`）                                                | 分段控件标准行为       |
| 点击热区   | 每段 `weight(1f)` 等分，纵向 `padding(6.dp)`                                   |                |



---

## 3. 动画数学（弹动参数如何工作）

`spring(dampingRatio, stiffness)` 是 Compose 弹簧物理模型：

- `dampingRatio = 1` 临界阻尼：最快且**无回弹**。
- `dampingRatio < 1`（本控件 **0.7**）：欠阻尼，动画**会越过目标再弹回**，产生弹性感；越接近 0 越晃。
- `dampingRatio > 1`：过阻尼，缓慢逼近目标，无回弹。
- `stiffness`：只影响速度/时长，不影响回弹有无。400 是"中等偏快"档位。

位移模型（水平一维）：

```
动画对象        Animatable(0f)          // 单位：px
动画目标        stepPx × selectedIndex  // stepPx = 轨道内容宽 / N
每帧位置        offset.x = anim.value
动画触发        LaunchedEffect(selectedIndex) { anim.animateTo(target, spring(0.7, 400)) }
```

- 胶囊用 `align(CenterStart)` 固定左基准，再以 `offset { IntOffset(anim.value.toInt(), 0) }` 平移。
- **胶囊宽度与动画步长必须同源**，否则胶囊无法对齐目标格。产品代码的口径：动画步长 = 轨道标称宽 200 dp ÷ N（3 段 ≈ 66.7 dp/格），胶囊宽 = 布局按 `fillMaxWidth(1f/N)` 等分得出；两侧同除以 N，视觉对齐成立。复现时保持**同一宽度来源**（要么都按 200 dp，要么都按扣除 4 dp 内边距后的内容区），不要混用。

---

## 4. 可直接复现的参考实现（Compose）

```kotlin
// 依赖：androidx.compose.animation / foundation / runtime / ui；MiuixTheme 或替换为 Material 色板

@Composable
fun SegmentedToggle(
    options: List<String>,          // 例：listOf("日", "周", "月")
    selectedIndex: Int,             // 0..N-1
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val anim = remember { Animatable(0f) }

    LaunchedEffect(selectedIndex) {
        val contentWidthPx = with(density) { ToggleSpec.ContentWidth.toPx() }
        val stepPx = contentWidthPx / options.size
        anim.animateTo(
            targetValue = stepPx * selectedIndex,
            animationSpec = spring(dampingRatio = ToggleSpec.DampingRatio, stiffness = ToggleSpec.Stiffness)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ToggleSpec.CornerRadius))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(ToggleSpec.Padding)
            .width(ToggleSpec.ContentWidth)
    ) {
        // —— 指示胶囊（下层）——
        Box(
            Modifier
                .fillMaxWidth(1f / options.size)
                .height(ToggleSpec.PillHeight)
                .offset { IntOffset(anim.value.toInt(), 0) }
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(ToggleSpec.CornerRadius))
                .background(MiuixTheme.colorScheme.primary)
        )
        // —— 文字层（上层，兄弟节点）——
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MiuixTheme.colorScheme.onPrimary
                                else MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// —— 规格常量集中定义（第三方复现时唯一需要修改的数值处）——
private object ToggleSpec {
    val CornerRadius = 28.dp      // 轨道 & 胶囊圆角
    val ContentWidth = 200.dp     // 轨道标称宽度（动画步长与 fillMaxWidth 的口径来源）
    val Padding = 4.dp
    val PillHeight = 32.dp
    val DampingRatio = 0.7f       // 欠阻尼 → 轻微回弹
    val Stiffness = 400f          // 中等刚度
}
```

调用示例：

```kotlin
SegmentedToggle(
    options = listOf("日", "周", "月"),
    selectedIndex = pieMode,
    onSelect = { pieMode = it }
)
```

---

## 5. 实现注意点（给复现者的 checklist）

1. **胶囊与文字层必须是同一容器内的兄弟节点**：胶囊在下、文字层在上。若把胶囊包进文字层会破坏 offset 对齐。
2. **宽度与步长同源**：改 `ContentWidth` 或分段数时，动画步长（`stepPx = width / N`）与胶囊宽（`1/N`）会自动跟随，不要单独写死数字。
3. **圆角一致**：轨道与胶囊用同一 `CornerRadius`（28 dp），视觉才连续。
4. **去涟漪**：`clickable` 必须传 `indication = null` + 自建 `MutableInteractionSource`，否则点击会闪水波（本项目在类似场景踩过"闪一下"的坑）。
5. **颜色走主题**：全部使用 `colorScheme.*`，深色模式无需额外适配。
6. **动画单位是 px**：`Animatable<Float>` 存 px，offset 用 `IntOffset(anim.value.toInt(), 0)`；dp 值须经 `LocalDensity` 换算。
7. **若要求"硬"手感**（无回弹）：把 `dampingRatio` 调到 `1f`（临界阻尼）；若想更快：调大 `stiffness`。回弹强弱只受 `dampingRatio` 影响。

---

## 附录：与产品代码的差异（本规格 = 净化/参数化版本）

- 产品代码中该控件**内联**在 `HistoryScreen.kt` 的 `pieContent` 卡片内，未抽成独立组件（本文件第 4 节已抽取并参数化）。
- 产品代码动画步长用轨道标称宽 `200.dp.toPx() / N` 计算（胶囊宽则用 `fillMaxWidth(1f/N)` 由布局给出），两者在 3 段场景下视觉对齐；本规格将两种口径统一说明，复现时保持单一口径即可。
- 标签文案在产品中走 i18n 资源（`calendar_day_mode` 等，五套语言对齐）。
