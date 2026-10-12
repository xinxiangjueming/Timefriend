package com.example.timefriend.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.timefriend.ui.ThemeTransition
import com.example.timefriend.ui.ThemeTransitionOverlay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val LightGrayBackground = Color(0xFFF0F0F0)
private val LightGraySurface = Color(0xFFE8E8E8)

@Composable
fun TimefriendTheme(
    // 目标深浅：默认读 LocalConfiguration（本函数位于根部 provider 之外，读到的恒为
    // 系统真值）。此参数是「目标深浅」，经下方闸门延迟生效；全部渲染取材见 appliedDark。
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 主题切换圆形揭露过渡开关（ThemeTransition）：仅特殊场景显式关掉 */
    themeTransition: Boolean = true,
    content: @Composable () -> Unit
) {
    // ── 主题切换闸门（迁移自 SportLink SportLinkTheme）────────────────────
    // 「目标深浅」targetDark 与「已生效深浅」appliedDark 分离：目标变化时先截旧界面
    // 快照（ThemeTransition 圆孔擦除动画用）再放行，全部渲染取材以 appliedDark 为准——
    // 系统切换深浅色（本应用纯跟随系统）统一播放「旧快照 + 右下角圆孔扩散露出新主题」
    // 动画，消除硬切。首次组合 appliedDark 即终值，无动画。
    var appliedDark by remember { mutableStateOf(darkTheme) }
    val view = LocalView.current

    // ── 回前台兜底（对齐 SportLink 2026-09-29 方案）─────────────────────
    // 后台期间系统切换深浅色的已知坑：ViewRootImpl 不向已停止的窗口分发配置，
    // LocalConfiguration 停在旧值 → darkTheme 参数读不到变化 → 闸门永不触发 =
    // 回前台颜色不变（真机 bug）。宿主 resources 的 uiMode 在 onResume 时已是系统
    // 真值，这里在 ON_RESUME 读真值落「兜底覆盖」resumeOverride；与参数一致时落 null
    // 不参与（前台系统切换等参数可靠场景零影响）。
    var resumeOverride by remember { mutableStateOf(null as Boolean?) }
    val targetDark = resumeOverride ?: darkTheme

    val hostActivity = remember(view) { ThemeTransition.activityOf(view.context) }
    DisposableEffect(hostActivity, darkTheme) {
        val activity = hostActivity
        val owner = activity as? LifecycleOwner
        // owner == null 已涵盖 activity == null（as? 对 null 返回 null）
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val fresh = isNightModeOf(activity)
                resumeOverride = if (fresh != darkTheme) fresh else null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(targetDark) {
        if (appliedDark == targetDark) return@LaunchedEffect
        if (!themeTransition) { appliedDark = targetDark; return@LaunchedEffect }
        // 已有快照在播（动画未播完又发生新切换）：直接放行，overlay 继续播
        if (ThemeTransition.snapshot != null) { appliedDark = targetDark; return@LaunchedEffect }
        // 宿主未 RESUMED（被半透明二级页盖着、paused 但可见）：不截图、不播动画，直接
        // 放行——不双层各播一遍，也避免对着被盖住的窗口截图。回前台场景（从桌面/
        // 多任务/息屏返回）闸门在 onResume 之后的首帧才触发，宿主必已 RESUMED →
        // 走截图 + 圆孔揭露（后台错过的变化回前台也播动画，不硬切）。
        val host = ThemeTransition.activityOf(view.context)
        if (host == null || !ThemeTransition.canAnimateFrom(host)) {
            appliedDark = targetDark
            return@LaunchedEffect
        }
        // 系统切换路径：立即截旧界面；截图失败则无动画直接切（降级不劣于现状）
        ThemeTransition.capture(host)
        appliedDark = targetDark
    }

    // 参数目标刷新即撤销兜底覆盖：darkTheme 随前台配置分发更新时，参数比兜底覆盖新
    // （撤销后 targetDark 取参数值；若与已落地的 appliedDark 相同，闸门不重放）。
    // 兜底场景参数停在旧值不动，本 effect 不重跑，覆盖存续至下一次 ON_RESUME 重估。
    LaunchedEffect(darkTheme) {
        if (resumeOverride != null) resumeOverride = null
    }

    val colors = if (appliedDark) {
        // 仅覆写页面底色/卡面色；surfaceContainer 系列保持 miuix 官方默认（浅 #F2F2F2/#E8E8E8、
        // 深 #323232/#242424）——弹窗取消键底色直接取 surfaceContainerHigh，对齐 SportLink
        // （sportlink miuix 色板无覆写；2026-10-13 用户反馈按钮填充色不一致，覆写是根因）。
        darkColorScheme(
            background = Color(0xFF1A1A1A),
            surface = Color(0xFF222222)
        )
    } else {
        lightColorScheme(
            background = LightGrayBackground,
            surface = LightGraySurface
        )
    }

    // 系统栏图标明暗以 appliedDark（闸门后已生效深浅）为准，与圆孔揭露动画同步切换；
    // 不读系统真值——否则图标在动画放行前就翻转，与界面脱节。
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !appliedDark
            controller.isAppearanceLightNavigationBars = !appliedDark
        }
    }

    // LocalConfiguration 深色位覆盖：绕过本函数直接调 isSystemInDarkTheme() 的读点
    // （DialogBlur.kt 等）以 appliedDark 为基准——与圆孔擦除动画同步切换，否则会在
    // 系统切换瞬间硬切。只改 NIGHT 位，density/locale/fontScale 等其余字段原样复制；
    // 基准是根部的系统真实 configuration。LocalConfiguration 是 staticCompositionLocalOf：
    // 覆盖值变化 → 整个子树重组（放行瞬间的一次性成本），无需 recreate。
    val baseConfig = LocalConfiguration.current
    val appConfig = remember(baseConfig, appliedDark) {
        Configuration(baseConfig).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (appliedDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }

    CompositionLocalProvider(LocalConfiguration provides appConfig) {
        MiuixTheme(
            colors = colors,
        ) {
            // Box 不加约束：尺寸由 content 决定（全屏 content → 覆盖层全屏）。
            // 覆盖层最后声明盖在 content 之上，动画期间拦截全部触摸、播完自动移除。
            Box {
                content()
                if (themeTransition) ThemeTransitionOverlay()
            }
        }
    }
}

/** 系统深色真值（activity resources 的 uiMode；等价 NavigationBarHelper.isNightMode） */
private fun isNightModeOf(activity: Activity): Boolean =
    (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
