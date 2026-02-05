package com.example.vod

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit // Important import for the KTX fix
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var layoutForm: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 1. Init Views
        layoutForm = findViewById(R.id.layoutLoginForm)
        layoutLoading = findViewById(R.id.layoutLoading)
        etUser = findViewById(R.id.etUsername)
        etPass = findViewById(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // 2. Init Storage (SharedPreferences)
        // FIX 1: Removed redundant "Context." qualifier
        prefs = getSharedPreferences("VOD_PREFS", MODE_PRIVATE)

        // 3. CHECK FOR SAVED CREDENTIALS
        val savedUser = prefs.getString("KEY_USER", null)
        val savedPass = prefs.getString("KEY_PASS", null)

        if (savedUser != null && savedPass != null) {
            performLogin(savedUser, savedPass)
        }

        // 4. Handle Manual Login Click
        btnLogin.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(this, "Please enter details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin(user: String, pass: String) {
        layoutForm.visibility = View.GONE
        layoutLoading.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.login(user, pass)

                withContext(Dispatchers.Main) {
                    if (response.status == "success") {
                        // FIX 2: Use KTX 'edit' extension (auto-applies)
                        prefs.edit {
                            putString("KEY_USER", user)
                            putString("KEY_PASS", pass)
                        }

                        Toast.makeText(applicationContext, "Welcome back!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        showLoginError("Login Failed")
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    try {
                        val errorJson = e.response()?.errorBody()?.string()
                        val errorResponse = Gson().fromJson(errorJson, ApiResponse::class.java)
                        showLoginError(errorResponse.message ?: "Invalid Credentials")
                    } catch (_: Exception) { // FIX 3: Renamed 'ex' to '_'
                        showLoginError("Invalid Credentials")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showLoginError("Connection Error: ${e.message}")
                }
            }
        }
    }

    private fun showLoginError(msg: String) {
        // FIX 4: Use KTX 'edit' extension
        prefs.edit {
            clear()
        }

        layoutLoading.visibility = View.GONE
        layoutForm.visibility = View.VISIBLE

        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}