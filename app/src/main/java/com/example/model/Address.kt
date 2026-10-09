package com.example.model

enum class SudanCity(val titleAr: String) {
    KHARTOUM("الخرطوم"),
    OMDURMAN("أم درمان"),
    BAHRI("بحري - الخرطوم بحري"),
    PORT_SUDAN("بورتسودان"),
    WAD_MADANI("ود مدني")
}

data class Address(
    val id: String,
    val title: String, // e.g. "المنزل", "العمل", "منزل العائلة"
    val city: SudanCity,
    val area: String, // e.g. "حي الرياض - شارع المشتل", "الملازمين", "كافوري"
    val street: String,
    val buildingNumber: String,
    val floorOrApartment: String = "",
    val landmark: String = "",
    val recipientName: String,
    val recipientPhone: String,
    val isDefault: Boolean = false,
    val latitude: Double = 15.5898,
    val longitude: Double = 32.5599
) {
    val fullAddressText: String
        get() = "${city.titleAr}، $area، شارع $street" + (if (landmark.isNotBlank()) " (بالقرب من $landmark)" else "")
}
