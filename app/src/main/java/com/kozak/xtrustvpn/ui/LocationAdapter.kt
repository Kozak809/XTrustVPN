package com.kozak.xtrustvpn.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kozak.xtrustvpn.R

data class LocationItem(
    val name: String,
    val code: String, // e.g., "Singapore" -> "SG" map needed or use name if API uses full name? 
                      // example.md says: /api/location/SG for Singapore. 
                      // example.html data-n="Singapore".
                      // I need a mapping from "Singapore" to "SG".
    val flag: String,
    val ping: String,
    val lat: Double,
    val lng: Double,
    var isSelected: Boolean = false
)

class LocationAdapter(
    private var items: List<LocationItem>,
    private val onClick: (LocationItem) -> Unit
) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

    private var filteredItems = items

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.itemContainer)
        val flag: TextView = view.findViewById(R.id.flag)
        val name: TextView = view.findViewById(R.id.name)
        val ping: TextView = view.findViewById(R.id.ping)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_new, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position]
        holder.flag.text = item.flag
        holder.name.text = item.name
        holder.ping.text = item.ping

        if (item.isSelected) {
            holder.container.setBackgroundResource(R.drawable.bg_item_selected)
            holder.ping.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success_green))
        } else {
            holder.container.setBackgroundResource(R.drawable.bg_item_normal)
            holder.ping.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_gray))
        }

        holder.itemView.setOnClickListener {
            items.forEach { it.isSelected = false }
            item.isSelected = true
            notifyDataSetChanged()
            onClick(item)
        }
    }

    override fun getItemCount(): Int = filteredItems.size

    fun filter(query: String) {
        filteredItems = if (query.isEmpty()) {
            items
        } else {
            items.filter { it.name.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
