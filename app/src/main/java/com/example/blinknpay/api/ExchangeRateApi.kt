package com.example.blinknpay.api

import com.example.blinknpay.models.ExchangeRateResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {

    /**
     * Fetches live rates using KES as the base currency.
     * End-point structure: https://v6.exchangerate-api.com/v6/YOUR_API_KEY/latest/KES
     */
    @GET("v6/{apiKey}/latest/KES")
    suspend fun getLatestKesRates(
        @Path("apiKey") apiKey: String
    ): Response<ExchangeRateResponse>
}