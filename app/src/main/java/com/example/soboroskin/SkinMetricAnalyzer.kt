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

    // 🚀 스크린샷에 나온 실제 파일명 리스트
    private val modelFiles = mapOf(
        "moisture"   to "reg_moisture_model.ptl",
        "dryness"    to "class_dryness_model.ptl",      // class_ 로 시작함
        "elasticity" to "reg_elasticity_R2_model.ptl", // _R2 가 붙음
        "pore"       to "reg_pore_model.ptl"
    )

    init {
        modelFiles.forEach { (key, fileName) ->
            try {
                val path = assetFilePath(context, fileName)
                models[key] = Module.load(path)
                Log.d("SkinAI", "✅ $fileName 로드 성공")
            } catch (e: Exception) {
                Log.e("SkinAI", "❌ $fileName 로드 실패: ${e.message}")
            }
        }
    }

    fun analyze(bitmap: Bitmap): Map<String, Float> {
        val results = mutableMapOf<String, Float>()
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resized,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        models.forEach { (key, model) ->
            try {
                model?.let {
                    val output = it.forward(IValue.from(inputTensor)).toTensor()
                    results[key] = output.dataAsFloatArray[0]
                }
            } catch (e: Exception) {
                results[key] = 0f
            }
        }
        return results
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        context.assets.open(assetName).use { isStream ->
            FileOutputStream(file).use { os -> isStream.copyTo(os) }
        }
        return file.absolutePath
    }
}