package com.example.soboroskin.ui.scan

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.SkinMetricAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DiagnosisResultFragment : Fragment(R.layout.fragment_diagnosis_result) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoPath = arguments?.getString("photo_path") ?: ""

        // PyTorch 네이티브 라이브러리가 없을 수 있어 try-catch 처리
        val analyzer: SkinMetricAnalyzer? = try {
            SkinMetricAnalyzer(requireContext())
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w("DiagnosisResult", "PyTorch 라이브러리 없음, 더미 데이터 표시", e)
            null
        } catch (e: Throwable) {
            android.util.Log.w("DiagnosisResult", "SkinMetricAnalyzer 초기화 실패", e)
            null
        }

        if (photoPath.isNotEmpty() && File(photoPath).exists()) {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            view.findViewById<ImageView>(R.id.ivResultPhoto).setImageBitmap(bitmap)

            if (analyzer != null && bitmap != null) {
                lifecycleScope.launch {
                    val results = withContext(Dispatchers.Default) {
                        try { analyzer.analyze(bitmap) }
                        catch (t: Throwable) { null }
                    }
                    updateUI(view, results ?: getDummyResults())
                }
            } else {
                // PyTorch 없을 때 더미 데이터로 UI 채우기
                updateUI(view, getDummyResults())
            }
        } else {
            updateUI(view, getDummyResults())
        }

        view.findViewById<Button>(R.id.btnDone).setOnClickListener {
            (activity as? MainActivity)?.onDiagnosisSaved()
        }
    }

    /** PyTorch 라이브러리가 없을 때 보여줄 임시 더미 결과 */
    private fun getDummyResults(): Map<String, Float> = mapOf(
        "moisture"   to kotlin.random.Random.nextInt(45, 85).toFloat(),
        "dryness"    to kotlin.random.Random.nextInt(15, 55).toFloat(),
        "elasticity" to (kotlin.random.Random.nextInt(55, 90) / 10f),
        "pore"       to (kotlin.random.Random.nextInt(20, 70) / 10f)
    )

    // DiagnosisResultFragment.kt의 updateUI 함수 부분

    private fun updateUI(view: View, results: Map<String, Float>) {
        // 1. 수분
        val moisture = results["moisture"] ?: 0f
        view.findViewById<TextView>(R.id.txtMoistureValue).text = "${moisture.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbMoisture).progress = moisture.toInt()

        // 2. 건조함 (드디어 에러 해결!)
        val dryness = results["dryness"] ?: 0f
        view.findViewById<TextView>(R.id.txtDrynessValue).text = "${dryness.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbDryness).progress = dryness.toInt()

        // 3. 탄력
        val elasticity = results["elasticity"] ?: 0f
        view.findViewById<TextView>(R.id.txtElasticityValue).text = String.format("%.1f", elasticity)
        view.findViewById<ProgressBar>(R.id.pbElasticity).progress = (elasticity * 10).toInt()

        // 4. 모공
        val pore = results["pore"] ?: 0f
        view.findViewById<TextView>(R.id.txtPoreValue).text = String.format("%.1f", pore)
        view.findViewById<ProgressBar>(R.id.pbPore).progress = (pore * 10).toInt()
    }
}