# 历史页「日 / 周 / 月」分段切换控件解析

> 适用代码：`app/src/main/java/com/example/timefriend/ui/screen/HistoryScreen.kt`（`HistoryScreen` 内 `pieContent`，约 111–170 行）。
> 控件定性：**单选分段选择控件（Segmented Control）**，本项目采用 **MIUI 风格「滑动胶囊指示器」**（sliding-pill indicator）的纯 Compose 自绘实现。

---

## 1. 这个控件是什么

它在界面上的样子：一个带圆角的浅灰小条，内部左侧有一个主色（primary）胶囊，胶囊上浮着当前选中的文字；点「周」「月」时胶囊会**弹性滑动**到对应位置。整条宽度固定，三等分。

- **功能语义**：在「当日 / 本周 / 本月」三个统计口径间做**单选切换**，驱动下方饼图（`DayPieChart`）显示不同时间范围的时间记录。
- **本质**：与 iOS `UISegmentedControl`、Material 3 的 `SingleChoiceSegmentedButtonRow` 是同一类控件 —— 一组互斥选项 + 一个指示当前项的高亮块。
- **本项目形态**：高亮块不是静态圆角块，而是会**平移动画**的"胶囊"，视觉上更接近 MIUI / 部分国产 ROM 的滑动切换风格。

### 常见叫法对照

| 语境 | 叫法 |
|---|---|
| 通用 UI 术语 | 分段控件 / 分段选择器（Segmented Control / Segmented Selector） |
| iOS | `UISegmentedControl` |
| Material 3（Compose） | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` |
| 本项目代码 | 注释里叫 "Day/Week/Month toggle with sliding pill"（带滑动胶囊的切换器） |
| 状态变量 | `pieMode: Int`（0=日，1=周，2=月） |

---

## 2. 界面位置与数据作用

历史页在**竖屏**下为上下两块卡片：上方日历卡，下方即本控件所在的统计卡（`pieContent` 的 `Card`）；**横屏**下两块卡片左右并排，本控件位于右侧统计卡顶部。

控件本身不带任何数据，只维护一个 `pieMode`，上层据此换数据源：

```kotlin
var pieMode by remember { mutableStateOf(0) } // 0 = day, 1 = week, 2 = month

val pieRecords = when (pieMode) {
    0 -> records        // 当天记录
    1 -> weekRecords    // 本周记录
    else -> monthRecords
}
// pieRecords 随后传给 DayPieChart(records = pieRecords, ...)
```

> 注：同文件 96–100 行的 `pieLabel`（`history_day_total` 等）目前只赋值、未参与 UI 渲染；`history_day_total/week/month_total` 字符串仍在 `strings.xml` 中存在，属**可清理的死代码/死资源**（不影响运行）。

---

## 3. 视觉规格速查

以竖屏典型情况为例（取值全部来自代码）：

| 项 | 值 |
|---|---|
| 容器背景 | `MiuixTheme.colorScheme.surfaceContainerHigh`（浅灰） |
| 容器圆角 | `RoundedCornerShape(GlobalCornerRadius)`，`GlobalCornerRadius = 28.dp`（`ui/theme/Shape.kt`） |
| 容器内边距 | `4.dp` |
| 内容区宽度 | 固定 `200.dp`（三等分，每格 ≈ 66.7dp） |
| 胶囊（指示器） | 宽 = 内容区 1/3，高 `32.dp`，背景 `colorScheme.primary`，圆角同上 |
| 胶囊动画 | `spring(dampingRatio = 0.7f, stiffness = 400f)`，水平平移 |
| 选项文字 | `13.sp`；选中：加粗 + `onPrimary`（主色上的文字色）；未选中：常规 + `onSurfaceContainer` 且 `alpha = 0.4f` |
| 点击热区 | 每格 `weight(1f)` 等分 + 纵向 `padding(6.dp)`；**无点击涟漪** |

---

## 4. 实现结构拆解（从上到下）

代码把控件内联写在 `pieContent` 的 `Card > Column` 里，没有抽成独立 Composable（文件底部另有一个未被调用的 `PieModeChip` 旧实现，见第 7 节）。

```
┌─ Box（容器：surfaceContainerHigh 背景 + 4dp padding）────────┐
│  ┌─ Box（胶囊 pill：primary 背景，宽 1/3，高 32dp）───────┐   │
│  │    ← offset.x 由 Animatable 驱动（chipAnim.value）       │   │
│  └───────────────────────────────────────────────────────┘   │
│  ┌─ Row（文字层，三等分 Box 各 weight(1f)，可点击）─────────┐ │
│  │   「日」        「周」        「月」                    │   │
│  └───────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 4.1 标签源（i18n）

```kotlin
val chipLabels = listOf(
    stringResource(R.string.calendar_day_mode),   // 日
    stringResource(R.string.calendar_week_mode),  // 周
    stringResource(R.string.calendar_month_mode), // 月
)
```

三个文案来自 `values/strings.xml`，与其余 4 套语言（en / zh-rTW / ja / ko）对齐 —— **改文案须同步五处**。

### 4.2 动画状态（胶囊位移）

