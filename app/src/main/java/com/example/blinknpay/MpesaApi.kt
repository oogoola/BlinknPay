package com.example.blinknpay

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object MpesaApi {

    private const val TAG = "MpesaApi"

    // Replace with your actual app keys (sandbox or production)
    private const val SHORTCODE = "174379"
    private const val PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
    private const val CALLBACK_URL = "https://us-central1-blinknpay.cloudfunctions.net/stkPushCallback"

    private const val CONSUMER_KEY = "4V1w6zy7LeQDGy3JiFGyfPUP30jG9rmVkH9CGhAuEudZk4Re"
    private const val CONSUMER_SECRET = "5q8P3hoxt6VG1mHgTccAR9ONUvs1dp95phnWdZH2xJhZl7K6ZRsEzdFz8EYcuwE4"

    private val client = OkHttpClient()

    fun stkPush(
        businessShortCode: String,
        accountReference: String,
        amount: String,
        phoneNumber: String,
        callback: (Boolean, String) -> Unit
    ) {
        if (phoneNumber.isEmpty()) {
            callback(false, "User phone number missing")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val password = generatePassword(businessShortCode, PASSKEY, timestamp)

        getAccessToken { token ->
            if (token == null) {
                callback(false, "Failed to get access token")
                return@getAccessToken
            }

            // Build JSON body
            val jsonBody = JSONObject().apply {
                put("BusinessShortCode", businessShortCode)
                put("Password", password)
                put("Timestamp", timestamp)
                put("TransactionType", "CustomerPayBillOnline")
                put("Amount", amount)
                put("PartyA", phoneNumber)
                put("PartyB", businessShortCode)
                put("PhoneNumber", phoneNumber)
                put("CallBackURL", CALLBACK_URL)
                put("AccountReference", accountReference)
                put("TransactionDesc", "Payment from BlinknPay")
            }

            // Fix: single RequestBody
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, jsonBody.toString())

            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest")
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "STK Push failed", e)
                    callback(false, e.message ?: "Network error")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val errorBody = it.body?.string()
                            Log.e(TAG, "STK Push error: $errorBody")
                            callback(false, errorBody ?: "STK Push failed")
                        } else {
                            val respBody = it.body?.string()
                            Log.d(TAG, "STK Push response: $respBody")
                            callback(true, "STK Push triggered successfully")
                        }
                    }
                }
            })
        }
    }

    private fun generatePassword(shortcode: String, passkey: String, timestamp: String): String {
        val str = "$shortcode$passkey$timestamp"
        return Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun getAccessToken(callback: (token: String?) -> Unit) {
        val url = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"
        val credentials = "$CONSUMER_KEY:$CONSUMER_SECRET"
        val basicAuth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", basicAuth)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Access token request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Access token response error: ${it.body?.string()}")
                        callback(null)
                    } else {
                        val json = JSONObject(it.body?.string() ?: "")
                        val token = json.optString("access_token", null)
                        callback(token)
                    }
                }
            }
        })
    }
}
