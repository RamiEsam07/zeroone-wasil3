package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Address
import com.example.model.Cart
import com.example.model.CartItem
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.WasilPrimary
import com.example.ui.theme.WasilSecondary

@Composable
fun CartScreen(
    cart: Cart,
    currentAddress: Address?,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearCart: () -> Unit,
    onChangeAddressClick: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onExploreStoresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cart.isEmpty) {
        EmptyStateView(
            icon = Icons.Default.ShoppingBag,
            title = "سلتك فارغة حالياً",
            description = "تصفح أفضل المطاعم والمتاجر في السودان وأضف وجباتك ومشترياتك المفضلة",
            actionButtonText = "تصفح المتاجر الآن",
            onActionClick = onExploreStoresClick,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("cart_screen")
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Store Info Header Card
                item {
                    val store = cart.store
                    if (store != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cart_store_header"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = WasilPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = store.nameAr,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${store.area} • توصيل في ${store.deliveryTimeMinutes} دقيقة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = onClearCart,
                                    modifier = Modifier.testTag("clear_cart_button")
                                ) {
                                    Text(text = "تفريغ", color = StatusCancelled)
                                }
                            }
                        }
                    }
                }

                // Delivery Address Selection Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_delivery_address_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = WasilSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "عنوان التوصيل: ${currentAddress?.title ?: "اختر عنواناً"}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = currentAddress?.fullAddressText ?: "يرجى تحديد عنوان التوصيل",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            TextButton(onClick = onChangeAddressClick) {
                                Text(text = "تغيير", color = WasilPrimary)
                            }
                        }
                    }
                }

                // Section Title: Order Items
                item {
                    Text(
                        text = "عناصر الطلب (${cart.totalItemsCount})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Cart Items List
                items(cart.items) { item ->
                    CartItemCard(
                        item = item,
                        onQuantityChange = { q -> onQuantityChange(item.product.id, q) },
                        onRemove = { onRemoveItem(item.product.id) }
                    )
                }

                // Price Summary Bill Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_price_breakdown"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ملخص الفاتورة",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PriceRow(label = "المجموع الفرعي للأصناف", value = cart.subtotal)
                            Spacer(modifier = Modifier.height(8.dp))
                            PriceRow(label = "رسوم التوصيل المباشر", value = cart.deliveryFee)
                            Spacer(modifier = Modifier.height(8.dp))
                            PriceRow(label = "رسوم الخدمة والتشغيل", value = cart.serviceFee)

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الإجمالي النهائي",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "%,.0f ج.س".format(cart.total),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = WasilPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Sticky Checkout Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cart_bottom_bar"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onProceedToCheckout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_to_checkout_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WasilPrimary)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "متابعة إلى إتمام الطلب",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "%,.0f ج.س".format(cart.total),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_${item.product.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = item.product.imageUrl,
                    contentDescription = item.product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )

                if (item.selectedOptions.isNotEmpty()) {
                    Text(
                        text = item.selectedOptions.joinToString(" • ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.specialInstructions.isNotBlank()) {
                    Text(
                        text = "ملاحظة: ${item.specialInstructions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WasilSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "%,.0f ج.س".format(item.totalPrice),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = WasilPrimary
                    )
                )
            }

            // Stepper controls
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (item.quantity > 1) onQuantityChange(item.quantity - 1)
                            else onRemove()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (item.quantity > 1) Icons.Default.Remove else Icons.Default.DeleteOutline,
                            contentDescription = "تقليل أو حذف",
                            tint = if (item.quantity > 1) MaterialTheme.colorScheme.onSurface else StatusCancelled,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = { onQuantityChange(item.quantity + 1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "زيادة",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "%,.0f ج.س".format(value),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
