package com.gocheap.travel.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gocheap.travel.data.model.CalendarDate
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightShapes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import flighttp.shared.generated.resources.Res
import com.gocheap.travel.ui.component.storedDateLabel
import com.gocheap.travel.ui.component.monthName
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.confirm
import flighttp.shared.generated.resources.drop_off_time
import flighttp.shared.generated.resources.end
import flighttp.shared.generated.resources.select
import flighttp.shared.generated.resources.date_label
import flighttp.shared.generated.resources.start_label
import flighttp.shared.generated.resources.time_label
import flighttp.shared.generated.resources.weekday_short_1
import flighttp.shared.generated.resources.weekday_short_2
import flighttp.shared.generated.resources.weekday_short_3
import flighttp.shared.generated.resources.weekday_short_4
import flighttp.shared.generated.resources.weekday_short_5
import flighttp.shared.generated.resources.weekday_short_6
import flighttp.shared.generated.resources.weekday_short_7
import flighttp.shared.generated.resources.pick_up_time_label

/**
 * The shared date picker, pushed from the flight, hotel and car search cards.
 *
 * In [CalendarMode.Range] the first tap sets the start and clears the end, and the next tap closes
 * the span — unless it lands on or before the start, which restarts the selection instead of
 * producing a backwards range.
 *
 * There is still no "disable past dates" rule. The app now has a clock — [CalendarDate.today] —
 * so one could be added; it is a deliberate gap rather than an impossible one.
 */
