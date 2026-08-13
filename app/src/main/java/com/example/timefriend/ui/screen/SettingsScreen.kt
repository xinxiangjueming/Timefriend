package com.example.timefriend.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.example.timefriend.viewmodel.SyncStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    server: String,
    username: String,
    password: String,
    syncStatus: SyncStatus,
    statusMessage: String?,
    blurLevel: Float,
    onBlurLevelChange: (Float) -> Unit,
    batteryOptimized: Boolean,
    notificationGranted: Boolean,
    accessibilityEnabled: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBackup: () -> Unit,
    onSync: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onBack: () -> Unit,
    onScrollingUp: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = syncStatus == SyncStatus.LOADING
    val ctx = LocalContext.current
    val collapsePrefs = remember { ctx.getSharedPreferences("settings_collapse", android.content.Context.MODE_PRIVATE) }
    var webDavExpanded by remember { mutableStateOf(collapsePrefs.getBoolean("webdav", true)) }
    var permExpanded by remember { mutableStateOf(collapsePrefs.getBoolean("perm", true)) }
    val statusColor = when (syncStatus) {
        SyncStatus.SUCCESS -> MiuixTheme.colorScheme.primary
        SyncStatus.ERROR -> androidx.compose.ui.graphics.Color(0xFFE53935)
        else -> MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f)
    }

    val scrollState = rememberScrollState()

    // 恢复底栏：手指离开屏幕 或 内容不再超出屏幕
    LaunchedEffect(scrollState.isScrollInProgress, scrollState.maxValue) {
        if (!scrollState.isScrollInProgress) {
            onScrollingUp(false)
        } else if (scrollState.maxValue <= 0 || scrollState.value >= scrollState.maxValue) {
            onScrollingUp(false)
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 手指向下滑（delta > 10px）→ 隐藏底栏
                // 手指向上滑（delta < -10px）→ 显示底栏
                if (available.y > 10) onScrollingUp(true)
                else if (available.y < -10) onScrollingUp(false)
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(scrollState)
            // 顶部避状态栏(顶栏在设置页隐藏,防止第一张卡片被遮挡);
            // 横屏时左避状态栏条、右避手势条;置于 scroll 之内 → 滚动时可侵入系统栏区域,保持沉浸语义
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // WebDAV section
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title row — only arrow is clickable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.webdav_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(GlobalCornerRadius))
                            .clickable {
                                webDavExpanded = !webDavExpanded
                                collapsePrefs.edit().putBoolean("webdav", webDavExpanded).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (webDavExpanded) "▲" else "▼",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                        )
                    }
                }

                AnimatedVisibility(visible = webDavExpanded) {
                    Column {

                Spacer(modifier = Modifier.height(20.dp))

                // Server
                FieldLabel(stringResource(R.string.webdav_server))
                Spacer(modifier = Modifier.height(6.dp))
                SettingsTextField(
                    value = server,
                    onValueChange = onServerChange,
                    hint = stringResource(R.string.webdav_server_hint),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Username
                FieldLabel(stringResource(R.string.webdav_username))
                Spacer(modifier = Modifier.height(6.dp))
                SettingsTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    hint = "",
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                FieldLabel(stringResource(R.string.webdav_password))
                Spacer(modifier = Modifier.height(6.dp))
                SettingsTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    hint = "",
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 自动备份目录提示
                Text(
                    text = stringResource(R.string.webdav_auto_dir),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Status message
                if (statusMessage != null) {
                    Text(
                        text = statusMessage,
                        fontSize = 14.sp,
                        color = statusColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Action buttons
                Button(
                    onClick = onBackup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    cornerRadius = GlobalCornerRadius,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        disabledColor = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        disabledContentColor = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = if (isLoading && syncStatus == SyncStatus.LOADING) stringResource(R.string.webdav_backing_up)
                               else stringResource(R.string.webdav_backup),
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    cornerRadius = GlobalCornerRadius,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                        disabledColor = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        disabledContentColor = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = if (isLoading && syncStatus == SyncStatus.LOADING) stringResource(R.string.webdav_syncing)
                               else stringResource(R.string.webdav_sync),
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainer
                    )
                }
                    } // Column inside AnimatedVisibility
                } // AnimatedVisibility
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permissions section
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title row — only arrow is clickable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.permissions_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(GlobalCornerRadius))
                            .clickable {
                                permExpanded = !permExpanded
                                collapsePrefs.edit().putBoolean("perm", permExpanded).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (permExpanded) "▲" else "▼",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                        )
                    }
                }

                AnimatedVisibility(visible = permExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Battery optimization
                        PermissionItem(
                            title = stringResource(R.string.perm_battery),
                            description = stringResource(R.string.perm_battery_desc),
                            granted = batteryOptimized,
                            onClick = onRequestBattery
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Notification
                        PermissionItem(
                            title = stringResource(R.string.perm_notification),
                            description = stringResource(R.string.perm_notification_desc),
                            granted = notificationGranted,
                            onClick = onRequestNotification
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Accessibility
                        PermissionItem(
                            title = stringResource(R.string.perm_accessibility),
                            description = stringResource(R.string.perm_accessibility_desc),
                            granted = accessibilityEnabled,
                            onClick = onRequestAccessibility
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tip
                        Text(
                            text = stringResource(R.string.perm_tip),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Blur level section
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.settings_blur_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_blur_level, blurLevel.toInt()),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Slider(
                    value = blurLevel,
                    onValueChange = onBlurLevelChange,
                    valueRange = 0f..60f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        cornerRadius = GlobalCornerRadius,
        colors = CardDefaults.defaultColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            val statusText = if (granted) stringResource(R.string.perm_granted)
                             else stringResource(R.string.perm_not_granted)
            val statusColor = if (granted) MiuixTheme.colorScheme.primary
                              else androidx.compose.ui.graphics.Color(0xFFE53935)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    color = statusColor
                )
                if (!granted) {
                    Button(
                        onClick = onClick,
                        modifier = Modifier.height(44.dp),
                        cornerRadius = GlobalCornerRadius,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.perm_grant_button),
                            fontSize = 13.sp,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f)
    )
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.outline,
                shape = RoundedCornerShape(GlobalCornerRadius)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 15.sp,
            color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            innerTextField()
        }
    )
}
