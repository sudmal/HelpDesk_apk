package com.sputnik.helpdesk.ui.detail

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.model.Comment
import com.sputnik.helpdesk.databinding.ItemCommentBinding

class CommentAdapter(private val serverUrl: String = "") :
    ListAdapter<Comment, CommentAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        holder.binding.tvAuthor.text = c.author ?: "Неизвестно"
        holder.binding.tvBody.text   = c.body
        holder.binding.tvDate.text   = runCatching { c.created_at.substring(0, 16).replace("T", " ") }.getOrDefault("")

        val rv = holder.binding.recyclerCommentAttachments
        if (c.attachments.isEmpty()) {
            rv.visibility = View.GONE
        } else {
            rv.visibility = View.VISIBLE
            if (rv.adapter == null) {
                rv.layoutManager = LinearLayoutManager(rv.context, LinearLayoutManager.HORIZONTAL, false)
                rv.adapter = AttachmentAdapter(serverUrl)
            }
            (rv.adapter as AttachmentAdapter).submitList(c.attachments)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(a: Comment, b: Comment) = a.id == b.id
            override fun areContentsTheSame(a: Comment, b: Comment) = a == b
        }
    }
}
