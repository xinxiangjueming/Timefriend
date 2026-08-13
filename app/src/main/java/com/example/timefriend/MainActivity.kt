package com.example.timefriend

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.timefriend.ui.screen.MainScreen
import com.example.timefriend.ui.theme.TimefriendTheme
import com.example.timefriend.util.NavigationBarHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NavigationBarHelper.setupEdgeToEdge(
            this,
            lightStatusBar = !NavigationBarHelper.isNightMode(this)
        )

        // Debug: dump window state
        val w = window
        val navBarColor = w.navigationBarColor
        val statusBarColor = w.statusBarColor
        val flags = w.attributes.flags
        val systemUiVisibility = w.attributes.systemUiVisibility
        val decorBg = w.decorView.background
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        val navBarContrast = controller.isAppearanceLightNavigationBars

        Log.d("NavBarDebug", "=== Window State ===")
        Log.d("NavBarDebug", "navigationBarColor=#${Integer.toHexString(navBarColor)}")
        Log.d("NavBarDebug", "statusBarColor=#${Integer.toHexString(statusBarColor)}")
        Log.d("NavBarDebug", "flags=0x${Integer.toHexString(flags)}")
        Log.d("NavBarDebug", "systemUiVisibility=0x${Integer.toHexString(systemUiVisibility)}")
        Log.d("NavBarDebug", "decorBg=${decorBg?.javaClass?.simpleName ?: "null"}")
        Log.d("NavBarDebug", "isAppearanceLightNavBars=$navBarContrast")
        Log.d("NavBarDebug", "fitsSystemWindows=${w.decorView.fitsSystemWindows}")

        // Check decoration view children for background
        val decorView = w.decorView as android.view.ViewGroup
        for (i in 0 until decorView.childCount) {
            val child = decorView.getChildAt(i)
            Log.d("NavBarDebug", "decor child[$i]: ${child.javaClass.simpleName} bg=${child.background?.javaClass?.simpleName ?: "null"} fitsSystemWindows=${child.fitsSystemWindows}")
        }

        setContent {
            TimefriendTheme {
                MainScreen()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转/深浅色切换后重新应用 edge-to-edge。
        // configChanges 声明了 orientation|screenSize 等,旋转不重建 Activity,
        // 只回调本方法;若不在此重设,系统会按主题/默认值重放窗口属性(如导航栏颜色),
        // 小白条退回不透明白底。
        val light = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        NavigationBarHelper.setupEdgeToEdge(this, lightStatusBar = light)
        // 系统可能在配置变更后按主题重放窗口属性(如导航栏颜色),
        // 延迟一帧再设一次,确保重放之后仍是透明导航栏。
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            NavigationBarHelper.setupEdgeToEdge(this, lightStatusBar = light)
        }
        // 注意:不能调 recreate()(会丢输入/滚动状态并闪烁);
        // Compose 通过 LocalConfiguration / WindowInsets 自动响应旋转。
        // 180° 翻转(landscape↔reverseLandscape)不回调本方法,由 NavigationBarHelper
        // 内的 decorView insets 监听兜底,无需在此处理。
    }
}
