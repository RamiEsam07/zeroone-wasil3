package com.example.model

data class User(
    val id: String = "usr_001",
    val name: String = "محمد أحمد عبد الله",
    val phone: String = "+249 912 345 678",
    val email: String = "mohammed@example.com",
    val avatarUrl: String = "",
    val defaultAddressId: String = "addr_001"
)
