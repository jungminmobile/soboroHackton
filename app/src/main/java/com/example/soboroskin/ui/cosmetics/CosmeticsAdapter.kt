package com.example.soboroskin.ui.cosmetics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.databinding.ItemCosmeticProductBinding

class CosmeticsAdapter(private val products: List<CosmeticProduct>) :
    RecyclerView.Adapter<CosmeticsAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(private val binding: ItemCosmeticProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: CosmeticProduct) {
            binding.tvBrand.text       = product.brand
            binding.tvProductName.text = product.name
            binding.tvPrice.text       = product.price
            binding.tvSkinMatch.text   = product.skinMatch
            // 실제 이미지 없으므로 placeholder 사용
            binding.ivProduct.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemCosmeticProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) =
        holder.bind(products[position])

    override fun getItemCount() = products.size
}
