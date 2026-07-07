package com.yourname.pillpantry.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class OffProduct(
    val product_name: String? = null,
    val brands: String? = null
)

data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null
)

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,brands"
    ): OffResponse
}

data class ProductLookupResult(val name: String, val brand: String?)

/**
 * Open Food Facts is a free, open, crowdsourced product database — no API
 * key required. https://openfoodfacts.github.io/openfoodfacts-server/api/
 */
class OpenFoodFactsRepository {

    private val api: OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenFoodFactsApi::class.java)

    /** Returns null on any failure (offline, timeout, not found) so callers can fall back silently. */
    suspend fun lookup(barcode: String): ProductLookupResult? {
        return try {
            val response = api.getProduct(barcode)
            val name = response.product?.product_name?.trim()
            if (response.status != 1 || name.isNullOrEmpty()) return null
            val brand = response.product.brands?.split(",")?.firstOrNull()?.trim()
            ProductLookupResult(name = name, brand = brand?.ifEmpty { null })
        } catch (e: Exception) {
            null
        }
    }
}
