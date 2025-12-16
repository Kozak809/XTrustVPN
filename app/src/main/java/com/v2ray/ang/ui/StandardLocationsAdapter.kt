package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemStandardLocationBinding
import com.v2ray.ang.dto.StandardLocation

class StandardLocationsAdapter(
    private val onClick: (StandardLocation) -> Unit
) : RecyclerView.Adapter<StandardLocationsAdapter.VH>() {

    private val items = mutableListOf<StandardLocation>()
    private var selectedCode: String? = null

    fun submitList(newItems: List<StandardLocation>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelected(code: String?) {
        selectedCode = code
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemStandardLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvFlag.text = item.flag
        holder.binding.tvName.text = item.name
        holder.binding.tvPing.text = item.pingText

        val selected = item.code == selectedCode
        holder.binding.root.isSelected = selected
        holder.binding.root.setBackgroundResource(
            if (selected) com.v2ray.ang.R.drawable.status_pill_bg else android.R.color.transparent
        )
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemStandardLocationBinding) : RecyclerView.ViewHolder(binding.root)
}
