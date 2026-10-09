package com.example.repository

import com.example.api.CreateOrderRequest
import com.example.api.DeliveryApiService
import com.example.api.OrderItemRequest
import com.example.model.Address
import com.example.model.Cart
import com.example.model.CartItem
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.ProductOption
import com.example.model.Store
import com.example.model.StoreCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeliveryRepositoryImpl(
    private val apiService: DeliveryApiService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : DeliveryRepository {

    private val _storesFlow = MutableStateFlow<List<Store>>(emptyList())
    override val storesFlow: StateFlow<List<Store>> = _storesFlow.asStateFlow()

    private val _cartFlow = MutableStateFlow(Cart())
    override val cartFlow: StateFlow<Cart> = _cartFlow.asStateFlow()

    private val _addressesFlow = MutableStateFlow<List<Address>>(emptyList())
    override val addressesFlow: StateFlow<List<Address>> = _addressesFlow.asStateFlow()

    private val _selectedAddressFlow = MutableStateFlow<Address?>(null)
    override val selectedAddressFlow: StateFlow<Address?> = _selectedAddressFlow.asStateFlow()

    private val _ordersFlow = MutableStateFlow<List<Order>>(emptyList())
    override val ordersFlow: StateFlow<List<Order>> = _ordersFlow.asStateFlow()

    private val _activeOrderFlow = MutableStateFlow<Order?>(null)
    override val activeOrderFlow: StateFlow<Order?> = _activeOrderFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(false)
    override val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()

    init {
        scope.launch {
            refreshStores()
            refreshAddresses()
            refreshOrders()
        }
    }

    override suspend fun refreshStores(category: StoreCategory?, query: String?) {
        _isLoadingFlow.value = true
        val result = apiService.getStores(category, query)
        result.onSuccess { list ->
            _storesFlow.value = list
        }
        _isLoadingFlow.value = false
    }

    override suspend fun getStoreById(storeId: String): Result<Store> {
        return apiService.getStoreById(storeId)
    }

    override suspend fun getStoreProducts(storeId: String, category: String?): Result<List<Product>> {
        return apiService.getStoreProducts(storeId, category)
    }

    override suspend fun getProductById(productId: String): Result<Product> {
        return apiService.getProductById(productId)
    }

    override fun addToCart(
        product: Product,
        quantity: Int,
        selectedOptions: List<ProductOption>,
        specialInstructions: String,
        store: Store
    ): Boolean {
        val currentCart = _cartFlow.value

        // If cart has items from another store, reset to this new store
        val targetStore = if (currentCart.store != null && currentCart.store.id != store.id) {
            store
        } else {
            currentCart.store ?: store
        }

        val baseItems = if (currentCart.store != null && currentCart.store.id != store.id) {
            emptyList()
        } else {
            currentCart.items
        }

        val existingIndex = baseItems.indexOfFirst {
            it.product.id == product.id && it.selectedOptions == selectedOptions
        }

        val updatedItems = if (existingIndex != -1) {
            baseItems.mapIndexed { index, item ->
                if (index == existingIndex) {
                    item.copy(
                        quantity = item.quantity + quantity,
                        specialInstructions = if (specialInstructions.isNotBlank()) specialInstructions else item.specialInstructions
                    )
                } else item
            }
        } else {
            baseItems + CartItem(
                product = product,
                quantity = quantity,
                selectedOptions = selectedOptions,
                specialInstructions = specialInstructions
            )
        }

        _cartFlow.value = currentCart.copy(
            store = targetStore,
            items = updatedItems
        )
        return true
    }

    override fun updateCartItemQuantity(productId: String, newQuantity: Int) {
        val currentCart = _cartFlow.value
        if (newQuantity <= 0) {
            removeFromCart(productId)
            return
        }

        val updatedItems = currentCart.items.map { item ->
            if (item.product.id == productId) {
                item.copy(quantity = newQuantity)
            } else item
        }

        _cartFlow.value = currentCart.copy(
            items = updatedItems,
            store = if (updatedItems.isEmpty()) null else currentCart.store
        )
    }

    override fun removeFromCart(productId: String) {
        val currentCart = _cartFlow.value
        val updatedItems = currentCart.items.filterNot { it.product.id == productId }
        _cartFlow.value = currentCart.copy(
            items = updatedItems,
            store = if (updatedItems.isEmpty()) null else currentCart.store
        )
    }

    override fun clearCart() {
        _cartFlow.value = Cart()
    }

    override fun updateCustomerNotes(notes: String) {
        _cartFlow.value = _cartFlow.value.copy(customerNotes = notes)
    }

    override suspend fun selectDeliveryAddress(address: Address) {
        _selectedAddressFlow.value = address
    }

    override suspend fun addDeliveryAddress(address: Address): Result<Address> {
        val res = apiService.addAddress(address)
        res.onSuccess { added ->
            refreshAddresses()
            _selectedAddressFlow.value = added
        }
        return res
    }

    override suspend fun refreshAddresses() {
        val res = apiService.getAddresses()
        res.onSuccess { list ->
            _addressesFlow.value = list
            if (_selectedAddressFlow.value == null || !list.any { it.id == _selectedAddressFlow.value?.id }) {
                _selectedAddressFlow.value = list.find { it.isDefault } ?: list.firstOrNull()
            }
        }
    }

    override suspend fun submitOrder(
        paymentMethod: PaymentMethod,
        customerNotes: String
    ): Result<Order> {
        val currentCart = _cartFlow.value
        val store = currentCart.store
            ?: return Result.failure(IllegalStateException("لا يوجد متجر محدد"))
        if (currentCart.items.isEmpty()) {
            return Result.failure(IllegalStateException("السلة فارغة"))
        }

        val address = _selectedAddressFlow.value
            ?: _addressesFlow.value.firstOrNull()
            ?: apiService.getAddresses().getOrNull()?.firstOrNull()
            ?: return Result.failure(IllegalStateException("يرجى اختيار عنوان توصيل"))

        val req = CreateOrderRequest(
            storeId = store.id,
            deliveryAddressId = address.id,
            items = currentCart.items.map {
                OrderItemRequest(
                    productId = it.product.id,
                    quantity = it.quantity,
                    specialInstructions = it.specialInstructions
                )
            },
            paymentMethod = paymentMethod,
            customerNotes = if (customerNotes.isNotBlank()) customerNotes else currentCart.customerNotes
        )

        _isLoadingFlow.value = true
        val result = apiService.createOrder(req)
        result.onSuccess { createdOrder ->
            clearCart()
            _activeOrderFlow.value = createdOrder
            refreshOrders()
        }
        _isLoadingFlow.value = false
        return result
    }

    override suspend fun refreshOrders() {
        val res = apiService.getMyOrders()
        res.onSuccess { list ->
            _ordersFlow.value = list
            // If active order is null, pick the latest active one if available
            if (_activeOrderFlow.value == null) {
                _activeOrderFlow.value = list.firstOrNull { it.status.isActive }
            } else {
                // Keep active order updated with refreshed data
                val updated = list.find { it.id == _activeOrderFlow.value?.id }
                if (updated != null) {
                    _activeOrderFlow.value = updated
                }
            }
        }
    }

    override suspend fun getOrderById(orderId: String): Result<Order> {
        return apiService.getOrderById(orderId)
    }

    override suspend fun setActiveOrder(order: Order?) {
        _activeOrderFlow.value = order
    }

    override suspend fun cancelOrder(orderId: String): Result<Order> {
        val res = apiService.cancelOrder(orderId)
        res.onSuccess { updated ->
            if (_activeOrderFlow.value?.id == orderId) {
                _activeOrderFlow.value = updated
            }
            refreshOrders()
        }
        return res
    }

    override suspend fun simulateAdvanceOrderStatus(orderId: String): Result<Order> {
        val res = apiService.advanceOrderStatus(orderId)
        res.onSuccess { updated ->
            if (_activeOrderFlow.value?.id == orderId) {
                _activeOrderFlow.value = updated
            }
            refreshOrders()
        }
        return res
    }
}
