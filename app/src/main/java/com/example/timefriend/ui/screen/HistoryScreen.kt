package com.example.timefriend.ui.screen

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timefriend.R
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.ui.component.DayPieChart
import com.example.timefriend.ui.component.MonthYearPickerDialog
import com.example.timefriend.ui.component.PieChartLegend
import com.example.timefriend.ui.component.PomodoroWeekChart
import com.example.timefriend.ui.theme.GlobalCornerRadius
import com.example.timefriend.viewmodel.DailyPomodoro
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── 底部卡片 Pager 无限循环常量（对齐 sportlink RunningDistanceCard.MILE_CARD_*）──
// HorizontalPager 原生不支持循环：伪造大 pageCount + 内容按 page % 真实页数映射，
// 初始页取 4 对齐的中心值（直接用 Int.MAX_VALUE/2 会错落到奇数页，sportlink 同款历史坑），
// 两侧都有页可滑，用户实际滑不到 0 / Int.MAX_VALUE 边界。
private const val SUMMARY_PAGE_COUNT = 2
private const val SUMMARY_PAGER_TOTAL = Int.MAX_VALUE
private val SUMMARY_PAGER_CENTER =
    (SUMMARY_PAGER_TOTAL / 2) / SUMMARY_PAGE_COUNT * SUMMARY_PAGE_COUNT

/** 把"真实页索引 realPage (0..1)"映射到无限循环伪 Pager 上、靠近中心且同余的伪造页索引。 */
private fun summaryFakePageFor(realPage: Int): Int {
    val r = (realPage % SUMMARY_PAGE_COUNT + SUMMARY_PAGE_COUNT) % SUMMARY_PAGE_COUNT
    return SUMMARY_PAGER_CENTER + r
}

