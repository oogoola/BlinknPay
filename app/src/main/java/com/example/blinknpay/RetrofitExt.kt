package com.example.blinknpay // Ensure this matches your project structure

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Extension to simplify Retrofit calls for STKPushResponse.
 * This removes the need for 'override' keywords inside your Fragment logic.
 */
fun Call<STKPushResponse>.enqueue(
    onSuccess: (STKPushResponse) -> Unit,
    onError: (Int, String?) -> Unit,
    onFailure: (Throwable) -> Unit
) {
    // We use the standard Retrofit enqueue here
    this.enqueue(object : Callback<STKPushResponse> {

        override fun onResponse(call: Call<STKPushResponse>, response: Response<STKPushResponse>) {
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    onSuccess(body)
                } else {
                    onError(response.code(), "Empty Response Body")
                }
            } else {
                // Extracts the error message from the M-Pesa server response
                val errorJson = response.errorBody()?.string()
                onError(response.code(), errorJson)
            }
        }

        override fun onFailure(call: Call<STKPushResponse>, t: Throwable) {
            // Triggered by internet loss, timeout, or DNS failure
            onFailure(t)
        }
    })
}