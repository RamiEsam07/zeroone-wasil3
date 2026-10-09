package com.example.api

import com.example.model.Address
import com.example.model.Driver
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.ProductOption
import com.example.model.Store
import com.example.model.StoreCategory
import com.example.model.SudanCity

object DeliverySampleData {

    val sampleAddresses = listOf(
        Address(
            id = "addr_001",
            title = "المنزل",
            city = SudanCity.KHARTOUM,
            area = "الرياض",
            street = "شارع المشتل - مربع 11",
            buildingNumber = "عمارة النخيل 4",
            floorOrApartment = "الطابق 2 - شقة 5",
            landmark = "خلف مستشفى مكة للعيون",
            recipientName = "محمد أحمد عبد الله",
            recipientPhone = "+249 912 345 678",
            isDefault = true
        ),
        Address(
            id = "addr_002",
            title = "العمل / المكتب",
            city = SudanCity.KHARTOUM,
            area = "العمارات",
            street = "شارع 15",
            buildingNumber = "برج السلام التجاري",
            floorOrApartment = "الدور الرابع - مكتب 402",
            landmark = "مقابل بنك الخرطوم",
            recipientName = "محمد أحمد",
            recipientPhone = "+249 912 345 678",
            isDefault = false
        ),
        Address(
            id = "addr_003",
            title = "بيت العائلة",
            city = SudanCity.OMDURMAN,
            area = "الملازمين",
            street = "شارع الدكاترة",
            buildingNumber = "منزل رقم 28",
            floorOrApartment = "أرضي",
            landmark = "قرب جامع الخليفة",
            recipientName = "الحاجة فاطمة",
            recipientPhone = "+249 123 998 877",
            isDefault = false
        )
    )