```kotlin
val chipAnim = remember { Animatable(0f) }            // 胶囊 x 偏移（px）
val chipDensity = LocalDensity.current
LaunchedEffect(pieMode) {                            // pieMode 变化 → 弹性滑到对应格
    val containerWidthPx = with(chipDensity) { 200.dp.toPx() }
    val chipWidthPx = containerWidthPx / chipLabels.size
    chipAnim.animateTo(chipWidthPx * pieMode,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f))
}
```

- 目标偏移 = **整条内容区宽的 1/3 × 选项序号**（0、1、2），与胶囊自身宽（内容区 1/3）配套，保证胶囊恰好落在目标格。
- `dampingRatio = 0.7` 略欠阻尼，到位时有轻微弹性回弹手感；`stiffness = 400` 属中等刚度。

### 4.3 容器 + 胶囊

```kotlin
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(GlobalCornerRadius))
        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
        .padding(4.dp)
        .width(200.dp)
) {
    // —— 胶囊（先画，位于文字层之下）——
    Box(
        modifier = Modifier
            .fillMaxWidth(1f / chipLabels.size)      // 三等分之一
            .height(32.dp)
            .offset { IntOffset(chipAnim.value.toInt(), 0) }  // 动画位移
            .align(Alignment.CenterStart)            // 基准：左端
            .clip(RoundedCornerShape(GlobalCornerRadius))
            .background(MiuixTheme.colorScheme.primary)
    )
    // —— 文字层见 4.4 ——
}
```

要点：胶囊用 `align(CenterStart)` 定基准 + `offset` 水平平移，圆角直接复用 28dp，背景为主色。

### 4.4 文字层（三个可点选项）

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    chipLabels.forEachIndexed { index, label ->
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null            // 关键：去掉点击涟漪
                ) { pieMode = index }            // 单选：直接覆盖 int
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (pieMode == index) FontWeight.Bold else FontWeight.Normal,
                color = if (pieMode == index) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
            )
        }
    }
}
```

- 文字层与胶囊是**兄弟节点**：胶囊在下、文字在上，二者都直接放在容器 Box 内。
- `indication = null` + 自建 `MutableInteractionSource`：抑制整行点击时的默认涟漪效果（分段控件普遍不需要水波反馈）。
- 选中态通过 `pieMode == index` 判断，文字色/字重即时切换；胶囊由 `LaunchedEffect(pieMode)` 动画跟上。

---

## 5. 交互与动画时序

1. 用户点击某一格 → `pieMode = index`（瞬时更新，文字层立刻高亮）。
2. `pieMode` 变化触发重组 + `LaunchedEffect(pieMode)` 重启。
3. `chipAnim.animateTo(chipWidthPx * index)`：胶囊从当前位置按 spring 曲线滑到目标格。
4. 同一重组里，`pieRecords` 随 `pieMode` 换数据源，`DayPieChart` 用 `AnimatedContent`？—— 否，饼图本身无过渡动画，直接重绘新数据（旧实现 `PieModeChip` 同理）。

返回键 / 手势无特殊处理（非弹窗），系统默认即可。

---

## 6. 工程注意点

- **宽度写死 200.dp**：内容区宽与动画步长都以 `200.dp` 为基准且**手动保持一致**。若未来增加第 4 个选项或改为自适应宽度，两处都要同步改（动画里用 `chipLabels.size` 计算步长，但 200dp 是字面量）。这是目前最脆的耦合点。
- **胶囊宽度 = 内容区 / 选项数**：用 `fillMaxWidth(1f / size)`，与动画步长同源推导，一般不会漂移。
- **颜色全部走 `MiuixTheme.colorScheme`**：自动适配深/浅色主题，无需额外处理。
- **无涟漪点击**：三段控件刻意不加 ripple；若以后想加，可换 `clickable` 默认值（去掉 `indication = null`）。
- **不要用 `Dialog` / 弹窗思路实现此类内联切换控件**：它属于页面内静态控件，直接放在布局树即可（参照项目对弹窗类浮层的 MIUIX 约束，本控件与模糊无关）。

---

## 7. 相关遗留代码（可清理）

- `HistoryScreen.kt` 底部（约 464–487 行）有一个**私有 `PieModeChip` Composable 从未被调用**：它是旧版"两个独立小圆角按钮"方案的残留，功能上已被本控件（4.2–4.4）取代，建议删除。
- 第 96–100 行的 `pieLabel` 与 `history_day_total / week / month` 三字符串：当前未用于任何 UI 文本，若确认不再需要可一并清理（清理前先在五套 strings.xml 里确认无其他引用）。

---

## 8. 关键文件索引

| 文件 | 内容 |
|---|---|
| `ui/screen/HistoryScreen.kt` | 控件实现（111–170 行）、遗留 `PieModeChip`（464–487 行）、日历与饼图布局 |
| `ui/theme/Shape.kt` | `GlobalCornerRadius = 28.dp` |
| `ui/component/PieChart.kt` | `DayPieChart`（被本控件切换的数据展示） |
| `res/values(-en/-zh-rTW/-ja/-ko)/strings.xml` | `calendar_day_mode / calendar_week_mode / calendar_month_mode`（五套对齐） |
