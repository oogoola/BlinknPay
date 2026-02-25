package com.example.blinknpay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.databinding.ItemAgentCardBinding
import kotlin.math.max
import kotlin.math.min

class AgentAdapter(
    private val agents: MutableList<DiscoveredAgent> = mutableListOf(),
    private val onAgentClick: ((DiscoveredAgent) -> Unit)? = null
) : RecyclerView.Adapter<AgentAdapter.AgentViewHolder>() {

    inner class AgentViewHolder(val binding: ItemAgentCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val binding = ItemAgentCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AgentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        val agent = agents[position]
        val binding = holder.binding

        binding.txtAgentName.text = agent.name
        binding.txtNetwork.text = agent.network
        binding.txtDistance.text = "${agent.distanceMeters} m away"

        updateSignalBars(binding.signalBars, agent.signalStrength)

        holder.itemView.setOnClickListener {
            onAgentClick?.invoke(agent)
        }

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 300
        }
        holder.itemView.startAnimation(fadeIn)
    }

    override fun getItemCount(): Int = agents.size

    fun addOrUpdateAgent(agent: DiscoveredAgent) {
        val index = agents.indexOfFirst {
            it.name == agent.name && it.network == agent.network
        }

        if (index >= 0) {
            agents[index] = agent
            notifyItemChanged(index)
        } else {
            agents.add(agent)
            agents.sortBy { it.distanceMeters }
            notifyDataSetChanged()
        }
    }

    fun clearAgents() {
        val size = agents.size
        agents.clear()
        notifyItemRangeRemoved(0, size)
    }

    // -------------------------------------------------
    // Helpers (Fixed for AS 4.1.3)
    // -------------------------------------------------

    private fun updateSignalBars(container: LinearLayout, strength: Int) {
        val context = container.context ?: return
        container.removeAllViews()

        val safeStrength = max(1, min(strength, 5))

        // Pre-calculate shared values
        val marginPx = dpToPx(container, 1)
        val barWidthPx = dpToPx(container, 4)

        for (i in 1..5) {
            // 1. Explicitly create the bar view
            val bar = View(context)

            // 2. Calculate dynamic height
            val barHeightPx = dpToPx(container, 3 * (i + 1))

            // 3. Create and configure LayoutParams
            val params = LinearLayout.LayoutParams(barWidthPx, barHeightPx)
            params.setMargins(marginPx, 0, marginPx, 0)

            // 4. Assign params and style the bar
            bar.layoutParams = params

            val colorStr = if (i <= safeStrength) "#00E676" else "#40FFFFFF"
            bar.setBackgroundColor(Color.parseColor(colorStr))

            // 5. Add to container
            container.addView(bar)
        }
    }

    private fun dpToPx(view: View, dp: Int): Int {
        val density = view.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}