package com.example.skywelllogger

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.lang.reflect.Proxy

class MainActivity : AppCompatActivity() {

    private val TAG = "SKYWELL_TEST"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tv = TextView(this).apply {
            text = "SkyWell Logger запущен.\nСмотрите логи в терминале Mac."
            textSize = 20f
            setPadding(50, 50, 50, 50)
        }
        setContentView(tv)

        Log.d(TAG, "=== APP STARTED ===")

        try {
            // 1. Car.createCar(context)
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val carInstance = createCarMethod.invoke(null, this)

            if (carInstance == null) {
                Log.e(TAG, "!!! Car instance is NULL")
                return
            }

            // 2. car.getCarManager("property")
            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val pm = getCarManagerMethod.invoke(carInstance, "property")

            if (pm == null) {
                Log.e(TAG, "!!! CarPropertyManager is NULL")
                return
            }
            Log.d(TAG, "CarPropertyManager OK")

            // 3. Создаем коллбэк через Прокси
            val callbackInterface = Class.forName("android.car.hardware.CarPropertyManager\$CarPropertyEventCallback")
            val callbackProxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface)
            ) { _, method, args ->
                if (method.name == "onChangeEvent" && args != null && args.isNotEmpty()) {
                    try {
                        val event = args[0]
                        if (event != null) {
                            val propId = event.javaClass.getMethod("getPropertyId").invoke(event) as Int
                            val value = event.javaClass.getMethod("getValue").invoke(event)
                            Log.i(TAG, "PUSH >>> 0x${Integer.toHexString(propId)} = $value")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing event: ${e.message}")
                    }
                } else if (method.name == "onErrorEvent") {
                    Log.e(TAG, "PUSH ERROR")
                }
                null
            }

            // 4. Подписываемся на ID (ИСПРАВЛЕНО: Float вместо Int для частоты)
            val pmClass = pm.javaClass
            val registerMethod = pmClass.getMethod(
                "registerCallback", 
                callbackInterface, 
                Int::class.javaPrimitiveType, 
                Float::class.javaPrimitiveType // <--- Вот тут была ошибка
            )
            
            val ids = listOf(
                0x11600309, // Заряд
                0x1160030c, // Мощность
                0x11600204, // Пробег
                0x11600517, // Неизвестно (возможно напряжение)
                0x11600518, // Неизвестно (возможно мощность)
                0x11400454, // Педали/руль
                0x11400e60  // Быстрое свойство
            )

            for (id in ids) {
                try {
                    // Передаем 1.0f, так как метод ожидает float
                    registerMethod.invoke(pm, callbackProxy, id, 1.0f) 
                    Log.d(TAG, "Subscribed to 0x${Integer.toHexString(id)}")
                } catch (e: Exception) {
                    Log.e(TAG, "FAILED TO SUBSCRIBE 0x${Integer.toHexString(id)}: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
        }
    }
}
