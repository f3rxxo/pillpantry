package com.yourname.pillpantry.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Single-user Firestore access layer.
 *
 * Data model:
 *   users/{userId}/groceries/{itemId}
 *   users/{userId}/vitamins/{vitaminId}
 *
 * Auth is anonymous (one Firebase user per install), matching the original
 * single-user app plan — no login screen needed.
 */
class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Signs in anonymously if needed and returns the stable uid for this device. */
    suspend fun ensureSignedIn(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in failed")
    }

    private fun groceriesRef(userId: String) =
        db.collection("users").document(userId).collection("groceries")

    private fun vitaminsRef(userId: String) =
        db.collection("users").document(userId).collection("vitamins")

    fun observeGroceries(userId: String): Flow<List<Grocery>> = callbackFlow {
        val registration = groceriesRef(userId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Grocery::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun observeVitamins(userId: String): Flow<List<Vitamin>> = callbackFlow {
        val registration = vitaminsRef(userId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Vitamin::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /** Looks up an item by barcode. Returns the doc id if found, else null. */
    suspend fun findGroceryByBarcode(userId: String, barcode: String): Grocery? {
        val snap = groceriesRef(userId).whereEqualTo("barcode", barcode).limit(1).get().await()
        return snap.toObjects(Grocery::class.java).firstOrNull()
    }

    suspend fun findVitaminByBarcode(userId: String, barcode: String): Vitamin? {
        val snap = vitaminsRef(userId).whereEqualTo("barcode", barcode).limit(1).get().await()
        return snap.toObjects(Vitamin::class.java).firstOrNull()
    }

    suspend fun incrementGroceryQuantity(userId: String, itemId: String, delta: Long) {
        groceriesRef(userId).document(itemId)
            .update("quantity", com.google.firebase.firestore.FieldValue.increment(delta))
            .await()
    }

    suspend fun setGroceryOnShoppingList(userId: String, itemId: String, onList: Boolean) {
        groceriesRef(userId).document(itemId).update("onShoppingList", onList).await()
    }

    fun observeShoppingList(userId: String): Flow<List<Grocery>> = callbackFlow {
        val registration = groceriesRef(userId)
            .whereEqualTo("onShoppingList", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Grocery::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    suspend fun addGrocery(userId: String, name: String, barcode: String) {
        val grocery = mapOf("name" to name, "barcode" to barcode, "quantity" to 1L)
        groceriesRef(userId).add(grocery).await()
    }

    suspend fun addVitamin(
        userId: String,
        name: String,
        barcode: String,
        dailyDosage: Long,
        refillThreshold: Long
    ) {
        val vitamin = mapOf(
            "name" to name,
            "barcode" to barcode,
            "currentPills" to 30L,
            "dailyDosage" to dailyDosage,
            "refillThreshold" to refillThreshold,
            "lastTaken" to null
        )
        vitaminsRef(userId).add(vitamin).await()
    }

    /** Returns the new pill count after taking a dose. */
    suspend fun takeDose(userId: String, vitamin: Vitamin): Long {
        val newCount = (vitamin.currentPills - vitamin.dailyDosage).coerceAtLeast(0)
        vitaminsRef(userId).document(vitamin.id)
            .update(
                mapOf(
                    "currentPills" to newCount,
                    "lastTaken" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .await()
        return newCount
    }

    suspend fun updateRefillThreshold(userId: String, vitaminId: String, threshold: Long) {
        vitaminsRef(userId).document(vitaminId).update("refillThreshold", threshold).await()
    }
}
