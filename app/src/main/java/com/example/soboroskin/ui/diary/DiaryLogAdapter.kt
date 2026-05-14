package com.example.soboroskin.ui.diary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.ItemDiaryLogAiBinding
import com.example.soboroskin.databinding.ItemDiarySeparatorBinding
import java.text.SimpleDateFormat
import java.util.*

// ── 리스트 아이템 타입 ──────────────────────────────────────────
sealed class DiaryListItem {
    data class Entry(val entity: DiagnosisEntity) : DiaryListItem()
    data class Separator(val label: String)        : DiaryListItem()
}

// ── 어댑터 ──────────────────────────────────────────────────────
class DiaryLogAdapter(
    private val onItemClick:     (DiagnosisEntity) -> Unit,
    private val onItemLongClick: (DiagnosisEntity) -> Unit
) : ListAdapter<DiaryListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_ENTRY     = 0
        private const val TYPE_SEPARATOR = 1
    }

    // 선택 상태
    var isSelectionMode: Boolean = false
        set(value) { field = value; notifyDataSetChanged() }

    private val selectedIds = mutableSetOf<Long>()

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
    }

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()
    fun getSelectedCount(): Int = selectedIds.size

    // ── ViewHolders ──────────────────────────────────────────────

    inner class EntryViewHolder(private val binding: ItemDiaryLogAiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entity: DiagnosisEntity) {
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            binding.tvDate.text     = sdf.format(Date(entity.date))
            binding.tvSkinType.text = entity.skinType

            binding.tvScoreMoisture.text   = "💧${entity.moistureScore}"
            binding.tvScoreOil.text        = "✨${entity.oilScore}"
            binding.tvScoreTrouble.text    = "🔴${entity.troubleScore}"
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

            // ── 선택 상태 UI ──
            val isSelected = selectedIds.contains(entity.id)
            binding.viewSelectedOverlay.visibility =
                if (isSelectionMode && isSelected) View.VISIBLE else View.INVISIBLE
            binding.tvCheckBadge.visibility =
                if (isSelectionMode && isSelected) View.VISIBLE else View.GONE

            // ── 클릭 핸들러 ──
            binding.root.setOnClickListener {
                if (isSelectionMode) onItemClick(entity)
            }
            binding.root.setOnLongClickListener {
                onItemLongClick(entity)
                true
            }
        }
    }

    inner class SeparatorViewHolder(private val binding: ItemDiarySeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(label: String) {
            binding.tvSeparatorLabel.text = label
        }
    }

    // ── Adapter overrides ────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DiaryListItem.Entry     -> TYPE_ENTRY
        is DiaryListItem.Separator -> TYPE_SEPARATOR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ENTRY -> EntryViewHolder(
                ItemDiaryLogAiBinding.inflate(inflater, parent, false)
            )
            else -> SeparatorViewHolder(
                ItemDiarySeparatorBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DiaryListItem.Entry     -> (holder as EntryViewHolder).bind(item.entity)
            is DiaryListItem.Separator -> (holder as SeparatorViewHolder).bind(item.label)
        }
    }

    // ── DiffUtil ─────────────────────────────────────────────────

    class DiffCallback : DiffUtil.ItemCallback<DiaryListItem>() {
        override fun areItemsTheSame(old: DiaryListItem, new: DiaryListItem): Boolean = when {
            old is DiaryListItem.Entry     && new is DiaryListItem.Entry     ->
                old.entity.id == new.entity.id
            old is DiaryListItem.Separator && new is DiaryListItem.Separator ->
                old.label == new.label
            else -> false
        }
        override fun areContentsTheSame(old: DiaryListItem, new: DiaryListItem) = old == new
    }
}
