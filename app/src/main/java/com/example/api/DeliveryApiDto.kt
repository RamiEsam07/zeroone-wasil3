package com.example.api

import com.example.model.Address
import com.example.model.OrderItem
import com.example.model.PaymentMethod

data class CreateOrderRequest(
    val storeId: String,
    val deliveryAddressId: String,
    val items: List<OrderItemRequest>,
    val paymentMethod: PaymentMethod,
    val customerNotes: String = ""
)

data class OrderItemRequest(
    val productId: String,
    val quantity: Int,
    val specialInstructions: String = ""
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String? = null
)
