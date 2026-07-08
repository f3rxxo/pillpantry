package com.yourname.pillpantry.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The "portions used per day" cutoff. Central US time, matching the user's household. */
private val PORTION_TIMEZONE: ZoneId = ZoneId.of("America/Chicago")
private const val PORTION_CUTOVER_HOUR = 7

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
            .update("quantity", FieldValue.increment(delta))
            .await()
    }

    suspend fun setGroceryOnShoppingList(userId: String, itemId: String, onList: Boolean) {
        groceriesRef(userId).document(itemId).update("onShoppingList", onList).await()
    }

    suspend fun setVitaminOnShoppingList(userId: String, itemId: String, onList: Boolean) {
        vitaminsRef(userId).document(itemId).update("onShoppingList", onList).await()
    }

    /** Groceries AND vitamins currently flagged onShoppingList = true, merged into one flow. */
    fun observeShoppingList(userId: String): Flow<ShoppingListSnapshot> = callbackFlow {
        var latestGroceries = emptyList<Grocery>()
        var latestVitamins = emptyList<Vitamin>()

        fun emit() = trySend(ShoppingListSnapshot(latestGroceries, latestVitamins))

        val groceryReg = groceriesRef(userId)
            .whereEqualTo("onShoppingList", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                latestGroceries = snapshot?.toObjects(Grocery::class.java) ?: emptyList()
                emit()
            }

        val vitaminReg = vitaminsRef(userId)
            .whereEqualTo("onShoppingList", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                latestVitamins = snapshot?.toObjects(Vitamin::class.java) ?: emptyList()
                emit()
            }

        awaitClose {
            groceryReg.remove()
            vitaminReg.remove()
        }
    }

    /**
     * @param quantity number of units/packages on hand (e.g. 1 carton of eggs)
     * @param portions remaining individual portions (e.g. 3 eggs left)
     * @param portionsThreshold once [portions] drops to/below this, the item
     *   is auto-added to the shopping list
     */
    suspend fun addGrocery(
        userId: String,
        name: String,
        barcode: String,
        quantity: Long,
        portions: Long,
        portionsThreshold: Long
    ) {
        // How many portions one unit represents, e.g. 1 carton = 3 portions.
        // Used later to restock portions correctly when this barcode is
        // scanned again (see restockGrocery).
        val portionsPerUnit = if (quantity > 0) (portions / quantity).coerceAtLeast(1) else portions.coerceAtLeast(1)

        val grocery = mapOf(
            "name" to name,
            "barcode" to barcode,
            "quantity" to quantity,
            "portions" to portions,
            "portionsPerUnit" to portionsPerUnit,
            "portionsThreshold" to portionsThreshold,
            "lastPortionUpdate" to FieldValue.serverTimestamp(),
            "onShoppingList" to (portions <= portionsThreshold)
        )
        groceriesRef(userId).add(grocery).await()
    }

    /**
     * Restocks a grocery item by [units] (default 1 — used both when
     * re-scanning a barcode and when manually restocking via a swipe
     * action). Bumps quantity by [units] and portions by
     * `portionsPerUnit * units`, clearing the shopping-list flag if that
     * brings it back above the threshold.
     */
    suspend fun restockGrocery(userId: String, item: Grocery, units: Long = 1): Long {
        val newQuantity = item.quantity + units
        val newPortions = item.portions + (item.portionsPerUnit * units)
        groceriesRef(userId).document(item.id)
            .update(
                mapOf(
                    "quantity" to newQuantity,
                    "portions" to newPortions,
                    "lastPortionUpdate" to FieldValue.serverTimestamp(),
                    "onShoppingList" to (newPortions <= item.portionsThreshold)
                )
            )
            .await()
        return newPortions
    }

    suspend fun deleteGrocery(userId: String, itemId: String) {
        groceriesRef(userId).document(itemId).delete().await()
    }

    suspend fun deleteVitamin(userId: String, itemId: String) {
        vitaminsRef(userId).document(itemId).delete().await()
    }

    /** Manually adds [pillsToAdd] pills without going through "Take Dose". */
    suspend fun restockVitamin(userId: String, item: Vitamin, pillsToAdd: Long): Long {
        val newCount = item.currentPills + pillsToAdd
        vitaminsRef(userId).document(item.id)
            .update(
                mapOf(
                    "currentPills" to newCount,
                    "onShoppingList" to (newCount <= item.refillThreshold)
                )
            )
            .await()
        return newCount
    }

    suspend fun addVitamin(
        userId: String,
        name: String,
        barcode: String,
        dailyDosage: Long,
        refillThreshold: Long,
        pillsPerBottle: Long
    ) {
        val vitamin = mapOf(
            "name" to name,
            "barcode" to barcode,
            "currentPills" to pillsPerBottle,
            "dailyDosage" to dailyDosage,
            "refillThreshold" to refillThreshold,
            "lastTaken" to null,
            "onShoppingList" to (pillsPerBottle <= refillThreshold)
        )
        vitaminsRef(userId).add(vitamin).await()
    }

    /** Returns the new pill count after taking a dose. Auto-flags the shopping list when low. */
    suspend fun takeDose(userId: String, vitamin: Vitamin): Long {
        val newCount = (vitamin.currentPills - vitamin.dailyDosage).coerceAtLeast(0)
        vitaminsRef(userId).document(vitamin.id)
            .update(
                mapOf(
                    "currentPills" to newCount,
                    "lastTaken" to FieldValue.serverTimestamp(),
                    "onShoppingList" to (newCount <= vitamin.refillThreshold)
                )
            )
            .await()
        return newCount
    }

    suspend fun updateRefillThreshold(userId: String, vitaminId: String, threshold: Long) {
        vitaminsRef(userId).document(vitaminId).update("refillThreshold", threshold).await()
    }

    /**
     * Catch-up logic for the daily portion decrement. Rather than relying on
     * a background job firing at exactly 7am (Android won't reliably do
     * that for a personal app — see README), this looks at how many 7am
     * Central-time boundaries have passed since each grocery item's
     * [Grocery.lastPortionUpdate] and decrements portions by 1 per boundary
     * crossed. Call this on app launch (and optionally from a best-effort
     * WorkManager job) so the count is always correct whenever the user
     * actually opens the app, even after several days away.
     */
    suspend fun applyMissedPortionDecrements(userId: String, now: Instant = Instant.now()) {
        val snapshot = groceriesRef(userId).get().await()
        val groceries = snapshot.toObjects(Grocery::class.java)
        val batch = db.batch()
        var hasUpdates = false

        for (item in groceries) {
            val last = item.lastPortionUpdate ?: continue
            if (item.portions <= 0) continue

            val elapsedDays = elapsed7amBoundaries(last.toInstant(), now)
            if (elapsedDays <= 0) continue

            val newPortions = (item.portions - elapsedDays).coerceAtLeast(0)
            val docRef = groceriesRef(userId).document(item.id)
            batch.update(
                docRef,
                mapOf(
                    "portions" to newPortions,
                    "lastPortionUpdate" to FieldValue.serverTimestamp(),
                    "onShoppingList" to (item.onShoppingList || newPortions <= item.portionsThreshold)
                )
            )
            hasUpdates = true
        }

        if (hasUpdates) batch.commit().await()
    }

    /** Number of 7am Central-time boundaries crossed between [from] and [to]. */
    private fun elapsed7amBoundaries(from: Instant, to: Instant): Long {
        fun effectiveDay(instant: Instant): LocalDate {
            val zoned: ZonedDateTime = instant.atZone(PORTION_TIMEZONE)
            return if (zoned.hour < PORTION_CUTOVER_HOUR) {
                zoned.toLocalDate().minusDays(1)
            } else {
                zoned.toLocalDate()
            }
        }
        val days = ChronoUnit.DAYS.between(effectiveDay(from), effectiveDay(to))
        return days.coerceAtLeast(0)
    }
}

data class ShoppingListSnapshot(
    val groceries: List<Grocery> = emptyList(),
    val vitamins: List<Vitamin> = emptyList()
) {
    val isEmpty: Boolean get() = groceries.isEmpty() && vitamins.isEmpty()
}
