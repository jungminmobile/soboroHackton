package com.example.soboroskin

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream

class SkinMetricAnalyzer(private val context: Context) {
    private val models = mutableMapOf<String, Module?>()
    private val metricNames = listOf("moisture", "oil", "elasticity", "pore")

    init {
        // 🚀 초기화 시 4개 모델을 미리 로드
        metricNames.forEach { name ->
            try {
                val path = assetFilePath(context, "reg_${name}_model.ptl")
                models[name] = Module.load(path)
                Log.d("SkinAI", "✅ $name 모델 로드 완료")
            } catch (e: Exception) {
                Log.e("SkinAI", "❌ $name 모델 로드 실패: ${e.message}")
            }
        }
    }

    fun analyze(bitmap: Bitmap): Map<String, Float> {
        val results = mutableMapOf<String, Float>()

        // 1. 전처리: 모델이 학습된 224x224 크기로 이미지 리사이징
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        // 2. 텐서(Tensor) 변환: 이미지를 AI가 이해하는 숫자 배열로 변환
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resized,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        // 3. 각 모델 실행 (Inference)
        models.forEach { (name, model) ->
            try {
                model?.let {
                    val output = it.forward(IValue.from(inputTensor)).toTensor()
                    results[name] = output.dataAsFloatArray[0]
                }
            } catch (e: Exception) {
                Log.e("SkinAI", "$name 분석 중 오류: ${e.message}")
                results[name] = 0f
            }
        }
        return results
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }
}