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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private val SHIZUKU_CODE = 1001
    private var isGranted by mutableStateOf(false)
    private var isEnabled by mutableStateOf(false)
    private var gamePackage by mutableStateOf("") 
    private var statusText by mutableStateOf("جاهز")

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkShizukuPermission()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            isGranted = false
            statusText = "انقطع الاتصال بـ Shizuku"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(this)

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
                            value = gamePackage,
                            onValueChange = { gamePackage = it },
                            label = { Text("اسم حزمة اللعبة", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true, 
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                textDirection = TextDirection.Ltr 
                            ),
                            trailingIcon = {
                                if (gamePackage.isNotEmpty()) {
                                    TextButton(onClick = { gamePackage = "" }) {
                                        Text("مسح", color = Color.Red)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B4D8),
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color(0xFF00B4D8)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                if (!isGranted) {
                                    requestShizuku()
                                } else if (gamePackage.isNotBlank()) {
                                    isEnabled = !isEnabled
                                    toggleFpsLock(gamePackage, isEnabled)
                                } else {
                                    statusText = "الرجاء كتابة اسم الحزمة أولاً"
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
                        Text(
                            text = statusText, 
                            color = Color.Yellow,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            isGranted = true
            statusText = "جاهز للاستخدام"
        } else {
            requestShizuku()
        }
    }

    private fun requestShizuku() {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == SHIZUKU_CODE) {
            isGranted = grantResult == PackageManager.PERMISSION_GRANTED
            if (isGranted) statusText = "تم منح الصلاحية"
        }
    }

    private fun toggleFpsLock(pkg: String, enable: Boolean) {
        statusText = "جاري التنفيذ..."
        val command = if (enable) {
            "cmd game set --fps 120 $pkg && cmd game set --mode 1 $pkg"
        } else {
            "cmd game set --fps 0 $pkg && cmd game set --mode 0 $pkg"
        }
        
        Thread {
            try {
                val method = Shizuku::class.java.declaredMethods.firstOrNull {
                    it.name == "newProcess" && it.parameterTypes.size == 3
                } ?: throw Exception("لم يتم العثور على دالة newProcess")

                method.isAccessible = true
                
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val output = StringBuilder()
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                var errLine: String?
                while (errorReader.readLine().also { errLine = it } != null) {
                    output.append(errLine).append("\n")
                }
                
                process.waitFor()
                
                runOnUiThread {
                    isEnabled = enable
                    statusText = if (output.isBlank()) "تم التنفيذ بنجاح" else "النتيجة: $output"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText = "فشل: ${e.message}"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(this)
    }
}
