package com.example.blinknpay

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavDestination

/**
 * Safe navigation extension to avoid multiple rapid navigations
 */
fun NavController.safeNavigate(directions: NavDirections) {
    currentDestination?.getAction(directions.actionId)?.let {
        try {
            navigate(directions)
        } catch (e: Exception) {
            Log.e("NavController", "Navigation failed: ${e.message}")
        }
    }
}

/**
 * Safe navigation by resource ID
 */
fun NavController.safeNavigate(resId: Int) {
    currentDestination?.getAction(resId)?.let {
        try {
            navigate(resId)
        } catch (e: Exception) {
            Log.e("NavController", "Navigation failed: ${e.message}")
        }
    }
}
