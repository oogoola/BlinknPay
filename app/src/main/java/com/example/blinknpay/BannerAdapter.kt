package com.example.blinknpay

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.R

// ----------------------
// Banner Data Types
// ----------------------
sealed class BannerItem {
    data class Image(val resId: Int) : BannerItem()
    data class Video(val uri: Uri) : BannerItem()
}

// ----------------------
// Adapter
// ----------------------
class BannerAdapter(private val banners: List<BannerItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_IMAGE = 1
    private val TYPE_VIDEO = 2

    override fun getItemViewType(position: Int): Int = when (banners[position]) {
        is BannerItem.Image -> TYPE_IMAGE
        is BannerItem.Video -> TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_IMAGE) {
            val view = inflater.inflate(R.layout.item_banner, parent, false)
            ImageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_banner_video, parent, false)
            VideoViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = banners[position]) {
            is BannerItem.Image -> {
                (holder as ImageViewHolder).bind(item)
            }
            is BannerItem.Video -> {
                (holder as VideoViewHolder).bind(item)
            }
        }
    }

    override fun getItemCount(): Int = banners.size

    // ================================
    // Image ViewHolder
    // ================================
    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bannerImage: ImageView = view.findViewById(R.id.bannerImage)

        fun bind(item: BannerItem.Image) {
            bannerImage.setImageResource(item.resId)
        }
    }

    // ================================
    // Video ViewHolder
    // ================================
    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val videoView: VideoView = view.findViewById(R.id.bannerVideo)

        fun bind(item: BannerItem.Video) {
            try {
                videoView.setVideoURI(item.uri)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
                    videoView.start()
                }
                videoView.setOnErrorListener { _, _, _ -> true } // Ignore playback errors
            } catch (_: Exception) {
                // Safely ignore any binding errors
            }
        }

        fun stopVideo() {
            if (videoView.isPlaying) videoView.pause()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is VideoViewHolder) {
            holder.stopVideo()
        }
        super.onViewDetachedFromWindow(holder)
    }
}
