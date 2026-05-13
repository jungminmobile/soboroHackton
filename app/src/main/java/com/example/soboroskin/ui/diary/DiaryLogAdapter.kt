package com.example.soboroskin.ui.diary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soboroskin.R
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.ItemDiaryLogAiBinding
import java.text.SimpleDateFormat
import java.util.*

class DiaryLogAdapter :
    ListAdapter<DiagnosisEntity, DiaryLogAdapter.DiaryViewHolder>(DiffCallback()) {

    inner class DiaryViewHolder(private val binding: ItemDiaryLogAiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entity: DiagnosisEntity) {
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            binding.tvDate.text = sdf.format(Date(entity.date))
            binding.tvSkinType.text = entity.skinType

            binding.tvScoreMoisture.text = "💧${entity.moistureScore}"
            binding.tvScoreOil.text = "✨${entity.oilScore}"
            binding.tvScoreTrouble.text = "🔴${entity.troubleScore}"
            binding.tvScoreElasticity.text = "💜${entity.elasticityScore}"

            if (entity.notes.isNotEmpty()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = entity.notes
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            if (entity.photoPath.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(entity.photoPath)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .into(binding.ivPhoto)
            } else {
                binding.ivPhoto.setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemDiaryLogAiBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DiaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<DiagnosisEntity>() {
        override fun areItemsTheSame(old: DiagnosisEntity, new: DiagnosisEntity) =
            old.id == new.id

        override fun areContentsTheSame(old: DiagnosisEntity, new: DiagnosisEntity) =
            old == new
    }
}
