package com.example.blinknpay

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class PinIndicator {

    /**
     * Animate a dot to pulse outward when a PIN digit is entered.
     */
    fun animateDot(dot: View) {
        dot.visibility = View.VISIBLE
        dot.scaleX = 0.2f
        dot.scaleY = 0.2f
        dot.alpha = 0f

        dot.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .alpha(1f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                dot.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Reset all PIN dots to invisible (e.g., when deleting PIN).
     */
    fun resetDots(dots: List<View>) {
        dots.forEach { dot ->
            dot.visibility = View.INVISIBLE
            dot.scaleX = 1f
            dot.scaleY = 1f
            dot.alpha = 0f
        }
    }

    /**
     * Optional: Animate a dot backward (like delete).
     */
    fun removeDot(dot: View) {
        dot.animate()
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { dot.visibility = View.INVISIBLE }
            .start()
    }
}
