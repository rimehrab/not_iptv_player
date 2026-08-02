package dev.rimehrab.terebi

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var focusedPosition = 0

    class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val number: TextView = row.findViewById(R.id.channelNumber)
        val logo: ImageView = row.findViewById(R.id.channelLogo)
        val name: TextView = row.findViewById(R.id.channelName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false) as LinearLayout
        return VH(row)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = channels[position]
        val isSelected = position == focusedPosition

        holder.number.text = (position + 1).toString()
        holder.name.text = channel.name

        if (channel.logo != null) {
            holder.logo.load(channel.logo)
        } else {
            holder.logo.setImageDrawable(null)
        }

        holder.row.setBackgroundColor(
            if (isSelected) holder.row.context.getColor(R.color.teal_highlight) else 0x00000000
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
