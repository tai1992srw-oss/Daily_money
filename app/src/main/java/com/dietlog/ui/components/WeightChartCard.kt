package com.dietlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dietlog.data.diet.DaySummary
import com.dietlog.ui.theme.DietGreen
import com.dietlog.ui.theme.DietTeal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 直近の体重推移を折れ線で表示するカード。
 * 体重の記録がない日は点を打たず、日付間隔は実際の日数に比例させる。
 */
@Composable
fun WeightChartCard(
    history: List<DaySummary>,
    modifier: Modifier = Modifier
) {
    val points = history.filter { it.weightKg != null }
    if (points.isEmpty()) return

    val weights = points.map { it.weightKg!! }
    val latest = points.last()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "体重の推移（直近30日）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "最新 ${latest.weightKg} kg（${shortDate(latest.date)}）" +
                    " ・ 最高 ${weights.max()} ・ 最低 ${weights.min()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (points.size < 2) {
                Text(
                    text = "体重の記録が2日分たまるとグラフが表示されます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val firstDay = LocalDate.parse(points.first().date)
                    val lastDay = LocalDate.parse(points.last().date)
                    val daySpan = ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtLeast(1)

                    val minW = weights.min()
                    val maxW = weights.max()
                    // 縦のレンジは最低1kg確保して、日々の細かい上下で暴れて見えないようにする
                    val span = (maxW - minW).coerceAtLeast(1.0)
                    val mid = (maxW + minW) / 2
                    val top = mid + span / 2 + 0.2
                    val range = span + 0.4

                    fun x(p: DaySummary): Float =
                        ChronoUnit.DAYS.between(firstDay, LocalDate.parse(p.date)).toFloat() /
                            daySpan * size.width

                    fun y(w: Double): Float = ((top - w) / range).toFloat() * size.height

                    drawLine(gridColor, Offset(0f, y(maxW)), Offset(size.width, y(maxW)), 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, y(minW)), Offset(size.width, y(minW)), 1.dp.toPx())

                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val px = x(p)
                        val py = y(p.weightKg!!)
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, DietGreen, style = Stroke(width = 2.dp.toPx()))

                    points.forEach { p ->
                        drawCircle(DietTeal, radius = 3.dp.toPx(), center = Offset(x(p), y(p.weightKg!!)))
                    }
                    drawCircle(
                        DietGreen,
                        radius = 5.dp.toPx(),
                        center = Offset(x(latest), y(latest.weightKg!!))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = shortDate(points.first().date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = shortDate(points.last().date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/** "2026-08-27" → "08/27" */
private fun shortDate(date: String): String = date.takeLast(5).replace('-', '/')
