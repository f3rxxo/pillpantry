package com.yourname.pillpantry.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Grocery(
    @DocumentId val id: String = "",
    val name: String = "",
    val barcode: String = "",
    val quantity: Long = 0
)

data class Vitamin(
    @DocumentId val id: String = "",
    val name: String = "",
    val barcode: String = "",
    val currentPills: Long = 0,
    val dailyDosage: Long = 1,
    val refillThreshold: Long = 10,
    @ServerTimestamp val lastTaken: Date? = null
) {
    val isLow: Boolean get() = currentPills <= refillThreshold
}
