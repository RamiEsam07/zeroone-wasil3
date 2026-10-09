package com.example.api

import com.example.model.Address
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.Store
import com.example.model.StoreCategory

/**
 * Production-ready API interface contract.
 * Mirrors modern RESTful / GraphQL endpoints:
 * GET /api/stores
 * GET /api/stores/:id
 * GET /api/stores/:id/products
 * GET /api/products/:id
 * GET /api/addresses
 * POST /api/addresses
 * POST /api/orders
 * GET /api/orders/:id
 * GET /api/my-orders
 * PATCH /api/orders/:id/status (for simulating/testing driver and store actions)
 */
interface DeliveryApiService {
    suspend fun getStores(
        category: StoreCategory? = null,
        query: String? = null,
        onlyOpen: Boolean = false
    ): Result<List<Store>>

    suspend fun getStoreById(storeId: String): Result<Store>

    suspend fun getStoreProducts(
        storeId: String,
        category: String? = null
    ): Result<List<Product>>

    suspend fun getProductById(productId: String): Result<Product>

    suspend fun getAddresses(): Result<List<Address>>

    suspend fun addAddress(address: Address): Result<Address>

    suspend fun setDefaultAddress(addressId: String): Result<Address>

    suspend fun createOrder(request: CreateOrderRequest): Result<Order>

    suspend fun getOrderById(orderId: String): Result<Order>

    suspend fun getMyOrders(): Result<List<Order>>

    suspend fun cancelOrder(orderId: String): Result<Order>

    suspend fun advanceOrderStatus(orderId: String): Result<Order>
}
