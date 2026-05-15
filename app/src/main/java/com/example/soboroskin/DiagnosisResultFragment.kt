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
        val analyzer = SkinMetricAnalyzer(requireContext())

        if (photoPath.isNotEmpty() && File(photoPath).exists()) {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            view.findViewById<ImageView>(R.id.ivResultPhoto).setImageBitmap(bitmap)

            lifecycleScope.launch {
                val results = withContext(Dispatchers.Default) {
                    analyzer.analyze(bitmap)
                }

                // 🚀 Analyzer에서 정한 key값으로 데이터 출력
                updateUI(view, results)
            }
        }

        view.findViewById<Button>(R.id.btnDone).setOnClickListener {
            (activity as? MainActivity)?.onDiagnosisSaved()
        }
    }

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