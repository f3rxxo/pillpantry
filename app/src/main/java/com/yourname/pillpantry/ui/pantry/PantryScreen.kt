package com.yourname.pillpantry.ui.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.Grocery
import com.yourname.pillpantry.data.Vitamin
import com.yourname.pillpantry.notifications.NotificationHelper
import kotlinx.coroutines.launch

/** Shared background color for grocery/vitamin/shopping-list rows across the app. */
val ListItemColor = Color(0xFF6395EE)

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

    // Delete confirmation state (shared between the two tabs)
    var groceryPendingDelete by remember { mutableStateOf<Grocery?>(null) }
    var vitaminPendingDelete by remember { mutableStateOf<Vitamin?>(null) }

    // Manual restock dialog state
    var groceryPendingRestock by remember { mutableStateOf<Grocery?>(null) }
    var vitaminPendingRestock by remember { mutableStateOf<Vitamin?>(null) }
    var restockAmountInput by remember { mutableStateOf("1") }

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

        Spacer(Modifier.height(6.dp))
        Text(
            "Swipe an item right to restock, left to delete",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        when (tab) {
            PantryTab.GROCERIES -> {
                if (groceries.isEmpty()) {
                    EmptyState("No groceries yet. Scan something!")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(groceries, key = { it.id }) { item ->
                            SwipeActionRow(
                                itemKey = item.id,
                                onSwipeToDelete = { groceryPendingDelete = item },
                                onSwipeToRestock = {
                                    restockAmountInput = "1"
                                    groceryPendingRestock = item
                                }
                            ) {
                                GroceryRow(
                                    item = item,
                                    onToggleShoppingList = {
                                        scope.launch {
                                            repository.setGroceryOnShoppingList(userId, item.id, !item.onShoppingList)
                                        }
                                    }
                                )
                            }
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
                            SwipeActionRow(
                                itemKey = vitamin.id,
                                onSwipeToDelete = { vitaminPendingDelete = vitamin },
                                onSwipeToRestock = {
                                    restockAmountInput = "30"
                                    vitaminPendingRestock = vitamin
                                }
                            ) {
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
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    // --- Delete confirmations ---

    groceryPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { groceryPendingDelete = null },
            title = { Text("Delete \"${item.name}\"?") },
            text = { Text("This removes it from your groceries list entirely.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteGrocery(userId, item.id) }
                    groceryPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { groceryPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    vitaminPendingDelete?.let { vitamin ->
        AlertDialog(
            onDismissRequest = { vitaminPendingDelete = null },
            title = { Text("Delete \"${vitamin.name}\"?") },
            text = { Text("This removes it from your vitamins list entirely.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteVitamin(userId, vitamin.id) }
                    vitaminPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vitaminPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    // --- Manual restock dialogs ---

    groceryPendingRestock?.let { item ->
        AlertDialog(
            onDismissRequest = { groceryPendingRestock = null },
            title = { Text("Restock \"${item.name}\"") },
            text = {
                Column {
                    Text(
                        "Each unit adds ${item.portionsPerUnit} portions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restockAmountInput,
                        onValueChange = { restockAmountInput = it },
                        label = { Text("Units to add") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val units = restockAmountInput.toLongOrNull()
                    if (units != null && units > 0) {
                        scope.launch { repository.restockGrocery(userId, item, units) }
                        groceryPendingRestock = null
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { groceryPendingRestock = null }) { Text("Cancel") }
            }
        )
    }

    vitaminPendingRestock?.let { vitamin ->
        AlertDialog(
            onDismissRequest = { vitaminPendingRestock = null },
            title = { Text("Restock \"${vitamin.name}\"") },
            text = {
                OutlinedTextField(
                    value = restockAmountInput,
                    onValueChange = { restockAmountInput = it },
                    label = { Text("Pills to add") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pills = restockAmountInput.toLongOrNull()
                    if (pills != null && pills > 0) {
                        scope.launch { repository.restockVitamin(userId, vitamin, pills) }
                        vitaminPendingRestock = null
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { vitaminPendingRestock = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Wraps [content] in a swipeable row: swipe right (start-to-end) triggers
 * [onSwipeToRestock], swipe left (end-to-start) triggers [onSwipeToDelete].
 * Both calls snap the row back to settled rather than actually dismissing
 * it — the underlying Firestore listener is what removes/updates the row
 * once the corresponding repository call completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionRow(
    itemKey: Any,
    onSwipeToDelete: () -> Unit,
    onSwipeToRestock: () -> Unit,
    content: @Composable () -> Unit
) {
    // Scoped by itemKey so each row gets fresh swipe state rather than
    // reusing a stale one if the list reorders (e.g. alphabetical resort
    // after a rename).
    val state = key(itemKey) {
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onSwipeToDelete()
                        false // don't actually dismiss; the row updates via Firestore listener
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onSwipeToRestock()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> true
                }
            },
            positionalThreshold = { distance -> distance * 0.35f }
        )
    }

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val (color, icon, alignment, label) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeBackground(Color(0xFF2E7D32), Icons.Default.Add, Alignment.CenterStart, "Restock")
                SwipeToDismissBoxValue.EndToStart ->
                    SwipeBackground(Color(0xFFC62828), Icons.Default.Delete, Alignment.CenterEnd, "Delete")
                SwipeToDismissBoxValue.Settled ->
                    SwipeBackground(Color.Transparent, null, Alignment.Center, "")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = label, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) {
        content()
    }
}

private data class SwipeBackground(
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val alignment: Alignment,
    val label: String
)

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
private fun GroceryRow(item: Grocery, onToggleShoppingList: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = ListItemColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Qty: ${item.quantity} · ${item.portions} portions left" +
                        if (item.isLowOnPortions) " · refill soon" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isLowOnPortions) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.85f)
                )
            }
            IconButton(onClick = onToggleShoppingList) {
                Icon(
                    imageVector = if (item.onShoppingList) {
                        Icons.Filled.ShoppingCart
                    } else {
                        Icons.Outlined.ShoppingCart
                    },
                    contentDescription = if (item.onShoppingList) {
                        "Remove from shopping list"
                    } else {
                        "Add to shopping list"
                    },
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun VitaminRow(vitamin: Vitamin, onTakeDose: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = ListItemColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(vitamin.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${vitamin.currentPills} pills left" + if (vitamin.isLow) " · refill soon" else "",
                    color = if (vitamin.isLow) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onTakeDose, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text("Take Dose", color = ListItemColor)
            }
        }
    }
}
