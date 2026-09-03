package com.dietlog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.dietlog.data.diet.AdviceEntry
import com.dietlog.data.diet.MealRecord
import com.dietlog.ui.theme.DietAmber
import com.dietlog.ui.theme.DietTeal
import java.text.NumberFormat

@Composable
fun MealItem(
    meal: MealRecord,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    var showPhoto by remember(meal.photoUrls) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (meal.photoUrls.isNotEmpty()) {
                MealThumbnail(
                    photoUrls = meal.photoUrls,
                    onClick = { showPhoto = true }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meal.meal.isNotBlank()) {
                        Text(
                            text = meal.meal,
                            style = MaterialTheme.typography.labelMedium,
                            color = DietTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (meal.time.isNotBlank()) {
                        Text(
                            text = meal.time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                Text(
                    text = meal.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (meal.hasPlace) {
                    PlaceChip(name = meal.placeName, url = meal.placeUrl)
                }
                if (meal.note.isNotBlank()) {
                    Text(
                        text = meal.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                // 本人の感想と星は、計算メモより目立つレビュー風に出す
                if (meal.rating != null || meal.comment.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        meal.rating?.let { stars ->
                            Text(
                                text = "★".repeat(stars.coerceIn(1, 5)),
                                style = MaterialTheme.typography.labelMedium,
                                color = DietAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (meal.comment.isNotBlank()) {
                            Text(
                                text = "💬 ${meal.comment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = numberFormat.format(meal.kcal) + " kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showPhoto) {
        PhotoDialog(urls = meal.photoUrls, onDismiss = { showPhoto = false })
    }
}

/** 1枚目をサムネイルに出し、2枚以上あるときは右下に枚数バッジを重ねる。 */
@Composable
private fun MealThumbnail(
    photoUrls: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photoUrls.first(),
            contentDescription = if (photoUrls.size > 1) {
                "食事の写真 ${photoUrls.size} 枚"
            } else {
                "食事の写真"
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        if (photoUrls.size > 1) {
            Text(
                text = "${photoUrls.size}枚",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/** 店名（＋地図・食べログへのリンク）。名前が無く URL だけのときは「地図を開く」と出す。 */
@Composable
private fun PlaceChip(
    name: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val label = name.ifBlank { "地図を開く" }
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(
                if (url.isNotBlank()) Modifier.clickable { uriHandler.openUri(url) } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = DietTeal,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 写真タップで開く拡大表示。複数枚は横スワイプで切り替え、タップで閉じる。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoDialog(urls: List<String>, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState { urls.size }

    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = pagerState) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = urls[page],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    )
                }
            }
            if (urls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AdviceCard(
    advice: AdviceEntry,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = DietTeal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = advice.type,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DietTeal
                    )
                    if (advice.time.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = advice.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                Text(
                    text = advice.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
