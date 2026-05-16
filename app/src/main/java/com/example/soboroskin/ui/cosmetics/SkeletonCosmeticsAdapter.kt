package com.example.soboroskin.ui.cosmetics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.databinding.ItemCosmeticSkeletonBinding

class SkeletonCosmeticsAdapter(private val count: Int = 4) :
    RecyclerView.Adapter<SkeletonCosmeticsAdapter.ViewHolder>() {

    class ViewHolder(binding: ItemCosmeticSkeletonBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemCosmeticSkeletonBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {}

    override fun getItemCount() = count
}
