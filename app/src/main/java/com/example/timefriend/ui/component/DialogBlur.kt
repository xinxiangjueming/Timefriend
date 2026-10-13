package com.example.timefriend.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timefriend.ui.theme.GlobalCornerRadius
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 弹窗模糊统一封装（对齐 SportLink 2026-08-24 定稿架构：外部 Haze + 内部 miuix）。
 *
 * 两条采样链路：
 * - 内部玻璃卡：miuix-blur 同窗口 backdrop（首页常驻 layerBackdrop 源 + 卡片 textureBlur 10f + 高光描边）。
 * - 外部 scrim：Haze 同窗口采样（hazeSource 条件挂首页内容层 + 浮层 scrim hazeEffect 20dp + 25% 压暗）。
 *
 * 核心铁律（miuix-blur / Haze 共同）：模糊消费方（弹窗卡片 / scrim）必须与采样源是
 * **同窗口兄弟节点**——嵌套在源内部会自采样 → 异常。因此嵌套调用点渲染的弹窗
 * （如 PomodoroScreen 内的番茄自定义分钟弹窗）必须经 [DialogOverlay] 注册到宿主
 * slot（[DialogOverlaySlot]）渲染，与源呈兄弟关系。
 *
 * 与 SportLink 的差异：Timefriend minSdk=33，miuix textureBlur（AGSL RuntimeShader）
 * 与 Haze 真模糊（RenderEffect API 31+）恒可用，故无 API<33 实心卡降级、无
 * FLAG_BLUR_BEHIND 独立窗口兜底分支。首页 backdrop 因导航栏/Toast 常驻消费而
 * 常驻录制（不同于 SportLink 的仅弹窗活跃挂载）；Haze 源按 openCount 条件挂载，
 * 避免整屏 Haze 采样常驻消耗 GPU。今日/番茄钟页弹窗（SleepInputDialog /
 * CustomValueDialog）经 miuixBlur=false 选用实心 tint 卡，不走内部 miuix 模糊；
 * 其余弹窗（如历史页 MonthYearPickerDialog）维持玻璃卡默认值。
 */

/** 弹窗玻璃卡模糊半径（dp，对齐 SportLink 2026-09-01 定稿 10f） */
const val DialogGlassBlurRadius = 10f

/**
 * 玻璃卡 tint 不透明度（叠加在模糊层之上，对齐 SportLink 2026-08-22 定稿
 * 深浅色统一 0.55：玻璃感与文字对比度平衡）。
 */
const val DialogGlassTintAlpha = 0.55f

/** 玻璃卡高光描边带宽（dp，对齐 SportLink 定稿 1.5） */
const val DialogGlassHighlightWidth = 1.5f

/** 玻璃卡高光（加粗带宽版 Middle 预设，按深浅色；库预设见 miuix blur Highlight.kt） */
val DialogGlassHighlightLight = Highlight(
    width = DialogGlassHighlightWidth.dp,
    style = Highlight.GlassStrokeMiddleLight.style,
)
val DialogGlassHighlightDark = Highlight(
    width = DialogGlassHighlightWidth.dp,
    style = Highlight.GlassStrokeMiddleDark.style,
)

/** 玻璃卡实色描边（1dp，黑/白 20%，对齐 SportLink 定稿） */
val DialogGlassBorderWidth = 1.dp
val DialogGlassBorderLight = Color(0x33000000)
val DialogGlassBorderDark = Color(0x33FFFFFF)

/**
 * 玻璃卡 tint 底色（叠加在模糊层之上，tint 不透明度 [DialogGlassTintAlpha]）。
 * 对齐 SportLink 弹窗底 DialogBgDay=SurfaceDay=#FFFFFF / DialogBgNight=#3A3A3A——
 * 勿用 MiuixTheme.surfaceContainerHigh（本项目曾被覆写为 #E0E0E0/#3A3A3A，
 * 与取消键底色耦合，偏离 SportLink 实机观感）。
 */
val DialogGlassTintColorLight = Color.White
val DialogGlassTintColorDark = Color(0xFF3A3A3A)

/**
 * 弹窗外部 scrim 毛玻璃样式：25% 黑压暗底 + 20dp 模糊（对齐 SportLink DialogBlurStyle）。
 * 模糊层自带压暗底色，弹窗外部显示模糊后的页面内容（替代纯压暗 scrim）。
 */
val DialogBlurStyle: HazeStyle = HazeStyle(
    backgroundColor = Color.Black.copy(alpha = 0.25f),
    blurRadius = 20.dp,
    tint = null,
)

/**
 * 弹窗外部 Haze 效果节点（挂全屏 scrim）。
 * clipToAreasBounds=false 保证模糊层不被裁剪到源矩形（全屏模糊）；
 * hazeState 为 null（无宿主兜底场景）时回落纯压暗 scrim。
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.dialogHazeEffect(hazeState: HazeState?): Modifier =
    if (hazeState != null) this.hazeEffect(state = hazeState, style = DialogBlurStyle) {
        clipToAreasBounds = false
    } else this.background(Color.Black.copy(alpha = 0.25f))

/**
 * 弹窗外部模糊采样源（挂首页内容层，与浮层 slot 兄弟）。
 * 仅弹窗活跃（openCount > 0）时挂载，避免 Haze 整屏采样常驻消耗 GPU。
 */
