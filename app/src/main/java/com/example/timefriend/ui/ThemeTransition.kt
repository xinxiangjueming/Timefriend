package com.example.timefriend.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 主题切换圆形揭露过渡（迁移自 SportLink：从屏幕右下角边框开始展开，过渡到整个界面）。
 *
 * 机制（纯应用内自绘，不触碰 configuration / 不 recreate——与本主题架构一致）：
 * 1. [begin]（应用内手动切换，当前无调用点、保留供扩展）/ [capture]
 *    （TimefriendTheme 闸门，系统深浅色切换）在真正生效前先 PixelCopy 截取当前窗口像素
 *    （旧主题界面；手动路径延迟 240ms 等下拉弹层收起）；
 * 2. 截图完成后写入 [snapshot] 并由调用方切主题——同批状态变更、同帧重组：
 *    下层界面已是新主题，顶层叠加「旧快照 + 右下角圆孔」覆盖层，首帧观感与切换前
 *    完全一致（旧快照全覆盖），无跳变；
 * 3. 圆孔半径 0 → 屏幕对角线（圆心=右下角顶点，最远点=左上角，800ms easeIn 前慢后快）：
 *    孔外旧快照，孔内直接透出真实新界面——被扫过的区域颜色立即变为目标颜色；
 *    圆孔边缘经 BlurMaskFilter 羽化（FEATHER_PX 带宽），新旧两色柔和渐变不硬切。
 *    孔径扫满全屏即清除快照层，无收尾淡出阶段。
 * PixelCopy 失败（极少见）→ 直接切换，无动画降级。
 */
object ThemeTransition {

    /** 切换前的旧主题全窗快照；非 null 时 [ThemeTransitionOverlay] 播放圆孔擦除动画 */
    var snapshot by mutableStateOf(null as ImageBitmap?)
        private set

    /** 当前快照持有者标记：[clear] 只在快照仍是传入项时生效。
     *  快照可能被多个 composition 的 overlay 同时播放（TimefriendTheme 已内置 overlay），
     *  必须防止某个 overlay 先播完把快照清掉、其余 overlay 提前消失导致擦除动画半途中断。 */
    private var owner: ImageBitmap? = null

    /** 动画播完由覆盖层清除快照（顶层 composable 无法访问 private setter，走此入口）；
     *  仅当快照仍是传入的 [snap] 时清除（幂等，多 overlay 并发安全） */
    fun clear(snap: ImageBitmap?) {
        if (owner != null && owner === snap) {
            snapshot = null
            owner = null
        }
    }

