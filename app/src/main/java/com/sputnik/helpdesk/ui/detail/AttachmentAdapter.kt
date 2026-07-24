package com.sputnik.helpdesk.ui.detail

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sputnik.helpdesk.data.model.Attachment

class AttachmentAdapter(private val serverUrl: String) :
    RecyclerView.Adapter<AttachmentAdapter.VH>() {

    private var items: List<Attachment> = emptyList()

    fun submitList(list: List<Attachment>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val img = ImageView(parent.context).apply {
            val dp72 = (72 * parent.context.resources.displayMetrics.density).toInt()
            layoutParams = ViewGroup.MarginLayoutParams(dp72, dp72).apply { marginEnd = 8 }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF2A2A2A.toInt())
        }
        return VH(img)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val url = if (item.url.startsWith("http")) item.url
                  else serverUrl.trimEnd('/') + item.url
        holder.image.load(url) { crossfade(true) }
        holder.image.setOnClickListener {
            it.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    override fun getItemCount() = items.size
}