fun Modifier.dialogHazeSource(state: DialogOverlayState): Modifier =
    then(if (state.openCount > 0) Modifier.hazeSource(state.hazeState) else Modifier)

/** 单个 slot 条目：content 可观察，每次弹窗重组更新引用，驱动宿主 slot 渲染最新内容 */
class DialogEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

/**
 * 弹窗浮层宿主状态。
 * @param backdrop miuix 采样源（首页常驻录制层，导航栏/Toast 共用）
 * @param hazeState Haze 外部模糊采样状态（与 backdrop 并存，同源双离屏录制）
 */
class DialogOverlayState(
    val backdrop: LayerBackdrop,
    val hazeState: HazeState,
) {
    /** 当前活跃弹窗数；> 0 才挂 hazeSource */
    var openCount by mutableIntStateOf(0)

    /** slot 注册表：弹窗浮层按注册顺序叠加渲染 */
    val entries = mutableStateListOf<DialogEntry>()
}

/** 供页面/弹窗读取的宿主状态；无宿主时为 null（就地渲染兜底，保持可用） */
val LocalDialogOverlay = staticCompositionLocalOf<DialogOverlayState?> { null }

/**
 * 弹窗浮层注册入口：把弹窗内容注册到宿主 slot（渲染为采样源兄弟节点）。
 * 无宿主（LocalDialogOverlay 为 null）→ 就地渲染兜底。
 *
 * 状态说明：弹窗内容 lambda 捕获调用方 State 对象（remember 状态留在调用点组合
 * 位置），内部输入/开关等状态变化仍能正确驱动 slot 处重组。
 */
@Composable
fun DialogOverlay(content: @Composable () -> Unit) {
    val state = LocalDialogOverlay.current
    if (state == null) {
        content()
        return
    }
    val entry = remember { DialogEntry() }
    // 每次重组更新条目内容（可观察 → 宿主 slot 渲染最新，弹窗内部状态变化不失效）
    entry.content = content
    DisposableEffect(Unit) {
        state.openCount++
        state.entries.add(entry)
        onDispose {
            state.openCount--
            state.entries.remove(entry)
        }
    }
}

/**
 * 宿主 slot 渲染入口：挂在页面根，与采样源（layerBackdrop/hazeSource 所在节点）呈兄弟。
 * 用 entry 对象作 key（非索引）：不同弹窗同帧切换时强制全新组合，避免内容 lambda 被复用跳过
 * （SportLink 2026-08-29 实测坑）。
 */
@Composable
fun DialogOverlaySlot(state: DialogOverlayState) {
    for (entry in state.entries) {
        key(entry) { entry.content() }
    }
}

/**
 * 弹窗卡片统一视觉。
 *
 * 玻璃卡模式（miuixBlur = true，默认）：miuix textureBlur 10f + 高光描边 + tint 0.55
 * + 1dp 实色描边。
 * 实心卡模式（miuixBlur = false，今日/番茄钟页弹窗）：去除卡片内部 miuix 模糊与高光
 * 描边，tint 改为不透明实色（浅色 White / 深色 #3A3A3A，对齐 SportLink DialogBg 实底），
 * 避免半透明卡透出外部 Haze scrim 导致对比度下降；1dp 描边/阴影/进入动画保留。
 *
 * 链序铁律（对齐 SportLink）：外层 clip 必须在 textureBlur 之前（Miuix 模糊层是
 * 节点边界+padding 矩形，不随 shape 裁剪）；点击拦截放最后。
 *
 * @param shape    卡片 shape（RoundedCornerShape(GlobalCornerRadius)）
 * @param miuixBlur 是否启用卡片内部 miuix textureBlur 玻璃模糊（默认 true；
 *                  false = 不透明实心 tint 卡）
 */
@Composable
fun Modifier.dialogGlassCard(
    state: DialogOverlayState,
    shape: Shape,
    miuixBlur: Boolean = true,
): Modifier {
    val isDark = isSystemInDarkTheme()
    val tintColor = if (isDark) DialogGlassTintColorDark else DialogGlassTintColorLight
    return this
        .clip(shape)
        .then(
            if (miuixBlur) {
                Modifier.textureBlur(
                    state.backdrop, shape,
                    blurRadius = DialogGlassBlurRadius,
                    // 玻璃边缘高光（miuix Highlight，加粗带宽版 Middle 预设）
                    highlight = if (isDark) DialogGlassHighlightDark else DialogGlassHighlightLight,
                )
            } else {
                Modifier
            }
        )
        .background(
            // 玻璃模式：半透明 tint 叠加在 miuix 模糊之上；实心模式：tint 不透明
            // （透出外部 Haze scrim 会显脏且对比度下降）。底色均对齐 SportLink DialogBg。
            if (miuixBlur) tintColor.copy(alpha = DialogGlassTintAlpha) else tintColor
        )
        .border(
            width = DialogGlassBorderWidth,
            color = if (isDark) DialogGlassBorderDark else DialogGlassBorderLight,
            shape = shape,
        )
        // 点击拦截：scrim 是外层全屏 Box + clickable{onDismiss}，卡片内空白点击若无消费节点
        // 会冒泡到 scrim 误触发关闭；空 onClick 消费卡片内点击阻断冒泡（内层按钮先命中不受影响）
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { }
}

