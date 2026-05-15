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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.MainActivity
import com.example.soboroskin.SkinAnalyzer // 또는 프로젝트 내의 분석기 클래스명 확인
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

        // 분석기 초기화
        skinAnalyzer = try {
            SkinAnalyzer(requireContext())
        } catch (t: Throwable) {
            Log.e("ScanFragment", "SkinAnalyzer init failed", t)
            null
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnClose.setOnClickListener {
            (activity as? MainActivity)?.closeScanOverlay()
        }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnCapture.setOnClickListener {
            takePicture()
        }

        binding.btnRetake.setOnClickListener {
            hidePhotoPreview()
        }

        // 민감도 설정 시 수치 표시
        binding.seekbarConf.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                val conf = 0.02f + progress * 0.01f
                binding.tvConfValue.text = String.format("%.2f", conf)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })

        // 분석하기 버튼 클릭 시
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
                    startAnalysis(null)
                }
            }
        ) ?: startAnalysis(null)
    }

    private fun handleGalleryUri(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val ins = requireContext().contentResolver.openInputStream(uri) ?: return@withContext null
                    val dest = File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(dest).use { ins.copyTo(it) }
                    ins.close()
                    dest
                } catch (t: Throwable) {
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

    private fun showPhotoPreview(file: File) {
        val bitmap = loadBitmapWithExifRotation(file) ?: return
        capturedBitmap = bitmap
        binding.ivPreview.setImageBitmap(bitmap)
        binding.layoutPhotoPreview.visibility = View.VISIBLE
        binding.overlayFaceRegions.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            } catch (_: Throwable) {}
        }

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
                catch (t: Throwable) { null }
            }

            if (result == null) {
                analyzeSkin(photoPath)
                return@launch
            }

            if (!result.faceDetected) {
                hideAnalyzing()
                Toast.makeText(requireContext(), "얼굴을 감지하지 못했어요.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            analyzeSkin(photoPath, minOf(100, result.detections.size * 5), result, imageW, imageH)
        }
    }

    private fun analyzeSkin(
        photoPath: String,
        troubleScore: Int = Random.nextInt(5, 60),
        analysisResult: com.example.soboroskin.AnalysisResult? = null,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ) {
        // AI 분석을 하지 않고 넘기는 단계 (팀원 대기 중)
        val skinTypes = listOf("건성", "지성", "복합성", "민감성", "중성")
        val skinType = skinTypes.random()
        val moisture = Random.nextInt(40, 95)
        val oil = Random.nextInt(20, 85)
        val elasticity = Random.nextInt(50, 90)

        val acneCount = analysisResult?.detections?.size ?: 0
        val aiComment = if (acneCount > 0) "트러블이 ${acneCount}개 감지됐어요." else "피부 상태가 아주 좋습니다!"

        hideAnalyzing()

        // 🚀 핵심 수정 부분: MainActivity로 형변환하여 showScanResult 호출
        (requireActivity() as MainActivity).showScanResult(
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