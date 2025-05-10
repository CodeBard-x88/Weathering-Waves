package com.app.weatheringwaves.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.weatheringwaves.R
import com.app.weatheringwaves.api.retrofit.ApiConfig
import com.app.weatheringwaves.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.gms.location.LocationServices

class WeatherUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val location = getLastKnownLocation()
            if (location != null) {
                val latLong = "${location.latitude},${location.longitude}"
                val response = ApiConfig.getApiService().getForecastData(
                    apiKey = BuildConfig.WEATHER_API_KEY,
                    location = latLong,
                    days = "1",
                    airQuality = "no",
                    alerts = "no"
                ).execute()

                if (response.isSuccessful && response.body() != null) {
                    val weatherData = response.body()!!.current
                    sendNotification(weatherData.condition.text, weatherData.tempC.toString())
                }

                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    cont.resume(location)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }

    private fun sendNotification(condition: String, temperature: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, "weather_updates")
            .setContentTitle("Weather Update")
            .setContentText("Current condition: $condition, Temperature: $temperature°C")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }
}
