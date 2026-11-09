package com.example.fpslocker

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private val SHIZUKU_CODE = 1001
    private var isGranted by mutableStateOf(false)
    private var isEnabled by mutableStateOf(false)
    private var gamePackage by mutableStateOf("com.tencent.ig")
    private var statusText by mutableStateOf("جاهز")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shizuku.addRequestPermissionResultListener(this)
        checkShizukuPermission()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                    color = Color(0xFF121212)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "وضع الشيزوكو: ${if (isGranted) "متصل" else "غير متصل"}",
                            color = if (isGranted) Color.Green else Color.Red,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { gamePackage = it },
                            label = { Text("اسم حزمة اللعبة", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                if (!isGranted) {
                                    requestShizuku()
                                } else {
                                    isEnabled = !isEnabled
                                    toggleFpsLock(gamePackage, isEnabled)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEnabled) Color.Red else Color(0xFF00B4D8)
                            )
                        ) {
                            Text(
                                text = if (isEnabled) "إيقاف 120 إطار" else "تفعيل 120 إطار",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = statusText, color = Color.Yellow)
                    }
                }
            }
        }
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            isGranted = true
        } else {
            requestShizuku()
        }
    }

    private fun requestShizuku() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == SHIZUKU_CODE) {
            isGranted = grantResult == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun toggleFpsLock(pkg: String, enable: Boolean) {
        statusText = "جاري التنفيذ..."
        val command = if (enable) {
            "cmd game set --fps 120 $pkg && cmd game set --mode 1 $pkg"
        } else {
            "cmd game set --fps 0 $pkg && cmd game reset $pkg"
        }
        
        Thread {
            try {
                val method = Shizuku::class.java.getMethod(
                    "newProcess", 
                    Array<String>::class.java, 
                    Array<String>::class.java, 
                    String::class.java
                )
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) { }
                process.waitFor()
                
                runOnUiThread {
                    statusText = if (enable) "تم التفعيل بنجاح" else "تم الإيقاف بنجاح"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText = "خطأ: ${e.message}"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
    }
}
