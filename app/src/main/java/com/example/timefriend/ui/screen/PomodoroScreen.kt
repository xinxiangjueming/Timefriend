package com.example.timefriend.ui.screen

import android.content.BroadcastReceiver
import android.content.res.Configuration
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.service.PomodoroService
import com.example.timefriend.ui.theme.GlobalCornerRadius
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val focusOptions = listOf(15, 25, 35)
private val restOptions = listOf(5, 8, 10)
private val SkyBlue = Color(0xFF87CEEB)

@Composable
fun PomodoroScreen(
    onSaveRecord: (TimeCategory, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    var focusMinutes by remember { mutableIntStateOf(25) }
    var restMinutes by remember { mutableIntStateOf(5) }
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

                // Detect focus → rest transition (phase ended naturally)
                if (prevIsFocusPhase && !focus && rem > 0) {
                    val elapsed = focusMinutes
                    if (elapsed >= 3) {
                        onSaveRecord(TimeCategory.INVESTMENT, elapsed)
                    }
                    vibe(vibrator, longArrayOf(0, 1500))
                }
                // Detect rest ended naturally (remaining == 0, was not focus)
                if (!prevIsFocusPhase && !focus && rem == 0) {
                    onSaveRecord(TimeCategory.FIXED, restMinutes)
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
        // Request current state from service (in case service is already running)
        context.sendBroadcast(
            android.content.Intent(PomodoroService.ACTION_REQUEST_STATE).setPackage(context.packageName)
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    if (showCustomDialog) {
        CustomMinutesDialog(
            title = stringResource(R.string.pomodoro_custom_title),
            onDismiss = { showCustomDialog = false },
            onConfirm = { mins ->
                if (customTarget == "focus") focusMinutes = mins
                else restMinutes = mins
                showCustomDialog = false
            }
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
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
                    enabled = !isRunning
                )
                Spacer(modifier = Modifier.height(20.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_rest),
                    options = restOptions,
                    selectedValue = restMinutes,
                    onSelect = { restMinutes = it },
                    onCustom = { customTarget = "rest"; showCustomDialog = true },
                    enabled = !isRunning
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
                        val elapsed = if (isFocusPhase) {
                            focusMinutes - (remainingSeconds + 59) / 60
                        } else {
                            restMinutes - (remainingSeconds + 59) / 60
                        }
                        val category = if (isFocusPhase) TimeCategory.INVESTMENT else TimeCategory.FIXED
                        if (elapsed >= 3) {
                            onSaveRecord(category, elapsed)
                        }
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
                .padding(horizontal = 16.dp),
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
                    enabled = !isRunning
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionRow(
                    label = stringResource(R.string.pomodoro_rest),
                    options = restOptions,
                    selectedValue = restMinutes,
                    onSelect = { restMinutes = it },
                    onCustom = { customTarget = "rest"; showCustomDialog = true },
                    enabled = !isRunning
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
                        val elapsed = if (isFocusPhase) {
                            focusMinutes - (remainingSeconds + 59) / 60
                        } else {
                            restMinutes - (remainingSeconds + 59) / 60
                        }
                        val category = if (isFocusPhase) TimeCategory.INVESTMENT else TimeCategory.FIXED
                        if (elapsed >= 3) {
                            onSaveRecord(category, elapsed)
                        }
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
                .height(48.dp)
                .alpha(if (isRunning) 1f else 0f),
            enabled = isRunning,
            cornerRadius = GlobalCornerRadius,
            colors = ButtonDefaults.buttonColors(
                color = Color(0xFF1A237E),
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
    enabled: Boolean
) {
    val isCustom = selectedValue !in options

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
                    label = stringResource(R.string.pomodoro_minutes_format, minutes),
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
        if (isCustom) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pomodoro_minutes_format, selectedValue),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
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
private fun CustomMinutesDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableIntStateOf(20) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
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
                        onClick = { if (value > 1) value-- },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    Text(
                        text = String.format("%02d", value),
                        fontSize = 48.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    TextButton(
                        text = "+",
                        onClick = { if (value < 120) value++ },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )
                    )
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onConfirm(value) },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

private fun vibe(vibrator: Vibrator?, pattern: LongArray) {
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
