package com.yourname.pillpantry.ui.shoppinglist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.pillpantry.data.CustomShoppingItem
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.Grocery
import com.yourname.pillpantry.data.ShoppingListSnapshot
import com.yourname.pillpantry.data.Vitamin
import com.yourname.pillpantry.ui.pantry.ListItemColor
import com.yourname.pillpantry.ui.pantry.VitaminItemColor
import kotlinx.coroutines.launch

/** Background color for custom (untracked) shopping list entries. */
private val CustomItemColor = Color(0xFF546E7A)

/**
 * Shows everything currently on the shopping list — groceries (flagged
 * manually from Pantry, or automatically once portions drop to the
 * threshold), vitamins (flagged automatically once pills drop to the
 * refill threshold), and custom free-text items added directly here for
 * things you don't want PillPantry tracking stock levels for.
 *
 * Checking off a grocery/vitamin just clears its shopping-list flag — it
 * stays in its Pantry list either way. Checking off (or deleting) a custom
 * item removes it outright, since there's no underlying tracked item.
 */
@Composable
fun ShoppingListScreen(userId: String, repository: FirebaseRepository) {
    val scope = rememberCoroutineScope()

    val snapshot by produceState(initialValue = ShoppingListSnapshot(), userId) {
        repository.observeShoppingList(userId).collect { value = it }
    }

    var newItemName by remember { mutableStateOf("") }

    fun addCustomItem() {
        val name = newItemName.trim()
        if (name.isEmpty()) return
        scope.launch { repository.addCustomShoppingItem(userId, name) }
        newItemName = ""
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("Shopping List", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItemName,
                onValueChange = { newItemName = it },
                label = { Text("Add an item") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { addCustomItem() }) {
                Icon(Icons.Default.Add, contentDescription = "Add to shopping list")
            }
        }
        Text(
            "For one-off items you don't want tracked in Pantry — groceries and " +
                "vitamins still show up here automatically when they run low.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (snapshot.isEmpty) {
            Text(
                "Nothing needed right now.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(snapshot.customItems, key = { "custom_${it.id}" }) { item ->
                    ShoppingRow(
                        title = item.name,
                        subtitle = null,
                        color = CustomItemColor,
                        actionIcon = Icons.Default.Delete,
                        actionDescription = "Delete",
                        onAction = {
                            scope.launch { repository.deleteCustomShoppingItem(userId, item.id) }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                items(snapshot.groceries, key = { "grocery_${it.id}" }) { item ->
                    ShoppingRow(
                        title = item.name,
                        subtitle = "${item.portions} portions left (threshold: ${item.portionsThreshold})",
                        color = ListItemColor,
                        actionIcon = Icons.Default.Check,
                        actionDescription = "Got it — remove from list",
                        onAction = {
                            scope.launch { repository.setGroceryOnShoppingList(userId, item.id, false) }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                items(snapshot.vitamins, key = { "vitamin_${it.id}" }) { vitamin ->
                    ShoppingRow(
                        title = vitamin.name,
                        subtitle = "${vitamin.currentPills} pills left (threshold: ${vitamin.refillThreshold})",
                        color = VitaminItemColor,
                        actionIcon = Icons.Default.Check,
                        actionDescription = "Got it — remove from list",
                        onAction = {
                            scope.launch { repository.setVitaminOnShoppingList(userId, vitamin.id, false) }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ShoppingRow(
    title: String,
    subtitle: String?,
    color: Color,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit
) {
    Surface(shape = RoundedCornerShape(10.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = actionDescription, tint = Color.White)
            }
        }
    }
}
