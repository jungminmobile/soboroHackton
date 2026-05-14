package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentDiaryLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class DiaryLogFragment : Fragment() {

    private var _binding: FragmentDiaryLogBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DiaryLogAdapter
    private var allEntries: List<DiagnosisEntity> = emptyList()

    private val separatorThresholds = listOf(
        7   to "1주 전",
        21  to "3주 전",
        30  to "1달 전",
        90  to "3달 전",
        365 to "1년 전"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiaryLogAdapter(
            onItemClick     = { entity -> handleEntryClick(entity) },
            onItemLongClick = { entity -> enterSelectionMode(entity) }
        )

        binding.rvDiaryLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiaryLog.adapter = adapter

        // 전체 삭제
        binding.btnDeleteAll.setOnClickListener { showDeleteAllConfirmDialog() }

        // 선택 취소
        binding.btnCancelSelection.setOnClickListener { exitSelectionMode() }

        // 선택 삭제
        binding.btnDeleteSelected.setOnClickListener { showDeleteSelectedConfirmDialog() }

        // 데이터 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .getAllEntries()
                    .collectLatest { entries ->
                        allEntries = entries
                        adapter.submitList(buildListWithSeparators(entries))

                        val isEmpty = entries.isEmpty()
                        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
                        binding.btnDeleteAll.visibility = if (isEmpty) View.INVISIBLE else View.VISIBLE

                        // 삭제 후 선택 모드 자동 해제
                        if (isEmpty) exitSelectionMode()
                    }
            }
        }
    }

    // ─── 선택 모드 ───────────────────────────────────────────────

    private fun enterSelectionMode(entity: DiagnosisEntity) {
        if (!adapter.isSelectionMode) {
            adapter.isSelectionMode = true
            adapter.toggleSelection(entity.id)
            updateSelectionBar()
            binding.layoutSelectionBar.visibility = View.VISIBLE
            binding.layoutHeader.visibility       = View.GONE
        }
    }

    private fun handleEntryClick(entity: DiagnosisEntity) {
        if (adapter.isSelectionMode) {
            adapter.toggleSelection(entity.id)
            updateSelectionBar()
        }
    }

    private fun exitSelectionMode() {
        adapter.clearSelection()
        binding.layoutSelectionBar.visibility = View.GONE
        binding.layoutHeader.visibility       = View.VISIBLE
    }

    private fun updateSelectionBar() {
        val count = adapter.getSelectedCount()
        binding.tvSelectedCount.text = "${count}개 선택됨"
        // 0개면 삭제 버튼 비활성화
        binding.btnDeleteSelected.alpha = if (count > 0) 1f else 0.4f
        binding.btnDeleteSelected.isEnabled = count > 0
    }

    // ─── 선택 삭제 ───────────────────────────────────────────────

    private fun showDeleteSelectedConfirmDialog() {
        val count = adapter.getSelectedCount()
        if (count == 0) return
        AlertDialog.Builder(requireContext())
            .setTitle("선택 삭제")
            .setMessage("선택한 ${count}개의 기록을 삭제할까요?\n관련 여드름 기록도 함께 정리됩니다.")
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton("삭제") { _, _ -> deleteSelectedLogs() }
            .show()
    }

    private fun deleteSelectedLogs() {
        val ids = adapter.getSelectedIds().toList()
        if (ids.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(requireContext())

                // 사진 파일 삭제
                val toDelete = allEntries.filter { it.id in ids }
                for (d in toDelete) {
                    if (d.photoPath.isNotEmpty()) {
                        try { File(d.photoPath).delete() } catch (_: Exception) {}
                    }
                }

                // DB 삭제: 연결된 acne_spot_records 삭제 → 고아 acne_spots 정리 → diagnoses 삭제
                db.acneSpotRecordDao().deleteByDiagnosisIds(ids)
                db.acneSpotDao().deleteOrphaned()
                db.diagnosisDao().deleteByIds(ids)
            }

            exitSelectionMode()
            Toast.makeText(requireContext(), "삭제되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 전체 삭제 ───────────────────────────────────────────────

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.diary_delete_all_title))
            .setMessage(getString(R.string.diary_delete_all_msg))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.diary_delete_all)) { _, _ -> deleteAllLogs() }
            .show()
    }

    private fun deleteAllLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(requireContext())
                for (d in allEntries) {
                    if (d.photoPath.isNotEmpty()) {
                        try { File(d.photoPath).delete() } catch (_: Exception) {}
                    }
                }
                db.acneSpotRecordDao().deleteAll()
                db.acneSpotDao().deleteAll()
                db.diagnosisDao().deleteAll()
            }
            Toast.makeText(requireContext(), getString(R.string.diary_delete_all_done), Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 구분선 삽입 ─────────────────────────────────────────────

    private fun buildListWithSeparators(entries: List<DiagnosisEntity>): List<DiaryListItem> {
        if (entries.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val result = mutableListOf<DiaryListItem>()
        val insertedDays = mutableSetOf<Int>()

        for (entry in entries) {
            val daysAgo = TimeUnit.MILLISECONDS.toDays(now - entry.date).toInt()
            for ((days, label) in separatorThresholds) {
                if (daysAgo >= days && days !in insertedDays) {
                    result.add(DiaryListItem.Separator(label))
                    insertedDays.add(days)
                }
            }
            result.add(DiaryListItem.Entry(entry))
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
