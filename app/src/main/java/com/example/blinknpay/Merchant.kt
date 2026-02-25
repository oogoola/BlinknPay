package com.example.blinknpay

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Merchant(
    // 1. Fields from your original code
    val id: String = "",
    val name: String = "",
    val amount: Int = 0,
    val rssi: Int = 0,
    val merchantId: String = "",
    val merchantName: String = "",
    val advertId: String = "",
    val bluetoothName: String = "",

    // 2. Missing fields Firestore was warning about
    val proximityId: String = "",
    val upiId: String = "",
    val isActive: Boolean = false
) : Parcelable