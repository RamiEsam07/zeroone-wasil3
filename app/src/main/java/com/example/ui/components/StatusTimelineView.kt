package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OrderStatus
import com.example.ui.theme.StatusAccepted
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusNew
import com.example.ui.theme.StatusOnTheWay
import com.example.ui.theme.StatusPreparing
import com.example.ui.theme.StatusReady
import com.example.ui.theme.WasilPrimary
import com.example.ui.theme.WasilSecondary

@Composable
fun StatusTimelineView(
    currentStatus: OrderStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_status_timeline_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with current status summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = getStatusColor(currentStatus).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = currentStatus.titleAr,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = getStatusColor(currentStatus),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exceptional state banner if cancelled / rejected
            if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.REJECTED) {
                Surface(
                    color = StatusCancelled.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = StatusCancelled,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentStatus == OrderStatus.CANCELLED)
                                "تم إلغاء هذا الطلب ولا يمكن استكماله"
                            else
                                "نعتذر، تم رفض الطلب من قِبل المتجر",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusCancelled
                        )
                    }
                }
            } else {
                // Standard timeline steps
                val steps = OrderStatus.standardTimelineSteps
                val currentIndex = steps.indexOf(currentStatus)

                val pulseTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                steps.forEachIndexed { index, step ->
                    val isPast = index < currentIndex
                    val isCurrent = index == currentIndex
                    val isFuture = index > currentIndex

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Node + Vertical Line Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(28.dp)
                        ) {
                            // Indicator Node
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        color = when {
                                            isPast -> WasilPrimary
                                            isCurrent -> WasilSecondary
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .then(if (isCurrent) Modifier.alpha(pulseAlpha) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isPast -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "تم",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    isCurrent -> {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }
                                }
                            }

                            // Connecting line if not last step
                            if (index < steps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(28.dp)
                                        .background(
                                            if (isPast) WasilPrimary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Step Label
                        Column(modifier = Modifier.padding(top = 1.dp)) {
                            Text(
                                text = step.titleAr,
                                style = if (isCurrent) {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else if (isPast) {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = WasilPrimary
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )

                            if (isCurrent) {
                                Text(
                                    text = getStepDescription(step),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = WasilSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getStatusColor(status: OrderStatus): Color {
    return when (status) {
        OrderStatus.NEW -> StatusNew
        OrderStatus.ACCEPTED -> StatusAccepted
        OrderStatus.PREPARING -> StatusPreparing
        OrderStatus.READY -> StatusReady
        OrderStatus.PICKED_UP -> StatusReady
        OrderStatus.ON_THE_WAY -> StatusOnTheWay
        OrderStatus.DELIVERED -> StatusDelivered
        OrderStatus.REJECTED, OrderStatus.CANCELLED -> StatusCancelled
    }
}

private fun getStepDescription(status: OrderStatus): String {
    return when (status) {
        OrderStatus.NEW -> "تم إرسال تفاصيل طلبك للمتجر"
        OrderStatus.ACCEPTED -> "المتجر يراجع مكونات طلبك للموافقة"
        OrderStatus.PREPARING -> "طاقم العمل يجهز وجبتك أو مشترياتك بعناية"
        OrderStatus.READY -> "الطلب مغلف وجاهز بانتظار استلام المندوب"
        OrderStatus.PICKED_UP -> "المندوب تسلم الطلب وهو في اتجاهه إليك"
        OrderStatus.ON_THE_WAY -> "المندوب يقترب من موقع التسليم المحدد"
        OrderStatus.DELIVERED -> "شكراً لطلبك مع واصل! نتمنى لك تجربة ممتعة"
        OrderStatus.REJECTED -> "تم رفض الطلب"
        OrderStatus.CANCELLED -> "تم إلغاء الطلب"
    }
}
