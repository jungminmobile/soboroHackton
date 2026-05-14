package com.example.soboroskin.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.databinding.FragmentScanBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.soboroskin.SkinAnalyzer

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var skinAnalyzer: SkinAnalyzer? = null

    private var imageCapture: ImageCapture? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            showPermissionView()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        skinAnalyzer = try {
            SkinAnalyzer(requireContext())
        } catch (t: Throwable) {
            Log.e("ScanFragment", "SkinAnalyzer init failed — falling back to mock", t)
            null
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnClose.setOnClickListener {
            (activity as? MainActivity)?.closeScanOverlay()
        }

        binding.btnCapture.setOnClickListener {
            captureAndAnalyze()
        }

        binding.btnRequestPermission.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        hidePermissionView()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("ScanFragment", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureAndAnalyze() {
        showAnalyzing()

        val photoFile = File(
            requireContext().cacheDir,
            "skin_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val analyzer = skinAnalyzer
                    if (analyzer == null) {
                        analyzeSkin(photoFile.absolutePath)
                        return
                    }
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    analyzeWithAI(analyzer, bitmap, photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanFragment", "Capture failed", exception)
                    analyzeSkin("")
                }
            }
        ) ?: run {
            analyzeSkin("")
        }
    }
    private fun analyzeWithAI(analyzer: SkinAnalyzer, bitmap: Bitmap, photoPath: String) {
        val imageW = bitmap.width
        val imageH = bitmap.height
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    analyzer.analyze(bitmap)
                } catch (t: Throwable) {
                    Log.e("ScanFragment", "SkinAnalyzer.analyze failed — using mock", t)
                    null
                }
            }

            if (result == null) {
                analyzeSkin(photoPath)
                return@launch
            }

            // 감지 결과 확인용 Toast
            android.widget.Toast.makeText(
                requireContext(),
                "얼굴감지:${result.faceDetected} 여드름:${result.detections.size}개",
                android.widget.Toast.LENGTH_LONG
            ).show()

            if (!result.faceDetected) {
                hideAnalyzing()
                android.widget.Toast.makeText(
                    requireContext(),
                    "얼굴을 감지하지 못했어요. 정면을 바라봐 주세요.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 여드름 개수 기반 trouble 점수 계산
            val acneCount = result.detections.size
            val troubleScore = minOf(100, acneCount * 5)

            // 부위별 결과 로그
            result.partCounts.forEach { (part, count) ->
                Log.d("SkinAnalysis", "$part: ${count}개")
            }

            analyzeSkin(photoPath, troubleScore, result, imageW, imageH)
        }
    }
    private fun analyzeSkin(
        photoPath: String,
        troubleScore: Int = Random.nextInt(5, 60),
        analysisResult: com.example.soboroskin.AnalysisResult? = null,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ) {
        val skinTypes = listOf("건성", "지성", "복합성", "민감성", "중성")
        val skinType = skinTypes.random()

        val moisture = Random.nextInt(40, 95)
        val oil = Random.nextInt(20, 85)
        val elasticity = Random.nextInt(50, 90)

        val acneCount = analysisResult?.detections?.size ?: 0
        val comments = mapOf(
            "건성" to "수분이 부족한 상태예요. 보습 크림과 수분 에센스를 충분히 사용하고, 물을 많이 마셔보세요.",
            "지성" to "유분이 많은 상태예요. 저자극 클렌저로 꼼꼼히 세안하고 가벼운 수분 젤을 사용해보세요.",
            "복합성" to "T존은 유분이, 볼은 건조함이 있어요. 부위별로 다른 케어가 효과적이에요.",
            "민감성" to "피부가 민감한 상태예요. 자극이 적은 순한 제품을 사용하고 자외선 차단에 신경 써주세요.",
            "중성" to "균형 잡힌 좋은 피부 상태예요! 지금 루틴을 유지하고 수분 공급을 꾸준히 해주세요."
        )

        val aiComment = if (acneCount > 0) {
            "여드름이 ${acneCount}개 감지됐어요. ${comments[skinType] ?: ""}"
        } else {
            comments[skinType] ?: ""
        }

        hideAnalyzing()

        (activity as? MainActivity)?.showScanResult(
            skinType = skinType,
            moistureScore = moisture,
            oilScore = oil,
            troubleScore = troubleScore,
            elasticityScore = elasticity,
            aiComment = aiComment,
            photoPath = photoPath,
            detections = analysisResult?.detections ?: emptyList(),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private fun showAnalyzing() {
        binding.layoutAnalyzing.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
    }

    private fun hideAnalyzing() {
        binding.layoutAnalyzing.visibility = View.GONE
        binding.btnCapture.isEnabled = true
    }

    private fun showPermissionView() {
        binding.layoutPermission.visibility = View.VISIBLE
        binding.cameraPreview.visibility = View.GONE
    }

    private fun hidePermissionView() {
        binding.layoutPermission.visibility = View.GONE
        binding.cameraPreview.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