    val sampleStores = listOf(
        Store(
            id = "store_001",
            nameAr = "مطعم أمواج الخرطوم",
            nameEn = "Amwaj Restaurant",
            category = StoreCategory.RESTAURANTS,
            description = "أشهى المشاوي الشامية والشاورما والوجبات الشرقية المحبوبة في الخرطوم.",
            logoUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=200",
            coverUrl = "https://images.unsplash.com/photo-1544025162-d76694265947?w=800",
            rating = 4.8,
            reviewCount = 1420,
            deliveryTimeMinutes = 25,
            deliveryFee = 1500.0,
            minOrderAmount = 3000.0,
            isOpen = true,
            area = "الرياض - شارع المشتل",
            distanceKm = 1.8,
            tags = listOf("مشاوي", "شاورما", "أكلات شرقية"),
            productCategories = listOf("شاورما وساندوتشات", "مشاوي على الفحم", "أطباق شرقية", "مقبلات وسلطات"),
            isFeatured = true
        ),
        Store(
            id = "store_002",
            nameAr = "فول وطعمية البرنس",
            nameEn = "Al-Prince Foul & Falafel",
            category = StoreCategory.RESTAURANTS,
            description = "الفول السوداني المصلح بزيت السمسم والجبنة المضفرة والفلافل المقرمشة الساخنة.",
            logoUrl = "https://images.unsplash.com/photo-1541544741938-0af808871cc0?w=200",
            coverUrl = "https://images.unsplash.com/photo-1585238342024-78d387f4a707?w=800",
            rating = 4.9,
            reviewCount = 2890,
            deliveryTimeMinutes = 20,
            deliveryFee = 1200.0,
            minOrderAmount = 2000.0,
            isOpen = true,
            area = "العمارات - شارع 41",
            distanceKm = 2.1,
            tags = listOf("فول سوداني", "فطور شعبي", "طعمية"),
            productCategories = listOf("أطباق الفول", "ساندوتشات شعبية", "فلافل وطعمية", "مشروبات الصباح"),
            isFeatured = true
        ),
        Store(
            id = "store_003",
            nameAr = "حلويات ومخبوزات الشام",
            nameEn = "Al-Sham Sweets & Bakery",
            category = StoreCategory.CAFES,
            description = "كنافة نابلسية، بسبوسة، بقلاوة بالفستق وتشكيلة كيك وحلويات فاخرة.",
            logoUrl = "https://images.unsplash.com/photo-1587314168485-3236d6710814?w=200",
            coverUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800",
            rating = 4.7,
            reviewCount = 890,
            deliveryTimeMinutes = 35,
            deliveryFee = 1800.0,
            minOrderAmount = 4000.0,
            isOpen = true,
            area = "المنشية - الخرطوم",
            distanceKm = 3.5,
            tags = listOf("حلويات شرقية", "كنافة", "كيك ومناسبات"),
            productCategories = listOf("كنافة وبسبوسة", "حلويات مشكلة", "تورتات وكيك", "موالح ومعجنات"),
            isFeatured = true
        ),
        Store(
            id = "store_004",
            nameAr = "عصائر ومرطبات الواحة",
            nameEn = "Al-Waha Fresh Juices",
            category = StoreCategory.JUICES,
            description = "عصائر طبيعية طازجة: تبلدي، قونقليز، مانجو كسلا، جوافة وليمون بالنعناع.",
            logoUrl = "https://images.unsplash.com/photo-1622597467836-f3285f2131b8?w=200",
            coverUrl = "https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=800",
            rating = 4.9,
            reviewCount = 1120,
            deliveryTimeMinutes = 20,
            deliveryFee = 1000.0,
            minOrderAmount = 1500.0,
            isOpen = true,
            area = "اركويت - شارع البلابل",
            distanceKm = 2.0,
            tags = listOf("عصائر طبيعية", "تبلدي", "سموذي"),
            productCategories = listOf("عصائر سودانية أصيلة", "عصائر موسمية", "سموذي وميلك شيك", "سلطات فواكه"),
            isFeatured = false
        ),
        Store(
            id = "store_005",
            nameAr = "سوبرماركت السنابل الحديث",
            nameEn = "Sanabel Fresh Supermarket",
            category = StoreCategory.GROCERIES,
            description = "جميع المواد الغذائية، الألبان، الخضروات الطازجة ومستلزمات المنزل.",
            logoUrl = "https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=200",
            coverUrl = "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800",
            rating = 4.6,
            reviewCount = 670,
            deliveryTimeMinutes = 40,
            deliveryFee = 2000.0,
            minOrderAmount = 5000.0,
            isOpen = true,
            area = "كافوري - بحري",
            distanceKm = 4.8,
            tags = listOf("بقالة", "خضار وفواكه", "منتجات ألبان"),
            productCategories = listOf("ألبان وأجبان", "معلبات وزيوت", "خضروات وفواكه", "مشروبات ووجبات خفيفة"),
            isFeatured = false
        ),
        Store(
            id = "store_006",
            nameAr = "مخابز الأمل الذهبية",
            nameEn = "Al-Amal Bakeries",
            category = StoreCategory.BAKERIES,
            description = "رغيف بلدي ساخن، خبز فينو، كرواسون، مناقيش زعتر وجبنة عكاوي.",
            logoUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200",
            coverUrl = "https://images.unsplash.com/photo-1517433670267-08bbd4be890f?w=800",
            rating = 4.5,
            reviewCount = 430,
            deliveryTimeMinutes = 25,
            deliveryFee = 1200.0,
            minOrderAmount = 1500.0,
            isOpen = true,
            area = "أم درمان - العباسية",
            distanceKm = 3.2,
            tags = listOf("مخبوزات", "مناقيش", "خبز طازج"),
            productCategories = listOf("خبز طازج", "مناقيش وفطائر", "كرواسون ودونات", "شوابير وبسكويت"),
            isFeatured = false
        ),
        Store(
            id = "store_007",
            nameAr = "صيدلية المستقبل الكبرى",
            nameEn = "Al-Mustaqbal Pharmacy",
            category = StoreCategory.PHARMACY,
            description = "أدوية، فيتامينات، حليب أطفال، ومستحضرات العناية الشخصية الأولية.",
            logoUrl = "https://images.unsplash.com/photo-1576602976047-174e57a47881?w=200",
            coverUrl = "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800",
            rating = 4.9,
            reviewCount = 950,
            deliveryTimeMinutes = 30,
            deliveryFee = 1600.0,
            minOrderAmount = 3000.0,
            isOpen = true,
            area = "شارع الستين - الخرطوم",
            distanceKm = 2.7,
            tags = listOf("صيدلية", "أدوية", "عناية شخصية"),
            productCategories = listOf("مسكنات وأدوية خفيفة", "فيتامينات ومكملات", "عناية بالطفل", "إسعافات أولية"),
            isFeatured = false
        )
    )

