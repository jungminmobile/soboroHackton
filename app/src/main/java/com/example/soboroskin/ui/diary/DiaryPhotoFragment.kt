package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentDiaryPhotoBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DiaryPhotoFragment : Fragment() {

    private var _binding: FragmentDiaryPhotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PhotoGridAdapter()
        binding.rvDiaryPhotos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvDiaryPhotos.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getInstance(requireContext())
                .diagnosisDao()
                .getAiDiagnoses()
                .collectLatest { entries ->
                    val withPhotos = entries.filter { it.photoPath.isNotEmpty() }
                    adapter.submitList(withPhotos)
                    binding.layoutEmptyPhoto.visibility =
                        if (withPhotos.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PhotoGridAdapter :
    androidx.recyclerview.widget.ListAdapter<DiagnosisEntity, PhotoGridAdapter.PhotoViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<DiagnosisEntity>() {
            override fun areItemsTheSame(a: DiagnosisEntity, b: DiagnosisEntity) = a.id == b.id
            override fun areContentsTheSame(a: DiagnosisEntity, b: DiagnosisEntity) = a == b
        }
    ) {

    inner class PhotoViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val size = parent.width / 3
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(2, 2, 2, 2)
        }
        return PhotoViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val entity = getItem(position)
        Glide.with(holder.imageView.context)
            .load(entity.photoPath)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.imageView)
    }
}
