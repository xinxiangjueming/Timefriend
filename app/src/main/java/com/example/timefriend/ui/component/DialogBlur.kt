package com.example.timefriend.ui.component

import android.os.Build
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight as LiquidHighlight
import com.kyant.shapes.RoundedRectangle
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
 * 弹窗视觉统一封装 —— 按系统版本自适应两套链路（用户 2026-11-07 定调）：
 *
 * 【高版本 API 33+】完全照 Kyant Backdrop 官方
 * `catalog/destinations/DialogContent.kt`：
 * - 单一 kyant layerBackdrop 采样源：页面内容 + 压暗层（dim 画在内容层内部，
 *   既显示在屏幕上、又被 backdrop 录制，卡片折射的是「已压暗的背景」）；
 * - 卡片 `drawBackdrop`：`colorControls(brightness 浅0.2/深0, saturation 1.5)`
 *   → `blur(浅16dp/深8dp)` → `lens(24dp, 48dp, depthEffect = true)`，
 *   `highlight = Highlight.Plain`，`onDrawSurface = containerColor`；
 * - 官方 shape = `RoundedRectangle(48dp)`（G2 连续圆角；lens 的折射高度
 *   24dp 必须 ≤ 最小圆角）；
 * - 不再有 Haze 外部 scrim，浮层只保留点击外部关闭。所有弹窗统一玻璃卡。
 *
 * 【低版本 < API 33】原样保留 2026-10-13 定稿的 SportLink 架构：
 * - 外部 Haze 20dp 模糊 + 25% 压暗 scrim；
 * - 卡片 miuix textureBlur 10f + 高光描边 + tint 0.55（miuixBlur = true），
 *   或实心 tint 卡（miuixBlur = false，今日 / 番茄钟页弹窗）。
 *
 * 阈值依据：kyant `lens` 依赖 RuntimeShader（API 33 / TIRAMISU），与库自身
 * `isRuntimeShaderSupported()` 一致。本项目 minSdk = 33，当前设备恒走高版本分支；
 * 低版本分支是将来下调 minSdk 或 RuntimeShader 不可用时的兜底。
 *
 * 共同铁律：模糊消费方必须与采样源是同窗口兄弟节点；嵌套调用点的弹窗经
 * [DialogOverlay] 注册到宿主 slot 渲染，禁止自采样循环（官方明确会 SIGSEGV）。
 */

/**
 * 是否启用官方液态玻璃弹窗链路。
 * 与 backdrop 库 `isRuntimeShaderSupported()` 同一门槛（TIRAMISU = API 33）。
 */
val SupportsLiquidGlassDialog: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 官方 DialogContent：卡片表面底色（浅 #FAFAFA@0.6 / 深 #121212@0.4） */
val DialogContainerColorLight = Color(0xFFFAFAFA).copy(0.6f)
val DialogContainerColorDark = Color(0xFF121212).copy(0.4f)

/** 官方 DialogContent：画进 backdrop 采样源的压暗层（浅 #29293A@0.23 / 深 #121212@0.56） */
val DialogDimColorLight = Color(0xFF29293A).copy(0.23f)
val DialogDimColorDark = Color(0xFF121212).copy(0.56f)

fun dialogDimColor(isLightTheme: Boolean): Color =
    if (isLightTheme) DialogDimColorLight else DialogDimColorDark

/** 官方 DialogContent 的 48dp G2 连续圆角；RoundedRectangularShape 可被 lens 识别 */
val DialogLiquidShape: Shape = RoundedRectangle(48f.dp)

// ── 低版本链路（miuix + Haze，逐字保留 2026-10-13 定稿） ────────────────────

/** 弹窗玻璃卡模糊半径（dp，对齐 SportLink 2026-09-01 定稿 10f） */
const val DialogGlassBlurRadius = 10f

/** 玻璃卡 tint 不透明度（叠加在模糊层之上，对齐 SportLink 定稿 0.55） */
const val DialogGlassTintAlpha = 0.55f

/** 玻璃卡高光描边带宽（dp，对齐 SportLink 定稿 1.5） */
const val DialogGlassHighlightWidth = 1.5f

val DialogGlassHighlightLight = Highlight(
    width = DialogGlassHighlightWidth.dp,
    style = Highlight.GlassStrokeMiddleLight.style,
)
val DialogGlassHighlightDark = Highlight(
    width = DialogGlassHighlightWidth.dp,
    style = Highlight.GlassStrokeMiddleDark.style,
)

