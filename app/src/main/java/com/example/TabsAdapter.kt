package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabsAdapter(
    private val tabs: List<BrowserTab>,
    private val activeIndex: Int,
    private val onTabClick: (Int) -> Unit,
    private val onTabClose: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardTab: View = view.findViewById(R.id.cardTab)
        val tvTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvTabUrl)
        val btnClose: ImageButton = view.findViewById(R.id.btnCloseThisTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.tvTitle.text = if (tab.title.isBlank() || tab.url == "about:blank") "New Tab" else tab.title
        holder.tvUrl.text = if (tab.url == "about:blank") "Speed Dial" else tab.url

        if (position == activeIndex) {
            holder.cardTab.setBackgroundResource(R.drawable.bg_tab_card_active)
        } else {
            holder.cardTab.setBackgroundResource(R.drawable.bg_tab_card)
        }

        holder.cardTab.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onTabClick(pos)
            }
        }

        holder.btnClose.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onTabClose(pos)
            }
        }
    }

    override fun getItemCount(): Int = tabs.size
}
