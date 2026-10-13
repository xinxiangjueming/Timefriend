package com.example.timefriend.ui.screen

import android.content.res.Configuration
import android.util.Log
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.TimeFriendApp
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.example.timefriend.viewmodel.SyncStatus
import com.example.timefriend.wearable.WearableLink
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 设置分组标识（横屏分栏用，对齐 sportlink SettingsScreen 的 sections 机制）───
private const val SEC_WEBDAV = "webdav"
private const val SEC_PERM = "perm"
private const val SEC_BLUR = "blur"
private const val SEC_WATCH = "watch"

/**
 * 横屏右列的分组集合；其余分组归左列。归属固定写死（不按实时高度动态分配），
 * 保证展开/收起卡片时不会左右跳列（对齐 sportlink LANDSCAPE_RIGHT_COLUMN）。
 * 左列 = WebDAV + 手表同步；右列 = 权限 + 模糊等级。
 */
private val LANDSCAPE_RIGHT_COLUMN = setOf(SEC_PERM, SEC_BLUR)

/** 一个设置分组：标识（横屏分栏归属） + 卡片内容（对齐 sportlink SettingsSection） */
private data class SettingsSection(
    val id: String,
    val content: @Composable (Modifier) -> Unit,
)

/**
 * 横屏分栏中的一列：卡片纵向堆叠、每张卡保持自身自然高度，不做同行等高拉伸
 *（等高会把矮卡拉高、卡内出现大片空白，观感差；对齐 sportlink SettingsColumn）。
 */
@Composable
private fun SettingsColumn(
    sections: List<SettingsSection>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sections.forEach { section -> section.content(Modifier) }
    }
}

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

    // 手表同步服务开关：落独立 SP（冷启动由 TimeFriendApp 强制复位为关闭，
    // 开启状态仅对当前进程有效 —— 打开 App 不会自动弹手表端同步页）
    val syncPrefs = remember {
        ctx.getSharedPreferences(WearableLink.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    var watchSyncEnabled by remember {
        mutableStateOf(syncPrefs.getBoolean(WearableLink.KEY_WATCH_SYNC_ENABLED, false))
    }
    val statusColor = when (syncStatus) {
        SyncStatus.SUCCESS -> MiuixTheme.colorScheme.primary
        SyncStatus.ERROR -> androidx.compose.ui.graphics.Color(0xFFE53935)
        else -> MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f)
    }

    val scrollState = rememberScrollState()

    // 横屏两列：分组卡两两并排（阅读顺序左→右，与竖屏展开顺序一致；对齐 sportlink）
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 设置页所有分组卡先声明为「标识 + 可组合块」列表，再按屏幕方向发射
        //（对齐 sportlink SettingsScreen）：
        // - 竖屏：逐块单列（与改造前视觉一致）
        // - 横屏：左右两个独立 Column，各自 weight(1f) 均分屏宽，卡片保持自然高度
        val sections = ArrayList<SettingsSection>()

        sections.add(SettingsSection(SEC_WEBDAV) { cardModifier ->
        // WebDAV section
        Card(
            modifier = cardModifier,
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
        })

        sections.add(SettingsSection(SEC_PERM) { cardModifier ->
        // Permissions section
        Card(
            modifier = cardModifier,
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
        })

        sections.add(SettingsSection(SEC_BLUR) { cardModifier ->
        // Blur level section
        Card(
            modifier = cardModifier,
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
        })

        sections.add(SettingsSection(SEC_WATCH) { cardModifier ->
        // 手表同步服务：开启即唤起表端建立 LiFD 会话（App 启动只注册监听、绝不唤起；
        // 开关冷启动强制复位为关 —— 打开 App 不会弹手表同步页，安全口径同 Sportlink）
        Card(
            modifier = cardModifier,
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_watch_sync_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    MiuixSwitch(
                        checked = watchSyncEnabled,
                        onCheckedChange = { enabled ->
                            watchSyncEnabled = enabled
                            syncPrefs.edit()
                                .putBoolean(WearableLink.KEY_WATCH_SYNC_ENABLED, enabled)
                                .apply()
                            if (enabled) {
                                // 仅此处唤起表端建立会话（手表晚连时由 pendingWake 兜底：
                                // 监听注册成功那一刻自动拉起）；AAR 未就位时静默降级不崩设置页
                                runCatching {
                                    (ctx.applicationContext as TimeFriendApp)
                                        .container.wearableLink.wakeWatch()
                                }.onFailure { e ->
                                    Log.e("WatchSync", "wakeWatch failed (xms-wearable AAR missing?)", e)
                                }
                            }
                            // 关闭：不做反向操作（监听进程存活期间保持注册），仅不再唤起表端
                        }
                    )
                }
            }
        }
        })

        // ── 发射：竖屏单列 / 横屏左右两个独立列（各自自然高度，不强制等高）──
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsColumn(
                    modifier = Modifier.weight(1f),
                    sections = sections.filter { it.id !in LANDSCAPE_RIGHT_COLUMN }
                )
                SettingsColumn(
                    modifier = Modifier.weight(1f),
                    sections = sections.filter { it.id in LANDSCAPE_RIGHT_COLUMN }
                )
            }
        } else {
            sections.forEach { section -> section.content(Modifier) }
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
