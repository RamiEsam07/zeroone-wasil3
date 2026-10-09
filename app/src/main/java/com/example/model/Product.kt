package com.example.model

data class ProductOption(
    val id: String,
    val name: String,
    val extraPrice: Double = 0.0
)

data class Product(
    val id: String,
    val storeId: String,
    val name: String,
    val description: String,
    val price: Double, // in SDG (ج.س)
    val originalPrice: Double? = null,
    val imageUrl: String,
    val category: String,
    val isAvailable: Boolean = true,
    val preparationTimeMinutes: Int = 15,
    val calories: Int? = null,
    val isPopular: Boolean = false,
    val options: List<ProductOption> = emptyList()
)
