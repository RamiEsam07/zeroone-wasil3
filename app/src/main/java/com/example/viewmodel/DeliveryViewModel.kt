package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.MockDeliveryApiService
import com.example.model.Address
import com.example.model.Cart
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.ProductOption
import com.example.model.Store
import com.example.model.StoreCategory
import com.example.repository.DeliveryRepository
import com.example.repository.DeliveryRepositoryImpl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data class StoreList(val category: StoreCategory? = null, val initialSearchQuery: String = "") : Screen()
    data class StoreDetail(val storeId: String) : Screen()
    data object CartPage : Screen()
    data class AddressSelect(val isFromCheckout: Boolean = false) : Screen()
    data object Checkout : Screen()
    data class OrderSuccess(val orderId: String) : Screen()
    data class OrderTracking(val orderId: String) : Screen()
    data object MyOrders : Screen()
    data class OrderDetail(val orderId: String) : Screen()
    data object Profile : Screen()
}

enum class BottomTab {
    HOME,
    STORES,
    ORDERS,
    CART,
    PROFILE
}

data class ProductDetailModalState(
    val isOpen: Boolean = false,
    val product: Product? = null,
    val store: Store? = null,
    val quantity: Int = 1,
    val selectedOptions: List<ProductOption> = emptyList(),
    val instructions: String = ""
)

