package com.example.model

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val selectedOptions: List<ProductOption> = emptyList(),
    val specialInstructions: String = ""
) {
    val unitPrice: Double
        get() = product.price + selectedOptions.sumOf { it.extraPrice }

    val totalPrice: Double
        get() = unitPrice * quantity
}

data class Cart(
    val store: Store? = null,
    val items: List<CartItem> = emptyList(),
    val customerNotes: String = ""
) {
    val isEmpty: Boolean
        get() = items.isEmpty()

    val totalItemsCount: Int
        get() = items.sumOf { it.quantity }

    val subtotal: Double
        get() = items.sumOf { it.totalPrice }

    val deliveryFee: Double
        get() = if (isEmpty) 0.0 else (store?.deliveryFee ?: 0.0)

    val serviceFee: Double
        get() = if (isEmpty) 0.0 else 250.0 // SDG flat platform service fee

    val total: Double
        get() = if (isEmpty) 0.0 else (subtotal + deliveryFee + serviceFee)
}