    val sampleProducts = listOf(
        // Store 1: Amwaj
        Product(
            id = "prod_001",
            storeId = "store_001",
            name = "وجبة شاورما عربي دجاج سوبر",
            description = "خبز صاج مقرمش مع ثومية أصلية، بطاطس متبلة، مخلل، وصوص ثوم إضافي.",
            price = 4500.0,
            originalPrice = 5000.0,
            imageUrl = "https://images.unsplash.com/photo-1561651823-34feb02250e4?w=500",
            category = "شاورما وساندوتشات",
            isAvailable = true,
            preparationTimeMinutes = 15,
            calories = 780,
            isPopular = true,
            options = listOf(
                ProductOption("opt_1", "صوص ثوم إضافي", 300.0),
                ProductOption("opt_2", "جبنة موتزاريلا ذائبة", 600.0),
                ProductOption("opt_3", "حجم كبير جامبو", 1000.0)
            )
        ),
        Product(
            id = "prod_002",
            storeId = "store_001",
            name = "ساندوتش شاورما لحم بلدي",
            description = "لحم سوداني طازج مع طحينة وبقدونس وبصل متبل بالسماق في خبز صاج ساخن.",
            price = 3800.0,
            imageUrl = "https://images.unsplash.com/photo-1529006557810-274b9b2fc783?w=500",
            category = "شاورما وساندوتشات",
            isAvailable = true,
            preparationTimeMinutes = 12,
            calories = 620,
            isPopular = true,
            options = listOf(
                ProductOption("opt_4", "طحينة إضافية", 200.0),
                ProductOption("opt_5", "دبس رمان", 300.0)
            )
        ),
        Product(
            id = "prod_003",
            storeId = "store_001",
            name = "مشكل مشاوي أمواج (نصف كيلو)",
            description = "كباب لحم، شيش طاووق، كفتة مشوية على الفحم مع خضار مشوي وخبز حار وسلطات.",
            price = 12500.0,
            originalPrice = 14000.0,
            imageUrl = "https://images.unsplash.com/photo-1544025162-d76694265947?w=500",
            category = "مشاوي على الفحم",
            isAvailable = true,
            preparationTimeMinutes = 25,
            calories = 1150,
            isPopular = true
        ),
        Product(
            id = "prod_004",
            storeId = "store_001",
            name = "صحن حمص باللحمة المفرومة والصنوبر",
            description = "حمص ناعم مع زيت زيتون بكر ولحم مفروم بلدي مقلي مع صنوبر محمص.",
            price = 3200.0,
            imageUrl = "https://images.unsplash.com/photo-1574484284002-952d92456975?w=500",
            category = "مقبلات وسلطات",
            isAvailable = true,
            preparationTimeMinutes = 10,
            calories = 480
        ),

        // Store 2: Al-Prince Foul
        Product(
            id = "prod_005",
            storeId = "store_002",
            name = "صحن فول سوداني مصلح سوبر",
            description = "فول محلي مطبوخ على نار هادئة مع زيت سمسم بلدي، جبنة مضفرة، طماطم ودكوة.",
            price = 2200.0,
            imageUrl = "https://images.unsplash.com/photo-1585238342024-78d387f4a707?w=500",
            category = "أطباق الفول",
            isAvailable = true,
            preparationTimeMinutes = 8,
            calories = 520,
            isPopular = true,
            options = listOf(
                ProductOption("opt_6", "بيض مسلوق إضافي", 400.0),
                ProductOption("opt_7", "جبنة مضفرة زيادة", 500.0),
                ProductOption("opt_8", "شطة خضراء بالليمون", 150.0)
            )
        ),
        Product(
            id = "prod_006",
            storeId = "store_002",
            name = "ساندوتش طعمية مقرمشة بالدكوة والجبنة",
            description = "أقراص طعمية ساخنة مقلية طازجة مع صوص الدكوة وسلطة خضراء وجبنة بيضاء.",
            price = 1100.0,
            imageUrl = "https://images.unsplash.com/photo-1593560708920-61dd98c46a4e?w=500",
            category = "فلافل وطعمية",
            isAvailable = true,
            preparationTimeMinutes = 5,
            calories = 380,
            isPopular = true
        ),
        Product(
            id = "prod_007",
            storeId = "store_002",
            name = "صحن باذنجان مخلل وسلطة روب",
            description = "شرائح باذنجان متبلة بالثوم والخل مع سلطة لبن رايب بالخيار والنعناع.",
            price = 1400.0,
            imageUrl = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500",
            category = "أطباق الفول",
            isAvailable = true,
            preparationTimeMinutes = 5,
            calories = 210
        ),

        // Store 3: Al-Sham Sweets
        Product(
            id = "prod_008",
            storeId = "store_003",
            name = "صحن كنافة نابلسية بالجبنة السايحة",
            description = "كنافة خشنة ذهبية محشوة جبنة عكاوية فاخرة تسكب عليها الشيرة الساخنة.",
            price = 3900.0,
            originalPrice = 4400.0,
            imageUrl = "https://images.unsplash.com/photo-1587314168485-3236d6710814?w=500",
            category = "كنافة وبسبوسة",
            isAvailable = true,
            preparationTimeMinutes = 15,
            calories = 650,
            isPopular = true,
            options = listOf(
                ProductOption("opt_9", "فستق حلبي مدقوق زيادة", 600.0),
                ProductOption("opt_10", "قشطة بلدية طازجة", 800.0)
            )
        ),
        Product(
            id = "prod_009",
            storeId = "store_003",
            name = "كيلو بقلاوة مشكلة بالفستق والكاجو",
            description = "تشكيلة أصابع وزنود الست ومربعات البقلاوة المورقة بالسمن البلدي والمكسرات.",
            price = 15000.0,
            imageUrl = "https://images.unsplash.com/photo-1519869325930-281384150729?w=500",
            category = "حلويات مشكلة",
            isAvailable = true,
            preparationTimeMinutes = 10,
            calories = 1400,
            isPopular = true
        ),

        // Store 4: Al-Waha Juices
        Product(
            id = "prod_010",
            storeId = "store_004",
            name = "عصير تبلدي (قونقليز) مثلج أصيل (لتر)",
            description = "عصير التبلدي السوداني الطبيعي البارد المنعش بدون أي مواد حافظة.",
            price = 2200.0,
            imageUrl = "https://images.unsplash.com/photo-1622597467836-f3285f2131b8?w=500",
            category = "عصائر سودانية أصيلة",
            isAvailable = true,
            preparationTimeMinutes = 5,
            calories = 180,
            isPopular = true
        ),
        Product(
            id = "prod_011",
            storeId = "store_004",
            name = "عصير مانجو كسلا طبيعي مركز (لتر)",
            description = "مانجو تومي سودانية طازجة ومختارة بعناية، مثلجة ومركزة بدون ماء إضافي.",
            price = 3200.0,
            imageUrl = "https://images.unsplash.com/photo-1546173159-315724a31696?w=500",
            category = "عصائر موسمية",
            isAvailable = true,
            preparationTimeMinutes = 5,
            calories = 240,
            isPopular = true
        ),

        // Store 5: Sanabel Supermarket
        Product(
            id = "prod_012",
            storeId = "store_005",
            name = "زيت طعام صباح عباد الشمس (1.8 لتر)",
            description = "زيت نقي خفيف وصحي للطبخ والقلي العائلي.",
            price = 7800.0,
            imageUrl = "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=500",
            category = "معلبات وزيوت",
            isAvailable = true,
            preparationTimeMinutes = 5
        ),
        Product(
            id = "prod_013",
            storeId = "store_005",
            name = "حليب كابو بودرة (عبوة 1 كجم)",
            description = "حليب مجفف كامل الدسم سريع الذوبان ومغذي.",
            price = 11500.0,
            imageUrl = "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=500",
            category = "ألبان وأجبان",
            isAvailable = true,
            preparationTimeMinutes = 5
        )
    )

