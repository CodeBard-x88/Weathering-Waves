package com.app.weatheringwaves.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.weatheringwaves.BuildConfig
import com.app.weatheringwaves.R
import com.app.weatheringwaves.adapter.ForecastHourlyAdapter
import com.app.weatheringwaves.api.response.ForecastResponse
import com.app.weatheringwaves.api.response.HourItem
import com.app.weatheringwaves.api.retrofit.ApiConfig
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.app.weatheringwaves.api.response.WeatherData
import com.app.weatheringwaves.api.response.Current
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.app.weatheringwaves.worker.WeatherUpdateWorker

class MainActivity : AppCompatActivity() {
    private lateinit var tvCityName: TextView
    private lateinit var tvCurrentDate: TextView
    private lateinit var tvCurrentWeather: TextView
    private lateinit var tvCurrentTemperature: TextView
    private lateinit var imgCurrentWeatherIcon: ImageView
    private lateinit var imgLocationDetails: ImageView
    private lateinit var imgSearchIcon: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSaveFavorite: Button
    private lateinit var btnLoadFavorite: Button

    private lateinit var materialCardView: MaterialCardView

    private lateinit var forecastHourlyList: List<HourItem>

    private lateinit var forecastHourlyAdapter: ForecastHourlyAdapter

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var selectedLatLong = ""
    private var defaultLatLong = ""
    private var currentLocation: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvCityName = findViewById(R.id.tv_city_name)
        tvCurrentDate = findViewById(R.id.tv_current_date)
        tvCurrentWeather = findViewById(R.id.tv_current_weather)
        tvCurrentTemperature = findViewById(R.id.tv_current_temperature)
        imgCurrentWeatherIcon = findViewById(R.id.img_icon_current_weather)
        imgSearchIcon = findViewById(R.id.img_icon_search)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocation()

        forecastHourlyList = arrayListOf()

        val linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        recyclerView = findViewById(R.id.rv_forecast_hourly)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = linearLayoutManager

        imgSearchIcon.setOnClickListener {
            openSearchFragment()
        }

        // set selected location from search
        supportFragmentManager.setFragmentResultListener(REQUEST_KEY, this) { _, bundle ->
            selectedLatLong = bundle.getString(DATA_KEY) ?: ""
            currentLocation = selectedLatLong
            getCurrentForecastData(selectedLatLong)
        }

        imgLocationDetails = findViewById(R.id.img_icon_location_details)
        imgLocationDetails.setOnClickListener {
            showDetailLocationData()
        }

