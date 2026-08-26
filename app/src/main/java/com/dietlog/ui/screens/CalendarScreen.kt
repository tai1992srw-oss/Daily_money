package com.dietlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dietlog.data.diet.DaySummary
import com.dietlog.data.diet.DietDay
import com.dietlog.ui.components.AdviceCard
import com.dietlog.ui.components.MealItem
import com.dietlog.ui.theme.DietGreen
import com.dietlog.ui.theme.DietTeal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CalendarHeader(
                month = uiState.month,
                isLoading = uiState.isLoading,
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() },
                onRefresh = { viewModel.refresh() }
            )

            if (!uiState.configured) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "先に「今日」タブでスプレッドシート連携を設定してください",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
                return@Column
            }

            if (uiState.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            WeekdayHeader()
            MonthGrid(
                month = uiState.month,
                today = uiState.today,
                days = uiState.days,
                onDayClick = { viewModel.selectDay(it) }
            )

            Text(
                text = "🟢 収支マイナス（消費が上回った日）　🔴 収支プラス　💡 アドバイスあり",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (uiState.selectedDate != null) {
            ModalBottomSheet(onDismissRequest = { viewModel.clearSelection() }) {
                DayDetailContent(
                    day = uiState.selectedDay,
                    isLoading = uiState.detailLoading
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    month: YearMonth,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "前の月")
        }
        Text(
            text = "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "次の月")
        }
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "更新")
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("日", "月", "火", "水", "木", "金", "土")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = when (index) {
                    0 -> MaterialTheme.colorScheme.error
                    6 -> DietTeal
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    days: Map<String, DaySummary>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOffset = month.atDay(1).dayOfWeek.value % 7 // 日曜=0
    val cells: List<LocalDate?> =
        List(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                summary = days[date.toString()],
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                onClick = { onDayClick(date) }
                            )
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    val hasData = summary != null && (summary.meals > 0 || summary.burnedKcal != null)
    val balanceColor = when {
        summary?.balanceKcal == null -> null
        summary.balanceKcal <= 0 -> DietGreen
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isToday) {
                    Modifier.border(2.dp, DietTeal, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = !isFuture && hasData) { onClick() }
            .padding(vertical = 4.dp)
            .heightIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isFuture) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (hasData) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(balanceColor ?: Color.Gray.copy(alpha = 0.4f))
            )
            Text(
                text = "${summary?.intakeKcal ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if ((summary?.adviceCount ?: 0) > 0) {
                Text(
                    text = "💡",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun DayDetailContent(
    day: DietDay?,
    isLoading: Boolean
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    if (isLoading || day == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val intake = day.totals.kcal
    val burned = day.sheetActivity?.totalKcal?.takeIf { it > 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = day.date.replace('-', '/'),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailStat("摂取", "${numberFormat.format(intake)} kcal")
                        DetailStat("消費", burned?.let { "${numberFormat.format(it)} kcal" } ?: "—")
                        DetailStat(
                            "収支",
                            if (burned != null) {
                                val b = intake - burned
                                "${if (b > 0) "+" else ""}${numberFormat.format(b)}"
                            } else "—"
                        )
                    }
                    val act = day.sheetActivity
                    if (act != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DetailStat(
                                "歩数",
                                if (act.steps > 0) numberFormat.format(act.steps) else "—"
                            )
                            DetailStat("睡眠", act.sleepH?.let { "$it h" } ?: "—")
                            DetailStat("体重", act.weightKg?.let { "$it kg" } ?: "—")
                        }
                    }
                }
            }
        }

        if (day.advice.isNotEmpty()) {
            item {
                Text(text = "アドバイス", style = MaterialTheme.typography.titleMedium)
            }
            items(day.advice) { entry ->
                AdviceCard(advice = entry)
            }
        }

        item {
            Text(text = "食事", style = MaterialTheme.typography.titleMedium)
        }
        if (day.meals.isEmpty()) {
            item {
                Text(
                    text = "記録がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            items(day.meals) { meal ->
                MealItem(meal = meal, numberFormat = numberFormat)
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