val DialogGlassBorderWidth = 1.dp
val DialogGlassBorderLight = Color(0x33000000)
val DialogGlassBorderDark = Color(0x33FFFFFF)

val DialogGlassTintColorLight = Color.White
val DialogGlassTintColorDark = Color(0xFF3A3A3A)

/** 低版本外部 Haze 毛玻璃样式：25% 黑压暗底 + 20dp 模糊 */
val DialogBlurStyle: HazeStyle = HazeStyle(
    backgroundColor = Color.Black.copy(alpha = 0.25f),
    blurRadius = 20.dp,
    tint = null,
)

@OptIn(ExperimentalHazeApi::class)
fun Modifier.dialogHazeEffect(hazeState: HazeState?): Modifier =
    if (hazeState != null) this.hazeEffect(state = hazeState, style = DialogBlurStyle) {
        clipToAreasBounds = false
    } else this.background(Color.Black.copy(alpha = 0.25f))

/** 低版本：仅弹窗活跃时挂 Haze 采样源（同源双离屏录制） */
fun Modifier.dialogHazeSource(state: DialogOverlayState): Modifier =
    then(if (state.openCount > 0) Modifier.hazeSource(state.hazeState) else Modifier)

// ── 版本自适应的采样源 / scrim ────────────────────────────────────────────

/**
 * 内容层采样源（挂首页内容 Column，位于 `liquidLayerBackdrop` 之后）：
 * - 高版本：把官方 dim 压暗层画在内容之上，屏幕显示压暗且被 kyant backdrop 录制；
 * - 低版本：保留 Haze 采样源。
 * 两者都只在弹窗活跃时生效，避免常驻 GPU 开销。
 */
fun Modifier.dialogScrimSource(state: DialogOverlayState, dimColor: Color): Modifier =
    if (state.openCount <= 0) this
    else if (SupportsLiquidGlassDialog) {
        this.drawWithContent {
            drawContent()
            drawRect(dimColor)
        }
    } else {
        this.dialogHazeSource(state)
    }

/**
 * 弹窗浮层 scrim：
 * - 高版本：压暗已在采样源内，浮层只负责点击外部关闭（无宿主时才降级压暗）；
 * - 低版本：Haze 20dp 模糊 + 25% 压暗。
 */
fun Modifier.dialogScrimEffect(state: DialogOverlayState?): Modifier =
    if (SupportsLiquidGlassDialog) {
        if (state != null) this else this.background(Color.Black.copy(alpha = 0.25f))
    } else {
        this.dialogHazeEffect(state?.hazeState)
    }

// ── 弹窗注册 / 宿主 slot（两套链路共用） ──────────────────────────────────

/** 单个 slot 条目：content 可观察，每次弹窗重组更新引用，驱动宿主 slot 渲染最新内容 */
class DialogEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

/**
 * 弹窗浮层宿主状态。
 * @param backdrop miuix 采样源（低版本卡片 / Toast 共用）
 * @param hazeState Haze 外部模糊采样状态（低版本 scrim）
 * @param liquidBackdrop kyant Backdrop 采样源（高版本官方液态玻璃卡片）
 */
class DialogOverlayState(
    val backdrop: LayerBackdrop,
    val hazeState: HazeState,
    val liquidBackdrop: Backdrop,
) {
    /** 当前活跃弹窗数；> 0 才挂采样源 / 压暗层 */
    var openCount by mutableIntStateOf(0)

    /** slot 注册表：弹窗浮层按注册顺序叠加渲染 */
    val entries = mutableStateListOf<DialogEntry>()
}

/** 供页面/弹窗读取的宿主状态；无宿主时为 null（就地渲染兜底，保持可用） */
val LocalDialogOverlay = staticCompositionLocalOf<DialogOverlayState?> { null }

/**
 * 弹窗浮层注册入口：把弹窗内容注册到宿主 slot（渲染为采样源兄弟节点）。
 * 无宿主（LocalDialogOverlay 为 null）→ 就地渲染兜底。
 */
