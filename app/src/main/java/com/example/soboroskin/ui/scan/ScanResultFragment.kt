package com.example.soboroskin.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentScanResultBinding
import kotlinx.coroutines.launch

class ScanResultFragment : Fragment() {

    private var _binding: FragmentScanResultBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_SKIN_TYPE = "skinType"
        private const val ARG_MOISTURE = "moisture"
        private const val ARG_OIL = "oil"
        private const val ARG_TROUBLE = "trouble"
        private const val ARG_ELASTICITY = "elasticity"
        private const val ARG_COMMENT = "comment"
        private const val ARG_PHOTO = "photo"
        private const val ARG_DETECTIONS = "detections"
        private const val ARG_IMAGE_W = "imageW"
        private const val ARG_IMAGE_H = "imageH"

        fun newInstance(
            skinType: String,
            moistureScore: Int,
            oilScore: Int,
            troubleScore: Int,
            elasticityScore: Int,
            aiComment: String,
            photoPath: String,
            detections: List<com.example.soboroskin.AcneDetection> = emptyList(),
            imageWidth: Int = 0,
            imageHeight: Int = 0
        ): ScanResultFragment {
            return ScanResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SKIN_TYPE, skinType)
                    putInt(ARG_MOISTURE, moistureScore)
                    putInt(ARG_OIL, oilScore)
                    putInt(ARG_TROUBLE, troubleScore)
                    putInt(ARG_ELASTICITY, elasticityScore)
                    putString(ARG_COMMENT, aiComment)
                    putString(ARG_PHOTO, photoPath)
                    putSerializable(ARG_DETECTIONS, ArrayList(detections))
                    putInt(ARG_IMAGE_W, imageWidth)
                    putInt(ARG_IMAGE_H, imageHeight)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val skinType = arguments?.getString(ARG_SKIN_TYPE) ?: "건성"
        val moisture = arguments?.getInt(ARG_MOISTURE) ?: 0
        val oil = arguments?.getInt(ARG_OIL) ?: 0
        val trouble = arguments?.getInt(ARG_TROUBLE) ?: 0
        val elasticity = arguments?.getInt(ARG_ELASTICITY) ?: 0
        val comment = arguments?.getString(ARG_COMMENT) ?: ""
        val photoPath = arguments?.getString(ARG_PHOTO) ?: ""
        @Suppress("UNCHECKED_CAST")
        val detections = (arguments?.getSerializable(ARG_DETECTIONS) as? ArrayList<com.example.soboroskin.AcneDetection>) ?: arrayListOf()
        val imageW = arguments?.getInt(ARG_IMAGE_W) ?: 0
        val imageH = arguments?.getInt(ARG_IMAGE_H) ?: 0

        bindResults(skinType, moisture, oil, trouble, elasticity, comment, photoPath, detections, imageW, imageH)

        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnSaveToDiary.setOnClickListener {
            saveToDiary(skinType, moisture, oil, trouble, elasticity, comment, photoPath)
        }

        binding.btnRetry.setOnClickListener {
            (activity as? MainActivity)?.closeScanOverlay()
            (activity as? MainActivity)?.openScanOverlay()
        }
    }

    private fun bindResults(
        skinType: String,
        moisture: Int,
        oil: Int,
        trouble: Int,
        elasticity: Int,
        comment: String,
        photoPath: String,
        detections: List<com.example.soboroskin.AcneDetection>,
        imageW: Int,
        imageH: Int
    ) {
        binding.tvSkinType.text = skinType
        binding.tvMoistureScore.text = getString(R.string.score_format, moisture)
        binding.tvOilScore.text = getString(R.string.score_format, oil)
        binding.tvTroubleScore.text = getString(R.string.score_format, trouble)
        binding.tvElasticityScore.text = getString(R.string.score_format, elasticity)
        binding.tvAiComment.text = comment

        binding.progressMoisture.progress = moisture
        binding.progressOil.progress = oil
        binding.progressTrouble.progress = trouble
        binding.progressElasticity.progress = elasticity

        // 사진 로드
        if (photoPath.isNotEmpty()) {
            Glide.with(this)
                .load(photoPath)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(binding.ivCapturedPhoto)
        }

        // 여드름 박스 오버레이
        if (detections.isNotEmpty() && imageW > 0 && imageH > 0) {
            binding.overlayView.setResults(detections, imageW, imageH)
        } else {
            binding.overlayView.clear()
        }
    }

    private fun saveToDiary(
        skinType: String,
        moisture: Int,
        oil: Int,
        trouble: Int,
        elasticity: Int,
        comment: String,
        photoPath: String
    ) {
        val entity = DiagnosisEntity(
            skinType = skinType,
            moistureScore = moisture,
            oilScore = oil,
            troubleScore = trouble,
            elasticityScore = elasticity,
            aiComment = comment,
            photoPath = photoPath,
            isManual = false
        )

        lifecycleScope.launch {
            AppDatabase.getInstance(requireContext())
                .diagnosisDao()
                .insert(entity)

            requireActivity().runOnUiThread {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.result_saved),
                    Toast.LENGTH_SHORT
                ).show()
                (activity as? MainActivity)?.onDiagnosisSaved()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
