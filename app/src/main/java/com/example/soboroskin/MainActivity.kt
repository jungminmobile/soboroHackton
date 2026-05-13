package com.example.soboroskin

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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
        setupFab()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // 가운데 placeholder 항목 클릭 막기
        binding.bottomNav.menu.findItem(R.id.placeholder_scan)?.isEnabled = false
    }

    private fun setupFab() {
        binding.fabScan.setOnClickListener {
            if (isScanOpen) {
                closeScanOverlay()
            } else {
                openScanOverlay()
            }
        }
    }

    fun openScanOverlay() {
        isScanOpen = true

        // ScanFragment를 오버레이 컨테이너에 로드
        val scanFragment = ScanFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.scan_fragment_container, scanFragment)
            .commit()

        // 슬라이드 업 애니메이션
        binding.scanOverlayContainer.visibility = View.VISIBLE
        binding.scanOverlayContainer.translationY = binding.scanOverlayContainer.height.toFloat()
        binding.scanOverlayContainer.animate()
            .translationY(0f)
            .setDuration(350)
            .start()

        // FAB 아이콘 변경 (X로)
        binding.fabScan.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
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
            }
            .start()

        // FAB 아이콘 원래대로
        binding.fabScan.setImageResource(R.drawable.ic_scan)
    }

    fun showScanResult(
        skinType: String,
        moistureScore: Int,
        oilScore: Int,
        troubleScore: Int,
        elasticityScore: Int,
        aiComment: String,
        photoPath: String
    ) {
        val resultFragment = com.example.soboroskin.ui.scan.ScanResultFragment.newInstance(
            skinType = skinType,
            moistureScore = moistureScore,
            oilScore = oilScore,
            troubleScore = troubleScore,
            elasticityScore = elasticityScore,
            aiComment = aiComment,
            photoPath = photoPath
        )
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.scan_fragment_container, resultFragment)
            .commit()
    }

    fun onDiagnosisSaved() {
        closeScanOverlay()
        // 기록장 탭으로 이동
        binding.bottomNav.selectedItemId = R.id.diaryFragment
    }

    override fun onBackPressed() {
        if (isScanOpen) {
            closeScanOverlay()
        } else {
            super.onBackPressed()
        }
    }
}
