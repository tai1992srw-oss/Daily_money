package com.dietlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dietlog.data.diet.DietSettings

@Composable
fun DietSettingsDialog(
    settings: DietSettings,
    onDismiss: () -> Unit,
    onSave: (apiUrl: String, token: String, targetKcal: Int) -> Unit
) {
    var apiUrl by remember { mutableStateOf(settings.apiUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var targetKcal by remember { mutableStateOf(settings.targetKcal.toString()) }

    val target = targetKcal.toIntOrNull()
    val isValid = apiUrl.isNotBlank() && token.isNotBlank() && target != null && target > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カロリー記録の設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("スプレッドシート連携（GAS）の情報を入力してください。セットアップ手順は docs/diet-setup.md 参照。")
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = { Text("API URL") },
                    placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("トークン") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetKcal,
                    onValueChange = { targetKcal = it },
                    label = { Text("目標摂取カロリー (kcal/日)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiUrl.trim(), token.trim(), target ?: 0) },
                enabled = isValid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
