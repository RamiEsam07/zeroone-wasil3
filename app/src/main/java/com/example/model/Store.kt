package com.example.model

enum class StoreCategory(val id: String, val titleAr: String, val iconName: String) {
    ALL("all", "الكل", "restaurant"),
    RESTAURANTS("restaurants", "مطاعم", "fastfood"),
    CAFES("cafes", "كافيهات وحلويات", "coffee"),
    GROCERIES("groceries", "بقالة وسوبرماركت", "shopping_cart"),
    BAKERIES("bakeries", "مخابز ومعجنات", "bakery_dining"),
    JUICES("juices", "عصائر طبيعية", "local_drink"),
    PHARMACY("pharmacy", "صيدليات", "local_pharmacy")
}

data class Store(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val category: StoreCategory,
    val description: String,
    val logoUrl: String,
    val coverUrl: String,
    val rating: Double,
    val reviewCount: Int,
    val deliveryTimeMinutes: Int, // e.g., 30 mins
    val deliveryFee: Double,      // in SDG (ج.س)
    val minOrderAmount: Double,   // in SDG
    val isOpen: Boolean = true,
    val area: String = "الرياض - الخرطوم",
    val distanceKm: Double = 2.4,
    val tags: List<String> = emptyList(),
    val productCategories: List<String> = emptyList(),
    val isFeatured: Boolean = false
)
