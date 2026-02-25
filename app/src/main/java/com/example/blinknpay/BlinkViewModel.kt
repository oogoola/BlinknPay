package com.example.blinknpay

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BlinkViewModel : ViewModel() {

    // --- Financial Balances ---
    private val _totalBalance = MutableLiveData<String>().apply { value = "KES 0.00" }
    val totalBalance: LiveData<String> = _totalBalance

    private val _exchangeBalance = MutableLiveData<String>().apply { value = "KES 0.00" }
    val exchangeBalance: LiveData<String> = _exchangeBalance

    private val _chamaBalance = MutableLiveData<String>().apply { value = "KES 0.00" }
    val chamaBalance: LiveData<String> = _chamaBalance

    // --- Live Exchange Rates (For the Horizontal Flag Banner) ---
    private val _usdRate = MutableLiveData<String>().apply { value = "128.50" }
    val usdRate: LiveData<String> = _usdRate

    private val _gbpRate = MutableLiveData<String>().apply { value = "162.20" }
    val gbpRate: LiveData<String> = _gbpRate

    private val _rpidStatus = MutableLiveData<String>().apply { value = "Active" }
    val rpidStatus: LiveData<String> = _rpidStatus

    /**
     * Sources bank balances for the Unified Payment Interface.
     * In a real scenario, this calls your quad-channel engine handshakes.
     */
    fun sourceAllBalances() {
        // Simulated data sourcing from linked UPI bank accounts
        val exchange = 1500.00
        val chama = 3920.00
        val walletMain = 5000.00

        _exchangeBalance.value = "KES %.2f".format(exchange)
        _chamaBalance.value = "KES %.2f".format(chama)
        _totalBalance.value = "KES %.2f".format(walletMain + exchange + chama)
    }

    /**
     * Updates exchange rates. This can be triggered when the
     * WiFi SSID or BLE channel detects a local liquidity provider.
     */
    fun refreshExchangeRates() {
        // These would be fetched from your Exchange service backend
        _usdRate.value = "129.10"
        _gbpRate.value = "163.45"
        _rpidStatus.value = "Broadcasting"
    }
}