        // material card view rounded top only
        val shapeDrawable = MaterialShapeDrawable().apply {
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, 70f)
                .setTopRightCorner(CornerFamily.ROUNDED, 70f)
                .build()
        }
        materialCardView = findViewById(R.id.materialCardView_hourly)
        materialCardView.background = shapeDrawable

        btnSaveFavorite = findViewById(R.id.btn_save_favorite)
        btnSaveFavorite.setOnClickListener {
            saveFavoriteLocation()
        }

        btnLoadFavorite = findViewById(R.id.btn_load_favorite)
        btnLoadFavorite.setOnClickListener {
            loadFavoriteLocationDetails()
        }

        createNotificationChannel()
        scheduleWeatherUpdates()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permission ->
        when {
            permission[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                getCurrentLocation()
            }

            permission[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                getCurrentLocation()
            }

            else -> {
                Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latLong = "${location.latitude},${location.longitude}"
                    currentLocation = latLong;
                    getCurrentForecastData(latLong)
                    Log.d("LatLong", latLong)
                } else {
                    Log.e("LatLong", "Location Not Found")
                }
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentForecastData(location: String) {
        val client = ApiConfig.getApiService().getForecastData(
            apiKey = BuildConfig.WEATHER_API_KEY,
            location = location,
            days = "2",
            airQuality = "yes",
            alerts = "no"
        )
        client.enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(
                call: Call<ForecastResponse>, response: Response<ForecastResponse>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // city name
                        tvCityName.text = responseBody.location.name

                        // current date
                        val inputDateFormat =
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val outputDateFormat =
                            SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
                        val getLocalTime = responseBody.location.localtime
                        val inputLocalTime = inputDateFormat.parse(getLocalTime)
                        val outputLocalTime = outputDateFormat.format(inputLocalTime!!)
                        tvCurrentDate.text = outputLocalTime

                        // current weather
                        tvCurrentWeather.text = responseBody.current.condition.text

                        // current weather icon
                        Glide.with(this@MainActivity)
                            .load("https:" + responseBody.current.condition.icon).override(300, 300)
                            .into(imgCurrentWeatherIcon)

                        // current temperature
                        val temperature =
                            responseBody.current.tempC.toString().substringBefore(".") + "°C"
                        tvCurrentTemperature.text = temperature

                        // forecast hourly (hanya satu hari saja)
//                        val hours = responseBody.forecast.forecastday[0].hour
//                        val time = responseBody.location.localtime
//
//                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
//
//                        val now = Date()
//
//                        val filtered = hours.filter { hourData ->
//                            val itemDate = dateFormat.parse(hourData.time)
//                            itemDate?.after(now) == true && itemDate.before(Date(now.time + 7 * 60 * 60 * 1000))
//                        }
//                         val result = filtered.take(5)

                        // forecast hourly today and tomorrow (if the time exceeds 23.00)
                        val tzId = responseBody.location.tzId
                        val timezone = TimeZone.getTimeZone(tzId)

                        // get data up to two days
                        val allHours = responseBody.forecast.forecastday.flatMap { it.hour }

                        val calendarNow = Calendar.getInstance(timezone)
                        val now = calendarNow.time

                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        dateFormat.timeZone = timezone

                        // filter frown now until next 8 hours
                        val filteredHours = allHours.filter { hour ->
                            val hourDate = dateFormat.parse(hour.time)
                            hourDate?.after(now) == true && hourDate.before(Date(now.time + 8 * 60 * 60 * 1000))
                        }

                        forecastHourlyList = filteredHours
                        forecastHourlyAdapter = ForecastHourlyAdapter(
                            context = this@MainActivity, forecastHourlyList = forecastHourlyList
                        )
                        recyclerView.adapter = forecastHourlyAdapter

                        updateWeatherUI(responseBody.current.condition.text)
                    }
                }
                Log.d("Success", "onResponse: ${response.code()}")
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                Log.e("MainActivity", "Error: ${t.message}")
                Toast.makeText(
                    this@MainActivity, "Failed to load data. Please try again.", Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showDetailLocationData() {
        // send default location or selected location
        if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    defaultLatLong = "${location.latitude},${location.longitude}"
                    currentLocation = defaultLatLong
                    Log.d("LatLong", "${location.latitude},${location.longitude}")

                    val intent = Intent(this, LocationDetailActivity::class.java)
                    intent.putExtra(DEFAULT_LOC_KEY, defaultLatLong)
                    intent.putExtra(SELECTED_LOC_KEY, selectedLatLong)
                    startActivity(intent)
                } else {
                    Log.e("LatLong", "Location Not Found")
                }
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun openSearchFragment() {
        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<View>(R.id.main).visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SearchLocationFragment())
            .addToBackStack(null)
            .commit()

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                findViewById<View>(R.id.main).visibility = View.VISIBLE
                findViewById<View>(R.id.fragment_container).visibility = View.GONE
            }
        }

    }

    private fun saveFavoriteLocation() {
        val sharedPreferences = getSharedPreferences("WeatheringWavesPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val latLong = currentLocation

        if (latLong.isNotEmpty() && latLong.contains(",")) {
            val parts = latLong.split(",")
            editor.putString("favorite_latitude", parts[0])
            editor.putString("favorite_longitude", parts[1])
            editor.apply()
            Toast.makeText(this, "Location saved as favorite", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location data not available yet. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadFavoriteLocationDetails() {
        val sharedPreferences = getSharedPreferences("WeatheringWavesPrefs", Context.MODE_PRIVATE)
        val latitude = sharedPreferences.getString("favorite_latitude", null)
        val longitude = sharedPreferences.getString("favorite_longitude", null)
        if (latitude != null && longitude != null) {
            val latLong = "$latitude,$longitude"
            getCurrentForecastData(latLong)
        } else {
            Toast.makeText(this, "No favorite location saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBackgroundTheme(condition: String) {
        val rootLayout = findViewById<View>(R.id.root_layout)
        val backgroundDrawable = when {
            // Clear or sunny
            condition.contains("sunny", ignoreCase = true) || condition.contains("clear", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#FFD700"), Color.parseColor("#FFA500")) // bright yellow-orange
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Cloudy or Overcast
            condition.contains("cloudy", ignoreCase = true) || condition.contains("overcast", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#B0BEC5"), Color.parseColor("#78909C")) // gray tones
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Rain, drizzle, showers
            condition.contains("rain", ignoreCase = true) || condition.contains("drizzle", ignoreCase = true)
                    || condition.contains("showers", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#5C6BC0"), Color.parseColor("#1E88E5")) // blue/purple rain
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Thunderstorm, lightning
            condition.contains("thunder", ignoreCase = true) || condition.contains("lightning", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#424242"), Color.parseColor("#000000")) // dark gray/black
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Snow, blizzard
            condition.contains("snow", ignoreCase = true) || condition.contains("blizzard", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#ECEFF1"), Color.parseColor("#CFD8DC")) // white/light gray
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Fog, mist, haze
            condition.contains("fog", ignoreCase = true) || condition.contains("mist", ignoreCase = true)
                    || condition.contains("haze", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#90A4AE"), Color.parseColor("#B0BEC5")) // bluish gray
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Ice, sleet, freezing rain
            condition.contains("ice", ignoreCase = true) || condition.contains("sleet", ignoreCase = true)
                    || condition.contains("freezing", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#B3E5FC"), Color.parseColor("#81D4FA")) // icy blue tones
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Smoke, dust, sand
            condition.contains("smoke", ignoreCase = true) || condition.contains("dust", ignoreCase = true)
                    || condition.contains("sand", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#D7CCC8"), Color.parseColor("#A1887F")) // dusty brown
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Partly cloudy or mostly sunny
            condition.contains("partly", ignoreCase = true) || condition.contains("mostly", ignoreCase = true) -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#FFF176"), Color.parseColor("#FFD54F")) // light yellow/gold
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }

            // Default fallback
            else -> {
                GradientDrawable().apply {
                    colors = intArrayOf(Color.parseColor("#87CEEB"), Color.parseColor("#B0E0E6")) // default sky blue
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }
        }

        rootLayout.background = backgroundDrawable
    }


    private fun updateWeatherUI(currentCondition: String) {
        updateBackgroundTheme(currentCondition)
    }

    private fun updateLocationUI() {
        tvCityName.text = currentLocation
    }

    private fun fetchWeatherData() {
        getCurrentForecastData(currentLocation)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Weather Updates"
            val descriptionText = "Daily weather update notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("weather_updates", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleWeatherUpdates() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weather_updates",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        const val REQUEST_KEY = "200"
        const val DATA_KEY = "100"
        const val SELECTED_LOC_KEY = "1"
        const val DEFAULT_LOC_KEY = "0"
    }
}