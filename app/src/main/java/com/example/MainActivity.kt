package com.example

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text

import com.example.ui.theme.MyApplicationTheme
import com.example.ui.SecureViewModel
import com.example.ui.ShieldChatApp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val prefs = applicationContext.getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE)
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        android.util.Log.e("FATAL_CRASH", "Uncaught exception", throwable)
        prefs.edit().putString("crash_log", throwable.stackTraceToString()).commit()
        System.exit(2)
    }
    super.onCreate(savedInstanceState)
        // Initialize Firebase
    try {
        val apiKey = BuildConfig.FIREBASE_API_KEY
        val appId = BuildConfig.FIREBASE_APP_ID
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val dbUrl = "https://sheild-76698-default-rtdb.asia-southeast1.firebasedatabase.app/"
        
        if (apiKey.isNotEmpty() && !apiKey.contains("YOUR_") &&
            appId.isNotEmpty() && !appId.contains("YOUR_") &&
            projectId.isNotEmpty() && !projectId.contains("YOUR_")) {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .setDatabaseUrl(dbUrl)
                    .build()
                FirebaseApp.initializeApp(applicationContext, options)
            }
            Log.d("ShieldChat", "Firebase Initialized successfully via Secrets")
        }
    } catch (e: Throwable) {
         Log.w("ShieldChat", "Firebase initialization failed: \${e.message}")
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val secureViewModel: SecureViewModel = viewModel()
        val protect = secureViewModel.screenshotProtection.collectAsState().value
        var crashLog by androidx.compose.runtime.remember { 
            androidx.compose.runtime.mutableStateOf<String?>(applicationContext.getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE).getString("crash_log", null))
        }

        if (crashLog != null) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Text("CRASH (tap to clear):\n$crashLog", color = Color.White) }
                    item {
                        androidx.compose.material3.Button(onClick = { 
                            applicationContext.getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                            crashLog = null
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(protect) {
              try {
            if (protect) {
              // Intentionally bypassed FLAG_SECURE for streaming emulator compatibility
              // window.setFlags(
              //   WindowManager.LayoutParams.FLAG_SECURE,
              //   WindowManager.LayoutParams.FLAG_SECURE
              // )
            } else {
              // window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
          } catch (e: Throwable) {
             Log.e("ShieldChat", "Could not set FLAG_SECURE: \${e.message}")
          }
        }
        
        ShieldChatApp(secureViewModel)
      }
    }
  }
}
}
