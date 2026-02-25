package com.example.blinknpay

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Data class for STK Push request
data class STKPushRequest(
    val BusinessShortCode: String,
    val Password: String,
    val Timestamp: String,
    val TransactionType: String = "CustomerPayBillOnline",
    val Amount: Double,
    val PartyA: String,
    val PartyB: String,
    val PhoneNumber: String,
    val CallBackURL: String,
    val AccountReference: String,
    val TransactionDesc: String
)

// Data class for STK Push response
data class STKPushResponse(
    val MerchantRequestID: String?,
    val CheckoutRequestID: String?,
    val ResponseCode: String?,
    val ResponseDescription: String?,
    val CustomerMessage: String?
)

// Retrofit API interface
interface DarajaApi {
    @POST("mpesa/stkpush/v1/processrequest")
    fun stkPush(
        @Header("Authorization") token: String,
        @Body request: STKPushRequest
    ): Call<STKPushResponse>
}
