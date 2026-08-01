package dev.rimehrab.iptvplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var focusedPosition = 0

    class VH(val row: TextView) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false) as TextView
        return VH(row)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = channels[position]
        holder.row.text = channel.name
        holder.row.isSelected = position == focusedPosition
        holder.row.setBackgroundColor(
            if (position == focusedPosition) 0x33FFFFFF else 0x00000000
        )
        holder.row.setOnClickListener { onSelect(position) }
        holder.row.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                focusedPosition = position
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount() = channels.size

    fun currentFocusedPosition() = focusedPosition

    fun setFocused(position: Int) {
        focusedPosition = position.coerceIn(0, channels.size - 1)
        notifyDataSetChanged()
    }
}
