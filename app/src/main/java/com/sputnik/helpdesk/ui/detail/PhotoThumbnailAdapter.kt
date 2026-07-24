package com.sputnik.helpdesk.ui.detail

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sputnik.helpdesk.databinding.ItemPhotoThumbnailBinding

class PhotoThumbnailAdapter : RecyclerView.Adapter<PhotoThumbnailAdapter.VH>() {

    private val photos = mutableListOf<Uri>()

    inner class VH(val binding: ItemPhotoThumbnailBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri, onRemove: () -> Unit) {
            binding.ivThumb.load(uri) {
                size(240, 240)
                crossfade(true)
            }
            binding.btnRemovePhoto.setOnClickListener { onRemove() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemPhotoThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = photos[position]
        holder.bind(uri) {
            val idx = photos.indexOf(uri)
            if (idx >= 0) { photos.removeAt(idx); notifyItemRemoved(idx) }
        }
    }

    override fun getItemCount() = photos.size

    fun addPhotos(uris: List<Uri>) {
        val start = photos.size
        photos.addAll(uris)
        notifyItemRangeInserted(start, uris.size)
    }

    fun getPhotos(): List<Uri> = photos.toList()

    fun clearPhotos() {
        val count = photos.size
        photos.clear()
        notifyItemRangeRemoved(0, count)
    }
}
