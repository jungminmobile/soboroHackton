package com.example.soboroskin.ui.diary

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AddManualRecordDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(
            android.R.layout.simple_list_item_1, null
        )

        // 간단한 다이얼로그 (실제 프로젝트에서는 커스텀 레이아웃으로 교체)
        return AlertDialog.Builder(requireContext())
            .setTitle("피부 상태 직접 기록")
            .setMessage("진단 버튼을 사용하면 AI가 자동으로 분석해드려요.\n수동 기록은 추후 업데이트됩니다.")
            .setPositiveButton("확인") { _, _ -> }
            .create()
    }
}
