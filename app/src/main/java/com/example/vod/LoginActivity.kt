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
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.vod.utils.Constants
import com.example.vod.utils.ErrorHandler
import com.example.vod.utils.NetworkUtils
import com.example.vod.utils.OrientationUtils
import com.example.vod.utils.SecurePrefs
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.lang.ref.WeakReference

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var layoutForm: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationUtils.applyPreferredOrientation(this)
        setContentView(R.layout.activity_login)

        // 1. Init Views
        layoutForm = findViewById(R.id.layoutLoginForm)
        layoutLoading = findViewById(R.id.layoutLoading)
        etUser = findViewById(R.id.etUsername)
        etPass = findViewById(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // 2. Init secure storage with crash-safe fallback.
        prefs = SecurePrefs.get(this, Constants.PREFS_NAME)

        // 3. CHECK FOR SAVED CREDENTIALS
        val savedUser = prefs.getString(Constants.KEY_USER, null)
        val savedPass = prefs.getString(Constants.KEY_PASS, null)
        val savedCsrfToken = prefs.getString(Constants.KEY_CSRF_TOKEN, null)

        NetworkClient.updateCsrfToken(savedCsrfToken)

        if (savedUser != null && savedPass != null) {
            performLogin(savedUser, savedPass)
        }

        // 4. Handle Manual Login Click
        btnLogin.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            
            if (validateInput(user, pass)) {
                performLogin(user, pass)
            }
        }
    }

    /**
     * Validate username and password input.
     * @return true if valid, false otherwise
     */
    private fun validateInput(user: String, pass: String): Boolean {
        // Clear previous errors
        etUser.error = null
        etPass.error = null

        return when {
            user.isEmpty() -> {
                etUser.error = "Username is required"
                etUser.requestFocus()
                false
            }
            user.length < Constants.MIN_USERNAME_LENGTH -> {
                etUser.error = "Username must be at least ${Constants.MIN_USERNAME_LENGTH} characters"
                etUser.requestFocus()
                false
            }
            pass.isEmpty() -> {
                etPass.error = "Password is required"
                etPass.requestFocus()
                false
            }
            pass.length < Constants.MIN_PASSWORD_LENGTH -> {
                etPass.error = "Password must be at least ${Constants.MIN_PASSWORD_LENGTH} characters"
                etPass.requestFocus()
                false
            }
            else -> true
        }
    }

    private fun performLogin(user: String, pass: String) {
        // Check network connectivity before attempting login
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        layoutForm.visibility = View.GONE
        layoutLoading.visibility = View.VISIBLE

        // Use WeakReference to prevent activity leak
        val weakActivity = WeakReference(this)

        // Use lifecycleScope for automatic cancellation on activity destruction
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.api.login(
                    user = user,
                    pass = pass,
                    appVersionName = BuildConfig.VERSION_NAME.ifBlank { "unknown" },
                    appVersionCode = BuildConfig.VERSION_CODE
                )

                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    
                    if (response.status == "success") {
                        val csrfToken = response.csrfToken?.takeIf { it.isNotBlank() }
                        val accountExpiry = response.accountExpiry?.takeIf { it.isNotBlank() }
                            ?: response.user?.expiryDate?.takeIf { it.isNotBlank() }
                        val updateFeedUrl = response.updateFeedUrl?.takeIf { it.isNotBlank() }
                        NetworkClient.updateCsrfToken(csrfToken)

                        prefs.edit {
                            putString(Constants.KEY_USER, user)
                            putString(Constants.KEY_PASS, pass)
                            if (csrfToken != null) {
                                putString(Constants.KEY_CSRF_TOKEN, csrfToken)
                            } else {
                                remove(Constants.KEY_CSRF_TOKEN)
                            }
                            if (accountExpiry != null) {
                                putString(Constants.KEY_ACCOUNT_EXPIRY, accountExpiry)
                            } else {
                                remove(Constants.KEY_ACCOUNT_EXPIRY)
                            }
                            if (updateFeedUrl != null) {
                                putString(Constants.KEY_UPDATE_FEED_URL, updateFeedUrl)
                            } else {
                                remove(Constants.KEY_UPDATE_FEED_URL)
                            }
                        }

                        Toast.makeText(activity.applicationContext, "Welcome back!", Toast.LENGTH_SHORT).show()
                        // Navigate to profile selection after login
                        activity.startActivity(Intent(activity, ProfileSelectionActivity::class.java))
                        activity.finish()
                    } else {
                        activity.showLoginError("Login Failed", clearSavedCredentials = true)
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    val activity = weakActivity.get() ?: return@withContext
                    val clearSavedCredentials = e.code() == 401 || e.code() == 403
                    try {
                        val errorJson = e.response()?.errorBody()?.string()
                        val errorResponse = Gson().fromJson(errorJson, ApiResponse::class.java)
                        activity.showLoginError(
                            errorResponse.message ?: "Invalid Credentials",
                            clearSavedCredentials = clearSavedCredentials
                        )
                    } catch (_: Exception) {
                        activity.showLoginError(
                            "Invalid Credentials",
                            clearSavedCredentials = clearSavedCredentials
                        )
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e)
                        activity.showLoginError(null, clearSavedCredentials = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weakActivity.get()?.let { activity ->
                        ErrorHandler.handleNetworkError(activity, e, "Connection Error")
                        activity.showLoginError(null, clearSavedCredentials = false)
                    }
                }
            }
        }
    }

    private fun showLoginError(msg: String?, clearSavedCredentials: Boolean) {
        if (clearSavedCredentials) {
            NetworkClient.updateCsrfToken(null)
            prefs.edit {
                remove(Constants.KEY_USER)
                remove(Constants.KEY_PASS)
                remove(Constants.KEY_CSRF_TOKEN)
                remove(Constants.KEY_ACCOUNT_EXPIRY)
            }
        }

        layoutLoading.visibility = View.GONE
        layoutForm.visibility = View.VISIBLE

        msg?.let {
            Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
        }
    }
}
