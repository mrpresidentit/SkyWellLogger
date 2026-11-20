package com.example.skywelllogger

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
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
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val TAG = "SKYWELL_TEST"
    private var car: Car? = null
    private var pm: CarPropertyManager? = null

    private val UPDATE_URL = "https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПОЗИТОРИЙ/releases/latest/download/app-debug.apk"

    private val IDS = mapOf(
        0x11600309 to "EV_BATTERY_LEVEL (SoC)",
        0x1160030c to "EV_CHARGE_RATE (Power)",
        0x11600204 to "PERF_ODOMETER (Mileage)",
        0x11600517 to "BATTERY_PROP_0x11600517",
        0x11600518 to "BATTERY_PROP_0x11600518",
        0x11400454 to "PEDAL_OR_STEER_0x11400454",
        0x11400e60 to "FAST_PROP_0x11400e60"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val btnUpdate = Button(this).apply {
            text = "Проверить обновления"
            setOnClickListener { downloadAndInstallUpdate() }
        }
        layout.addView(btnUpdate)

        val tvStatus = TextView(this).apply { text = "Логи пишутся в logcat..."; textSize = 18f }
        layout.addView(tvStatus)
        
        setContentView(layout)

        try {
            car = Car.createCar(this)
            pm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            Log.d(TAG, "=== APP STARTED ===")

            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(v: CarPropertyValue<*>) {
                    val name = IDS[v.propertyId] ?: "0x${Integer.toHexString(v.propertyId)}"
                    Log.i(TAG, "PUSH >>> $name = ${v.value}")
                }
                override fun onErrorEvent(id: Int, zone: Int) {
                    Log.e(TAG, "PUSH ERROR on $id")
                }
            }

            for (id in IDS.keys) {
                try {
                    pm?.registerCallback(cb, id, CarPropertyManager.SENSOR_RATE_NORMAL)
                } catch (e: Exception) {
                    Log.e(TAG, "FAILED TO SUBSCRIBE ${IDS[id]}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
        }
    }

    private fun downloadAndInstallUpdate() {
        Toast.makeText(this, "Скачивание обновления...", Toast.LENGTH_SHORT).show()

        object : AsyncTask<Void, Void, File?>() {
            override fun doInBackground(vararg params: Void?): File? {
                return try {
                    val url = URL(UPDATE_URL)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "Ошибка скачивания: ${connection.responseCode}")
                        return null
                    }

                    val apkFile = File(cacheDir, "update.apk")
                    val output = FileOutputStream(apkFile)
                    connection.inputStream.copyTo(output)
                    output.flush()
                    output.close()
                    apkFile
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка скачивания: ${e.message}")
                    null
                }
            }

            override fun onPostExecute(result: File?) {
                if (result != null && result.exists()) {
                    installApk(result)
                } else {
                    Toast.makeText(this@MainActivity, "Не удалось скачать APK", Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка установки: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        car?.disconnect()
    }
}