class DeliveryViewModel(
    private val repository: DeliveryRepository = DeliveryRepositoryImpl(MockDeliveryApiService())
) : ViewModel() {

    // Navigation & Screen Stack
    private val _screenStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val currentScreen: StateFlow<Screen> = MutableStateFlow(Screen.Home)

    private val _currentTab = MutableStateFlow(BottomTab.HOME)
    val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

    // Flows from repository
    val stores: StateFlow<List<Store>> = repository.storesFlow
    val cart: StateFlow<Cart> = repository.cartFlow
    val addresses: StateFlow<List<Address>> = repository.addressesFlow
    val selectedAddress: StateFlow<Address?> = repository.selectedAddressFlow
    val orders: StateFlow<List<Order>> = repository.ordersFlow
    val activeOrder: StateFlow<Order?> = repository.activeOrderFlow
    val isLoading: StateFlow<Boolean> = repository.isLoadingFlow

    // Search and Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow(StoreCategory.ALL)
    val selectedCategoryFilter: StateFlow<StoreCategory> = _selectedCategoryFilter.asStateFlow()

    // Selected Store & Products
    private val _selectedStore = MutableStateFlow<Store?>(null)
    val selectedStore: StateFlow<Store?> = _selectedStore.asStateFlow()

    private val _storeProducts = MutableStateFlow<List<Product>>(emptyList())
    val storeProducts: StateFlow<List<Product>> = _storeProducts.asStateFlow()

    private val _selectedStoreCategory = MutableStateFlow<String?>("الكل")
    val selectedStoreCategory: StateFlow<String?> = _selectedStoreCategory.asStateFlow()

    // Product Modal
    private val _productModalState = MutableStateFlow(ProductDetailModalState())
    val productModalState: StateFlow<ProductDetailModalState> = _productModalState.asStateFlow()

    // Tracking Order Details
    private val _trackedOrder = MutableStateFlow<Order?>(null)
    val trackedOrder: StateFlow<Order?> = _trackedOrder.asStateFlow()

    // User Feedback Event
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    // Checkout payment method selection
    private val _checkoutPaymentMethod = MutableStateFlow(PaymentMethod.CASH_ON_DELIVERY)
    val checkoutPaymentMethod: StateFlow<PaymentMethod> = _checkoutPaymentMethod.asStateFlow()

    init {
        // Sync screen stack with currentScreen
        viewModelScope.launch {
            _screenStack.collect { stack ->
                (currentScreen as MutableStateFlow).value = stack.lastOrNull() ?: Screen.Home
            }
        }
    }

    fun clearCustomerData() {
        repository.clearCustomerData()
        _trackedOrder.value = null
        _selectedStore.value = null
        _storeProducts.value = emptyList()
        _selectedStoreCategory.value = "الكل"
        _productModalState.value = ProductDetailModalState()
        _searchQuery.value = ""
        _selectedCategoryFilter.value = StoreCategory.ALL
        _screenStack.value = listOf(Screen.Home)
        _currentTab.value = BottomTab.HOME
    }

    fun refreshOrdersNow() {
        viewModelScope.launch {
            repository.refreshOrders()
            _trackedOrder.value?.let { current ->
                repository.getOrderById(current.id).onSuccess { refreshed ->
                    _trackedOrder.value = refreshed
                }
            }
        }
    }

    fun refreshBackendData() {
        viewModelScope.launch {
            repository.refreshStores(_selectedCategoryFilter.value, _searchQuery.value)
            repository.refreshAddresses()
            repository.refreshOrders()
        }
    }

    fun navigateTo(screen: Screen) {
        val current = _screenStack.value
        // Avoid duplicating exact same top screen
        if (current.lastOrNull() == screen) return
        _screenStack.value = current + screen

        // Sync tab if applicable
        when (screen) {
            is Screen.Home -> _currentTab.value = BottomTab.HOME
            is Screen.StoreList -> _currentTab.value = BottomTab.STORES
            is Screen.MyOrders -> _currentTab.value = BottomTab.ORDERS
            is Screen.CartPage -> _currentTab.value = BottomTab.CART
            is Screen.Profile -> _currentTab.value = BottomTab.PROFILE
            else -> {}
        }
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        return if (current.size > 1) {
            _screenStack.value = current.dropLast(1)
            // Update bottom tab to reflect new top screen
            when (_screenStack.value.lastOrNull()) {
                is Screen.Home -> _currentTab.value = BottomTab.HOME
                is Screen.StoreList -> _currentTab.value = BottomTab.STORES
                is Screen.MyOrders -> _currentTab.value = BottomTab.ORDERS
                is Screen.CartPage -> _currentTab.value = BottomTab.CART
                is Screen.Profile -> _currentTab.value = BottomTab.PROFILE
                else -> {}
            }
            true
        } else {
            false
        }
    }

    fun switchTab(tab: BottomTab) {
        _currentTab.value = tab
        when (tab) {
            BottomTab.HOME -> {
                _screenStack.value = listOf(Screen.Home)
            }
            BottomTab.STORES -> {
                _screenStack.value = listOf(Screen.StoreList())
            }
            BottomTab.ORDERS -> {
                _screenStack.value = listOf(Screen.MyOrders)
            }
            BottomTab.CART -> {
                _screenStack.value = listOf(Screen.CartPage)
            }
            BottomTab.PROFILE -> {
                _screenStack.value = listOf(Screen.Profile)
            }
        }
    }

    // Search and Store Filter
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            repository.refreshStores(_selectedCategoryFilter.value, query)
        }
    }

    fun onCategoryFilterSelected(category: StoreCategory) {
        _selectedCategoryFilter.value = category
        viewModelScope.launch {
            repository.refreshStores(category, _searchQuery.value)
        }
    }

    // Store Details
    fun openStore(storeId: String) {
        viewModelScope.launch {
            val res = repository.getStoreById(storeId)
            res.onSuccess { store ->
                _selectedStore.value = store
                _selectedStoreCategory.value = "الكل"
                loadStoreProducts(storeId)
                navigateTo(Screen.StoreDetail(storeId))
            }
        }
    }

    fun loadStoreProducts(storeId: String, category: String? = null) {
        viewModelScope.launch {
            val filterCat = if (category == "الكل" || category.isNullOrBlank()) null else category
            val res = repository.getStoreProducts(storeId, filterCat)
            res.onSuccess { prods ->
                _storeProducts.value = prods
            }
        }
    }

    fun onStoreCategoryFilterSelected(category: String) {
        _selectedStoreCategory.value = category
        val store = _selectedStore.value ?: return
        loadStoreProducts(store.id, category)
    }

    // Product Dialog Modal
    fun openProductModal(product: Product, store: Store? = null) {
        val s = store ?: _selectedStore.value ?: stores.value.find { it.id == product.storeId }
        _productModalState.value = ProductDetailModalState(
            isOpen = true,
            product = product,
            store = s,
            quantity = 1,
            selectedOptions = emptyList(),
            instructions = ""
        )
    }

    fun closeProductModal() {
        _productModalState.value = ProductDetailModalState()
    }

    fun updateProductModalQuantity(quantity: Int) {
        if (quantity in 1..20) {
            _productModalState.value = _productModalState.value.copy(quantity = quantity)
        }
    }

    fun toggleProductOption(option: ProductOption) {
        val current = _productModalState.value.selectedOptions.toMutableList()
        if (current.contains(option)) {
            current.remove(option)
        } else {
            current.add(option)
        }
        _productModalState.value = _productModalState.value.copy(selectedOptions = current)
    }

    fun updateProductInstructions(instructions: String) {
        _productModalState.value = _productModalState.value.copy(instructions = instructions)
    }

    fun addProductFromModalToCart() {
        val state = _productModalState.value
        val product = state.product ?: return
        val store = state.store ?: stores.value.find { it.id == product.storeId } ?: return

        repository.addToCart(
            product = product,
            quantity = state.quantity,
            selectedOptions = state.selectedOptions,
            specialInstructions = state.instructions,
            store = store
        )

        closeProductModal()
        emitSnackbar("تمت إضافة \"${product.name}\" إلى السلة")
    }

    // Cart operations
    fun updateCartQuantity(productId: String, newQuantity: Int) {
        repository.updateCartItemQuantity(productId, newQuantity)
    }

    fun removeCartItem(productId: String) {
        repository.removeFromCart(productId)
        emitSnackbar("تم حذف المنتج من السلة")
    }

    fun clearCart() {
        repository.clearCart()
        emitSnackbar("تم تفريغ السلة")
    }

    // Address operations
    fun selectAddress(address: Address) {
        viewModelScope.launch {
            repository.selectDeliveryAddress(address)
            emitSnackbar("تم اختيار عنوان: ${address.title}")
        }
    }

    fun addNewAddress(address: Address) {
        viewModelScope.launch {
            val res = repository.addDeliveryAddress(address)
            res.onSuccess {
                emitSnackbar("تم حفظ العنوان بنجاح")
            }
        }
    }

    // Checkout & Order
    fun setPaymentMethod(method: PaymentMethod) {
        _checkoutPaymentMethod.value = method
    }

    fun placeOrder(customerNotes: String) {
        viewModelScope.launch {
            val res = repository.submitOrder(
                paymentMethod = _checkoutPaymentMethod.value,
                customerNotes = customerNotes
            )
            res.onSuccess { order ->
                _trackedOrder.value = order
                navigateTo(Screen.OrderSuccess(order.id))
            }.onFailure { err ->
                emitSnackbar(err.message ?: "حدث خطأ أثناء إرسال الطلب")
            }
        }
    }

    // Order Tracking & Details
    fun openOrderTracking(orderId: String) {
        viewModelScope.launch {
            val res = repository.getOrderById(orderId)
            res.onSuccess { order ->
                _trackedOrder.value = order
                repository.setActiveOrder(order)
                navigateTo(Screen.OrderTracking(orderId))
            }
        }
    }

    fun openOrderDetail(orderId: String) {
        viewModelScope.launch {
            val res = repository.getOrderById(orderId)
            res.onSuccess { order ->
                _trackedOrder.value = order
                navigateTo(Screen.OrderDetail(orderId))
            }
        }
    }

    fun advanceOrderSimulation(orderId: String) {
        viewModelScope.launch {
            val res = repository.simulateAdvanceOrderStatus(orderId)
            res.onSuccess { updated ->
                _trackedOrder.value = updated
                emitSnackbar("تحديث الحالة: ${updated.status.titleAr}")
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val res = repository.cancelOrder(orderId)
            res.onSuccess { updated ->
                _trackedOrder.value = updated
                emitSnackbar("تم إلغاء الطلب بنجاح")
            }.onFailure { err ->
                emitSnackbar(err.message ?: "تعذر إلغاء الطلب")
            }
        }
    }

    fun reorder(order: Order) {
        viewModelScope.launch {
            val storeRes = repository.getStoreById(order.storeId)
            val store = storeRes.getOrNull() ?: stores.value.find { it.id == order.storeId }
            if (store == null) {
                emitSnackbar("المتجر غير متوفر حالياً")
                return@launch
            }
            repository.clearCart()
            order.items.forEach { item ->
                val prodRes = repository.getProductById(item.productId)
                val prod = prodRes.getOrNull()
                if (prod != null) {
                    repository.addToCart(
                        product = prod,
                        quantity = item.quantity,
                        selectedOptions = emptyList(),
                        specialInstructions = item.specialInstructions,
                        store = store
                    )
                }
            }
            emitSnackbar("تمت إضافة عناصر الطلب السابق إلى السلة")
            navigateTo(Screen.CartPage)
        }
    }

    private fun emitSnackbar(msg: String) {
        viewModelScope.launch {
            _snackbarMessages.emit(msg)
        }
    }
}