@Composable
fun CalendarScreen(
    title: String,
    mode: CalendarMode,
    initialStart: String?,
    initialEnd: String?,
    onBack: () -> Unit,
    onConfirm: (DateSelection) -> Unit,
    modifier: Modifier = Modifier,
    withTime: Boolean = false,
    initialStartTime: String? = null,
    initialEndTime: String? = null,
    spanUnit: String = "nights",
) {
    var start by remember { mutableStateOf(CalendarDate.parse(initialStart)) }
    var end by remember { mutableStateOf(if (mode == CalendarMode.Range) CalendarDate.parse(initialEnd) else null) }
    var startTime by remember { mutableStateOf(TimeSlots.normalise(initialStartTime)) }
    var endTime by remember { mutableStateOf(TimeSlots.normalise(initialEndTime)) }

    val today = remember { CalendarDate.today() }
    // A year ahead, laid end to end. The page scrolls through them rather than stepping a month at
    // a time: picking a date six weeks out is a scroll, not six taps on an arrow.
    val months = remember(today) {
        val first = CalendarDate(today.year, today.month, 1)
        List(MonthsAhead) { first.plusMonths(it) }
    }
    val listState = rememberLazyListState()

    // Opens on the month the caller is already looking at. Jumped to rather than animated — an
    // animation on open draws the eye to the scrolling instead of to the dates.
    LaunchedEffect(months) {
        val target = CalendarDate.parse(initialStart) ?: today
        val index = months.indexOfFirst { it.year == target.year && it.month == target.month }
        if (index > 0) listState.scrollToItem(index)
    }

    val canConfirm = when (mode) {
        CalendarMode.Single -> start != null
        CalendarMode.Range -> start != null && end != null
    }

    val onDayClick: (CalendarDate) -> Unit = { date ->
        when (mode) {
            CalendarMode.Single -> {
                start = date
                end = null
            }
            // Tapping on or before the start means "I meant to begin here instead".
            CalendarMode.Range -> {
                val current = start
                if (current == null || end != null || date <= current) {
                    start = date
                    end = null
                } else {
                    end = date
                }
            }
        }
    }

    InnerPage(
        title = title,
        onBack = onBack,
        modifier = modifier,
        contentTop = 0.dp,
    ) {
        // Outside the list, so the columns stay labelled however far down the year you scroll.
        WeekdayHeader()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(
                items = months,
                key = { _, month -> "${month.year}-${month.month}" },
            ) { index, month ->
                MonthSection(
                    month = month,
                    today = today,
                    start = start,
                    end = end,
                    onDayClick = onDayClick,
                    showDivider = index < months.lastIndex,
                )
            }
        }

        // Pinned above the footer rather than trailing the months: a rental's times are part of
        // the same decision as its dates, and a year of scrolling should not stand between them.
        if (withTime) {
            Column(modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal)) {
                HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
                Spacer(Modifier.height(16.dp))
                TimeRow(
                    label = stringResource(if (mode == CalendarMode.Range) Res.string.pick_up_time_label else Res.string.time_label),
                    selected = startTime,
                    onSelect = { startTime = it },
                )
                if (mode == CalendarMode.Range) {
                    Spacer(Modifier.height(14.dp))
                    TimeRow(
                        label = stringResource(Res.string.drop_off_time),
                        selected = endTime,
                        onSelect = { endTime = it },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        SelectionFooter(
            mode = mode,
            start = start,
            end = end,
            canConfirm = canConfirm,
            startTime = startTime.takeIf { withTime },
            endTime = endTime.takeIf { withTime && mode == CalendarMode.Range },
            spanUnit = spanUnit,
            onConfirm = {
                val chosen = start ?: return@SelectionFooter
                onConfirm(
                    DateSelection(
                        start = chosen.label,
                        end = end?.label,
                        startTime = startTime.takeIf { withTime },
                        endTime = endTime.takeIf { withTime && mode == CalendarMode.Range },
                    ),
                )
            },
        )
    }
}

/**
 * Column labels, in pills the way the reference app sets them. Monday first: a trip is picked by
 * weekend, and a week that ends on Sunday keeps the two weekend days beside each other.
 */
@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 10.dp),
    ) {
        WeekdayResources.map { stringResource(it) }.forEach { label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Inset rather than spaced, so each pill stays centred over its own column.
                    .padding(horizontal = 3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FlightColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = FlightColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One month of the scroll: its name, its weeks, and a rule separating it from the next. */
@Composable
private fun MonthSection(
    month: CalendarDate,
    today: CalendarDate,
    start: CalendarDate?,
    end: CalendarDate?,
    onDayClick: (CalendarDate) -> Unit,
    showDivider: Boolean,
) {
    val weeks = remember(month) { calendarWeeks(month) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            // The month carries the weight and the year trails it: scrolling a year of these, the
            // month is what you are reading and the year only matters at the turn.
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(monthName(month)) }
                append(" ")
                append(month.year.toString())
            },
            style = MaterialTheme.typography.headlineSmall,
            color = FlightColors.TextPrimary,
            modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
        )

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FlightSpacing.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            today = today,
                            start = start,
                            end = end,
                            onClick = onDayClick,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = FlightColors.Divider,
                modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: CalendarDate?,
    today: CalendarDate,
    start: CalendarDate?,
    end: CalendarDate?,
    onClick: (CalendarDate) -> Unit,
) {
    if (date == null) {
        Spacer(Modifier.weight(1f).height(CellHeight))
        return
    }

    val hasRange = start != null && end != null && start < end
    val isStart = date == start
    val isEnd = date == end
    val isEndpoint = isStart || isEnd
    // Null-checked in the same chain as the comparison: `hasRange` is a separate value, so it
    // buys no smart cast here.
    val inBand = start != null && end != null && date > start && date < end
    // A flight cannot leave yesterday. Past days stay on the grid so the month keeps its shape,
    // but they are faded and refuse the tap.
    val isPast = date < today

    Box(
        modifier = Modifier.weight(1f).height(CellHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn behind the day, and only half a cell at each end, so the band starts and stops
        // under the selected circles instead of squaring off beside them.
        when {
            hasRange && isStart -> Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .background(FlightColors.PrimarySoft),
            )

            hasRange && isEnd -> Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .background(FlightColors.PrimarySoft),
            )

            inBand -> Box(
                modifier = Modifier.fillMaxSize().background(FlightColors.PrimarySoft),
            )
        }

        Box(
            modifier = Modifier
                .size(CellHeight)
                .clip(CircleShape)
                .background(if (isEndpoint) FlightColors.Primary else Color.Transparent)
                .clickable(enabled = !isPast) { onClick(date) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isEndpoint -> Color.White
                    isPast -> FlightColors.TextPrimary.copy(alpha = 0.3f)
                    else -> FlightColors.TextPrimary
                },
            )
        }
    }
}

