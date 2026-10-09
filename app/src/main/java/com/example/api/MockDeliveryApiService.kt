package com.example.api

import com.example.model.Address
import com.example.model.Driver
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.Store
import com.example.model.StoreCategory
import kotlinx.coroutines.delay
import java.util.UUID

class MockDeliveryApiService : DeliveryApiService {

    private val stores = DeliverySampleData.sampleStores.toMutableList()
    private val products = DeliverySampleData.sampleProducts.toMutableList()
    private val addresses = DeliverySampleData.sampleAddresses.toMutableList()
    private val orders = DeliverySampleData.initialActiveOrders().toMutableList()

    override suspend fun getStores(
        category: StoreCategory?,
        query: String?,
        onlyOpen: Boolean
    ): Result<List<Store>> {
        delay(150) // simulate network latency
        var result = stores.toList()

        if (category != null && category != StoreCategory.ALL) {
            result = result.filter { it.category == category }
        }

        if (!query.isNullOrBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.nameAr.lowercase().contains(q) ||
                it.nameEn.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }

        if (onlyOpen) {
            result = result.filter { it.isOpen }
        }

        return Result.success(result)
    }

    override suspend fun getStoreById(storeId: String): Result<Store> {
        delay(100)
        val store = stores.find { it.id == storeId }
        return if (store != null) {
            Result.success(store)
        } else {
            Result.failure(NoSuchElementException("المتجر غير موجود"))
        }
    }

    override suspend fun getStoreProducts(storeId: String, category: String?): Result<List<Product>> {
        delay(120)
        var list = products.filter { it.storeId == storeId }
        if (!category.isNullOrBlank()) {
            list = list.filter { it.category == category }
        }
        return Result.success(list)
    }

    override suspend fun getProductById(productId: String): Result<Product> {
        delay(80)
        val prod = products.find { it.id == productId }
        return if (prod != null) {
            Result.success(prod)
        } else {
            Result.failure(NoSuchElementException("المنتج غير موجود"))
        }
    }

    override suspend fun getAddresses(): Result<List<Address>> {
        delay(100)
        return Result.success(addresses.toList())
    }

    override suspend fun addAddress(address: Address): Result<Address> {
        delay(200)
        val newAddress = if (address.isDefault) {
            // Unset previous defaults
            val updated = addresses.map { it.copy(isDefault = false) }.toMutableList()
            addresses.clear()
            addresses.addAll(updated)
            address
        } else {
            address
        }
        addresses.add(0, newAddress)
        return Result.success(newAddress)
    }

    override suspend fun setDefaultAddress(addressId: String): Result<Address> {
        delay(150)
        val target = addresses.find { it.id == addressId }
            ?: return Result.failure(NoSuchElementException("العنوان غير موجود"))

        val updated = addresses.map {
            it.copy(isDefault = it.id == addressId)
        }
        addresses.clear()
        addresses.addAll(updated)
        return Result.success(target.copy(isDefault = true))
    }

    override suspend fun createOrder(request: CreateOrderRequest): Result<Order> {
        delay(350)
        val store = stores.find { it.id == request.storeId }
            ?: return Result.failure(IllegalArgumentException("المتجر غير موجود"))

        val address = addresses.find { it.id == request.deliveryAddressId }
            ?: addresses.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("يجب اختيار عنوان توصيل صالح"))

        val orderItems = request.items.mapNotNull { itemReq ->
            val product = products.find { it.id == itemReq.productId }
            if (product != null) {
                OrderItem(
                    id = "item_${UUID.randomUUID().toString().take(6)}",
                    productId = product.id,
                    productName = product.name,
                    unitPrice = product.price,
                    quantity = itemReq.quantity,
                    specialInstructions = itemReq.specialInstructions
                )
            } else null
        }

        if (orderItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("السلة فارغة أو المنتجات غير متوفرة"))
        }

        val subtotal = orderItems.sumOf { it.totalPrice }
        val deliveryFee = store.deliveryFee
        val serviceFee = 250.0
        val total = subtotal + deliveryFee + serviceFee

        val newOrder = Order(
            id = "ord_${System.currentTimeMillis() % 100000}",
            orderNumber = "WAS-${(10000..99999).random()}",
            customerId = "usr_001",
            storeId = store.id,
            storeName = store.nameAr,
            storeLogoUrl = store.logoUrl,
            storePhone = "+249 183 456 789",
            deliveryAddress = address,
            driver = null, // Driver assigned once accepted/picked up
            items = orderItems,
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            serviceFee = serviceFee,
            total = total,
            paymentMethod = request.paymentMethod,
            status = OrderStatus.NEW,
            createdAtMillis = System.currentTimeMillis(),
            estimatedDeliveryMinutes = store.deliveryTimeMinutes + 10,
            customerNotes = request.customerNotes
        )

        orders.add(0, newOrder)
        return Result.success(newOrder)
    }

    override suspend fun getOrderById(orderId: String): Result<Order> {
        delay(100)
        val order = orders.find { it.id == orderId }
        return if (order != null) {
            Result.success(order)
        } else {
            Result.failure(NoSuchElementException("الطلب غير موجود"))
        }
    }

    override suspend fun getMyOrders(): Result<List<Order>> {
        delay(150)
        return Result.success(orders.sortedByDescending { it.createdAtMillis })
    }

    override suspend fun cancelOrder(orderId: String): Result<Order> {
        delay(200)
        val index = orders.indexOfFirst { it.id == orderId }
        if (index == -1) return Result.failure(NoSuchElementException("الطلب غير موجود"))

        val existing = orders[index]
        if (existing.status != OrderStatus.NEW && existing.status != OrderStatus.ACCEPTED) {
            return Result.failure(IllegalStateException("لا يمكن إلغاء الطلب بعد بدء التجهيز أو انطلاق المندوب"))
        }

        val cancelled = existing.copy(status = OrderStatus.CANCELLED)
        orders[index] = cancelled
        return Result.success(cancelled)
    }

    override suspend fun advanceOrderStatus(orderId: String): Result<Order> {
        delay(100)
        val index = orders.indexOfFirst { it.id == orderId }
        if (index == -1) return Result.failure(NoSuchElementException("الطلب غير موجود"))

        val existing = orders[index]
        val steps = OrderStatus.standardTimelineSteps
        val currentIndex = steps.indexOf(existing.status)

        if (currentIndex in 0 until steps.lastIndex) {
            val nextStatus = steps[currentIndex + 1]
            val driverAssigned = if (nextStatus == OrderStatus.PICKED_UP || nextStatus == OrderStatus.ON_THE_WAY || nextStatus == OrderStatus.DELIVERED) {
                existing.driver ?: DeliverySampleData.sampleDriver
            } else {
                existing.driver
            }
            val updated = existing.copy(status = nextStatus, driver = driverAssigned)
            orders[index] = updated
            return Result.success(updated)
        }

        return Result.success(existing)
    }
}
