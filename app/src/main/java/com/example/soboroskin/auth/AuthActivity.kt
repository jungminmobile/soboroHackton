package com.example.soboroskin.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // 이미 로그인된 상태면 바로 메인으로
        if (FirebaseAuth.getInstance().currentUser != null) {
            goToMain()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.auth_container, LoginFragment())
                .commit()
        }
    }

    fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
