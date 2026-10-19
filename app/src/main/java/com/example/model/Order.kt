package com.example.model

enum class OrderStatus(val titleAr: String, val stepOrder: Int) {
    NEW("تم إنشاء الطلب", 1),
    ACCEPTED("المتجر قبل الطلب", 2),
    PREPARING("جاري تجهيز الطلب", 3),
    READY("الطلب جاهز للاستلام", 4),
    PICKED_UP("المندوب استلم الطلب", 5),
    ON_THE_WAY("المندوب في الطريق إليك", 6),
    DELIVERED("تم التسليم بنجاح", 7),
    REJECTED("تم رفض الطلب", -1),
    CANCELLED("تم إلغاء الطلب", -2);

    val isTerminal: Boolean
        get() = this == DELIVERED || this == REJECTED || this == CANCELLED

    val isActive: Boolean
        get() = !isTerminal

    companion object {
        val standardTimelineSteps = listOf(
            NEW,
            ACCEPTED,
            PREPARING,
            READY,
            PICKED_UP,
            ON_THE_WAY,
            DELIVERED
        )
    }
}

enum class PaymentMethod(val id: String, val titleAr: String, val subtitleAr: String) {
    CASH_ON_DELIVERY("cash", "الدفع عند الاستلام (كاش)", "ادفع نقداً عند استلام طلبك من المندوب"),
    BANKAK("bankak", "بنكك (بنك الخرطوم)", "تحويل فوري عبر تطبيق بنكك"),
    FAWRY("fawry", "فوري (بنك فيصل الإسلامي)", "دفع سريع عبر محفظة فوري")
}

data class Driver(
    val id: String = "drv_001",
    val name: String = "عثمان الفاتح",
    val phone: String = "+249 923 884 102",
    val vehicleType: String = "دراجة نارية (موتور)",
    val plateNumber: String = "خ ر ٨٤٢١",
    val rating: Double = 4.9,
    val photoUrl: String = ""
)

data class OrderItem(
    val id: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val specialInstructions: String = ""
) {
    val totalPrice: Double
        get() = unitPrice * quantity
}

data class Order(
    val id: String,
    val orderNumber: String,
    val customerId: String,
    val storeId: String,
    val storeName: String,
    val storeLogoUrl: String,
    val storePhone: String = "+249 183 456 789",
    val deliveryAddress: Address,
    val driver: Driver? = null,
    val items: List<OrderItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val serviceFee: Double = 0.0,
    val total: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH_ON_DELIVERY,
    val status: OrderStatus = OrderStatus.NEW,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val estimatedDeliveryMinutes: Int = 35,
    val customerNotes: String = "",
    val taxAmount: Double = 0.0,
    val distanceKm: Double = 0.0
)
