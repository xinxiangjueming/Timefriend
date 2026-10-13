package com.example.timefriend.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.viewmodel.DailyPomodoro
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

/** Focus bar blue (matches the pomodoro start button) and rest bar red (matches the focus ring). */
private val FocusBarBlue = Color(0xFF87CEEB)
private val RestBarRed = Color(0xFFE53935)

/**
 * Weekly pomodoro bar chart: for each day of the current week (Monday..Sunday,
 * data provided by HistoryViewModel.pomodoroWeek), a blue bar (focus minutes,
 * left) and a red bar (rest minutes, right). Weekday labels sit below the bars
 * in Mon..Sun order; the weekday header array is Sunday-first, hence the
 * `dayOfWeek.value % 7` mapping. Future days of the week render as zero bars.
 *
 * Expects to be placed inside a Column — the bar area flexes via weight(1f).
 */
@Composable
fun PomodoroWeekChart(
    days: List<DailyPomodoro>,
    modifier: Modifier = Modifier
) {
    val weekdays = androidx.compose.ui.platform.LocalContext.current
        .resources.getStringArray(R.array.weekday_short).toList()

    // Grow-in animation, same pattern as DayPieChart.
    var animPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (animPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "pomodoro_week_anim"
    )
    LaunchedEffect(days) { animPlayed = false; animPlayed = true }

    // 今天判定按日期比对：本周窗口（周一起始）中"今天"不一定是最后一项
    //（原 index == lastIndex 的假设只在"最近 7 天"窗口下成立）
    val todayStr = remember { LocalDate.now().toString() }

    Column(modifier = modifier) {
        // Legend: blue = focus, red = rest (reuses existing pomodoro strings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = FocusBarBlue, label = stringResource(R.string.pomodoro_focus))
            Spacer(modifier = Modifier.size(20.dp))
            LegendDot(color = RestBarRed, label = stringResource(R.string.pomodoro_rest))
        }

        Spacer(modifier = Modifier.height(10.dp))

    // DrawScope 内不可调用 @Composable 的 MiuixTheme，提前取色
    val baselineColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val labelBase = MiuixTheme.colorScheme.onSurface

    // Bars（heightIn：卡片等高前首帧 wrap 兜底，保证图区有最低可见高度）
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .heightIn(min = 120.dp)
    ) {
            if (days.isEmpty()) return@Canvas
            val maxVal = maxOf(
                days.maxOf { it.focusMinutes },
                days.maxOf { it.restMinutes },
                60 // floor so small datasets don't blow bars to full height
            ).toFloat()

            val baselinePad = 2.dp.toPx()
            val usableH = size.height - baselinePad
            val groupW = size.width / days.size
            val barW = groupW * 0.30f
            val innerGap = groupW * 0.08f
            val groupPad = (groupW - (barW * 2 + innerGap)) / 2f
            val corner = minOf(barW / 2f, 4.dp.toPx())

            days.forEachIndexed { index, day ->
                val groupStart = index * groupW + groupPad
                listOf(
                    day.focusMinutes to FocusBarBlue,
                    day.restMinutes to RestBarRed
                ).forEachIndexed { barIndex, (minutes, color) ->
                    if (minutes > 0) {
                        val barH = (minutes / maxVal) * usableH * animProgress
                        val x = groupStart + barIndex * (barW + innerGap)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, usableH - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
                }
            }

            // Baseline
            drawLine(
                color = baselineColor,
                start = Offset(0f, usableH + baselinePad / 2f),
                end = Offset(size.width, usableH + baselinePad / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Weekday labels (today highlighted by date; Mon..Sun order)
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                val isToday = day.date == todayStr
                val weekIndex = runCatching {
                    LocalDate.parse(day.date).dayOfWeek.value % 7
                }.getOrDefault(index % 7)
                Text(
                    text = weekdays.getOrElse(weekIndex) { "" },
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MiuixTheme.colorScheme.primary
                            else labelBase.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