@Composable
fun DialogOverlay(content: @Composable () -> Unit) {
    val state = LocalDialogOverlay.current
    if (state == null) {
        content()
        return
    }
    val entry = remember { DialogEntry() }
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
 * 宿主 slot 渲染入口：挂在页面根，与采样源所在节点呈兄弟。
 * 用 entry 对象作 key：不同弹窗同帧切换时强制全新组合。
 */
@Composable
fun DialogOverlaySlot(state: DialogOverlayState) {
    for (entry in state.entries) {
        key(entry) { entry.content() }
    }
}

/**
 * 弹窗卡片统一视觉（版本自适应）。
 *
 * 高版本（官方 DialogContent）：kyant `drawBackdrop` 折射含压暗层的背景，
 * 效果链 `colorControls → blur → lens(depthEffect)` + `Highlight.Plain`
 * + `onDrawSurface(containerColor)`；不额外裁剪（drawBackdrop 内部按 shape 裁剪，
 * 外层 clip 会裁掉 blur/lens 的边缘采样区）。所有弹窗一致，`miuixBlur` 被忽略。
 *
 * 低版本：链序逐字保留 SportLink 定稿 —— clip → textureBlur → tint → border
 * → 点击拦截；`miuixBlur = false` 为不透明实心卡。
 */
@Composable
fun Modifier.dialogGlassCard(
    state: DialogOverlayState,
    shape: Shape,
    miuixBlur: Boolean = true,
): Modifier {
    val isDark = isSystemInDarkTheme()

    if (SupportsLiquidGlassDialog) {
        val containerColor =
            if (isDark) DialogContainerColorDark else DialogContainerColorLight
        return this
            .drawBackdrop(
                backdrop = state.liquidBackdrop,
                shape = { shape },
                effects = {
                    colorControls(
                        brightness = if (isDark) 0f else 0.2f,
                        saturation = 1.5f
                    )
                    blur((if (isDark) 8f.dp else 16f.dp).toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                },
                highlight = { LiquidHighlight.Plain },
                onDrawSurface = { drawRect(containerColor) }
            )
            .border(
                width = DialogGlassBorderWidth,
                color = if (isDark) DialogGlassBorderDark else DialogGlassBorderLight,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { }
    }

    val tintColor = if (isDark) DialogGlassTintColorDark else DialogGlassTintColorLight
    return this
        .clip(shape)
        .then(
            if (miuixBlur) {
                Modifier.textureBlur(
                    state.backdrop, shape,
                    blurRadius = DialogGlassBlurRadius,
                    highlight = if (isDark) DialogGlassHighlightDark else DialogGlassHighlightLight,
                )
            } else {
                Modifier
            }
        )
        .background(
            if (miuixBlur) tintColor.copy(alpha = DialogGlassTintAlpha) else tintColor
        )
        .border(
            width = DialogGlassBorderWidth,
            color = if (isDark) DialogGlassBorderDark else DialogGlassBorderLight,
            shape = shape,
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { }
}

/** 弹窗进入动画：alpha 0→1 + scale 0.9→1（150ms，对齐 SportLink dialogEnterAnim） */
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

/** 弹窗宽度限制（横屏 60% / 低 DPI 95% / 默认 90% 屏宽） */
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
 * 弹窗卡片 8dp 柔和阴影（旧 shadow API + clip=false）。
 * ⚠️ 勿改用 Compose 1.9 dropShadow：与 miuix textureBlur 组合实机出现方形偏移模糊层。
 */
@Composable
fun Modifier.dialogCardShadow(shape: Shape): Modifier =
    this.shadow(8.dp, shape, clip = false)

/**
 * 弹窗统一脚手架：全屏 scrim（按版本选择 Haze / 官方透明 scrim + 内容层压暗）
 * + 居中卡片。宽度 / 进入动画 / 阴影 / 点击拦截 / BackHandler 为项目定稿，保留不变。
 * 调用方须把本组件包在 [DialogOverlay] 内（宿主 slot 处渲染，与采样源同窗口兄弟）。
 *
 * @param miuixBlur 仅低版本生效：false = 不透明实心卡（今日 / 番茄钟页弹窗）；
 *                  高版本所有弹窗统一为官方液态玻璃卡，忽略此参数。
 */
@Composable
fun BlurredDialogScaffold(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = if (SupportsLiquidGlassDialog) {
        DialogLiquidShape
    } else {
        RoundedCornerShape(GlobalCornerRadius)
    },
    miuixBlur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = LocalDialogOverlay.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dialogScrimEffect(state)
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
