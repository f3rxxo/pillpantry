package com.yourname.pillpantry.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.Vitamin
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(userId: String, repository: FirebaseRepository) {
    val scope = rememberCoroutineScope()

    val vitamins by produceState(initialValue = emptyList<Vitamin>(), userId) {
        repository.observeVitamins(userId).collect { value = it }
    }

    var edits by remember { mutableStateOf(mapOf<String, String>()) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("Settings & Alerts", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("User ID", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(userId, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This app uses anonymous sign-in, so your data is tied to this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Refill Thresholds", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        if (vitamins.isEmpty()) {
            Text("No vitamins tracked yet.", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
        } else {
            LazyColumn {
                items(vitamins, key = { it.id }) { vitamin ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(vitamin.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)

                            OutlinedTextField(
                                value = edits[vitamin.id] ?: vitamin.refillThreshold.toString(),
                                onValueChange = { edits = edits + (vitamin.id to it) },
                                modifier = Modifier.width(70.dp),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                onClick = {
                                    val raw = edits[vitamin.id] ?: return@Button
                                    val value = raw.toLongOrNull()
                                    if (value == null || value < 0) {
                                        snackbarMessage = "Enter a whole number of pills."
                                        return@Button
                                    }
                                    scope.launch {
                                        repository.updateRefillThreshold(userId, vitamin.id, value)
                                        snackbarMessage = "Saved."
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        snackbarMessage?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(2000)
                snackbarMessage = null
            }
            Surface(
                color = Color(0xFF00796B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
            ) {
                Text(it, color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
