package com.example.blinknpay

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val imageRes: Int,
    var quantity: Int = 1
)
