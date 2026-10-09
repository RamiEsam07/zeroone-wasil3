package com.example.repository

import com.example.api.DeliveryApiService
import com.example.model.Address
import com.example.model.Cart
import com.example.model.CartItem
import com.example.model.Order
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.ProductOption
import com.example.model.Store
import com.example.model.StoreCategory
import kotlinx.coroutines.flow.StateFlow

interface DeliveryRepository {
    val storesFlow: StateFlow<List<Store>>
    val cartFlow: StateFlow<Cart>
    val addressesFlow: StateFlow<List<Address>>
    val selectedAddressFlow: StateFlow<Address?>
    val ordersFlow: StateFlow<List<Order>>
    val activeOrderFlow: StateFlow<Order?>
    val isLoadingFlow: StateFlow<Boolean>

    suspend fun refreshStores(category: StoreCategory? = null, query: String? = null)
    suspend fun getStoreById(storeId: String): Result<Store>
    suspend fun getStoreProducts(storeId: String, category: String? = null): Result<List<Product>>
    suspend fun getProductById(productId: String): Result<Product>

    fun addToCart(
        product: Product,
        quantity: Int,
        selectedOptions: List<ProductOption> = emptyList(),
        specialInstructions: String = "",
        store: Store
    ): Boolean

    fun updateCartItemQuantity(productId: String, newQuantity: Int)
    fun removeFromCart(productId: String)
    fun clearCart()
    fun updateCustomerNotes(notes: String)

    suspend fun selectDeliveryAddress(address: Address)
    suspend fun addDeliveryAddress(address: Address): Result<Address>
    suspend fun refreshAddresses()

    suspend fun submitOrder(
        paymentMethod: PaymentMethod,
        customerNotes: String = ""
    ): Result<Order>

    suspend fun refreshOrders()
    suspend fun getOrderById(orderId: String): Result<Order>
    suspend fun setActiveOrder(order: Order?)
    suspend fun cancelOrder(orderId: String): Result<Order>
    suspend fun simulateAdvanceOrderStatus(orderId: String): Result<Order>
}
