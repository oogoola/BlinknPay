package com.example.blinknpay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class FluidHeaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6CAF10") // Chartreuse
        style = Paint.Style.FILL
    }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val dipHeight = h * 0.85f // Where the curve peaks down

        path.reset()
        path.lineTo(0f, h * 0.7f) // Left side height

        // This creates the fluid "stretch" toward the center
        path.cubicTo(
            w * 0.3f, h * 0.7f,  // Control point 1
            w * 0.45f, h,         // Control point 2 (pulls the curve down)
            w * 0.5f, h           // Center Point
        )
        path.cubicTo(
            w * 0.55f, h,         // Control point 3
            w * 0.7f, h * 0.7f,   // Control point 4
            w, h * 0.7f            // Right side height
        )

        path.lineTo(w, 0f)
        path.close()

        canvas.drawPath(path, paint)
    }
}