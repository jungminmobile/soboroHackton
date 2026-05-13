package com.example.soboroskin.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.databinding.ItemCommunityPostBinding

class CommunityAdapter(private val posts: List<CommunityPost>) :
    RecyclerView.Adapter<CommunityAdapter.PostViewHolder>() {

    inner class PostViewHolder(private val binding: ItemCommunityPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: CommunityPost) {
            binding.tvTitle.text          = post.title
            binding.tvContentPreview.text = post.preview
            binding.tvAuthor.text         = post.author
            binding.tvDate.text           = post.timeAgo
            binding.tvCategory.text       = post.skinType
            binding.tvLikes.text          = "❤️ ${post.likes}"
            binding.tvComments.text       = "💬 ${post.comments}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemCommunityPostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) =
        holder.bind(posts[position])

    override fun getItemCount() = posts.size
}
