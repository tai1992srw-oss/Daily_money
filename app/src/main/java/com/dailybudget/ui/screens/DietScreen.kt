package com.dailybudget.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.dailybudget.data.diet.MealRecord
import com.dailybudget.ui.components.DietSettingsDialog
import com.dailybudget.ui.theme.DietGreen
import com.dailybudget.ui.theme.DietTeal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun DietScreen(
    viewModel: DietViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.onPermissionResult()
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.settingsLoaded -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                !uiState.settings.isConfigured -> {
                    DietSetupPrompt(
                        onOpenSettings = { viewModel.showSettings() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    DietContent(
                        uiState = uiState,
                        onRefresh = { viewModel.refresh() },
                        onOpenSettings = { viewModel.showSettings() },
                        onRequestPermissions = {
                            permissionLauncher.launch(viewModel.healthConnectPermissions)
                        }
                    )
                }
            }

            if (uiState.showSettings) {
                DietSettingsDialog(
                    settings = uiState.settings,
                    onDismiss = { viewModel.hideSettings() },
                    onSave = { apiUrl, token, targetKcal ->
                        viewModel.saveSettings(apiUrl, token, targetKcal)
                    }
                )
            }
        }
    }
}

@Composable
private fun DietSetupPrompt(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "カロリー記録をはじめる",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Claude に食べたものを送るとスプレッドシートに記録され、" +
                "ここに今日のカロリー収支が表示されます。\n" +
                "まずはスプレッドシート連携（GAS）の設定をしてください。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onOpenSettings) {
            Text("設定する")
        }
    }
}

@Composable
private fun DietContent(
    uiState: DietUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val intake = uiState.day?.totals?.kcal ?: 0
    val burned = uiState.activity?.totalKcal

    Column(modifier = Modifier.fillMaxSize()) {
        DietSummarySection(
            intake = intake,
            burned = burned,
            steps = uiState.activity?.steps,
            isLoading = uiState.isLoading,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.weight(0.45f)
        )

        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
        ) {
            if (uiState.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            TargetProgressCard(
                intake = intake,
                targetKcal = uiState.settings.targetKcal,
                totals = uiState.day?.totals,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (!uiState.hcGranted) {
                if (uiState.hcAvailable) {
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("Health Connect と連携（歩数・消費カロリー）")
                    }
                } else {
                    Text(
                        text = "Health Connect が利用できないため消費カロリーは表示されません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Text(
                text = "今日の食事",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val meals = uiState.day?.meals ?: emptyList()
            if (meals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "まだ記録がありません\nClaude に食べたものを送ってみましょう",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(meals) { meal ->
                        MealItem(meal = meal, numberFormat = numberFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun DietSummarySection(
    intake: Int,
    burned: Int?,
    steps: Long?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    val numberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DietGreen, DietTeal)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LocalDate.now().format(dateFormatter),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
            Row {
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "同期",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "設定",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (burned != null) "今日のカロリー収支" else "今日の摂取カロリー",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            val mainValue = if (burned != null) intake - burned else intake
            val sign = if (burned != null && mainValue > 0) "+" else ""
            Text(
                text = "$sign${numberFormat.format(mainValue)} kcal",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStat(label = "摂取", value = "${numberFormat.format(intake)} kcal")
            SummaryStat(
                label = "消費",
                value = if (burned != null) "${numberFormat.format(burned)} kcal" else "—"
            )
            SummaryStat(
                label = "歩数",
                value = if (steps != null) "${numberFormat.format(steps)} 歩" else "—"
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TargetProgressCard(
    intake: Int,
    targetKcal: Int,
    totals: com.dailybudget.data.diet.MealTotals?,
    modifier: Modifier = Modifier
) {
    val remaining = targetKcal - intake

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "目標 $targetKcal kcal",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (remaining >= 0) "あと $remaining kcal" else "${-remaining} kcal オーバー",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) DietGreen else MaterialTheme.colorScheme.error
                )
            }

            LinearProgressIndicator(
                progress = (intake.toFloat() / targetKcal.coerceAtLeast(1)).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (remaining >= 0) DietGreen else MaterialTheme.colorScheme.error
            )

            if (totals != null) {
                Text(
                    text = "P ${totals.proteinG.toInt()}g ・ F ${totals.fatG.toInt()}g ・ C ${totals.carbsG.toInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun MealItem(
    meal: MealRecord,
    numberFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                if (meal.note.isNotBlank()) {
                    Text(
                        text = meal.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text = "${numberFormat.format(meal.kcal)} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
