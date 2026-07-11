package com.yourname.pillpantry.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    /** True if [lastTaken] falls on today's date (device-local timezone). */
    val takenToday: Boolean
        get() {
            val last = lastTaken ?: return false
            val lastDate = Instant.ofEpochMilli(last.time).atZone(ZoneId.systemDefault()).toLocalDate()
            return lastDate == LocalDate.now()
        }
}

/**
 * A one-off shopping list entry that isn't tracked in the groceries or
 * vitamins collections at all — e.g. "paper towels" that you don't want
 * PillPantry monitoring stock levels for, just something to remember to
 * buy. Checking one off deletes it outright, since there's no underlying
 * tracked item for it to "return" to.
 */
data class CustomShoppingItem(
    @DocumentId val id: String = "",
    val name: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
