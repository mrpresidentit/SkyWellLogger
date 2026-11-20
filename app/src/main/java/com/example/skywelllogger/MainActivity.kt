package com.example.skywelllogger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val TAG = "SKYWELL_TEST"
    
    private val UPDATE_URL = "https://github.com/mrpresidentit/SkyWellLogger/releases/latest/download/app-debug.apk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val tvStatus = TextView(this).apply {
            text = "SkyWell Logger\nСтатус: Ожидание данных..."
            textSize = 20f
        }
        layout.addView(tvStatus)

        val btnUpdate = Button(this).apply {
            text = "Скачать обновление с GitHub"
            setOnClickListener { 
                tvStatus.text = "Скачивание..."
                downloadAndInstallUpdate(tvStatus) 
            }
        }
        layout.addView(btnUpdate)

        setContentView(layout)

        // Логика VHAL (рефлексия)
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val carInstance = createCarMethod.invoke(null, this)
            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val pm = getCarManagerMethod.invoke(carInstance, "property")

            if (pm == null) return
            Log.d(TAG, "CarPropertyManager OK")

            val callbackInterface = Class.forName("android.car.hardware.CarPropertyManager\$CarPropertyEventCallback")
            val callbackProxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface)
            ) { _, method, args ->
                if (method.name == "onChangeEvent" && args != null && args.isNotEmpty()) {
                    val event = args[0]
                    val propId = event.javaClass.getMethod("getPropertyId").invoke(event) as Int
                    val value = event.javaClass.getMethod("getValue").invoke(event)
                    Log.i(TAG, "PUSH >>> 0x${Integer.toHexString(propId)} = $value")
                }
                null
            }

            val registerMethod = pm.javaClass.getMethod(
                "registerCallback", 
                callbackInterface, 
                Int::class.javaPrimitiveType, 
                Float::class.javaPrimitiveType 
            )
            
            val ids = listOf(0x11600309, 0x1160030c, 0x11600204, 0x11600517, 0x11600518, 0x11400454, 0x11400e60)

            for (id in ids) {
                try {
                    registerMethod.invoke(pm, callbackProxy, id, 1.0f) 
                } catch (e: Exception) {
                    Log.e(TAG, "FAILED TO SUBSCRIBE 0x${Integer.toHexString(id)}: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
        }
    }

    private fun downloadAndInstallUpdate(statusView: TextView) {
        Thread {
            try {
                val url = URL(UPDATE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true // Важно для GitHub!
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    runOnUiThread { statusView.text = "Ошибка: ${connection.responseCode}" }
                    return@Thread
                }

                val apkFile = File(cacheDir, "update.apk")
                val output = FileOutputStream(apkFile)
                connection.inputStream.copyTo(output)
                output.flush()
                output.close()

                runOnUiThread { 
                    statusView.text = "Скачано! Установка..."
                    installApk(apkFile) 
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download Error: ${e.message}")
                runOnUiThread { statusView.text = "Ошибка скачивания: ${e.message}" }
            }
        }.start()
    }

    private fun installApk(apkFile: File) {
        try {
            // Вариант 1: Тихая установка (если есть root)
            /*
            val process = Runtime.getRuntime().exec("su -c pm install -r ${apkFile.absolutePath}")
            if (process.waitFor() == 0) {
                Toast.makeText(this, "Обновлено! Перезапустите.", Toast.LENGTH_LONG).show()
                return
            }
            */

            // Вариант 2: Стандартная установка (выскочит окно "Установить?")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install Error: ${e.message}")
        }
    }
}
