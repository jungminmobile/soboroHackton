package com.example.soboroskin.ui.cosmetics

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.soboroskin.R
import com.example.soboroskin.databinding.ItemCosmeticProductBinding
import android.graphics.drawable.Drawable

class CosmeticsAdapter :
    ListAdapter<CosmeticProduct, CosmeticsAdapter.VH>(DiffCb()) {

    inner class VH(private val binding: ItemCosmeticProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: CosmeticProduct) {
            binding.tvBrand.text           = product.brand.uppercase()
            binding.tvProductName.text     = product.name
            binding.tvPrice.text           = product.price
            binding.tvCategory.text        = product.category
            binding.tvDescription.text     = product.description
            binding.tvWhyRecommended.text  = "✨ ${product.whyRecommended}"

            // 카테고리별 이모지
            binding.tvCategoryEmoji.text = when (product.category) {
                "토너"  -> "💧"
                "세럼"  -> "✨"
                "크림"  -> "🫙"
                "선크림" -> "☀️"
                else    -> "🧴"
            }
            binding.tvCategoryEmoji.visibility = View.VISIBLE

            // 제품 이미지 (Glide — 성공 시 이모지 숨김, 실패/빈 URL 시 이모지 표시)
            val url = product.imageUrl.trim()
            if (url.isEmpty()) {
                Glide.with(binding.root.context).clear(binding.ivProduct)
            } else {
                Glide.with(binding.root.context)
                    .load(url)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?, model: Any?,
                            target: Target<Drawable>, isFirstResource: Boolean
                        ): Boolean {
                            binding.tvCategoryEmoji.visibility = View.VISIBLE
                            return false
                        }
                        override fun onResourceReady(
                            resource: Drawable, model: Any,
                            target: Target<Drawable>?, dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.tvCategoryEmoji.visibility = View.GONE
                            return false
                        }
                    })
                    .into(binding.ivProduct)
            }

            // 카드 전체 클릭 → 올리브영 검색 (Gemini URL은 할루시네이션이라 신뢰 불가)
            binding.root.setOnClickListener {
                val query = Uri.encode("${product.brand} ${product.name}")
                val searchUrl = "https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query=$query"
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                    binding.root.context.startActivity(intent)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCosmeticProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<CosmeticProduct>() {
        override fun areItemsTheSame(a: CosmeticProduct, b: CosmeticProduct) =
            a.name == b.name && a.brand == b.brand
        override fun areContentsTheSame(a: CosmeticProduct, b: CosmeticProduct) = a == b
    }
}