@Composable
fun HistoryScreen(
    records: List<TimeRecord>,
    weekRecords: List<TimeRecord>,
    monthRecords: List<TimeRecord>,
    selectedDate: String,
    recordDates: Set<String>,
    pomodoroWeek: List<DailyPomodoro>,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pieMode by remember { mutableStateOf(0) } // 0 = day, 1 = week, 2 = month
    // 日历卡实测高度（px，onGloballyPositioned 上报）：饼图总结卡片与其等高，
    // 机制对齐 sportlink HistoryScreen 的 RunningDistanceCard（calendarHeightPx / density）
    var calendarHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current.density
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val calendarContent = @Composable {
        CalendarView(
            selectedDate = selectedDate,
            recordDates = recordDates,
            onSelectDate = onSelectDate
        )
    }

    val pieRecords = when (pieMode) {
        0 -> records
        1 -> weekRecords
        else -> monthRecords
    }
    val pieLabel = when (pieMode) {
        0 -> stringResource(R.string.history_day_total)
        1 -> stringResource(R.string.history_week_total)
        else -> stringResource(R.string.history_month_total)
    }

    // —— 底部卡片区：左右滑动双页（饼图总结 / 番茄钟周统计）——
    // 无限循环 + 页码落盘记忆（对齐 sportlink RunningDistanceCard + HistoryPrefs）：
    // 进页恢复上次停留的真实页；滑动到新页时落盘，下次进入恢复。
    val context = LocalContext.current
    val pagePrefs = remember {
        context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
    }
    val pagerState = rememberPagerState(
        initialPage = summaryFakePageFor(pagePrefs.getInt("summary_card_page", 0))
    ) { SUMMARY_PAGER_TOTAL }
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) {
        pagePrefs.edit()
            .putInt("summary_card_page", pagerState.currentPage % SUMMARY_PAGE_COUNT)
            .apply()
    }

    // 饼图页内容：不再自带 Card / 内部 verticalScroll（对齐 sportlink 各页设计——
    // 页内容 weight 弹性自适应 Pager 高度、无内部滚动）。内部滚动会在内容超出等高约束时
    // 抢走纵向滑动手势（卡内小幅滚动、整页不动），是竖屏"上滑看底部卡片"失效的根因之一；
    // 改为自适应后，纵向手势恒直达根滚动容器（整页上移）。
    val piePage = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Day/Week/Month toggle with sliding pill
            val chipLabels = listOf(
                stringResource(R.string.calendar_day_mode),
                stringResource(R.string.calendar_week_mode),
                stringResource(R.string.calendar_month_mode)
            )
            val chipAnim = remember { androidx.compose.animation.core.Animatable(0f) }
            val chipDensity = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(pieMode) {
                val containerWidthPx = with(chipDensity) { 200.dp.toPx() }
                val chipWidthPx = containerWidthPx / chipLabels.size
                chipAnim.animateTo(
                    chipWidthPx * pieMode,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.7f, stiffness = 400f
                    )
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(GlobalCornerRadius))
                    // 容器底色用 onSurface 8% 叠加：深色下比卡片底浅、浅色下比卡片底深，
                    // 恒与背景可区分（原 surfaceContainerHigh 深色 #242424 与卡片 #222222 几乎同色）
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(4.dp)
                    .width(200.dp)
            ) {
                // Pill
                val pillWidthFraction = 1f / chipLabels.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pillWidthFraction)
                        .height(32.dp)
                        .offset { androidx.compose.ui.unit.IntOffset(chipAnim.value.toInt(), 0) }
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(GlobalCornerRadius))
                        .background(MiuixTheme.colorScheme.primary)
                )
                // Labels
                Row(modifier = Modifier.fillMaxWidth()) {
                    chipLabels.forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { pieMode = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (pieMode == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (pieMode == index) MiuixTheme.colorScheme.onPrimary
                                        else MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (pieRecords.isNotEmpty()) {
                // 饼图弹性自适应：weight 分得剩余高度，aspectRatio 取宽高中较小值成正方形
                //（替代原固定 180dp + 内部 verticalScroll 兜底——等高约束变矮时内容自动收缩）
                DayPieChart(
                    records = pieRecords,
                    strokeWidth = 22.dp,
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PieChartLegend(records = pieRecords)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (pieMode) {
                            0 -> stringResource(R.string.history_no_records)
                            1 -> stringResource(R.string.history_no_records_week)
                            else -> stringResource(R.string.history_no_records_month)
                        },
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // 番茄钟周统计页：柱状图（左蓝=投入、右红=休息，最近 7 天）+ 底部两行汇总
    //（同饼图页：去 Card 包裹，由外层统一卡片容器承载；柱图 weight 弹性自适应、无内部滚动）
    val pomodoroPage = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PomodoroWeekChart(
                days = pomodoroWeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val focusTotal = pomodoroWeek.sumOf { it.focusMinutes }
            val restTotal = pomodoroWeek.sumOf { it.restMinutes }
            val focusText =
                if (focusTotal >= 60) stringResource(R.string.duration_hours_minutes, focusTotal / 60, focusTotal % 60)
                else stringResource(R.string.duration_minutes, focusTotal)
            val restText =
                if (restTotal >= 60) stringResource(R.string.duration_hours_minutes, restTotal / 60, restTotal % 60)
                else stringResource(R.string.duration_minutes, restTotal)

            Text(
                text = stringResource(R.string.history_pomodoro_week_focus, focusText),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.history_pomodoro_week_rest, restText),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }

    // 底部卡片容器（对齐 sportlink RunningDistanceCard 结构）：单卡承载 Pager + 卡内页指示点，
    // 卡高 = 日历卡实测高度（等高机制不变）。首帧 heightIn 限高兜底不可省：
    // 竖屏根 Column / 横屏右列的 verticalScroll 会向下传递无限最大高度，直接 wrap 会把
    // 无限高灌进 Pager（2026-10-13 冷启动 "infinity maximum height constraints" 实测崩溃，
    // adb logcat 完整栈确认）；heightIn(max) 夹成有界值，日历卡实测上报后切精确等高。
    val summaryCard = @Composable {
        Card(
            cornerRadius = GlobalCornerRadius,
            colors = CardDefaults.defaultColors(),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (calendarHeightPx > 0f) {
                        Modifier.height((calendarHeightPx / density).dp)
                    } else {
                        Modifier.heightIn(max = 400.dp)
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    if (page % SUMMARY_PAGE_COUNT == 0) piePage() else pomodoroPage()
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 页指示点：保留 Timefriend 的可点击跳页（sportlink PagerDots 为纯指示）；
                // 伪页大数下相对定位到当前页附近同余页（sportlink 深链定位同款偏移算法）
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(SUMMARY_PAGE_COUNT) { index ->
                        val selected = pagerState.currentPage % SUMMARY_PAGE_COUNT == index
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val current = pagerState.currentPage
                                    val offset = (index - (current % SUMMARY_PAGE_COUNT) +
                                        SUMMARY_PAGE_COUNT) % SUMMARY_PAGE_COUNT
                                    pagerScope.launch {
                                        pagerState.animateScrollToPage(current + offset)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 6.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxSize()
                // 横屏与竖屏同构（对齐 sportlink HistoryScreen）：整页一个滚动容器，
                // Row 内两卡为自然高度/等高（**不用 fillMaxHeight 撑满**），内容超高时整页滚动；
                // inset 属于滚动内容 → 滚动后整页（含卡片）可显示在状态栏区域
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(
                    cornerRadius = GlobalCornerRadius,
                    colors = CardDefaults.defaultColors(),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { calendarHeightPx = it.size.height.toFloat() }
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        calendarContent()
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    summaryCard()
                }
            }

            // 底部避让悬浮导航栏（对齐 sportlink 横屏整页预留 120dp）
            Spacer(modifier = Modifier.height(120.dp))
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                // 小屏兜底：日历 + 等高卡片 + 指示点可能超一屏。
                // verticalScroll 必须在 insets padding 之前（inset 属于滚动内容）：滚动后
                // 内容可显示在状态栏区域（对齐 sportlink 沉浸滚动语义）；若 insets 在
                // scroll 之外，视口顶边落在状态栏下缘 → 内容滚到顶即被裁切
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            Card(
                cornerRadius = GlobalCornerRadius,
                colors = CardDefaults.defaultColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { calendarHeightPx = it.size.height.toFloat() }
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    calendarContent()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            summaryCard()

            // 底部避让悬浮导航栏（64dp 本体 + 底部 inset + 8dp 边距 ≈ 100dp；对齐 sportlink
            // 底部 Spacer(120dp) 同款预留）：保证根滚动容器恒有滚动行程——竖屏"上滑把底部
            // 卡片/指示点滚出导航栏遮挡区"的关键。无此 Spacer 时内容总高（日历+等高卡片+
            // 指示点）≈ 视口高度，滚动余量≈0，上滑无响应且卡片底部被悬浮导航栏盖住。
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun CalendarView(
    selectedDate: String,
    recordDates: Set<String>,
    onSelectDate: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val context = LocalContext.current

    var baseDate by remember { mutableStateOf(Calendar.getInstance()) }
    // 年月选择弹窗显隐（对齐 SportLink CalendarGrid：点击标题弹出）
    var showMonthYearPicker by remember { mutableStateOf(false) }

    val today = dateFormat.format(java.util.Date())
    val weekdays = context.resources.getStringArray(R.array.weekday_short).toList()

    val monthTransitionSpec: AnimatedContentTransitionScope<Calendar>.() -> ContentTransform = {
        val fromMonth =
            initialState.get(Calendar.YEAR) * 12 + initialState.get(Calendar.MONTH)
        val toMonth =
            targetState.get(Calendar.YEAR) * 12 + targetState.get(Calendar.MONTH)
        // SportLink CalendarGrid 同款 direction 约定：
        // 切到下月（direction = 1）：新内容从右侧滑入、旧内容向左滑出（内容整体向左移动，
        // 与"向左滑切下月"的手势方向一致）；切到上月（direction = -1）：新内容从左侧滑入
        // （从左到右）、旧内容向右滑出。
        val direction = if (toMonth > fromMonth) 1 else -1
        val contentTransform =
            (slideInHorizontally(animationSpec = tween(250)) { it * direction } +
                fadeIn(animationSpec = tween(250)))
                .togetherWith(
                    slideOutHorizontally(animationSpec = tween(250)) { -it * direction } +
                        fadeOut(animationSpec = tween(250))
                )
        contentTransform.using(SizeTransform(clip = false) { _, _ -> tween(250) })
    }

    Column {
        // 第一行：上一月 ◀ | 年月标题（居中） | 下一月 ▶，三者同处一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.calendar_prev_month),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val prev = baseDate.clone() as Calendar
                        prev.add(Calendar.MONTH, -1)
                        baseDate = prev
                    }
                    .padding(8.dp),
                color = MiuixTheme.colorScheme.onSurface
            )
            AnimatedContent(
                targetState = baseDate,
                modifier = Modifier.weight(1f),
                transitionSpec = monthTransitionSpec,
                label = "calendar_month_title"
            ) { targetDate ->
                Text(
                    text = formatMonthLabel(targetDate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GlobalCornerRadius))
                        .clickable { showMonthYearPicker = true }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.calendar_next_month),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val next = baseDate.clone() as Calendar
                        next.add(Calendar.MONTH, 1)
                        baseDate = next
                    }
                    .padding(8.dp),
                color = MiuixTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 星期行 + 日期网格：整体做左右滑动 + 淡入淡出
        AnimatedContent(
            targetState = baseDate,
            modifier = Modifier
                .fillMaxWidth()
                // 日历内部左右滑动切换月份（与 SportLink CalendarGrid 同款：累计拖拽超 80px 触发）
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > 80f) {
                                val prev = baseDate.clone() as Calendar
                                prev.add(Calendar.MONTH, -1)
                                baseDate = prev
                            } else if (totalDrag < -80f) {
                                val next = baseDate.clone() as Calendar
                                next.add(Calendar.MONTH, 1)
                                baseDate = next
                            }
                        }
                    )
                },
            transitionSpec = monthTransitionSpec,
            label = "calendar_month_grid"
        ) { targetDate ->
            // 关键：必须使用 targetDate（各内容自己的月份），不要读外部 baseDate
            val cal = targetDate.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            // 下月日历（供尾部补位的暗淡日期生成日期串与跳月）
            val nextMonthCal = cal.clone() as Calendar
            nextMonthCal.add(Calendar.MONTH, 1)

            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 固定 42 格（6 行 × 7 列，SportLink CalendarGrid 同款「固定 6 行 + 邻月补位」）：
                // 前导空白（dayNum < 1）不绘制；当月（1..daysInMonth）正常绘制；
                // 尾部补下月暗淡日期（dayNum > daysInMonth → 显示下月 dayNum - daysInMonth），
                // 填满 6 行 → 卡片高度恒定，不随月份行数（4/5/6 行）跳变。
                for (row in 0 until 6) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val index = row * 7 + col
                            val dayNum = index - (firstDayOfWeek - 1) + 1
                            if (dayNum in 1..daysInMonth) {
                                cal.set(Calendar.DAY_OF_MONTH, dayNum)
                                val dateStr = dateFormat.format(cal.time)
                                DayCell(
                                    day = dayNum,
                                    isToday = dateStr == today,
                                    isSelected = dateStr == selectedDate,
                                    hasData = dateStr in recordDates,
                                    onClick = { onSelectDate(dateStr) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (dayNum > daysInMonth) {
                                // 暗淡的下月日期：点击切到下月并选中该日
                                val cellCal = nextMonthCal.clone() as Calendar
                                cellCal.set(Calendar.DAY_OF_MONTH, dayNum - daysInMonth)
                                val dateStr = dateFormat.format(cellCal.time)
                                DayCell(
                                    day = dayNum - daysInMonth,
                                    isToday = false,
                                    isSelected = false,
                                    hasData = false,
                                    isDimmed = true,
                                    onClick = {
                                        baseDate = cellCal
                                        onSelectDate(dateStr)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }

        // 年月选择弹窗（对齐 SportLink CalendarGrid：点击标题弹出，确认后跳月；
        // 经 DialogOverlay 注册到 MainScreen 宿主 slot，同窗口玻璃卡 + Haze scrim）
        if (showMonthYearPicker) {
            MonthYearPickerDialog(
                initialYear = baseDate.get(Calendar.YEAR),
                initialMonth = baseDate.get(Calendar.MONTH) + 1,
                onDismiss = { showMonthYearPicker = false },
                onConfirm = { year, month ->
                    val newMonth = baseDate.clone() as Calendar
                    newMonth.set(Calendar.YEAR, year)
                    newMonth.set(Calendar.MONTH, month - 1)
                    baseDate = newMonth
                },
            )
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDimmed: Boolean = false
) {
    val bgColor = when {
        isDimmed -> MiuixTheme.colorScheme.surface.copy(alpha = 0f)
        isSelected -> MiuixTheme.colorScheme.primary
        isToday -> MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MiuixTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val textColor = when {
        // 暗淡日期（邻月补位）：淡色显示，不参与选中/今天/数据着色
        isDimmed -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MiuixTheme.colorScheme.onPrimary
        isToday -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$day",
                fontSize = 14.sp,
                color = textColor
            )
            if (hasData && !isDimmed) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.onPrimary
                            else MiuixTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

@Composable
private fun PieModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh
    val textColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlobalCornerRadius))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

/**
 * 月份标签：固定 "yyyy/MM" 格式（对齐 SportLink CalendarGrid.formatMonthLabel），
 * 跨语言一致、无本地化依赖——避免各语言"年月"后缀差异（中文"2026年10月"/
 * 英文"October 2026"…），纯数字+斜杠对所有用户可读性一致。
 */
private fun formatMonthLabel(cal: Calendar): String =
    SimpleDateFormat("yyyy/MM", Locale.US).format(cal.time)
