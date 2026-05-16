package com.example.soboroskin.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.AcneDetection
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.databinding.FragmentScanResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanResultFragment : Fragment() {

    private var _binding: FragmentScanResultBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_SKIN_TYPE  = "skinType"
        private const val ARG_MOISTURE   = "moisture"
        private const val ARG_OIL        = "oil"
        private const val ARG_TROUBLE    = "trouble"
        private const val ARG_ELASTICITY = "elasticity"
        private const val ARG_COMMENT    = "comment"
        private const val ARG_PHOTO      = "photo"
        private const val ARG_DETECTIONS = "detections"
        private const val ARG_IMAGE_W    = "imageW"
        private const val ARG_IMAGE_H    = "imageH"

        fun newInstance(
            skinType: String,
            moistureScore: Int,
            oilScore: Int,
            troubleScore: Int,
            elasticityScore: Int,
            aiComment: String,
            photoPath: String,
            detections: List<AcneDetection> = emptyList(),
            imageWidth: Int = 0,
            imageHeight: Int = 0
        ): ScanResultFragment {
            return ScanResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SKIN_TYPE,  skinType)
                    putInt(ARG_MOISTURE,      moistureScore)
                    putInt(ARG_OIL,           oilScore)
                    putInt(ARG_TROUBLE,       troubleScore)
                    putInt(ARG_ELASTICITY,    elasticityScore)
                    putString(ARG_COMMENT,    aiComment)
                    putString(ARG_PHOTO,      photoPath)
                    putSerializable(ARG_DETECTIONS, ArrayList(detections))
                    putInt(ARG_IMAGE_W,       imageWidth)
                    putInt(ARG_IMAGE_H,       imageHeight)
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

        val skinType   = arguments?.getString(ARG_SKIN_TYPE)  ?: "건성"
        val moisture   = arguments?.getInt(ARG_MOISTURE)       ?: 0
        val oil        = arguments?.getInt(ARG_OIL)            ?: 0
        val trouble    = arguments?.getInt(ARG_TROUBLE)        ?: 0
        val elasticity = arguments?.getInt(ARG_ELASTICITY)     ?: 0
        val comment    = arguments?.getString(ARG_COMMENT)     ?: ""
        val photoPath  = arguments?.getString(ARG_PHOTO)       ?: ""

        @Suppress("UNCHECKED_CAST")
        val detections = (arguments?.getSerializable(ARG_DETECTIONS) as? ArrayList<AcneDetection>)
            ?: arrayListOf()
        val imageW = arguments?.getInt(ARG_IMAGE_W) ?: 0
        val imageH = arguments?.getInt(ARG_IMAGE_H) ?: 0

        bindResults(skinType, moisture, oil, trouble, elasticity, comment, photoPath, detections, imageW, imageH)

        // 1. 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // 🚀 2. 하단 '종합결과 확인' 버튼 클릭 시 상세 분석 페이지로 이동
        binding.btnNextDetail.setOnClickListener {
            // 사용자가 화면에서 탭하여 제외한 트러블을 반영한 최신 리스트를 확보합니다.
            val selectedDetections = if (detections.isNotEmpty() && imageW > 0 && imageH > 0)
                binding.overlayView.getSelectedDetections()
            else
                detections

            val bundle = Bundle().apply {
                putString("photo_path", photoPath)
                putString("skin_type", skinType)
                putInt("oil_score", oil)
                putInt("trouble_score", trouble)
                putString("ai_comment", comment)
                putSerializable("detections", ArrayList(selectedDetections)) // 체크된 트러블만 전달
                putInt("image_w", imageW)
                putInt("image_h", imageH)
            }
            val detailFragment = DiagnosisResultFragment().apply {
                arguments = bundle
            }

            // 오버레이 컨테이너 안에서 화면 교체
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.scan_fragment_container, detailFragment)
                .addToBackStack(null) // 뒤로가기 시 다시 결과창으로
                .commit()
        }

        // 3. 다시 찍기 버튼
        binding.btnRetry.setOnClickListener {
            (activity as? MainActivity)?.closeScanOverlay()
            (activity as? MainActivity)?.openScanOverlay()
        }
    }

    private fun bindResults(
        skinType: String, moisture: Int, oil: Int, trouble: Int, elasticity: Int,
        comment: String, photoPath: String,
        detections: List<AcneDetection>, imageW: Int, imageH: Int
    ) {
        if (photoPath.isNotEmpty()) {
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(photoPath)
                }
                if (_binding == null || bmp == null) return@launch
                binding.ivCapturedPhoto.setImageBitmap(bmp)

                binding.ivCapturedPhoto.post {
                    if (_binding == null) return@post
                    val values = FloatArray(9)
                    binding.ivCapturedPhoto.imageMatrix.getValues(values)
                    binding.overlayView.setDisplayTransform(
                        scale   = values[android.graphics.Matrix.MSCALE_X],
                        offsetX = values[android.graphics.Matrix.MTRANS_X],
                        offsetY = values[android.graphics.Matrix.MTRANS_Y]
                    )
                }
            }
        }

        if (detections.isNotEmpty() && imageW > 0 && imageH > 0) {
            binding.overlayView.setResults(detections, imageW, imageH)
            binding.layoutAcneHint.visibility = View.VISIBLE
            updateAcneCountText(detections.size, detections.size)

            binding.overlayView.onSelectionChanged = { selected, total ->
                updateAcneCountText(selected, total)
            }
        } else {
            binding.overlayView.clear()
            binding.layoutAcneHint.visibility = View.GONE
        }
    }

    private fun updateAcneCountText(selected: Int, total: Int) {
        if (_binding == null) return
        binding.tvAcneCount.text = if (selected == total)
            getString(R.string.result_acne_detected, total)
        else
            getString(R.string.result_acne_selected, selected, total)
    }

    private fun loadBitmapWithExifRotation(file: File): Bitmap? {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val flipH = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE
        val matrix = Matrix()
        if (degrees != 0f) matrix.postRotate(degrees)
        if (flipH) matrix.postScale(-1f, 1f, raw.width / 2f, raw.height / 2f)
        return if (degrees == 0f && !flipH) raw
        else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.overlayView.onSelectionChanged = null
        _binding = null
    }
}