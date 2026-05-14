package com.example.soboroskin.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.MainActivity
import com.example.soboroskin.SkinAnalyzer
import com.example.soboroskin.databinding.FragmentScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var skinAnalyzer: SkinAnalyzer? = null
    private var imageCapture: ImageCapture? = null
    private var capturedFile: File? = null
    private var capturedBitmap: Bitmap? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionView()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        handleGalleryUri(uri)
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

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // 촬영 버튼 → 사진 찍고 미리보기 표시
        binding.btnCapture.setOnClickListener {
            takePicture()
        }

        // 다시 찍기 → 카메라로 복귀
        binding.btnRetake.setOnClickListener {
            hidePhotoPreview()
        }

        // 민감도 슬라이더 (0.02 ~ 0.08, step 0.01, 7단계)
        binding.seekbarConf.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                val conf = 0.02f + progress * 0.01f
                binding.tvConfValue.text = String.format("%.2f", conf)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })

        // 분석하기 → conf 적용 후 AI 분석 시작
        binding.btnAnalyze.setOnClickListener {
            val file = capturedFile ?: return@setOnClickListener
            val conf = 0.02f + binding.seekbarConf.progress * 0.01f
            skinAnalyzer?.confThreshold = conf
            hidePhotoPreview()
            startAnalysis(file)
        }

        binding.btnRequestPermission.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("ScanFragment", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePicture() {
        val photoFile = File(requireContext().cacheDir, "skin_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedFile = photoFile
                    showPhotoPreview(photoFile)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanFragment", "Capture failed", exception)
                    // 카메라 오류 시 바로 mock 분석
                    startAnalysis(null)
                }
            }
        ) ?: startAnalysis(null)
    }

    private fun handleGalleryUri(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                // Uri → 캐시 파일로 복사 (EXIF 보정 & 경로 필요)
                try {
                    val ins = requireContext().contentResolver.openInputStream(uri) ?: return@withContext null
                    val dest = File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(dest).use { ins.copyTo(it) }
                    ins.close()
                    dest
                } catch (t: Throwable) {
                    Log.e("ScanFragment", "gallery copy failed", t)
                    null
                }
            }
            if (file != null && _binding != null) {
                capturedFile = file
                showPhotoPreview(file)
            }
        }
    }

    private fun loadBitmapWithExifRotation(file: File): Bitmap? {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
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

    private fun showPhotoPreview(file: File) {
        val bitmap = loadBitmapWithExifRotation(file) ?: return
        capturedBitmap = bitmap
        binding.ivPreview.setImageBitmap(bitmap)
        binding.layoutPhotoPreview.visibility = View.VISIBLE
        binding.overlayFaceRegions.clear()

        // EXIF 보정된 픽셀을 파일에 다시 저장 — 이후 모든 로딩이 올바른 방향을 씀
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            } catch (_: Throwable) {}
        }

        // 백그라운드에서 얼굴 윤곽선 감지 후 매끄러운 곡선 표시
        val analyzer = skinAnalyzer ?: return
        lifecycleScope.launch {
            val contours = withContext(Dispatchers.Default) {
                try { analyzer.getFaceContours(bitmap) }
                catch (t: Throwable) { null }
            }
            if (contours != null && _binding != null) {
                binding.overlayFaceRegions.setFaceContours(contours, bitmap.width, bitmap.height)
            }
        }
    }

    private fun hidePhotoPreview() {
        binding.layoutPhotoPreview.visibility = View.GONE
        binding.ivPreview.setImageBitmap(null)
        binding.overlayFaceRegions.clear()
        capturedBitmap = null
    }

    private fun startAnalysis(file: File?) {
        showAnalyzing()
        if (file == null) {
            analyzeSkin("")
            return
        }
        val analyzer = skinAnalyzer
        val bitmap = capturedBitmap ?: loadBitmapWithExifRotation(file)
        if (analyzer == null || bitmap == null) {
            analyzeSkin(file.absolutePath)
            return
        }
        analyzeWithAI(analyzer, bitmap, file.absolutePath)
    }

    private fun analyzeWithAI(analyzer: SkinAnalyzer, bitmap: Bitmap, photoPath: String) {
        val imageW = bitmap.width
        val imageH = bitmap.height
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                try { analyzer.analyze(bitmap) }
                catch (t: Throwable) {
                    Log.e("ScanFragment", "analyze failed", t)
                    null
                }
            }

            if (result == null) {
                analyzeSkin(photoPath)
                return@launch
            }

            if (!result.faceDetected) {
                hideAnalyzing()
                android.widget.Toast.makeText(
                    requireContext(),
                    "얼굴을 감지하지 못했어요. 정면을 바라봐 주세요.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val acneCount = result.detections.size
            result.partCounts.forEach { (part, count) ->
                Log.d("SkinAnalysis", "$part: ${count}개")
            }
            analyzeSkin(photoPath, minOf(100, acneCount * 5), result, imageW, imageH)
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
        val aiComment = if (acneCount > 0)
            "트러블이 ${acneCount}개 감지됐어요. ${comments[skinType] ?: ""}"
        else
            comments[skinType] ?: ""

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
