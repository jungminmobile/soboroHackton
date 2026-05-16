package com.example.soboroskin.ui.cosmetics

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.databinding.ItemCosmeticProductBinding

class CosmeticsAdapter :
    ListAdapter<CosmeticProduct, CosmeticsAdapter.VH>(DiffCb()) {

    inner class VH(private val binding: ItemCosmeticProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: CosmeticProduct) {
            binding.tvBrand.text          = product.brand.uppercase()
            binding.tvProductName.text    = product.name
            binding.tvPrice.text          = product.price
            binding.tvCategory.text       = product.category
            binding.tvDescription.text    = product.description
            binding.tvWhyRecommended.text = "✨ ${product.whyRecommended}"

            // 카드 전체 클릭 → 올리브영 검색
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
