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

            // 🚀 백그라운드에서 AI 분석 실행
            lifecycleScope.launch {
                val results = withContext(Dispatchers.Default) {
                    analyzer.analyze(bitmap)
                }

                // UI 업데이트 (수치 반영)
                updateUI(view, results)
            }
        }

        view.findViewById<Button>(R.id.btnDone).setOnClickListener {
            (activity as? MainActivity)?.onDiagnosisSaved()
        }
    }

    private fun updateUI(view: View, results: Map<String, Float>) {
        // 수분 (0~100 가정)
        val moisture = results["moisture"] ?: 0f
        view.findViewById<TextView>(R.id.txtMoistureValue).text = "${moisture.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbMoisture).progress = moisture.toInt()

        // 유분 (0~100 가정)
        val oil = results["oil"] ?: 0f
        view.findViewById<TextView>(R.id.txtOilValue).text = "${oil.toInt()}%"
        view.findViewById<ProgressBar>(R.id.pbOil).progress = oil.toInt()

        // 탄력 (0~10.0 가정)
        val elasticity = results["elasticity"] ?: 0f
        view.findViewById<TextView>(R.id.txtElasticityValue).text = String.format("%.1f", elasticity)
        view.findViewById<ProgressBar>(R.id.pbElasticity).progress = (elasticity * 10).toInt()

        // 모공 (0~10.0 가정)
        val pore = results["pore"] ?: 0f
        view.findViewById<TextView>(R.id.txtPoreValue).text = String.format("%.1f", pore)
        view.findViewById<ProgressBar>(R.id.pbPore).progress = (pore * 10).toInt()
    }
}