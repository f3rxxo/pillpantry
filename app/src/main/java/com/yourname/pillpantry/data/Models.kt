package com.yourname.pillpantry.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Grocery(
    @DocumentId val id: String = "",
    val name: String = "",
    val barcode: String = "",
    val quantity: Long = 0,
    val portions: Long = 0,
    val portionsPerUnit: Long = 1,
    val portionsThreshold: Long = 0,
    @ServerTimestamp val lastPortionUpdate: Date? = null,
    val onShoppingList: Boolean = false
) {
    val isLowOnPortions: Boolean get() = portions <= portionsThreshold
}

data class Vitamin(
    @DocumentId val id: String = "",
    val name: String = "",
    val barcode: String = "",
    val currentPills: Long = 0,
    val dailyDosage: Long = 1,
    val refillThreshold: Long = 10,
    @ServerTimestamp val lastTaken: Date? = null,
    val onShoppingList: Boolean = false
) {
    val isLow: Boolean get() = currentPills <= refillThreshold
}
