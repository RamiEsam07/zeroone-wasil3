package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusNew
import com.example.ui.theme.StatusOnTheWay
import com.example.ui.theme.StatusPreparing
import com.example.ui.theme.WasilPrimary
import com.example.ui.theme.WasilSecondary

@Composable
fun MyOrdersScreen(
    orders: List<Order>,
    onTrackOrderClick: (String) -> Unit,
    onOrderDetailClick: (String) -> Unit,
    onReorderClick: (Order) -> Unit,
    onExploreStoresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("الطلبات الحالية", "الطلبات السابقة")

    val activeOrders = orders.filter { it.status.isActive }
    val pastOrders = orders.filter { !it.status.isActive }

    val currentList = if (selectedTabIndex == 0) activeOrders else pastOrders

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_orders_screen")
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = WasilPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = WasilPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = if (index == 0 && activeOrders.isNotEmpty()) "$title (${activeOrders.size})" else title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                )
            }
        }

        if (currentList.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.ReceiptLong,
                title = if (selectedTabIndex == 0) "لا توجد طلبات جارية حالياً" else "لا توجد طلبات سابقة",
                description = if (selectedTabIndex == 0) "عند طلبك من أي متجر، ستتمكن من متابعة حالته مباشرة من هنا." else "سجلك خالي من الطلبات المنتهية حتى الآن.",
                actionButtonText = "تصفح المتاجر واطلب الآن",
                onActionClick = onExploreStoresClick
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(currentList) { order ->
                    OrderHistoryCard(
                        order = order,
                        onTrackClick = { onTrackOrderClick(order.id) },
                        onDetailClick = { onOrderDetailClick(order.id) },
                        onReorderClick = { onReorderClick(order) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: Order,
    onTrackClick: () -> Unit,
    onDetailClick: () -> Unit,
    onReorderClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick)
            .testTag("order_history_card_${order.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Store Name & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = WasilPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.storeName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Surface(
                    color = getOrderStatusColor(order.status).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = order.status.titleAr,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = getOrderStatusColor(order.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order Number & Items Preview
            Text(
                text = "طلب رقم: ${order.orderNumber} • ${order.items.size} أصناف",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val itemsSummary = order.items.joinToString("، ") { "${it.quantity}x ${it.productName}" }
            Text(
                text = itemsSummary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Total Price & Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "المبلغ الإجمالي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%,.0f ج.س".format(order.total),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = WasilPrimary
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (order.status.isActive) {
                        Button(
                            onClick = onTrackClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WasilPrimary),
                            modifier = Modifier.testTag("track_active_order_${order.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Moped, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "تتبع مباشر")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onReorderClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("reorder_button_${order.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "إعادة الطلب")
                        }
                    }
                }
            }
        }
    }
}

private fun getOrderStatusColor(status: OrderStatus): Color {
    return when (status) {
        OrderStatus.NEW -> StatusNew
        OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY -> StatusPreparing
        OrderStatus.PICKED_UP, OrderStatus.ON_THE_WAY -> StatusOnTheWay
        OrderStatus.DELIVERED -> StatusDelivered
        OrderStatus.REJECTED, OrderStatus.CANCELLED -> StatusCancelled
    }
}
