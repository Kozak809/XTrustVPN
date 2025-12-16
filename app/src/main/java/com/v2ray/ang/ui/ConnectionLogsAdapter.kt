package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemConnectionLogBinding
import com.v2ray.ang.dto.ConnectionLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConnectionLogsAdapter : RecyclerView.Adapter<ConnectionLogsAdapter.VH>() {

    private val items = mutableListOf<ConnectionLogEntry>()
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun submitList(newItems: List<ConnectionLogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemConnectionLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = holder.binding.root.context.getString(
            com.v2ray.ang.R.string.connection_log_title_format,
            item.serverName
        )
        holder.binding.tvSubtitle.text = holder.binding.root.context.getString(
            com.v2ray.ang.R.string.connection_log_subtitle_format,
            df.format(Date(item.timestampMillis)),
            item.serverAddress
        )
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemConnectionLogBinding) : RecyclerView.ViewHolder(binding.root)
}
