package com.yourname.pillpantry.ui.pantry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.Grocery
import com.yourname.pillpantry.data.Vitamin
import com.yourname.pillpantry.notifications.NotificationHelper
import kotlinx.coroutines.launch

private enum class PantryTab { GROCERIES, VITAMINS }

@Composable
fun PantryScreen(userId: String, repository: FirebaseRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(PantryTab.GROCERIES) }

    val groceries by produceState(initialValue = emptyList<Grocery>(), userId) {
        repository.observeGroceries(userId).collect { value = it }
    }
    val vitamins by produceState(initialValue = emptyList<Vitamin>(), userId) {
        repository.observeVitamins(userId).collect { value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabChip("Groceries", tab == PantryTab.GROCERIES, Modifier.weight(1f)) {
                tab = PantryTab.GROCERIES
            }
            TabChip("Vitamins", tab == PantryTab.VITAMINS, Modifier.weight(1f)) {
                tab = PantryTab.VITAMINS
            }
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            PantryTab.GROCERIES -> {
                if (groceries.isEmpty()) {
                    EmptyState("No groceries yet. Scan something!")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(groceries, key = { it.id }) { item ->
                            GroceryRow(
                                item = item,
                                onAdjust = { delta ->
                                    scope.launch {
                                        repository.setGroceryQuantity(userId, item.id, item.quantity + delta)
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            PantryTab.VITAMINS -> {
                if (vitamins.isEmpty()) {
                    EmptyState("No vitamins yet. Scan something!")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(vitamins, key = { it.id }) { vitamin ->
                            VitaminRow(
                                vitamin = vitamin,
                                onTakeDose = {
                                    scope.launch {
                                        val newCount = repository.takeDose(userId, vitamin)
                                        if (newCount <= vitamin.refillThreshold) {
                                            NotificationHelper.sendRefillAlert(context, vitamin.name, newCount)
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        color = if (selected) Color(0xFF00796B) else Color(0xFFEEEEEE),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF333333),
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = Color.Gray)
    }
}

@Composable
private fun GroceryRow(item: Grocery, onAdjust: (Long) -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButtonSquare("-") { onAdjust(-1) }
            Text(
                "${item.quantity}",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
            IconButtonSquare("+") { onAdjust(1) }
        }
    }
}

@Composable
private fun VitaminRow(vitamin: Vitamin, onTakeDose: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(vitamin.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${vitamin.currentPills} pills left" + if (vitamin.isLow) " · refill soon" else "",
                    color = if (vitamin.isLow) Color(0xFFD32F2F) else Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onTakeDose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))) {
                Text("Take Dose")
            }
        }
    }
}

@Composable
private fun IconButtonSquare(label: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF00796B),
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(32.dp),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
