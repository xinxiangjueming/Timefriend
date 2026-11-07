package com.example.timefriend.ui.screen

import android.content.BroadcastReceiver
import android.content.res.Configuration
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.service.PomodoroService
import com.example.timefriend.ui.component.BlurredDialogScaffold
import com.example.timefriend.ui.component.DialogButtonRow
import com.example.timefriend.ui.component.DialogCancelButton
import com.example.timefriend.ui.component.DialogConfirmButton
import com.example.timefriend.ui.component.DialogOverlay
import kotlinx.coroutines.delay
import com.example.timefriend.ui.theme.GlobalCornerRadius
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val focusOptions = listOf(15, 25, 35)
private val restOptions = listOf(5, 8, 10)
private val cycleOptions = listOf(1, 2, 4)
private const val CYCLE_CUSTOM_MAX = 20
private val SkyBlue = Color(0xFF87CEEB)

@Composable
fun PomodoroScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    var focusMinutes by remember { mutableIntStateOf(25) }
    var restMinutes by remember { mutableIntStateOf(5) }
    var cycleCount by remember { mutableIntStateOf(1) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isFocusPhase by remember { mutableStateOf(true) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customTarget by remember { mutableStateOf("focus") }
    var prevIsFocusPhase by remember { mutableStateOf(true) }

    // Request promoted notification permission on API 34+
    val promotedLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            promotedLauncher.launch("android.permission.POST_PROMOTED_NOTIFICATIONS")
        }
    }

    LaunchedEffect(focusMinutes, restMinutes, isRunning) {
        if (!isRunning) {
            remainingSeconds = if (isFocusPhase) focusMinutes * 60 else restMinutes * 60
        }
    }

    // Listen for service state broadcasts
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                val rem = intent.getIntExtra(PomodoroService.EXTRA_REMAINING, 0)
                val focus = intent.getBooleanExtra(PomodoroService.EXTRA_IS_FOCUS, true)
                val paused = intent.getBooleanExtra(PomodoroService.EXTRA_IS_PAUSED, false)

                // Detect focus → rest transition (phase ended naturally):
                // the session is persisted by PomodoroService; UI only vibrates.
                if (prevIsFocusPhase && !focus && rem > 0) {
                    vibe(vibrator, longArrayOf(0, 1500))
                }
                // Detect rest ended naturally (remaining == 0, was not focus)
                if (!prevIsFocusPhase && !focus && rem == 0) {
                    vibe(vibrator, longArrayOf(0, 200))
                }

                prevIsFocusPhase = focus
                isFocusPhase = focus
                isPaused = paused
                remainingSeconds = rem

                // Sync isRunning: if service is sending state with rem > 0, it's running
                if (rem > 0 && !isRunning) isRunning = true

                // Timer fully stopped (service sent 0 and was running)
                if (rem == 0 && isRunning) {
                    if (focus) {
                        // Focus ended, switch to rest happens in service
                    } else {
                        isRunning = false
                        isFocusPhase = true
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(PomodoroService.ACTION_STATE_UPDATE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Request current state from service
        context.sendBroadcast(
            android.content.Intent(PomodoroService.ACTION_REQUEST_STATE).setPackage(context.packageName)
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    // Fallback: if service didn't respond within 500ms, it's dead → reset UI
    LaunchedEffect(isRunning) {
        if (isRunning) {
            delay(500)
            if (isRunning && remainingSeconds == 0) {
                isRunning = false
                isFocusPhase = true
                remainingSeconds = focusMinutes * 60
            }
        }
    }

    // Safety net: if remaining hits 0 while "running", the service is gone → reset
    LaunchedEffect(remainingSeconds, isRunning) {
        if (isRunning && remainingSeconds == 0) {
            delay(600)
            if (isRunning && remainingSeconds == 0) {
                isRunning = false
                isFocusPhase = true
                remainingSeconds = focusMinutes * 60
            }
        }
    }

    if (showCustomDialog) {
        val (title, initial, max) = when (customTarget) {
            "cycles" -> Triple(
                stringResource(R.string.pomodoro_custom_count_title),
                cycleCount,
                CYCLE_CUSTOM_MAX
            )
            else -> Triple(stringResource(R.string.pomodoro_custom_title), 20, 120)
        }
        CustomValueDialog(
            title = title,
            initialValue = initial,
            maxValue = max,
            onDismiss = { showCustomDialog = false },
            onConfirm = { v ->
                when (customTarget) {
                    "cycles" -> cycleCount = v
                    "focus" -> focusMinutes = v
                    else -> restMinutes = v
                }
                showCustomDialog = false
            }
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                // 横屏:左避状态栏条、右避手势导航条
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                // 顶栏 overlay 悬浮在页面顶（不占位）：内容避让 状态栏 + 切换器高度（48dp）
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(start = 16.dp, end = 16.dp, top = 48.dp)
        ) {
            // Left: focus & rest options
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SectionRow(
                    label = stringResource(R.string.pomodoro_focus),
                    options = focusOptions,
                    selectedValue = focusMinutes,
                    onSelect = { focusMinutes = it },
                    onCustom = { customTarget = "focus"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_minutes_format, it) }
                )
                Spacer(modifier = Modifier.height(20.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_rest),
                    options = restOptions,
                    selectedValue = restMinutes,
                    onSelect = { restMinutes = it },
                    onCustom = { customTarget = "rest"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_minutes_format, it) }
                )
                Spacer(modifier = Modifier.height(20.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_cycles),
                    options = cycleOptions,
                    selectedValue = cycleCount,
                    onSelect = { cycleCount = it },
                    onCustom = { customTarget = "cycles"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_cycles_format, it) }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: circle centered
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                TimerCircle(
                    isRunning = isRunning,
                    isPaused = isPaused,
                    isFocusPhase = isFocusPhase,
                    remainingSeconds = remainingSeconds,
                    onToggle = {
                        if (!isRunning) {
                            // Start service
                            isRunning = true; isPaused = false
                            isFocusPhase = true; remainingSeconds = focusMinutes * 60
                            vibe(vibrator, longArrayOf(0, 50))
                            try {
                                val svcIntent = android.content.Intent(context, PomodoroService::class.java).apply {
                                    action = PomodoroService.ACTION_START
                                    putExtra(PomodoroService.EXTRA_FOCUS_MIN, focusMinutes)
                                    putExtra(PomodoroService.EXTRA_REST_MIN, restMinutes)
                                    putExtra(PomodoroService.EXTRA_CYCLES, cycleCount)
                                }
                                ContextCompat.startForegroundService(context, svcIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (isPaused) {
                            // Resume service
                            context.sendBroadcast(
                                android.content.Intent(PomodoroService.ACTION_RESUME).setPackage(context.packageName)
                            )
                        } else {
                            // Pause service
                            context.sendBroadcast(
                                android.content.Intent(PomodoroService.ACTION_PAUSE).setPackage(context.packageName)
                            )
                        }
                    },
                    onStop = {
                        // Elapsed time is persisted by PomodoroService (works
                        // even when the UI is not composed); UI just resets.
                        vibe(vibrator, longArrayOf(0, 100, 100, 100))
                        context.sendBroadcast(
                            android.content.Intent(PomodoroService.ACTION_STOP).setPackage(context.packageName)
                        )
                        isRunning = false; isPaused = false
                        isFocusPhase = true; remainingSeconds = focusMinutes * 60
                    }
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                // 顶栏 overlay 悬浮在页面顶（不占位）：内容避让 状态栏 + 切换器高度（48dp）
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(start = 16.dp, end = 16.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top section: focus & rest options
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_focus),
                    options = focusOptions,
                    selectedValue = focusMinutes,
                    onSelect = { focusMinutes = it },
                    onCustom = { customTarget = "focus"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_minutes_format, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_rest),
                    options = restOptions,
                    selectedValue = restMinutes,
                    onSelect = { restMinutes = it },
                    onCustom = { customTarget = "rest"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_minutes_format, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_cycles),
                    options = cycleOptions,
                    selectedValue = cycleCount,
                    onSelect = { cycleCount = it },
                    onCustom = { customTarget = "cycles"; showCustomDialog = true },
                    enabled = !isRunning,
                    optionLabel = { stringResource(R.string.pomodoro_cycles_format, it) }
                )
            }

            // Bottom section: circle centered in remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimerCircle(
                    isRunning = isRunning,
                    isPaused = isPaused,
                    isFocusPhase = isFocusPhase,
                    remainingSeconds = remainingSeconds,
                    onToggle = {
                        if (!isRunning) {
                            // Start service
                            isRunning = true; isPaused = false
                            isFocusPhase = true; remainingSeconds = focusMinutes * 60
                            vibe(vibrator, longArrayOf(0, 50))
                            try {
                                val svcIntent = android.content.Intent(context, PomodoroService::class.java).apply {
                                    action = PomodoroService.ACTION_START
                                    putExtra(PomodoroService.EXTRA_FOCUS_MIN, focusMinutes)
                                    putExtra(PomodoroService.EXTRA_REST_MIN, restMinutes)
                                    putExtra(PomodoroService.EXTRA_CYCLES, cycleCount)
                                }
                                ContextCompat.startForegroundService(context, svcIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (isPaused) {
                            // Resume service
                            context.sendBroadcast(
                                android.content.Intent(PomodoroService.ACTION_RESUME).setPackage(context.packageName)
                            )
                        } else {
                            // Pause service
                            context.sendBroadcast(
                                android.content.Intent(PomodoroService.ACTION_PAUSE).setPackage(context.packageName)
                            )
                        }
                    },
                    onStop = {
                        // Elapsed time is persisted by PomodoroService (works
                        // even when the UI is not composed); UI just resets.
                        vibe(vibrator, longArrayOf(0, 100, 100, 100))
                        context.sendBroadcast(
                            android.content.Intent(PomodoroService.ACTION_STOP).setPackage(context.packageName)
                        )
                        isRunning = false; isPaused = false
                        isFocusPhase = true; remainingSeconds = focusMinutes * 60
                    }
                )
            }
        }
    }
}

@Composable
private fun TimerCircle(
    isRunning: Boolean,
    isPaused: Boolean,
    isFocusPhase: Boolean,
    remainingSeconds: Int,
    onToggle: () -> Unit,
    onStop: () -> Unit
) {
    val circleBg = when {
        !isRunning -> SkyBlue
        isFocusPhase -> Color(0xFFE53935)
        else -> MiuixTheme.colorScheme.primary
    }
    val textColor = if (isRunning) Color.White else Color.White

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(circleBg)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (!isRunning) {
                Text(
                    text = stringResource(R.string.pomodoro_start),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            } else {
                val mm = remainingSeconds / 60
                val ss = remainingSeconds % 60
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%02d:%02d", mm, ss),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = textColor
                    )
                    val subLabel = if (isPaused) {
                        stringResource(R.string.pomodoro_resume)
                    } else if (isFocusPhase) {
                        stringResource(R.string.pomodoro_focus_phase)
                    } else {
                        stringResource(R.string.pomodoro_rest_phase)
                    }
                    Text(
                        text = subLabel,
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(48.dp)
                .alpha(if (isRunning) 1f else 0f),
            enabled = isRunning,
            cornerRadius = GlobalCornerRadius,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledColor = Color.Transparent,
                disabledContentColor = Color.Transparent
            )
        ) {
            Text(
                text = stringResource(R.string.pomodoro_stop),
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SectionRow(
    label: String,
    options: List<Int>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
    enabled: Boolean,
    optionLabel: @Composable (Int) -> String
) {
    val isCustom = selectedValue !in options
    // Freeze the last custom value: during the exit animation the selection
    // has already moved back to a preset chip, so the label must keep showing
    // the old custom value instead of flashing the newly selected preset.
    var frozenValue by remember { mutableIntStateOf(selectedValue) }
    if (isCustom) {
        frozenValue = selectedValue
    }

    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { minutes ->
                val isSelected = selectedValue == minutes && !isCustom
                ChipButton(
                    label = optionLabel(minutes),
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onSelect(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
            ChipButton(
                label = stringResource(R.string.pomodoro_custom),
                selected = isCustom,
                enabled = enabled,
                onClick = onCustom,
                modifier = Modifier.weight(1f)
            )
        }
        // 自定义值提示：出现/消失带过渡动画，避免自定义↔默认切换时布局跳变
        AnimatedVisibility(
            visible = isCustom,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = optionLabel(frozenValue),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceContainer
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        cornerRadius = GlobalCornerRadius,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            color = bgColor,
            disabledColor = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
            contentColor = textColor,
            disabledContentColor = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (enabled) textColor else MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun CustomValueDialog(
    title: String,
    initialValue: Int = 20,
    minValue: Int = 1,
    maxValue: Int = 120,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableIntStateOf(initialValue) }

    // 经 DialogOverlay 注册到 MainScreen 宿主 slot（同窗口浮层）。
    // 高版本（API 33+）：官方 DialogContent 液态玻璃卡（外部压暗画进采样源）；
    // 低版本：外部 Haze scrim（20dp 模糊 + 25% 压暗）+ 内部实心 tint 卡
    // （miuixBlur=false，浅色 White / 深色 #3A3A3A 不透明，无 miuix textureBlur）。
    // 宽度由 BlurredDialogScaffold 内部 dialogWidthAdaptive 统一。
    DialogOverlay {
        BackHandler { onDismiss() }
        BlurredDialogScaffold(onDismissRequest = onDismiss, miuixBlur = false) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        text = "−",
                        onClick = { if (value > minValue) value-- },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    Text(
                        text = String.format("%02d", value),
                        fontSize = 48.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    TextButton(
                        text = "+",
                        onClick = { if (value < maxValue) value++ },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                DialogButtonRow(
                    cancel = {
                        DialogCancelButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onDismiss
                        )
                    },
                    confirm = {
                        DialogConfirmButton(
                            text = stringResource(R.string.action_save),
                            onClick = { onConfirm(value) }
                        )
                    }
                )
            }
        }
    }
}

private fun vibe(vibrator: Vibrator?, pattern: LongArray) {
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
