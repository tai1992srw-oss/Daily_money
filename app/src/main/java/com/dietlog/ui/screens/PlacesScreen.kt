package com.dietlog.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dietlog.data.diet.PlaceVisit
import com.dietlog.ui.components.MealItem
import com.dietlog.ui.theme.DietTeal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )

    // 詳細画面が開いているときは戻るボタンで一覧へ
    BackHandler(enabled = uiState.selectedPlace != null) {
        viewModel.closePlace()
    }

    val selected = uiState.selectedPlace
    if (selected != null) {
        PlaceDetail(
            place = selected,
            uiState = uiState,
            numberFormat = numberFormat,
            onBack = { viewModel.closePlace() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        when {
            !uiState.configured -> EmptyMessage(
                title = "設定がまだです",
                body = "今日タブの⚙から スプレッドシート連携を設定してください。",
                modifier = Modifier.align(Alignment.Center)
            )

            uiState.error != null -> EmptyMessage(
                title = "読み込めませんでした",
                body = uiState.error ?: "",
                modifier = Modifier.align(Alignment.Center)
            )

            uiState.places.isEmpty() && !uiState.isLoading -> EmptyMessage(
                title = "まだ店の記録がありません",
                body = "食事を記録するときに Google マップや食べログの URL、" +
                    "または店名を Claude に送ると、ここにたまっていきます。",
                modifier = Modifier.align(Alignment.Center)
            )

            else -> PlacesList(
                uiState = uiState,
                numberFormat = numberFormat,
                onSelectSort = { viewModel.setSort(it) },
                onTogglePrefecture = { viewModel.togglePrefecture(it) },
                onSelectPlace = { viewModel.selectPlace(it) }
            )
        }

        PullRefreshIndicator(
            refreshing = uiState.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesList(
    uiState: PlacesUiState,
    numberFormat: NumberFormat,
    onSelectSort: (PlaceSort) -> Unit,
    onTogglePrefecture: (String) -> Unit,
    onSelectPlace: (PlaceVisit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "行った店",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = "${uiState.places.size} 店 ・ のべ ${uiState.totalVisits} 回",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PlaceSort.values()) { sort ->
                    FilterChip(
                        selected = uiState.sort == sort,
                        onClick = { onSelectSort(sort) },
                        label = { Text(sort.label) }
                    )
                }
            }
        }

        if (uiState.prefectures.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.prefectures) { prefecture ->
                        FilterChip(
                            selected = uiState.selectedPrefecture == prefecture,
                            onClick = { onTogglePrefecture(prefecture) },
                            label = { Text(prefecture) }
                        )
                    }
                }
            }
        }

        uiState.sections.forEach { section ->
            if (section.title != null) {
                item(key = "header-${section.title}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DietTeal,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                }
            }
            items(section.places, key = { "${section.title}-${it.name}-${it.url}" }) { place ->
                PlaceCard(
                    place = place,
                    showLocality = section.title != null,
                    numberFormat = numberFormat,
                    onClick = { onSelectPlace(place) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(
    place: PlaceVisit,
    showLocality: Boolean,
    numberFormat: NumberFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // エリア順のときは見出しに都道府県が出ているので、市区町村だけ見せる
    val areaLabel = if (showLocality) place.locality else place.area

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = DietTeal,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name.ifBlank { "(店名なし)" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildList {
                    if (areaLabel.isNotBlank()) add(areaLabel)
                    if (place.lastDate.isNotBlank()) add("最終 ${place.lastDate.replace('-', '/')}")
                    if (place.totalKcal > 0) add("計 ${numberFormat.format(place.totalKcal)} kcal")
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" ・ "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${place.visits}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DietTeal
                )
                Text(
                    text = "回",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 店の詳細画面：訪問回数などのサマリー、地図リンク、
 * その店で食べた全記録（日付ごと・写真つき）。
 */
@Composable
private fun PlaceDetail(
    place: PlaceVisit,
    uiState: PlacesUiState,
    numberFormat: NumberFormat,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "一覧に戻る"
                    )
                }
                Text(
                    text = place.name.ifBlank { "(店名なし)" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val stats = buildList {
                    if (place.area.isNotBlank()) add(place.area)
                    add("訪問 ${place.visits} 回")
                    add("記録 ${place.meals} 件")
                    if (place.totalKcal > 0) add("計 ${numberFormat.format(place.totalKcal)} kcal")
                }
                Text(
                    text = stats.joinToString(" ・ "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (place.firstDate.isNotBlank()) {
                    Text(
                        text = "初回 ${place.firstDate.replace('-', '/')} ・ " +
                            "最終 ${place.lastDate.replace('-', '/')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (place.url.isNotBlank()) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri(place.url) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("地図・店ページを開く")
                    }
                }
            }
        }

        when {
            uiState.placeMealsLoading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.placeMealsError != null -> item {
                Text(
                    text = uiState.placeMealsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            else -> {
                uiState.placeVisitDays.forEach { day ->
                    item(key = "day-${day.date}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = day.date.replace('-', '/'),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DietTeal
                            )
                            Text(
                                text = "${numberFormat.format(day.totalKcal)} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    items(day.meals) { meal ->
                        MealItem(
                            meal = meal,
                            numberFormat = numberFormat,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
