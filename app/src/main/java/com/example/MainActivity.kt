package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.components.WasilBottomBar
import com.example.ui.components.WasilTopBar
import com.example.ui.screens.AddressScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyOrdersScreen
import com.example.ui.screens.OrderDetailScreen
import com.example.ui.screens.OrderSuccessScreen
import com.example.ui.screens.OrderTrackingScreen
import com.example.ui.screens.ProductDetailDialog
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StoreDetailScreen
import com.example.ui.screens.StoreListScreen
import com.example.ui.theme.WasilDeliveryTheme
import com.example.viewmodel.BottomTab
import com.example.viewmodel.DeliveryViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: DeliveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WasilDeliveryTheme {
                // Sudanese Arabic RTL experience
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    WasilCustomerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun WasilCustomerApp(viewModel: DeliveryViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val activeOrder by viewModel.activeOrder.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val storeProducts by viewModel.storeProducts.collectAsState()
    val selectedStoreCategory by viewModel.selectedStoreCategory.collectAsState()
    val productModalState by viewModel.productModalState.collectAsState()
    val trackedOrder by viewModel.trackedOrder.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val addresses by viewModel.addresses.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val checkoutPaymentMethod by viewModel.checkoutPaymentMethod.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Determine if back button should be shown
    val isTopLevelScreen = currentScreen is Screen.Home ||
            currentScreen is Screen.StoreList ||
            currentScreen is Screen.MyOrders ||
            currentScreen is Screen.CartPage ||
            currentScreen is Screen.Profile

    val showBackButton = !isTopLevelScreen

    BackHandler(enabled = showBackButton) {
        viewModel.navigateBack()
    }

    // Bottom bar is shown on primary navigation screens
    val showBottomBar = currentScreen is Screen.Home ||
            currentScreen is Screen.StoreList ||
            currentScreen is Screen.MyOrders ||
            currentScreen is Screen.CartPage ||
            currentScreen is Screen.Profile

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Only show top bar on relevant screens (StoreDetail has its own custom visual header)
            if (currentScreen !is Screen.StoreDetail && currentScreen !is Screen.OrderSuccess && currentScreen !is Screen.OrderTracking && currentScreen !is Screen.OrderDetail) {
                val screenTitle = when (currentScreen) {
                    is Screen.Home -> null
                    is Screen.StoreList -> "المتاجر المتاحة"
                    is Screen.CartPage -> "سلة المشتريات"
                    is Screen.AddressSelect -> "عناوين التوصيل"
                    is Screen.Checkout -> "إتمام وتأكيد الطلب"
                    is Screen.MyOrders -> "سجل الطلبات"
                    is Screen.Profile -> "الملف الشخصي والإعدادات"
                    else -> "واصل"
                }

                WasilTopBar(
                    title = screenTitle,
                    currentAddress = selectedAddress,
                    showAddressSelector = currentScreen is Screen.Home,
                    showBackButton = showBackButton,
                    cartItemCount = cart.totalItemsCount,
                    onBackClick = { viewModel.navigateBack() },
                    onAddressClick = { viewModel.navigateTo(Screen.AddressSelect(isFromCheckout = false)) },
                    onCartClick = { viewModel.navigateTo(Screen.CartPage) }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                WasilBottomBar(
                    currentTab = currentTab,
                    cartItemCount = cart.totalItemsCount,
                    onTabSelected = { tab -> viewModel.switchTab(tab) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
                when (screen) {
                    is Screen.Home -> {
                        HomeScreen(
                            stores = stores,
                            activeOrder = activeOrder,
                            selectedCategory = selectedCategoryFilter,
                            onCategorySelect = { cat ->
                                viewModel.onCategoryFilterSelected(cat)
                                viewModel.navigateTo(Screen.StoreList(category = cat))
                            },
                            onStoreClick = { storeId -> viewModel.openStore(storeId) },
                            onProductClick = { prod -> viewModel.openProductModal(prod) },
                            onTrackOrderClick = { orderId -> viewModel.openOrderTracking(orderId) },
                            onViewAllStoresClick = { viewModel.navigateTo(Screen.StoreList()) },
                            onSearchClick = { viewModel.navigateTo(Screen.StoreList()) }
                        )
                    }

                    is Screen.StoreList -> {
                        StoreListScreen(
                            stores = stores,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategoryFilter,
                            onSearchChanged = { q -> viewModel.onSearchQueryChanged(q) },
                            onCategorySelected = { cat -> viewModel.onCategoryFilterSelected(cat) },
                            onStoreClick = { storeId -> viewModel.openStore(storeId) }
                        )
                    }

                    is Screen.StoreDetail -> {
                        val store = selectedStore ?: stores.find { it.id == screen.storeId }
                        if (store != null) {
                            StoreDetailScreen(
                                store = store,
                                products = storeProducts,
                                selectedCategory = selectedStoreCategory,
                                cart = cart,
                                onCategorySelect = { cat -> viewModel.onStoreCategoryFilterSelected(cat) },
                                onProductClick = { prod -> viewModel.openProductModal(prod, store) },
                                onAddProductClick = { prod -> viewModel.openProductModal(prod, store) },
                                onBackClick = { viewModel.navigateBack() },
                                onGoToCartClick = { viewModel.navigateTo(Screen.CartPage) }
                            )
                        }
                    }

                    is Screen.CartPage -> {
                        CartScreen(
                            cart = cart,
                            currentAddress = selectedAddress,
                            onQuantityChange = { prodId: String, q: Int -> viewModel.updateCartQuantity(prodId, q) },
                            onRemoveItem = { prodId: String -> viewModel.removeCartItem(prodId) },
                            onClearCart = { viewModel.clearCart() },
                            onChangeAddressClick = { viewModel.navigateTo(Screen.AddressSelect(isFromCheckout = true)) },
                            onProceedToCheckout = { viewModel.navigateTo(Screen.Checkout) },
                            onExploreStoresClick = { viewModel.switchTab(BottomTab.STORES) }
                        )
                    }

                    is Screen.AddressSelect -> {
                        AddressScreen(
                            addresses = addresses,
                            selectedAddress = selectedAddress,
                            onAddressSelect = { addr ->
                                viewModel.selectAddress(addr)
                                if (screen.isFromCheckout) {
                                    viewModel.navigateBack()
                                }
                            },
                            onAddNewAddress = { newAddr -> viewModel.addNewAddress(newAddr) },
                            isFromCheckout = screen.isFromCheckout,
                            onBackClick = { viewModel.navigateBack() }
                        )
                    }

                    is Screen.Checkout -> {
                        CheckoutScreen(
                            cart = cart,
                            currentAddress = selectedAddress,
                            customerPhone = selectedAddress?.recipientPhone ?: "+249 912 345 678",
                            selectedPaymentMethod = checkoutPaymentMethod,
                            onPaymentMethodSelect = { method -> viewModel.setPaymentMethod(method) },
                            onChangeAddressClick = { viewModel.navigateTo(Screen.AddressSelect(isFromCheckout = true)) },
                            onConfirmOrder = { notes -> viewModel.placeOrder(notes) }
                        )
                    }

                    is Screen.OrderSuccess -> {
                        val order = trackedOrder ?: orders.find { it.id == screen.orderId }
                        if (order != null) {
                            OrderSuccessScreen(
                                order = order,
                                onTrackOrderClick = { viewModel.openOrderTracking(order.id) },
                                onReturnHomeClick = { viewModel.switchTab(BottomTab.HOME) }
                            )
                        }
                    }

                    is Screen.OrderTracking -> {
                        val order = trackedOrder ?: orders.find { it.id == screen.orderId }
                        if (order != null) {
                            OrderTrackingScreen(
                                order = order,
                                onBackClick = { viewModel.navigateBack() },
                                onAdvanceSimulation = { viewModel.advanceOrderSimulation(order.id) },
                                onCancelOrder = { viewModel.cancelOrder(order.id) },
                                onViewReceiptClick = { viewModel.openOrderDetail(order.id) }
                            )
                        }
                    }

                    is Screen.MyOrders -> {
                        MyOrdersScreen(
                            orders = orders,
                            onTrackOrderClick = { orderId -> viewModel.openOrderTracking(orderId) },
                            onOrderDetailClick = { orderId -> viewModel.openOrderDetail(orderId) },
                            onReorderClick = { ord -> viewModel.reorder(ord) },
                            onExploreStoresClick = { viewModel.switchTab(BottomTab.STORES) }
                        )
                    }

                    is Screen.OrderDetail -> {
                        val order = trackedOrder ?: orders.find { it.id == screen.orderId }
                        if (order != null) {
                            OrderDetailScreen(
                                order = order,
                                onBackClick = { viewModel.navigateBack() },
                                onTrackClick = { viewModel.openOrderTracking(order.id) }
                            )
                        }
                    }

                    is Screen.Profile -> {
                        ProfileScreen(
                            onSavedAddressesClick = { viewModel.navigateTo(Screen.AddressSelect(isFromCheckout = false)) }
                        )
                    }
                }
            }

            // Global Product Detail & Customization Modal
            ProductDetailDialog(
                state = productModalState,
                onDismiss = { viewModel.closeProductModal() },
                onQuantityChange = { q -> viewModel.updateProductModalQuantity(q) },
                onOptionToggle = { opt -> viewModel.toggleProductOption(opt) },
                onInstructionsChange = { ins -> viewModel.updateProductInstructions(ins) },
                onAddToCart = { viewModel.addProductFromModalToCart() }
            )
        }
    }
}
