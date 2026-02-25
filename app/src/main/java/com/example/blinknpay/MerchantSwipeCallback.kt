package com.example.blinknpay.ui.merchant

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.R
import com.example.blinknpay.Receiver  // <- Use Receiver here

class MerchantSwipeCallback(
    private val context: Context,
    private val merchants: MutableList<Receiver>, // <-- changed from Merchant
    private val adapter: RecyclerView.Adapter<*>,
    private val listener: SwipeActionListener
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    interface SwipeActionListener {
        fun onSwipeRight(position: Int)
        fun onSwipeLeft(position: Int)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return

        val merchant = merchants[position]

        when (direction) {
            ItemTouchHelper.RIGHT -> listener.onSwipeRight(position)

            ItemTouchHelper.LEFT -> {
                listener.onSwipeLeft(position)

                // Move swiped merchant to the end
                merchants.removeAt(position)
                merchants.add(merchant)

                // Notify adapter to update
                adapter.notifyItemMoved(position, merchants.size - 1)
            }
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val iconSize = 64
        val iconMargin = (itemView.height - iconSize) / 2

        if (dX > 0) {
            paint.color = Color.parseColor("#0A7C3E")
            c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat(), paint)

            getIcon(R.drawable.ic_payy)?.let { icon ->
                val iconTop = itemView.top + iconMargin
                val iconLeft = itemView.left + iconMargin
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                icon.alpha = (255 * (dX / itemView.width)).toInt().coerceIn(0, 255)
                icon.draw(c)
            }

        } else if (dX < 0) {
            paint.color = Color.parseColor("#1E3A8A")
            c.drawRect(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), paint)

            getIcon(R.drawable.ic_catalog)?.let { icon ->
                val iconTop = itemView.top + iconMargin
                val iconRight = itemView.right - iconMargin
                icon.setBounds(iconRight - iconSize, iconTop, iconRight, iconTop + iconSize)
                icon.alpha = (255 * (-dX / itemView.width)).toInt().coerceIn(0, 255)
                icon.draw(c)
            }
        }

        super.onChildDraw(
            c,
            recyclerView,
            viewHolder,
            dX.coerceIn(-itemView.width.toFloat(), itemView.width.toFloat()),
            dY,
            actionState,
            isCurrentlyActive
        )
    }

    private fun getIcon(resId: Int) = ContextCompat.getDrawable(context, resId)
}