/**
 * 弹窗进入动画：alpha 0→1 + scale 0.9→1（150ms，对齐 SportLink dialogEnterAnim）。
 * Timefriend 弹窗均为同窗口浮层（无独立 Dialog 窗口），无需窗口级退出 fade。
 */
@Composable
fun Modifier.dialogEnterAnim(
    durationMillis: Int = 150,
    initialScale: Float = 0.9f,
): Modifier {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(initialScale) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(durationMillis)) }
        launch { scale.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * 弹窗宽度限制（逐字移植 SportLink ComposeDialogs.kt dialogWidthAdaptive）：
 * - 横屏：上限为屏幕宽 60%（屏幕宽大，90% 过宽）
 * - 竖屏：低 DPI（<200dpi，ldpi/mdpi）设备限制为屏幕 95%，否则 90%
 */
@Composable
fun Modifier.dialogWidthAdaptive(): Modifier {
    val configuration = LocalConfiguration.current
    val dpi = LocalContext.current.resources.displayMetrics.densityDpi
    val maxWidthDp = when {
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ->
            (configuration.screenWidthDp * 0.6f).dp
        dpi < 200 -> (configuration.screenWidthDp * 0.95f).dp
        else -> (configuration.screenWidthDp * 0.9f).dp
    }
    return this.widthIn(max = maxWidthDp)
}

/**
 * 弹窗卡片 8dp 柔和阴影（对齐 SportLink dialogCardShadow：旧版 shadow API + clip=false）。
 *
 * ⚠️ 勿改用 Compose 1.9 的 dropShadow 新 API：与 miuix textureBlur 的 recordLayer
 * 组合时实机出现「卡片内方形偏移模糊层」渲染 bug（2026-10-13 用户截图反馈）；
 * sportlink 同链路用旧 shadow(elevation, shape, clip=false) 无此问题。
 * shadow 必须放在 squircle/blur 绘制节点之前（外层），clip=false 显式（防裁内容）。
 */
@Composable
fun Modifier.dialogCardShadow(shape: Shape): Modifier =
    this.shadow(8.dp, shape, clip = false)

/**
 * 弹窗统一脚手架：全屏 scrim（外部 Haze 20dp 模糊 + 25% 压暗 + 点击外部关闭）
 * + 居中卡片（默认玻璃卡：miuix textureBlur 10f + 高光描边 + tint 0.55 + 1dp 实色描边
 * + 进入动画 + 阴影；miuixBlur=false 时为不透明实心 tint 卡，无内部 miuix 模糊）。
 *
 * 卡片链序逐字对齐 SportLink BlurredAlertDialog：
 * dialogWidthAdaptive → dialogEnterAnim → dialogCardShadow → clip → textureBlur → tint → border → 点击拦截。
 * 宽度由 [dialogWidthAdaptive] 统一约束（横屏 60%/低DPI 95%/默认 90% 屏宽），调用方无需传宽度。
 *
 * 结构：scrim 为父、卡片为子，卡片空点击拦截在 [dialogGlassCard] 内（防卡片内空白
 * 点击冒泡到 scrim 误关闭）。调用方须把本组件包在 [DialogOverlay] 内（宿主 slot 处
 * 渲染，与采样源同窗口兄弟）。无宿主降级：纯压暗 scrim + 实心卡，不崩溃。
 *
 * @param modifier  卡片额外约束（可选）
 * @param shape     卡片 shape（默认全局圆角）
 * @param miuixBlur 是否启用卡片内部 miuix textureBlur 玻璃模糊（默认 true；
 *                  false = 不透明实心 tint 卡，用于今日/番茄钟页弹窗）
 * @param content   卡片内容（自带 padding）
 */
@Composable
fun BlurredDialogScaffold(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlobalCornerRadius),
    miuixBlur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = LocalDialogOverlay.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 外部 Haze 模糊：模糊层自带 25% 黑压暗底（DialogBlurStyle），替代纯压暗 scrim
            .dialogHazeEffect(state?.hazeState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = modifier
                .dialogWidthAdaptive()
                .dialogEnterAnim()
                .dialogCardShadow(shape)
                .then(
                    if (state != null) Modifier.dialogGlassCard(state, shape, miuixBlur)
                    else Modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh, shape)
                ),
        ) {
            content()
        }
    }
}
