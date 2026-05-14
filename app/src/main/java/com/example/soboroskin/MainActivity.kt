package com.example.soboroskin

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.soboroskin.databinding.ActivityMainBinding
import com.example.soboroskin.ui.scan.ScanFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private var isScanOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 진단 탭은 nav graph 목적지가 없으므로 직접 처리
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> {
                    openScanOverlay()
                    false // 선택 상태 유지 안 함
                }
                else -> {
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
    }

    fun openScanOverlay() {
        if (isScanOpen) return
        isScanOpen = true

        binding.bottomNav.visibility = View.GONE

        val scanFragment = ScanFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.scan_fragment_container, scanFragment)
            .commit()

        binding.scanOverlayContainer.visibility = View.VISIBLE
        binding.scanOverlayContainer.translationY = binding.scanOverlayContainer.height.toFloat()
        binding.scanOverlayContainer.animate()
            .translationY(0f)
            .setDuration(350)
            .start()
    }

    fun closeScanOverlay() {
        isScanOpen = false
        binding.scanOverlayContainer.animate()
            .translationY(binding.scanOverlayContainer.height.toFloat())
            .setDuration(300)
            .withEndAction {
                binding.scanOverlayContainer.visibility = View.GONE
                supportFragmentManager.findFragmentById(R.id.scan_fragment_container)?.let { frag ->
                    supportFragmentManager.beginTransaction().remove(frag).commit()
                }
                binding.bottomNav.visibility = View.VISIBLE
            }
            .start()
    }

    fun showScanResult(
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
    ) {
        val resultFragment = com.example.soboroskin.ui.scan.ScanResultFragment.newInstance(
            skinType        = skinType,
            moistureScore   = moistureScore,
            oilScore        = oilScore,
            troubleScore    = troubleScore,
            elasticityScore = elasticityScore,
            aiComment       = aiComment,
            photoPath       = photoPath,
            detections      = detections,
            imageWidth      = imageWidth,
            imageHeight     = imageHeight
        )
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.scan_fragment_container, resultFragment)
            .commit()
    }

    fun onDiagnosisSaved() {
        closeScanOverlay()
        binding.bottomNav.selectedItemId = R.id.diaryFragment
    }

    override fun onBackPressed() {
        if (isScanOpen) {
            closeScanOverlay()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
