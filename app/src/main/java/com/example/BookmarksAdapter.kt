package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookmarksAdapter(
    private val bookmarks: List<Pair<String, String>>,
    private val onBookmarkClick: (String) -> Unit,
    private val onBookmarkDelete: (String) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvBookmarkTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvBookmarkUrl)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteBookmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = bookmarks[position]
        holder.tvTitle.text = if (item.first.isBlank()) item.second else item.first
        holder.tvUrl.text = item.second

        holder.itemView.setOnClickListener {
            onBookmarkClick(item.second)
        }

        holder.btnDelete.setOnClickListener {
            onBookmarkDelete(item.second)
        }
    }

    override fun getItemCount(): Int = bookmarks.size
}
