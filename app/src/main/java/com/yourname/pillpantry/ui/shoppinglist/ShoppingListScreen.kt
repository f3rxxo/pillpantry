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
import com.yourname.pillpantry.ui.pantry.ListItemColor
import kotlinx.coroutines.launch

/**
 * Shows groceries the user has flagged (from the Pantry tab) as needing a
 * restock. Checking an item off here just clears the flag — it stays in
 * the Pantry's Groceries list either way, since the flag doesn't affect
 * quantity tracking.
 */
@Composable
fun ShoppingListScreen(userId: String, repository: FirebaseRepository) {
    val scope = rememberCoroutineScope()

    val items by produceState(initialValue = emptyList<Grocery>(), userId) {
        repository.observeShoppingList(userId).collect { value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("Shopping List", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Text(
                "Nothing on your list. Add items from the Pantry tab.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(items, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ListItemColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Qty on hand: ${item.quantity}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repository.setGroceryOnShoppingList(userId, item.id, false)
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Got it — remove from list", tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
