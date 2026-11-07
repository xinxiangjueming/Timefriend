package com.example.timefriend.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timefriend.R
import com.example.timefriend.ui.component.BlurToast
import com.example.timefriend.ui.component.DialogOverlaySlot
import com.example.timefriend.ui.component.DialogOverlayState
import com.example.timefriend.ui.component.LocalDialogOverlay
import com.example.timefriend.ui.component.dialogDimColor
import com.example.timefriend.ui.component.dialogScrimSource
import com.example.timefriend.ui.theme.GlobalCornerRadius
import dev.chrisbanes.haze.HazeState
import com.example.timefriend.viewmodel.HistoryViewModel
import com.example.timefriend.viewmodel.MainViewModel
import com.example.timefriend.viewmodel.TimerEffect
import com.example.timefriend.viewmodel.TimerViewModel
import com.example.timefriend.viewmodel.WebDavViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.example.timefriend.ui.component.LiquidBottomTab
import com.example.timefriend.ui.component.LiquidBottomTabs
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLiquidBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings

@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel(),
    timerViewModel: TimerViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    webDavViewModel: WebDavViewModel = viewModel()
) {
    val mainState by mainViewModel.state.collectAsState()
    val timerState by timerViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val webDavState by webDavViewModel.state.collectAsState()
    val backdrop = rememberLayerBackdrop()
    // Kyant Backdrop 液态玻璃采样层：官方教程要求显式铺满背景色，否则底栏
    // 边缘会折射到「背景之外的透明像素」；与 miuix backdrop 并列录制，同窗口兄弟消费
    val backgroundColor = MiuixTheme.colorScheme.background
    val liquidBackdrop = rememberLiquidBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // 官方 DialogContent 口径：弹窗活跃时把压暗层画进内容（屏幕显示且被 kyant
    // backdrop 录制，卡片折射的是已压暗的背景）；低版本仍走 Haze 外部模糊。
    // 分支判定在 DialogBlur.kt 的 dialogScrimSource / dialogScrimEffect 内。
    val dimColor = dialogDimColor(!isSystemInDarkTheme())
    val dialogOverlayState = remember { DialogOverlayState(backdrop, HazeState(), liquidBackdrop) }
    val context = LocalContext.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var topTab by remember { mutableIntStateOf(0) }
    var settingsScrollingUp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        timerViewModel.effect.collect { effect ->
            if (effect is TimerEffect.TooShort) {
                toastMessage = null
                toastMessage = context.getString(R.string.toast_too_short)
            }
        }
    }

    CompositionLocalProvider(LocalDialogOverlay provides dialogOverlayState) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: content captured for blur backdrop（miuix layerBackdrop 常驻供
        // 低版本弹窗/Toast；kyant liquidLayerBackdrop 常驻供底栏与高版本液态玻璃弹窗；
        // dialogScrimSource 仅弹窗活跃时挂：高版本画官方 dim 压暗进采样源，低版本挂 Haze 源）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .layerBackdrop(backdrop)
                .liquidLayerBackdrop(liquidBackdrop)
                .dialogScrimSource(dialogOverlayState, dimColor)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize().weight(1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // 设置页：常驻 AnimatedVisibility，右入右出；zIndex 保证盖住常驻主内容
                    androidx.compose.animation.AnimatedVisibility(
                        visible = mainState.showSettings,
                        enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)) { it },
                        exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)) { it },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.background)
                        ) {
                        val pm = context.getSystemService(android.os.PowerManager::class.java)
                        var batteryOptimized by remember { mutableStateOf(false) }
                        var notificationGranted by remember { mutableStateOf(false) }
                        var accessibilityEnabled by remember { mutableStateOf(false) }

                        fun isAccessibilityServiceEnabled(): Boolean {
                            val service = context.packageName + "/" + com.example.timefriend.service.AppAccessibilityService::class.java.canonicalName
                            val enabledServices = android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                            ) ?: return false
                            return enabledServices.split(":").any { it.equals(service, ignoreCase = true) }
                        }

                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    batteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                                    notificationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    accessibilityEnabled = isAccessibilityServiceEnabled()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                        ) { granted -> notificationGranted = granted }

                        SettingsScreen(
                            server = webDavState.webDavServer,
                            username = webDavState.webDavUsername,
                            password = webDavState.webDavPassword,
                            syncStatus = webDavState.syncStatus,
                            statusMessage = webDavState.syncMessage,
                            batteryOptimized = batteryOptimized,
                            notificationGranted = notificationGranted,
                            accessibilityEnabled = accessibilityEnabled,
                            onServerChange = { webDavViewModel.updateServer(it) },
                            onUsernameChange = { webDavViewModel.updateUsername(it) },
                            onPasswordChange = { webDavViewModel.updatePassword(it) },
                            onBackup = { webDavViewModel.backup() },
                            onSync = { webDavViewModel.sync() },
                            onRequestBattery = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            onRequestNotification = {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onRequestAccessibility = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            onBack = {
                                webDavViewModel.saveSettings()
                                webDavViewModel.resetStatus()
                                mainViewModel.hideSettings()
                            },
                            onScrollingUp = { settingsScrollingUp = it },
                            blurLevel = mainState.blurLevel,
                            onBlurLevelChange = { mainViewModel.updateBlurLevel(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }

                    // 主内容：常驻组合（不随 showSettings 移除，今日/历史滚动等状态保持）
                    // settingsAnim: 0f=主内容可见; 1f=设置页打开（左移出屏+淡出+触摸拦截）
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val widthPx = with(density) { maxWidth.toPx() }
                        val settingsAnim = remember { Animatable(0f) }
                        LaunchedEffect(mainState.showSettings) {
                            settingsAnim.animateTo(
                                if (mainState.showSettings) 1f else 0f,
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)
                            )
                        }
                        // 主内容整体容器：平移出屏 + 淡出 + 触摸拦截（设置页 zIndex 1 命中靠前不受影响）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = -widthPx * settingsAnim.value
                                    alpha = 1f - 0.5f * settingsAnim.value
                                }
                                .pointerInput(mainState.showSettings) {
                                    if (mainState.showSettings) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                                e.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                        ) {
                        val slideAnim = remember { Animatable(if (mainState.currentTab == 1) 1f else 0f) }
                        var hasAnimated by remember { mutableStateOf(false) }
                        LaunchedEffect(mainState.currentTab) {
                            val target = if (mainState.currentTab == 1) 1f else 0f
                            if (hasAnimated) {
                                slideAnim.animateTo(target, animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f))
                            } else {
                                slideAnim.snapTo(target)
                                hasAnimated = true
                            }
                        }
                        // —— 历史页：从右滑入 / 向右滑出 ——
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (mainState.currentTab == 1) 2f else 0f)
                                .graphicsLayer {
                                    translationX = widthPx * (1f - slideAnim.value)
                                    alpha = 0.35f + 0.65f * slideAnim.value
                                }
                                .pointerInput(mainState.currentTab) {
                                    if (mainState.currentTab != 1) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                                e.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                        ) {
                            HistoryScreen(
                                records = historyState.historyRecords,
                                weekRecords = historyState.weekRecords,
                                monthRecords = historyState.monthRecords,
                                selectedDate = historyState.selectedDate,
                                recordDates = historyState.recordDates,
                                pomodoroWeek = historyViewModel.pomodoroWeek.collectAsState().value,
                                onSelectDate = { historyViewModel.selectDate(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // —— 今日页：向左滑出 / 从左滑入（顶栏随页面整体滑动，被历史/设置页覆盖）——
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (mainState.currentTab == 0) 2f else 0f)
                                .graphicsLayer {
                                    translationX = -widthPx * slideAnim.value
                                    alpha = 1f - 0.65f * slideAnim.value
                                }
                                .pointerInput(mainState.currentTab) {
                                    if (mainState.currentTab != 0) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                                e.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // 顶栏：今日/番茄钟 切换器——overlay 悬浮在页面顶部（zIndex 保证
                                // 绘制/命中在滚动内容之上），不再结构占位：内容区从页面顶开始
                                //（含状态栏区域），滚动后内容可显示在状态栏区域
                                //（对齐 sportlink 沉浸滚动语义）
                                run {
                                    val topTabLabels = listOf(
                                        stringResource(R.string.top_today),
                                        stringResource(R.string.top_pomodoro)
                                    )
                                    val topTabAnim = remember { Animatable(0f) }
                                    val topTabDensity = LocalDensity.current
                                    LaunchedEffect(topTab) {
                                        val innerW = with(topTabDensity) { (200.dp - 4.dp * 2).toPx() }
                                        val chipW = innerW / topTabLabels.size
                                        topTabAnim.animateTo(
                                            chipW * topTab,
                                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .zIndex(1f)
                                            .windowInsetsPadding(WindowInsets.statusBars)
                                            .padding(top = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(200.dp)
                                                .clip(RoundedCornerShape(GlobalCornerRadius))
                                                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
                                                .padding(4.dp)
                                        ) {
                                            // Pill
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.5f)
                                                    .height(32.dp)
                                                    .offset { IntOffset(topTabAnim.value.toInt(), 0) }
                                                    .align(Alignment.CenterStart)
                                                    .clip(RoundedCornerShape(GlobalCornerRadius))
                                                    .background(MiuixTheme.colorScheme.primary)
                                            )
                                            // Labels
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                topTabLabels.forEachIndexed { index, label ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable(
                                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                                indication = null
                                                            ) { topTab = index }
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (topTab == index) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (topTab == index) MiuixTheme.colorScheme.onPrimary
                                                                    else MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                // Nested slide+fade: Today / Pomodoro（顶栏切换，与底栏切换同款滑动+淡化）
                                // 内容区横向滑动亦可切换今日/番茄钟（与日历切月同款累计 80px 阈值；
                                // topTab 为状态委托，手势闭包读取的恒为最新值）
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDrag = 0f },
                                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                                onDragEnd = {
                                                    if (totalDrag < -80f && topTab == 0) {
                                                        topTab = 1
                                                    } else if (totalDrag > 80f && topTab == 1) {
                                                        topTab = 0
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    val topContentAnim = remember { Animatable(if (topTab == 1) 1f else 0f) }
                                    var hasTopTabAnimated by remember { mutableStateOf(false) }
                                    LaunchedEffect(topTab) {
                                        val target = if (topTab == 1) 1f else 0f
                                        if (hasTopTabAnimated) {
                                            topContentAnim.animateTo(
                                                target,
                                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)
                                            )
                                        } else {
                                            topContentAnim.snapTo(target)
                                            hasTopTabAnimated = true
                                        }
                                    }
                                    // 番茄钟（专注）：从右滑入 / 向右滑出
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(if (topTab == 1) 2f else 0f)
                                            .graphicsLayer {
                                                translationX = widthPx * (1f - topContentAnim.value)
                                                alpha = 0.35f + 0.65f * topContentAnim.value
                                            }
                                            .pointerInput(topTab) {
                                                if (topTab != 1) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val e = awaitPointerEvent(PointerEventPass.Initial)
                                                            e.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        PomodoroScreen(
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // 今日：向左滑出 / 从左滑入
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(if (topTab == 0) 2f else 0f)
                                            .graphicsLayer {
                                                translationX = -widthPx * topContentAnim.value
                                                alpha = 1f - 0.65f * topContentAnim.value
                                            }
                                            .pointerInput(topTab) {
                                                if (topTab != 0) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val e = awaitPointerEvent(PointerEventPass.Initial)
                                                            e.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        TodayScreen(
                                            records = timerState.todayRecords,
                                            activeCategory = timerState.activeCategory,
                                            timerElapsed = timerState.timerElapsed,
                                            onStartTimer = { timerViewModel.startTimer(it) },
                                            onStopTimer = { timerViewModel.stopTimer() },
                                            onSaveSleep = { timerViewModel.saveSleep(it) },
                                            onShowSleep = { showSleepDialog = true },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        } // 闭合主内容容器 Box
                    } // 闭合 BoxWithConstraints
                } // Box (settings_switch)
            }
        }

        // Layer 3: blur toast overlay
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                BlurToast(
                    message = msg,
                    backdrop = backdrop,
                    onDismiss = { toastMessage = null }
                )
            }
        }

        // Layer 4: 悬浮底栏（Kyant Backdrop 官方 LiquidBottomTabs 液态玻璃实现）
        // 容器 vibrancy→blur→lens 折射；内部滑块用 CombinedBackdrop(页面层 + tabs 层)
        // 做带色散的折射，按动放大 / 拖拽切 tab / 交互高光全部由官方组件承担。
        val showBottomBar = !mainState.showSettings || !settingsScrollingUp
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomBar,
            enter = androidx.compose.animation.slideInVertically(tween(200)) { it },
            exit = androidx.compose.animation.slideOutVertically(tween(200)) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                val selectedIndex = when {
                    mainState.showSettings -> 2
                    mainState.currentTab == 1 -> 1
                    else -> 0
                }
                val navItems = listOf(
                    Triple(Icons.Default.DateRange, stringResource(R.string.nav_today), 0),
                    Triple(Icons.AutoMirrored.Default.List, stringResource(R.string.nav_history), 1),
                    Triple(Icons.Default.Settings, stringResource(R.string.top_settings), 2)
                )
                // 底栏宽度 = 屏短边 × 90%、上限 600dp（对齐 sportlink FloatingTabWidth 口径）
                val navBarWidth = FloatingNavBarWidth()

                // 官方组件内部用 remember(selectedTabIndex) 持有 currentIndex、并在
                // LaunchedEffect 中捕获 onTabSelected，故这里用 rememberUpdatedState
                // 让 lambda 引用稳定且始终读到最新的 mainState / ViewModel 状态
                val showSettingsState = rememberUpdatedState(mainState.showSettings)
                fun selectNavTab(index: Int) {
                    when (index) {
                        0, 1 -> {
                            if (showSettingsState.value) {
                                webDavViewModel.saveSettings()
                                webDavViewModel.resetStatus()
                            }
                            mainViewModel.switchTab(index)
                        }
                        2 -> {
                            webDavViewModel.loadSettings()
                            mainViewModel.showSettings()
                        }
                    }
                }
                val selectedIndexState = rememberUpdatedState(selectedIndex)
                val selectNavTabState = rememberUpdatedState<(Int) -> Unit>({ index -> selectNavTab(index) })

                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndexState.value },
                    onTabSelected = { index -> selectNavTabState.value(index) },
                    backdrop = liquidBackdrop,
                    tabsCount = navItems.size,
                    modifier = Modifier
                        .padding(
                            bottom = WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding() + 8.dp
                        )
                        .width(navBarWidth)
                ) {
                    navItems.forEach { (icon, label, index) ->
                        LiquidBottomTab(onClick = { selectNavTabState.value(index) }) {
                            val isSelected = index == selectedIndex
                            val itemColor = MiuixTheme.colorScheme.onSurfaceContainer
                                .copy(alpha = if (isSelected) 1f else 0.4f)
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                                tint = itemColor
                            )
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = itemColor
                            )
                        }
                    }
                }
            }
        } // AnimatedVisibility

        // Layer 5: sleep input dialog（经 DialogOverlay 注册到宿主 slot 渲染，
        // 外部 Haze scrim + 内部 miuix 玻璃卡，BackHandler 在弹窗内部）
        if (showSleepDialog) {
            SleepInputDialog(
                onDismiss = { showSleepDialog = false },
                onSave = { minutes ->
                    timerViewModel.saveSleep(minutes)
                    showSleepDialog = false
                }
            )
        }

        // Layer 6: 弹窗浮层 slot（DialogOverlay 注册的弹窗在此渲染，与采样源兄弟防自采样）
        DialogOverlaySlot(dialogOverlayState)
    }
    }
}

/**
 * 悬浮底栏宽度口径（对齐 sportlink FloatingTabWidth）：屏**短边** × 90%、上限 600dp。
 * 横竖屏等宽（横屏短边 = 屏高 → 底栏不随长边拉长横跨全屏），平板/大屏封顶防过度变胖。
 * 水平居中由外层 AnimatedVisibility(BottomCenter) 承担。
 */
@Composable
private fun FloatingNavBarWidth(): Dp {
    val configuration = LocalConfiguration.current
    return (minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp * 0.9f)
        .coerceAtMost(600.dp)
}
