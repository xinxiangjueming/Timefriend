package com.example.timefriend.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.ui.component.DayPieChart
import com.example.timefriend.ui.component.PieChartLegend
import com.example.timefriend.ui.component.categoryColor
import com.example.timefriend.ui.theme.GlobalCornerRadius
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodayScreen(
    records: List<TimeRecord>,
    activeCategory: TimeCategory?,
    timerElapsed: Long,
    onStartTimer: (TimeCategory) -> Unit,
    onStopTimer: () -> Unit,
    onSaveSleep: (Int) -> Unit,
    onShowSleep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape: left = pie chart + legend, right = buttons + timer
        Row(
            modifier = modifier
                .fillMaxSize()
                // 横屏:左避状态栏条、右避手势导航条
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Left side: pie chart + legend
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DayPieChart(records = records, size = 180.dp, strokeWidth = 22.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    cornerRadius = GlobalCornerRadius,
                    colors = CardDefaults.defaultColors()
                ) {
                    PieChartLegend(
                        records = records,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right side: buttons + timer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2x2 grid for landscape
                val categories = TimeCategory.entries
                for (rowIndex in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (colIndex in 0..1) {
                            val category = categories[rowIndex * 2 + colIndex]
                            CategoryButton(
                                category = category,
                                isActive = activeCategory == category,
                                onClick = {
                                    if (category == TimeCategory.SLEEP) {
                                        onShowSleep()
                                    } else if (activeCategory == category) {
                                        onStopTimer()
                                    } else if (activeCategory == null) {
                                        onStartTimer(category)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                if (activeCategory != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        cornerRadius = GlobalCornerRadius,
                        colors = CardDefaults.defaultColors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(activeCategory.displayNameRes),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatElapsed(timerElapsed),
                                fontSize = 28.sp,
                                color = categoryColor(activeCategory)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Portrait: original vertical layout
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            DayPieChart(records = records, size = 220.dp, strokeWidth = 26.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                cornerRadius = GlobalCornerRadius,
                colors = CardDefaults.defaultColors()
            ) {
                PieChartLegend(
                    records = records,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeCategory.entries.forEach { category ->
                    CategoryButton(
                        category = category,
                        isActive = activeCategory == category,
                        onClick = {
                            if (category == TimeCategory.SLEEP) {
                                onShowSleep()
                            } else if (activeCategory == category) {
                                onStopTimer()
                            } else if (activeCategory == null) {
                                onStartTimer(category)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (activeCategory != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    cornerRadius = GlobalCornerRadius,
                    colors = CardDefaults.defaultColors()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(activeCategory.displayNameRes),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatElapsed(timerElapsed),
                            fontSize = 32.sp,
                            color = categoryColor(activeCategory)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryButton(
    category: TimeCategory,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = categoryColor(category)
    val activeRed = Color(0xFFE53935)
    val bgColor = if (isActive) {
        activeRed
    } else {
        categoryColor.copy(alpha = 0.5f)
    }
    val textColor = if (isActive) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        cornerRadius = GlobalCornerRadius,
        colors = ButtonDefaults.buttonColors(
            color = bgColor,
            disabledColor = bgColor,
            contentColor = textColor,
            disabledContentColor = textColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(category.displayNameRes),
                fontSize = 16.sp,
                color = textColor
            )
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
