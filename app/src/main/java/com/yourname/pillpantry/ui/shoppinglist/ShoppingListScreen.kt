package com.yourname.pillpantry.ui.shoppinglist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.Grocery
import com.yourname.pillpantry.data.ShoppingListSnapshot
import com.yourname.pillpantry.data.Vitamin
import com.yourname.pillpantry.ui.pantry.ListItemColor
import kotlinx.coroutines.launch

/**
 * Shows everything currently flagged onShoppingList = true — groceries
 * (flagged manually from Pantry, or automatically once portions drop to
 * the threshold) and vitamins (flagged automatically once pills drop to
 * the refill threshold). Checking an item off just clears the flag; it
 * stays in its Pantry list either way.
 */
@Composable
fun ShoppingListScreen(userId: String, repository: FirebaseRepository) {
    val scope = rememberCoroutineScope()

    val snapshot by produceState(initialValue = ShoppingListSnapshot(), userId) {
        repository.observeShoppingList(userId).collect { value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("Shopping List", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (snapshot.isEmpty) {
            Text(
                "Nothing needed right now — items show up here automatically " +
                    "when portions or pills run low, or you can add a grocery " +
                    "item manually from the Pantry tab.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(snapshot.groceries, key = { "grocery_${it.id}" }) { item ->
                    GroceryListRow(item) {
                        scope.launch { repository.setGroceryOnShoppingList(userId, item.id, false) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                items(snapshot.vitamins, key = { "vitamin_${it.id}" }) { vitamin ->
                    VitaminListRow(vitamin) {
                        scope.launch { repository.setVitaminOnShoppingList(userId, vitamin.id, false) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun GroceryListRow(item: Grocery, onCheckOff: () -> Unit) {
    ShoppingRow(
        title = item.name,
        subtitle = "${item.portions} portions left (threshold: ${item.portionsThreshold})",
        onCheckOff = onCheckOff
    )
}

@Composable
private fun VitaminListRow(vitamin: Vitamin, onCheckOff: () -> Unit) {
    ShoppingRow(
        title = vitamin.name,
        subtitle = "${vitamin.currentPills} pills left (threshold: ${vitamin.refillThreshold})",
        onCheckOff = onCheckOff
    )
}

@Composable
private fun ShoppingRow(title: String, subtitle: String, onCheckOff: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = ListItemColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCheckOff) {
                Icon(Icons.Filled.Check, contentDescription = "Got it — remove from list", tint = Color.White)
            }
        }
    }
}