    val sampleDriver = Driver(
        id = "drv_001",
        name = "عثمان الفاتح يوسف",
        phone = "+249 923 884 102",
        vehicleType = "موتور سكوتر ياماها أسود",
        plateNumber = "خ ر ٨٤٢١",
        rating = 4.9,
        photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"
    )

    fun initialActiveOrders(): List<Order> {
        val now = System.currentTimeMillis()
        return listOf(
            Order(
                id = "ord_1001",
                orderNumber = "WAS-98214",
                customerId = "usr_001",
                storeId = "store_001",
                storeName = "مطعم أمواج الخرطوم",
                storeLogoUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=200",
                storePhone = "+249 183 456 789",
                deliveryAddress = sampleAddresses.first(),
                driver = sampleDriver,
                items = listOf(
                    OrderItem("item_1", "prod_001", "وجبة شاورما عربي دجاج سوبر", 4500.0, 2, "مع ثومية إضافية وبدون مخلل"),
                    OrderItem("item_2", "prod_004", "صحن حمص باللحمة المفرومة والصنوبر", 3200.0, 1)
                ),
                subtotal = 12200.0,
                deliveryFee = 1500.0,
                serviceFee = 250.0,
                total = 13950.0,
                paymentMethod = PaymentMethod.BANKAK,
                status = OrderStatus.ON_THE_WAY,
                createdAtMillis = now - (18 * 60 * 1000), // 18 mins ago
                estimatedDeliveryMinutes = 12,
                customerNotes = "الرجاء الاتصال قبل الصعود للطابق الثاني"
            ),
            Order(
                id = "ord_1002",
                orderNumber = "WAS-87103",
                customerId = "usr_001",
                storeId = "store_002",
                storeName = "فول وطعمية البرنس",
                storeLogoUrl = "https://images.unsplash.com/photo-1541544741938-0af808871cc0?w=200",
                storePhone = "+249 183 221 400",
                deliveryAddress = sampleAddresses[1],
                driver = sampleDriver,
                items = listOf(
                    OrderItem("item_3", "prod_005", "صحن فول سوداني مصلح سوبر", 2200.0, 3, "زيت سمسم زيادة وجبنة مضفرة"),
                    OrderItem("item_4", "prod_006", "ساندوتش طعمية مقرمشة بالدكوة والجبنة", 1100.0, 4)
                ),
                subtotal = 11000.0,
                deliveryFee = 1200.0,
                serviceFee = 250.0,
                total = 12450.0,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
                status = OrderStatus.DELIVERED,
                createdAtMillis = now - (86400000L * 2), // 2 days ago
                estimatedDeliveryMinutes = 0,
                customerNotes = "مكتب الاستقبال في الدور الرابع"
            )
        )
    }
}
