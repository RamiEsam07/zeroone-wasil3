package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.Store
import com.example.model.StoreCategory
import com.example.ui.components.ProductCard
import com.example.ui.components.StoreCard
import com.example.ui.theme.WasilPrimary
import com.example.ui.theme.WasilSecondary
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    stores: List<Store>,
    activeOrder: Order?,
    selectedCategory: StoreCategory,
    onCategorySelect: (StoreCategory) -> Unit,
    onStoreClick: (String) -> Unit,
    onProductClick: (Product) -> Unit,
    onTrackOrderClick: (String) -> Unit,
    onViewAllStoresClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activePromoIndex by remember { mutableIntStateOf(0) }
    val promos = remember {
        listOf(
            Triple("كل طلباتك تصلك لباب بيتك", "من أفضل المطاعم والمتاجر القريبة منك", "توصيل سريع وموثوق ⚡"),
            Triple("جوعان؟ وصّلها ليك", "اختار وجبتك وخلي الباقي علينا", "طعمك المفضل أقرب 🍔"),
            Triple("طلبك علينا أسهل", "تصفح المتاجر واطلب بالطريقة البتناسبك", "اكتشف عروض وصّل 🧡")
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            activePromoIndex = (activePromoIndex + 1) % promos.size
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Active Order Alert Bar if exists and active
        if (activeOrder != null && activeOrder.status.isActive) {
            item {
                ActiveOrderBanner(
                    order = activeOrder,
                    onTrackClick = { onTrackOrderClick(activeOrder.id) },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Welcome heading
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "أهلاً بيك في وصّل 👋",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = WasilPrimary
                )
                Text(
                    text = "شنو نفسك نوصّل ليك اليوم؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search Bar Trigger
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Surface(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_bar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = WasilPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ابحث عن مطعم، طبق، أو بقالة في الخرطوم...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Hero Promotional Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("home_hero_banner")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_delivery_banner),
                    contentDescription = "عروض التوصيل",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay for clear Arabic text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                )

                Crossfade(targetState = activePromoIndex, label = "promo_banner_transition") { promoIndex ->
                    val promo = promos[promoIndex]
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            color = WasilSecondary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = promo.third,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = promo.first,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = promo.second,
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Categories Row
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "التصنيفات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("categories_row")
                ) {
                    items(StoreCategory.entries.toTypedArray()) { cat ->
                        CategoryChip(
                            category = cat,
                            isSelected = cat == selectedCategory,
                            onClick = { onCategorySelect(cat) }
                        )
                    }
                }
            }
        }

        // Featured Stores Header & Carousel
        item {
            val featuredStores = stores.filter { it.isFeatured }
            if (featuredStores.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المتاجر المميزة ⭐",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = onViewAllStoresClick) {
                            Text(text = "عرض الكل", color = WasilPrimary)
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(featuredStores) { store ->
                            StoreCard(
                                store = store,
                                onClick = { onStoreClick(store.id) },
                                modifier = Modifier.width(280.dp)
                            )
                        }
                    }
                }
            }
        }

        // Nearby All Stores List
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المتاجر القريبة منك",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${stores.size} متجر",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(stores) { store ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                StoreCard(
                    store = store,
                    onClick = { onStoreClick(store.id) }
                )
            }
        }
    }
}

@Composable
private fun ActiveOrderBanner(
    order: Order,
    onTrackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTrackClick)
            .testTag("active_order_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WasilPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Moped,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "طلبك قيد التنفيذ: ${order.storeName}",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color.White)
                    )
                    Text(
                        text = "${order.status.titleAr} • اضغط للمتابعة المباشرة",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f))
                    )
                }
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "تتبع",
                    color = WasilPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: StoreCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (category) {
        StoreCategory.ALL -> Icons.Default.Restaurant
        StoreCategory.RESTAURANTS -> Icons.Default.Fastfood
        StoreCategory.CAFES -> Icons.Default.LocalCafe
        StoreCategory.GROCERIES -> Icons.Default.ShoppingCart
        StoreCategory.BAKERIES -> Icons.Default.BakeryDining
        StoreCategory.JUICES -> Icons.Default.LocalDrink
        StoreCategory.PHARMACY -> Icons.Default.LocalPharmacy
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) WasilPrimary else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 0.dp else 1.dp,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier.testTag("category_chip_${category.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else WasilPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = category.titleAr,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
