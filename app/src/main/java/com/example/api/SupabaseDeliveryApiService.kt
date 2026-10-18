package com.example.api

import com.example.data.supabase.SupabaseRestClient
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseSessionProvider
import com.example.model.Address
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.Store
import com.example.model.StoreCategory
import com.example.model.SudanCity
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Customer-facing Supabase data source. All privileged writes go through validated RPCs.
 * The server remains authoritative for product prices, stock, ownership and order status.
 */
class SupabaseDeliveryApiService(
    private val client: SupabaseRestClient = SupabaseRestClient(),
) : DeliveryApiService {

    private suspend fun <T> ioResult(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private suspend fun currentSession(): SupabaseSession =
        SupabaseSessionProvider.validSessionOrNull()
            ?: throw IllegalStateException("سجّل الدخول أولاً قبل الوصول إلى بيانات حسابك.")

    private suspend fun accessToken(): String = currentSession().accessToken

    private suspend fun getArray(path: String, authenticated: Boolean = false): JSONArray {
        val token = if (authenticated) accessToken() else SupabaseSessionProvider.validSessionOrNull()?.accessToken
        return JSONArray(client.rest(path, accessToken = token).getOrThrow())
    }

    private suspend fun postObject(path: String, body: JSONObject, prefer: String? = null): JSONArray =
        JSONArray(
            client.rest(
                tableOrRpcPath = path,
                method = "POST",
                body = body.toString(),
                accessToken = accessToken(),
                prefer = prefer,
            ).getOrThrow()
        )

    override suspend fun getStores(
        category: StoreCategory?,
        query: String?,
        onlyOpen: Boolean,
    ): Result<List<Store>> = ioResult {
        val rows = getArray(
            "rest/v1/stores?select=*,category:categories(slug)&is_active=eq.true&is_open=eq.true&order=name_ar.asc"
        )
        buildList {
            for (i in 0 until rows.length()) {
                val store = rows.getJSONObject(i).toStore()
                if (category != null && category != StoreCategory.ALL && store.category != category) continue
                if (onlyOpen && !store.isOpen) continue
                if (!query.isNullOrBlank()) {
                    val q = query.trim()
                    if (!store.nameAr.contains(q, ignoreCase = true) &&
                        !store.nameEn.contains(q, ignoreCase = true) &&
                        !store.description.contains(q, ignoreCase = true)
                    ) continue
                }
                add(store)
            }
        }
    }

    override suspend fun getStoreById(storeId: String): Result<Store> = ioResult {
        val rows = getArray("rest/v1/stores?select=*,category:categories(slug)&id=eq." + safeValue(storeId))
        check(rows.length() > 0) { "المتجر غير موجود أو غير متاح حالياً." }
        rows.getJSONObject(0).toStore()
    }

    override suspend fun getStoreProducts(storeId: String, category: String?): Result<List<Product>> = ioResult {
        val rows = getArray(
            "rest/v1/products?select=*&store_id=eq." + safeValue(storeId) + "&is_active=eq.true&is_available=eq.true&order=name_ar.asc"
        )
        buildList {
            for (i in 0 until rows.length()) {
                val product = rows.getJSONObject(i).toProduct()
                if (!category.isNullOrBlank() && product.category != category) continue
                add(product)
            }
        }
    }

    override suspend fun getProductById(productId: String): Result<Product> = ioResult {
        val rows = getArray("rest/v1/products?select=*&id=eq." + safeValue(productId) + "&is_active=eq.true&is_available=eq.true")
        check(rows.length() > 0) { "المنتج غير موجود أو غير متاح حالياً." }
        rows.getJSONObject(0).toProduct()
    }

    override suspend fun getAddresses(): Result<List<Address>> = ioResult {
        val session = currentSession()
        val rows = getArray(
            "rest/v1/addresses?select=*&customer_id=eq." + safeValue(session.userId) + "&order=is_default.desc,created_at.desc",
            authenticated = true,
        )
        buildList {
            for (i in 0 until rows.length()) add(rows.getJSONObject(i).toAddress())
        }
    }

    override suspend fun addAddress(address: Address): Result<Address> = ioResult {
        val session = currentSession()
        val details = listOf(address.buildingNumber, address.floorOrApartment, address.landmark)
            .filter { it.isNotBlank() }.joinToString("، ")
        val body = JSONObject()
            .put("customer_id", session.userId)
            .put("label", address.title)
            .put("address_text", address.fullAddressText)
            .put("city", address.city.name)
            .put("area", address.area)
            .put("street", address.street)
            .put("building_number", address.buildingNumber)
            .put("floor_or_apartment", address.floorOrApartment)
            .put("landmark", address.landmark)
            .put("recipient_name", address.recipientName)
            .put("recipient_phone", address.recipientPhone)
            .put("latitude", address.latitude)
            .put("longitude", address.longitude)
            .put("details", details)
        val rows = postObject("rest/v1/addresses?select=*", body, prefer = "return=representation")
        check(rows.length() > 0) { "لم يرجع الخادم العنوان بعد حفظه." }
        val created = rows.getJSONObject(0).toAddress()
        if (address.isDefault) {
            setDefaultAddress(created.id).getOrThrow()
            getAddressById(created.id)
        } else {
            created
        }
    }

    override suspend fun setDefaultAddress(addressId: String): Result<Address> = ioResult {
        val response = client.rest(
            tableOrRpcPath = "rest/v1/rpc/wasil_set_default_address",
            method = "POST",
            body = JSONObject().put("p_address_id", addressId).toString(),
            accessToken = accessToken(),
        ).getOrThrow().trim()
        check(response == "true") { "تعذر تعيين العنوان الافتراضي." }
        getAddressById(addressId)
    }

    override suspend fun createOrder(request: CreateOrderRequest): Result<Order> = ioResult {
        require(request.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
            "الدفع الإلكتروني غير مفعّل بعد؛ اختر الدفع نقداً عند الاستلام."
        }
        val items = JSONArray()
        request.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("product_id", item.productId)
                    .put("quantity", item.quantity)
                    .put("special_instructions", item.specialInstructions.take(500))
            )
        }
        val body = JSONObject()
            .put("p_store_id", request.storeId)
            .put("p_address_id", request.deliveryAddressId)
            .put("p_items", items)
            .put("p_idempotency_key", request.idempotencyKey)
            .put("p_customer_notes", request.customerNotes.take(1000))
        val response = client.rest(
            tableOrRpcPath = "rest/v1/rpc/wasil_create_order",
            method = "POST",
            body = body.toString(),
            accessToken = accessToken(),
        ).getOrThrow().trim().trim('"')
        check(response.isNotBlank()) { "لم يرجع الخادم رقم الطلب." }
        getOrderById(response).getOrThrow()
    }

    override suspend fun getOrderById(orderId: String): Result<Order> = ioResult {
        val rows = getArray(
            "rest/v1/orders?select=*&id=eq." + safeValue(orderId),
            authenticated = true,
        )
        check(rows.length() > 0) { "الطلب غير موجود أو لا تملك صلاحية عرضه." }
        rows.getJSONObject(0).toOrder()
    }

    override suspend fun getMyOrders(): Result<List<Order>> = ioResult {
        val session = currentSession()
        val rows = getArray(
            "rest/v1/orders?select=*&customer_id=eq." + safeValue(session.userId) + "&order=placed_at.desc",
            authenticated = true,
        )
        val orders = mutableListOf<Order>()
        for (i in 0 until rows.length()) orders.add(rows.getJSONObject(i).toOrder())
        orders
    }

    override suspend fun cancelOrder(orderId: String): Result<Order> = ioResult {
        val response = client.rest(
            tableOrRpcPath = "rest/v1/rpc/wasil_cancel_order",
            method = "POST",
            body = JSONObject().put("p_order_id", orderId).put("p_reason", "طلب الإلغاء من العميل").toString(),
            accessToken = accessToken(),
        ).getOrThrow().trim()
        check(response == "true") { "تعذر إلغاء الطلب؛ ربما تغيّرت حالته." }
        getOrderById(orderId).getOrThrow()
    }

    override suspend fun advanceOrderStatus(orderId: String): Result<Order> =
        Result.failure(IllegalStateException("حالة الطلب يحدّثها المتجر والمندوب عبر النظام؛ لا يمكن محاكاتها من تطبيق العميل."))

    private suspend fun getAddressById(addressId: String): Address {
        val rows = getArray(
            "rest/v1/addresses?select=*&id=eq." + safeValue(addressId),
            authenticated = true,
        )
        check(rows.length() > 0) { "العنوان غير موجود." }
        return rows.getJSONObject(0).toAddress()
    }

    private suspend fun JSONObject.toOrder(): Order {
        val storeId = optString("store_id")
        val store = getStoreById(storeId).getOrNull()
        val addressId = optString("address_id").takeIf { it.isNotBlank() && it != "null" }
        val address = addressId?.let { runCatching { getAddressById(it) }.getOrNull() }
            ?: snapshotAddress(this)
        val itemRows = getArray(
            "rest/v1/order_items?select=*&order_id=eq." + safeValue(optString("id")) + "&order=id.asc",
            authenticated = true,
        )
        val items = buildList {
            for (i in 0 until itemRows.length()) {
                val item = itemRows.getJSONObject(i)
                add(
                    OrderItem(
                        id = item.optString("id"),
                        productId = item.optString("product_id").takeIf { it != "null" }.orEmpty(),
                        productName = item.optString("product_name_snapshot"),
                        unitPrice = item.optDouble("unit_price", 0.0),
                        quantity = item.optInt("quantity", 1),
                        specialInstructions = item.optString("special_instructions"),
                    )
                )
            }
        }
        val status = when (optString("status")) {
            "confirmed" -> OrderStatus.ACCEPTED
            "preparing" -> OrderStatus.PREPARING
            "ready_for_pickup", "driver_assigned" -> OrderStatus.READY
            "picked_up" -> OrderStatus.PICKED_UP
            "on_the_way" -> OrderStatus.ON_THE_WAY
            "delivered" -> OrderStatus.DELIVERED
            "cancelled" -> OrderStatus.CANCELLED
            else -> OrderStatus.NEW
        }
        return Order(
            id = optString("id"),
            orderNumber = "WAS-" + optLong("order_number").toString(),
            customerId = optString("customer_id"),
            storeId = storeId,
            storeName = store?.nameAr ?: optString("store_name_snapshot").ifBlank { "متجر وصّل" },
            storeLogoUrl = store?.logoUrl ?: optString("store_image_snapshot"),
            storePhone = optString("store_phone_snapshot").ifBlank { "" },
            deliveryAddress = address,
            driver = null,
            items = items,
            subtotal = optDouble("subtotal", 0.0),
            deliveryFee = optDouble("delivery_fee", 0.0),
            serviceFee = optDouble("service_fee", 0.0),
            total = optDouble("total", 0.0),
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            status = status,
            createdAtMillis = System.currentTimeMillis(),
            estimatedDeliveryMinutes = store?.deliveryTimeMinutes ?: 35,
            customerNotes = optString("customer_notes"),
            taxAmount = optDouble("tax_amount", 0.0),
            distanceKm = optDouble("distance_km", 0.0),
        )
    }

    private fun snapshotAddress(order: JSONObject): Address {
        val text = order.optString("delivery_address_snapshot").ifBlank { "عنوان التوصيل" }
        return Address(
            id = order.optString("address_id").takeIf { it != "null" }.orEmpty(),
            title = "عنوان الطلب",
            city = SudanCity.KHARTOUM,
            area = text,
            street = "",
            buildingNumber = "",
            recipientName = order.optString("recipient_name_snapshot"),
            recipientPhone = order.optString("recipient_phone_snapshot"),
            isDefault = false,
        )
    }

    private fun JSONObject.toStore(): Store {
        val categorySlug = optJSONObject("category")?.optString("slug").orEmpty()
        val category = StoreCategory.values().firstOrNull { it.id == categorySlug }
            ?: StoreCategory.ALL
        val image = optString("image_url")
        return Store(
            id = optString("id"),
            nameAr = optString("name_ar"),
            nameEn = optString("name_en"),
            category = category,
            description = optString("description"),
            logoUrl = image,
            coverUrl = image,
            rating = 0.0,
            reviewCount = 0,
            deliveryTimeMinutes = 35,
            deliveryFee = 0.0,
            minOrderAmount = 0.0,
            isOpen = optBoolean("is_open", false),
            area = optString("address_text"),
            distanceKm = 0.0,
            tags = emptyList(),
            productCategories = emptyList(),
            isFeatured = false,
        )
    }

    private fun JSONObject.toProduct(): Product = Product(
        id = optString("id"),
        storeId = optString("store_id"),
        name = optString("name_ar"),
        description = optString("description"),
        price = optDouble("price", 0.0),
        originalPrice = null,
        imageUrl = optString("image_url"),
        category = optString("category"),
        isAvailable = optBoolean("is_available", false) && optInt("stock_quantity", 0) > 0,
        preparationTimeMinutes = 15,
        calories = null,
        isPopular = false,
        options = emptyList(),
    )

    private fun JSONObject.toAddress(): Address {
        val cityName = optString("city").takeIf { it.isNotBlank() } ?: "KHARTOUM"
        val city = runCatching { SudanCity.valueOf(cityName) }.getOrDefault(SudanCity.KHARTOUM)
        return Address(
            id = optString("id"),
            title = optString("label").ifBlank { "عنوان التوصيل" },
            city = city,
            area = optString("area"),
            street = optString("street"),
            buildingNumber = optString("building_number"),
            floorOrApartment = optString("floor_or_apartment"),
            landmark = optString("landmark"),
            recipientName = optString("recipient_name"),
            recipientPhone = optString("recipient_phone"),
            isDefault = optBoolean("is_default", false),
            latitude = optDouble("latitude", 15.5898),
            longitude = optDouble("longitude", 32.5599),
        )
    }

    private fun safeValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
