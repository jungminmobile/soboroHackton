package com.example.soboroskin.ui.cosmetics

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.soboroskin.R
import com.example.soboroskin.databinding.ItemCosmeticProductBinding

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

            // 제품 이미지 (Glide — 실패 시 placeholder)
            Glide.with(binding.root.context)
                .load(product.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_cosmetic_placeholder)
                .error(R.drawable.bg_cosmetic_placeholder)
                .centerCrop()
                .into(binding.ivProduct)

            // 카드 전체 클릭 → 공식 사이트
            binding.root.setOnClickListener {
                val url = product.officialUrl.ifEmpty {
                    "https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query=${product.name}"
                }
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
