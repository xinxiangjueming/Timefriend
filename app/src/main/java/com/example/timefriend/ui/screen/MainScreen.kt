package com.example.timefriend.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timefriend.R
import com.example.timefriend.ui.component.BlurToast
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.example.timefriend.viewmodel.MainEffect
import com.example.timefriend.viewmodel.MainIntent
import com.example.timefriend.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var topTab by remember { mutableIntStateOf(0) }
    var settingsScrollingUp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MainEffect.TooShort -> {
                    toastMessage = null
                    toastMessage = context.getString(R.string.toast_too_short)
                }
                else -> {}
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
            // Top bar — inside Column so content naturally flows below it
            if (!state.showSettings && state.currentTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .clip(RoundedCornerShape(GlobalCornerRadius))
                        .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f), GlobalCornerRadius)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopTabItem(
                        text = stringResource(R.string.top_today),
                        selected = topTab == 0,
                        onClick = { topTab = 0 }
                    )
                    TopTabItem(
                        text = stringResource(R.string.top_pomodoro),
                        selected = topTab == 1,
                        onClick = { topTab = 1 }
                    )
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize().weight(1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                AnimatedContent(
                    targetState = state.showSettings,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "settings_switch"
                ) { showSettings ->
                    if (showSettings) {
                        val pm = context.getSystemService(android.os.PowerManager::class.java)
                        var batteryOptimized by remember { mutableStateOf(false) }
                        var notificationGranted by remember { mutableStateOf(false) }

                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    batteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                                    notificationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                        ) { granted -> notificationGranted = granted }

                        SettingsScreen(
                            server = state.webDavServer,
                            username = state.webDavUsername,
                            password = state.webDavPassword,
                            path = state.webDavPath,
                            syncStatus = state.syncStatus,
                            statusMessage = state.syncMessage,
                            batteryOptimized = batteryOptimized,
                            notificationGranted = notificationGranted,
                            onServerChange = { viewModel.handleIntent(MainIntent.UpdateWebDavServer(it)) },
                            onUsernameChange = { viewModel.handleIntent(MainIntent.UpdateWebDavUsername(it)) },
                            onPasswordChange = { viewModel.handleIntent(MainIntent.UpdateWebDavPassword(it)) },
                            onPathChange = { viewModel.handleIntent(MainIntent.UpdateWebDavPath(it)) },
                            onBackup = { viewModel.handleIntent(MainIntent.WebDavBackup) },
                            onSync = { viewModel.handleIntent(MainIntent.WebDavSync) },
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
                            onBack = { viewModel.handleIntent(MainIntent.HideSettings) },
                            onScrollingUp = { settingsScrollingUp = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(WindowInsets.statusBars.asPaddingValues())
                        )
                    } else {
                    // Persistent Box: all screens stay composed, crossfade via alpha.
                    // zIndex ensures the active screen is on top for both drawing and hit testing.
                    val crossfadeSpec: AnimationSpec<Float> = tween(200)
                    Box(modifier = Modifier.fillMaxSize()) {
                        val historyAlpha by animateFloatAsState(
                            targetValue = if (state.currentTab == 1) 1f else 0f,
                            animationSpec = crossfadeSpec,
                            label = "history_fade"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (state.currentTab == 1) 2f else 0f)
                                .alpha(historyAlpha)
                                .pointerInput(state.currentTab) {
                                    if (state.currentTab != 1) {
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
                                records = state.historyRecords,
                                weekRecords = state.weekRecords,
                                monthRecords = state.monthRecords,
                                selectedDate = state.selectedDate,
                                recordDates = state.recordDates,
                                onSelectDate = { viewModel.handleIntent(MainIntent.SelectHistoryDate(it)) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(WindowInsets.statusBars.asPaddingValues())
                            )
                        }

                        val todayAlpha by animateFloatAsState(
                            targetValue = if (state.currentTab == 0) 1f else 0f,
                            animationSpec = crossfadeSpec,
                            label = "today_fade"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (state.currentTab == 0) 2f else 0f)
                                .alpha(todayAlpha)
                                .pointerInput(state.currentTab) {
                                    if (state.currentTab != 0) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                                e.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                        ) {
                            val pomodoroAlpha by animateFloatAsState(
                                targetValue = if (topTab == 1) 1f else 0f,
                                animationSpec = crossfadeSpec,
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
                                        viewModel.handleIntent(MainIntent.SavePomodoroRecord(category, minutes))
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            val todayTabAlpha by animateFloatAsState(
                                targetValue = if (topTab == 0) 1f else 0f,
                                animationSpec = crossfadeSpec,
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
                                    records = state.todayRecords,
                                    activeCategory = state.activeCategory,
                                    timerElapsed = state.timerElapsed,
                                    onStartTimer = { viewModel.handleIntent(MainIntent.StartTimer(it)) },
                                    onStopTimer = { viewModel.handleIntent(MainIntent.StopTimer) },
                                    onSaveSleep = { viewModel.handleIntent(MainIntent.SaveSleep(it)) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    } // else (normal content)
                } // AnimatedContent (settings_switch)
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
        val showBottomBar = !state.showSettings || !settingsScrollingUp
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
            FloatingNavigationBar(
                color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                showDivider = true,
                modifier = Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50.dp) },
                    effects = {
                        textureBlurEffect(
                            blurRadiusX = BlurDefaults.BlurRadius,
                            blurRadiusY = BlurDefaults.BlurRadius
                        )
                    }
                )
            ) {
                FloatingNavigationBarItem(
                    selected = state.currentTab == 0,
                    onClick = { viewModel.handleIntent(MainIntent.SwitchTab(0)) },
                    icon = Icons.Default.DateRange,
                    label = stringResource(R.string.nav_today)
                )
                FloatingNavigationBarItem(
                    selected = state.currentTab == 1,
                    onClick = { viewModel.handleIntent(MainIntent.SwitchTab(1)) },
                    icon = Icons.AutoMirrored.Default.List,
                    label = stringResource(R.string.nav_history)
                )
                FloatingNavigationBarItem(
                    selected = state.showSettings,
                    onClick = { viewModel.handleIntent(MainIntent.ShowSettings) },
                    icon = MiuixIcons.Settings,
                    label = stringResource(R.string.top_settings)
                )
            }
        }
        } // AnimatedVisibility
    }
}

@Composable
private fun TopTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MiuixTheme.colorScheme.onSurfaceContainer
    } else {
        MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    Text(
        text = text,
        fontSize = 16.sp,
        color = color,
        fontWeight = fontWeight,
        modifier = Modifier
            .clip(RoundedCornerShape(GlobalCornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}
