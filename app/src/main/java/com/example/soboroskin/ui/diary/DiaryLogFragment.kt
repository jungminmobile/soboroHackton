package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.databinding.FragmentDiaryLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DiaryLogFragment : Fragment() {

    private var _binding: FragmentDiaryLogBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DiaryLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiaryLogAdapter()
        binding.rvDiaryLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiaryLog.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .getAllEntries()
                    .collectLatest { entries ->
                        adapter.submitList(entries)
                        binding.layoutEmpty.visibility =
                            if (entries.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
