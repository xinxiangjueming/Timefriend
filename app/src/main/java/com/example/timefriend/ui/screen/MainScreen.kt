package com.example.timefriend.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
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
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.example.timefriend.viewmodel.HistoryViewModel
import com.example.timefriend.viewmodel.MainViewModel
import com.example.timefriend.viewmodel.TimerEffect
import com.example.timefriend.viewmodel.TimerViewModel
import com.example.timefriend.viewmodel.WebDavViewModel
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
    val context = LocalContext.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: content captured for blur backdrop
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .layerBackdrop(backdrop)
        ) {
            // Top bar — always composed to reserve layout space; invisible when not on Today tab.
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
                // 只在今日页显示；隐藏时 AnimatedVisibility 移除占位，不残留状态栏高度
                AnimatedVisibility(
                    visible = !mainState.showSettings && mainState.currentTab == 0,
                    enter = fadeIn(animationSpec = tween(200)) +
                        expandVertically(animationSpec = tween(200), expandFrom = Alignment.Top),
                    exit = fadeOut(animationSpec = tween(200)) +
                        shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Top),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                }

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
                                onSelectDate = { historyViewModel.selectDate(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // —— 今日页：向左滑出 / 从左滑入（含内部 今日/专注 交叉淡化）——
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
                            // Nested crossfade: Today / Pomodoro
                            Box(modifier = Modifier.fillMaxSize()) {
                                val pomodoroAlpha by animateFloatAsState(
                                    targetValue = if (topTab == 1) 1f else 0f,
                                    animationSpec = tween(200),
                                    label = "pomodoro_fade"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(if (topTab == 1) 2f else 0f)
                                        .alpha(pomodoroAlpha)
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
                                        onSaveRecord = { category, minutes ->
                                            timerViewModel.savePomodoroRecord(category, minutes)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                val todayTabAlpha by animateFloatAsState(
                                    targetValue = if (topTab == 0) 1f else 0f,
                                    animationSpec = tween(200),
                                    label = "today_tab_fade"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(if (topTab == 0) 2f else 0f)
                                        .alpha(todayTabAlpha)
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
                                        modifier = Modifier.fillMaxSize()
                                    )
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

        // Layer 4: floating nav bar with blur + settings
        val showBottomBar = !mainState.showSettings || !settingsScrollingUp

        // Debug: log navigation bar insets
        val density = LocalDensity.current
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
        val navBarsBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val systemBarsBottomDp = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }
        val safeDrawingBottomDp = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
        val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val cutoutTopDp = with(density) { WindowInsets.displayCutout.getTop(this).toDp() }
        LaunchedEffect(Unit) {
            Log.d("NavBarDebug", "screen=${screenWidthDp}x${screenHeightDp}")
            Log.d("NavBarDebug", "API=${android.os.Build.VERSION.SDK_INT}")
            Log.d("NavBarDebug", "navigationBars.bottom=$navBarsBottomDp")
            Log.d("NavBarDebug", "systemBars.bottom=$systemBarsBottomDp")
            Log.d("NavBarDebug", "safeDrawing.bottom=$safeDrawingBottomDp")
            Log.d("NavBarDebug", "ime.bottom=$imeBottomDp")
            Log.d("NavBarDebug", "cutout.top=$cutoutTopDp")
            Log.d("NavBarDebug", "bar hardcoded bottom padding=22dp, bar height=64dp")
        }
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
            val navBarShape = RoundedCornerShape(50.dp)
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
            val pillInset = 5.dp
            val pillHeight = 54.dp
            val pillAnim = remember { Animatable(0f) }
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val horizontalPaddingPx = with(density) { FloatingNavigationBarDefaults.HorizontalOutSidePadding.toPx() }
            LaunchedEffect(selectedIndex) {
                val barWidthPx = screenWidthPx - horizontalPaddingPx * 2
                val itemWidthPx = barWidthPx / navItems.size
                val targetPx = itemWidthPx * selectedIndex + with(density) { pillInset.toPx() }
                pillAnim.animateTo(targetPx, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f))
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = FloatingNavigationBarDefaults.HorizontalOutSidePadding)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { navBarShape },
                        effects = {
                            textureBlurEffect(
                                blurRadiusX = mainState.blurLevel,
                                blurRadiusY = mainState.blurLevel
                            )
                        }
                    )
                    .clip(navBarShape)
                    .background(
                        MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        navBarShape
                    )
                    .height(64.dp)
                    .fillMaxWidth()
            ) {
                // Sliding pill indicator
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val itemWidth = maxWidth / navItems.size
                    val pw = itemWidth - pillInset * 2
                    val yPx = with(density) { 5.dp.roundToPx() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(pillAnim.value.toInt(), yPx) }
                            .size(pw, pillHeight)
                            .align(Alignment.TopStart)
                            .clip(navBarShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }
                // Navigation items
                Row(modifier = Modifier.fillMaxSize()) {
                    navItems.forEachIndexed { _, (icon, label, index) ->
                        val isSelected = index == selectedIndex
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val itemAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.4f,
                            animationSpec = tween(200),
                            label = "nav_item_alpha"
                        )
                        val pressedScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.88f else 1f,
                            animationSpec = tween(150),
                            label = "nav_item_press"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = pressedScale
                                    scaleY = pressedScale
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    when (index) {
                                        0 -> {
                                            if (mainState.showSettings) {
                                                webDavViewModel.saveSettings()
                                                webDavViewModel.resetStatus()
                                            }
                                            mainViewModel.switchTab(0)
                                        }
                                        1 -> {
                                            if (mainState.showSettings) {
                                                webDavViewModel.saveSettings()
                                                webDavViewModel.resetStatus()
                                            }
                                            mainViewModel.switchTab(1)
                                        }
                                        2 -> {
                                            webDavViewModel.loadSettings()
                                            mainViewModel.showSettings()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(22.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = itemAlpha)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = itemAlpha)
                                )
                            }
                        }
                    }
                }
            }
        }
        } // AnimatedVisibility
    }
}
