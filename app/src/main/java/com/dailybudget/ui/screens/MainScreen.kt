package com.dailybudget.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dailybudget.data.database.Transaction
import com.dailybudget.data.model.Category
import com.dailybudget.data.model.TransactionType
import com.dailybudget.ui.components.InitialSetupDialog
import com.dailybudget.ui.components.TransactionInputBottomSheet
import com.dailybudget.ui.components.TransactionItem
import com.dailybudget.ui.theme.Blue
import com.dailybudget.ui.theme.Purple
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isInitialized) {
        if (!uiState.isInitialized && !uiState.isLoading) {
            viewModel.showInitialSetup()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Budget display section with gradient background
                BudgetDisplaySection(
                    availableBudget = uiState.availableBudget,
                    currentDate = uiState.currentDate,
                    modifier = Modifier.weight(0.4f)
                )

                // Action buttons
                ActionButtons(
                    onExpenseClick = { viewModel.showExpenseInput() },
                    onIncomeClick = { viewModel.showIncomeInput() },
                    modifier = Modifier.padding(16.dp)
                )

                // Today's transactions
                TransactionsList(
                    transactions = uiState.transactions,
                    onDeleteTransaction = { viewModel.onDeleteTransaction(it) },
                    modifier = Modifier.weight(0.6f)
                )
            }

            // Bottom sheets and dialogs
            if (uiState.showExpenseInput) {
                TransactionInputBottomSheet(
                    type = TransactionType.EXPENSE,
                    onDismiss = { viewModel.hideExpenseInput() },
                    onSave = { amount, memo, category ->
                        viewModel.onAddExpense(amount, memo, category)
                    }
                )
            }

            if (uiState.showIncomeInput) {
                TransactionInputBottomSheet(
                    type = TransactionType.INCOME,
                    onDismiss = { viewModel.hideIncomeInput() },
                    onSave = { amount, memo, category ->
                        viewModel.onAddIncome(amount, memo, category)
                    }
                )
            }

            if (uiState.showInitialSetup) {
                InitialSetupDialog(
                    onComplete = { dailyBudget ->
                        viewModel.onInitialSetupComplete(dailyBudget)
                    }
                )
            }
        }
    }
}

@Composable
fun BudgetDisplaySection(
    availableBudget: Int,
    currentDate: java.time.LocalDate,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    val numberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Blue, Purple)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentDate.format(dateFormatter),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "今日使える予算",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "¥ ${numberFormat.format(availableBudget)}",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActionButtons(
    onExpenseClick: () -> Unit,
    onIncomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onExpenseClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("支出")
        }

        Button(
            onClick = onIncomeClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("収入")
        }
    }
}

@Composable
fun TransactionsList(
    transactions: List<Transaction>,
    onDeleteTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "今日の記録",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "まだ記録がありません",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onDelete = { onDeleteTransaction(transaction) }
                    )
                }
            }
        }
    }
}