    /**
     * 截取当前窗口像素（旧主题界面）并登记为过渡快照，供两条触发路径复用：
     * 应用内手动切换（[begin]，前置延迟等下拉弹层收起）/ 系统深浅色切换
     * （TimefriendTheme 闸门，零延迟——状态栏下拉面板是 SystemUI 窗口，不在本 app 窗口内）。
     * @return true = 快照已就绪（含「已有快照在播」的幂等场景，直接复用不打断）；
     *         false = 截图失败（窗口未就绪 / PixelCopy 失败），调用方直接切换、无动画降级
     */
    suspend fun capture(activity: Activity, delayMs: Long = 0): Boolean {
        // 已有快照在播（动画进行中又发生新的深浅切换）：复用现快照，不重复截、不打断
        if (snapshot != null) return true
        if (delayMs > 0) delay(delayMs)
        val decor = activity.window.decorView
        if (decor.width == 0 || decor.height == 0) return false
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val copied = suspendCancellableCoroutine { cont ->
            PixelCopy.request(
                activity.window, bitmap,
                { result -> cont.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
            )
        }
        if (!copied) return false
        val snap = bitmap.asImageBitmap()
        owner = snap
        snapshot = snap
        return true
    }

    /** 从 Compose 上下文向上找到宿主 Activity（TimefriendTheme 闸门在系统切换路径使用） */
    fun activityOf(context: Context): Activity? = context.findActivity()

    /**
     * 宿主是否处于「可播动画」状态：只有**已 RESUMED 的页**才播圆孔揭露
     * （对齐 SportLink 同名判据）。
     *
     * 闸门在目标深浅变化后的首帧触发，彼时宿主必已走完 onResume——从桌面/多任务/
     * 息屏回前台的「后台错过的深浅变化」由此自然放行播动画（回前台瞬间窗口表面停留的
     * 旧主题帧正是揭露的起点画面）。唯一被拦下的是「被半透明二级页盖着、paused 但
     * 可见」的本页：用户看的是二级页（它自己播动画），底下的本页静默换装，不双层
     * 各播一遍、也避免对着被盖住的窗口截图。
     */
    fun canAnimateFrom(activity: Activity?): Boolean {
        val owner = activity as? androidx.lifecycle.LifecycleOwner ?: return false
        return !activity.isFinishing &&
            owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    }

    /**
     * 捕获旧界面并切换主题（应用内手动切换入口；当前无调用点，保留供未来扩展）。
     * @param onSwitch 真正执行切换的回调；必须幂等且在主线程生效
     */
    fun begin(context: Context, onSwitch: () -> Unit) {
        val activity = context.findActivity() ?: run { onSwitch(); return }
        CoroutineScope(Dispatchers.Main.immediate).launch {
            // 等主题下拉弹层收起动画结束（240ms），快照不含菜单残影；
            // 截图失败也照常切换（无动画降级）
            capture(activity, delayMs = 240)
            onSwitch()
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

// ==================== 过渡参数（SportLink 用户校准 2026-09-26，原样迁移） ====================
/** 总时长 ms */
private const val TRANSITION_DURATION_MS = 800
/** 圆孔边缘羽化带宽 px：被扫过的区域立即显示新界面颜色，边缘新旧两色柔和渐变 */
private const val FEATHER_PX = 48f
/**
 * 孔径曲线：easeIn 慢启动——前 500ms 缓慢扩散（easeIn 在 t=0.625 时约走到一半），
 * 后 300ms 加速扩完，节奏「前慢后快」，避免开场从右下角瞬间闪出。
 */
private val REVEAL_EASING = CubicBezierEasing(0.42f, 0f, 1f, 1f)

/**
 * 主题切换过渡覆盖层：孔外=旧界面快照，孔内=直接透出真实新界面（被扫过的区域
 * 颜色立即变为目标颜色，不垫纯色、无收尾淡出）；圆孔边缘经 [FEATHER_PX] 羽化模糊，
 * 新旧两色柔和渐变。已内置在 [com.example.timefriend.ui.theme.TimefriendTheme] 内、
 * content 之上最后声明（勿在业务页面重复声明）。动画期间拦截全部触摸，
 * 扫满全屏后自动从组合中移除。
 */
@Composable
fun ThemeTransitionOverlay() {
    val snap = ThemeTransition.snapshot ?: return
    val progress = remember(snap) { Animatable(0f) }
    // 旧快照 shader + 羽化 blur paint 只随快照构建一次（每帧只换 path）
    val snapshotPaint = remember(snap) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.BitmapShader(
                snap.asAndroidBitmap(),
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP,
            )
            maskFilter = android.graphics.BlurMaskFilter(
                FEATHER_PX,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
    }
    LaunchedEffect(snap) {
        progress.animateTo(1f, tween(durationMillis = TRANSITION_DURATION_MS, easing = REVEAL_EASING))
        ThemeTransition.clear(snap)
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(snap) {
                // 动画期间吞掉所有触摸，避免误操作；覆盖层移除时协程随之取消
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        // 圆心=右下角顶点，最远可见点=左上角；留 5% 余量补 blur 内缩的圆边（175px > FEATHER_PX）
        val maxR = sqrt(w * w + h * h) * 1.05f
        val r = maxR * progress.value
        // ⚠️ 外框矩形必须放大到屏幕外（margin ≥ 2×blur）：BlurMaskFilter 作用于 path 的
        // 所有边缘——矩形边若贴着屏幕边界，blur 会向屏幕内扩散，四周出现一圈旧色羽化
        // 残留直到动画结束（SportLink 真机反馈 bug）。放大后矩形边的模糊落在屏幕外，
        // 只有圆孔边缘在屏幕内羽化。屏幕外区域由 CLAMP shader 采样快照边缘像素，颜色正确。
        val margin = FEATHER_PX * 2f
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-margin, -margin, w + margin, h + margin))
            addOval(Rect(center = Offset(w, h), radius = r))
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(outside.asAndroidPath(), snapshotPaint)
        }
    }
}
