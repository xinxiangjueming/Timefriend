package com.example.timefriend.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat

/**
 * 统一沉浸式系统栏入口(所有调用方唯一入口)。
 *
 * 1. enableEdgeToEdge(状态栏/导航栏双 auto(TRANSPARENT)) → 内容铺满全屏。
 * 2. 显式 navigationBarColor/statusBarColor = TRANSPARENT + 关闭 contrast(API>=Q):
 *    - 覆盖 androidx.activity 在 API 35 上 auto() 对导航栏 contrast(enforced=true) 的副作用(浅色白底遮罩);
 *    - 覆盖部分 ROM 配置变更后按系统默认值重放的不透明导航栏。
 * 3. decorView 注册 ViewCompat.setOnApplyWindowInsetsListener 兜底:
 *    - Android 12+ landscape↔reverseLandscape(portrait↔reversePortrait) 180° 翻转不回调
 *      onConfigurationChanged(Configuration.orientation 值不变),但系统栏 insets 必然变化
 *      (手势条换边/状态栏换边),监听 insets 变化后 post 一帧重放透明设置。
 *    - listener 内只调幂等的 applyEdgeToEdgeWindowProperties,不递归调本方法(避免覆盖 listener 成环)。
 *    - 返回原始 insets 不消费(Compose / 其他监听器仍需)。
 *
 * @param lightStatusBar 状态栏图标是否"深色图标"(浅色背景)。深色模式传 false(白图标)。
 *   null = 不干预图标明暗(跟随系统主题)。旋转/深浅色切换后重复调用会覆盖 listener 并刷新参数,不会累积。
 */
@Suppress("DEPRECATION")
object NavigationBarHelper {

    fun setupEdgeToEdge(activity: ComponentActivity, lightStatusBar: Boolean? = null) {
        val window = activity.window

        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        applyEdgeToEdgeWindowProperties(activity, lightStatusBar)

        // 180° 翻转(landscape↔reverseLandscape / portrait↔reversePortrait)时,
        // Android 12+ 不回调 onConfigurationChanged(Configuration.orientation 值不变),
        // 各 Activity onConfigurationChanged 里的重应用不执行,系统按默认值重放导航栏
        // 状态 → 小白条被白底包裹。旋转必然改变系统栏 insets(手势条换边/状态栏换边),
        // 故监听 decorView insets 变化,重放透明设置兜底。
        // 重复调用 setupEdgeToEdge 会覆盖此 listener,不会累积。
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            view.post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    applyEdgeToEdgeWindowProperties(activity, lightStatusBar)
                }
            }
            insets // 不消费:Compose / 其他监听器仍需原始 insets
        }
    }

    /** 幂等重放窗口层透明设置(只设窗口属性,不注册任何监听器——listener 内不可递归调 setupEdgeToEdge)。 */
    @Suppress("DEPRECATION")
    private fun applyEdgeToEdgeWindowProperties(activity: ComponentActivity, lightStatusBar: Boolean?) {
        val window = activity.window
        // 必须在 enableEdgeToEdge() 之后显式设置系统栏透明 + 关闭 contrast:
        // Android 15 (API 35) 上 SystemBarStyle.auto() 的 nightMode==0 会使
        // enableEdgeToEdge() 内部调用 setNavigationBarContrastEnforced(true),
        // 浅色模式导航栏会显示不透明白色遮罩;显式设置同时覆盖部分 ROM
        // 在配置变更(旋转)后按系统默认值重放的不透明白色导航栏。
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (lightStatusBar != null) {
            setStatusBarAppearance(activity, lightStatusBar)
        }
    }

    private fun setStatusBarAppearance(activity: Activity, light: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController ?: return
            controller.setSystemBarsAppearance(
                if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            // 按位保留现有 flags(LAYOUT_STABLE / LAYOUT_HIDE_NAVIGATION / LAYOUT_FULLSCREEN),
            // 只切换 LIGHT_STATUS_BAR 位,避免覆盖式赋值清掉 edge-to-edge 沉浸状态。
            @Suppress("DEPRECATION")
            val decor = activity.window.decorView
            @Suppress("DEPRECATION")
            val lightFlag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            decor.systemUiVisibility =
                if (light) decor.systemUiVisibility or lightFlag
                else decor.systemUiVisibility and lightFlag.inv()
        }
    }

    /** 与 Compose isSystemInDarkTheme() 一致的系统深色模式判断(读取 uiMode)。 */
    fun isNightMode(activity: Activity): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