/** A month padded out to whole Monday-first weeks; the pad days are nulls. */
private fun calendarWeeks(month: CalendarDate): List<List<CalendarDate?>> {
    val first = CalendarDate(month.year, month.month, 1)
    // dayOfWeek counts from Sunday, so shift it round to put Monday in the first column.
    val leading = (first.dayOfWeek + 6) % 7
    val length = CalendarDate.lengthOfMonth(month.year, month.month)

    val cells = buildList<CalendarDate?> {
        repeat(leading) { add(null) }
        (1..length).forEach { day -> add(CalendarDate(month.year, month.month, day)) }
        while (size % 7 != 0) add(null)
    }
    return cells.chunked(7)
}

@Composable
private fun SelectionFooter(
    mode: CalendarMode,
    start: CalendarDate?,
    end: CalendarDate?,
    canConfirm: Boolean,
    startTime: String?,
    endTime: String?,
    spanUnit: String,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlightColors.Surface)
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 16.dp),
    ) {
        HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionSummary(
                label = if (mode == CalendarMode.Range) stringResource(Res.string.start_label) else stringResource(Res.string.date_label),
                value = storedDateLabel(start?.label),
                caption = startTime,
                modifier = Modifier.weight(1f),
            )
            if (mode == CalendarMode.Range) {
                SelectionSummary(
                    label = stringResource(Res.string.end),
                    value = storedDateLabel(end?.label),
                    caption = endTime,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (mode == CalendarMode.Range && start != null && end != null) {
            Spacer(Modifier.height(8.dp))
            val span = start.daysUntil(end)
            // "nights" -> "night"; the singular is just the unit without its trailing s.
            Text(
                text = "$span ${if (span == 1) spanUnit.removeSuffix("s") else spanUnit}",
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = stringResource(Res.string.confirm),
            onClick = onConfirm,
            enabled = canConfirm,
        )
    }
}

@Composable
private fun SelectionSummary(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.TextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value ?: stringResource(Res.string.select),
            style = MaterialTheme.typography.titleMedium,
            color = if (value != null) FlightColors.TextPrimary else FlightColors.TextTertiary,
        )
        if (caption != null && value != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
            )
        }
    }
}

/**
 * Horizontal strip of half-hour slots. It opens scrolled to the current choice rather than at
 * midnight, so the selected chip is on screen without the user hunting for it.
 */
@Composable
private fun TimeRow(
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (TimeSlots.All.indexOf(selected) - 2).coerceAtLeast(0),
    )

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TimeSlots.All, key = { it }) { slot ->
                val isSelected = slot == selected
                Box(
                    modifier = Modifier
                        .clip(FlightShapes.Pill)
                        .background(if (isSelected) FlightColors.Primary else FlightColors.SurfaceMuted)
                        .clickable { onSelect(slot) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = slot,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) Color.White else FlightColors.TextPrimary,
                    )
                }
            }
        }
    }
}

private val CellHeight = 40.dp

/** How far ahead the scroll runs. A year is as far out as any of these searches sell. */
private const val MonthsAhead = 12



/**
 * The seven column headers, Monday first.
 *
 * Resources rather than letters: every language abbreviates its weekdays differently, and some do
 * not use two characters at all.
 */
private val WeekdayResources = listOf(
    Res.string.weekday_short_1, Res.string.weekday_short_2, Res.string.weekday_short_3,
    Res.string.weekday_short_4, Res.string.weekday_short_5, Res.string.weekday_short_6,
    Res.string.weekday_short_7,
)
