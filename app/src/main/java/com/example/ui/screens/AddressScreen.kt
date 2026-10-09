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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Address
import com.example.model.SudanCity
import com.example.ui.theme.WasilPrimary
import com.example.ui.theme.WasilSecondary
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(
    addresses: List<Address>,
    selectedAddress: Address?,
    onAddressSelect: (Address) -> Unit,
    onAddNewAddress: (Address) -> Unit,
    isFromCheckout: Boolean = false,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("address_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "عناوين التوصيل المحفوظة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WasilPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_new_address_top_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "إضافة عنوان جديد")
                    }
                }
            }

            items(addresses) { addr ->
                val isSelected = selectedAddress?.id == addr.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddressSelect(addr) }
                        .testTag("address_card_${addr.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) WasilPrimary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(WasilPrimary), width = 1.5.dp) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) WasilPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = addr.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (addr.isDefault) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = WasilPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "افتراضي",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WasilPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = addr.fullAddressText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (addr.buildingNumber.isNotBlank() || addr.floorOrApartment.isNotBlank()) {
                                Text(
                                    text = "${addr.buildingNumber} - ${addr.floorOrApartment}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "المستلم: ${addr.recipientName} • ${addr.recipientPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Add Address Bottom Sheet Dialog
        if (showAddDialog) {
            AddAddressModalBottomSheet(
                onDismiss = { showAddDialog = false },
                onSave = { newAddr ->
                    onAddNewAddress(newAddr)
                    showAddDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAddressModalBottomSheet(
    onDismiss: () -> Unit,
    onSave: (Address) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("المنزل") }
    var selectedCity by remember { mutableStateOf(SudanCity.KHARTOUM) }
    var cityExpanded by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("محمد أحمد") }
    var recipientPhone by remember { mutableStateOf("+249 912 345 678") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("add_address_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إضافة عنوان جديد للتوصيل",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إلغاء")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title Field (المنزل، العمل، إلخ)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("اسم العنوان (مثل: المنزل، المكتب، الشقة)") },
                modifier = Modifier.fillMaxWidth().testTag("input_address_title"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // City Picker
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCity.titleAr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("المدينة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp)
                )

                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false }
                ) {
                    SudanCity.entries.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city.titleAr) },
                            onClick = {
                                selectedCity = city
                                cityExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Area Field (الحي / المنطقة)
            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text("الحي أو المنطقة (مثال: الرياض، العمارات، كافوري)") },
                modifier = Modifier.fillMaxWidth().testTag("input_address_area"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Street Field
            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("الشارع أو المربع (مثال: شارع المشتل - مربع 11)") },
                modifier = Modifier.fillMaxWidth().testTag("input_address_street"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Building & Floor Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = building,
                    onValueChange = { building = it },
                    label = { Text("رقم المبنى") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = floor,
                    onValueChange = { floor = it },
                    label = { Text("الدور / الشقة") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Landmark Field (معلم بارز)
            OutlinedTextField(
                value = landmark,
                onValueChange = { landmark = it },
                label = { Text("معلم بارز قريب (مثال: خلف مسجد النور، قرب المستشفى)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recipient Phone
            OutlinedTextField(
                value = recipientPhone,
                onValueChange = { recipientPhone = it },
                label = { Text("رقم هاتف المستلم") },
                modifier = Modifier.fillMaxWidth().testTag("input_recipient_phone"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val newAddr = Address(
                        id = "addr_${UUID.randomUUID().toString().take(6)}",
                        title = title.ifBlank { "عنوان جديد" },
                        city = selectedCity,
                        area = area.ifBlank { "الرياض" },
                        street = street.ifBlank { "الشارع الرئيسي" },
                        buildingNumber = building,
                        floorOrApartment = floor,
                        landmark = landmark,
                        recipientName = recipientName,
                        recipientPhone = recipientPhone,
                        isDefault = false
                    )
                    onSave(newAddr)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_new_address_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WasilPrimary)
            ) {
                Text(
                    text = "حفظ العنوان واعتماده",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
