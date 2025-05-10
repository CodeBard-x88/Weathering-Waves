package com.app.weatheringwaves.ui

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.weatheringwaves.api.response.ForecastResponse
import com.app.weatheringwaves.api.response.ForecastdayItem
import com.app.weatheringwaves.api.retrofit.ApiConfig
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.text.substringBefore
import androidx.lifecycle.lifecycleScope
import com.app.weatheringwaves.BuildConfig
import com.app.weatheringwaves.R
import com.app.weatheringwaves.adapter.ForecastDailyAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationDetailActivity : AppCompatActivity() {

    private lateinit var tvCityNameDailyForecast: TextView
    private lateinit var tvMinMaxTemperature: TextView
    private lateinit var tvEpaIndex: TextView
    private lateinit var tvSeeDetailAirQuality: TextView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var tvUvIndex: TextView
    private lateinit var tvUvIndexDesc: TextView
    private lateinit var rv7DaysForecasts: RecyclerView

    // alert dialog views
    private lateinit var tvCo: TextView
    private lateinit var tvNo2: TextView
    private lateinit var tvO3: TextView
    private lateinit var tvSo2: TextView
    private lateinit var tvPm25: TextView
    private lateinit var tvPm10: TextView
    private lateinit var btnClose: FloatingActionButton

    private lateinit var forecastDailyList: List<ForecastdayItem>

    private lateinit var forecastDailyAdapter: ForecastDailyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_location_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvCityNameDailyForecast = findViewById(R.id.tv_city_name_daily_forecast)
        tvMinMaxTemperature = findViewById(R.id.tv_min_max_temperature)
        tvEpaIndex = findViewById(R.id.tv_epa_index)
        tvSeeDetailAirQuality = findViewById(R.id.tv_air_quality_detail)
        tvSunrise = findViewById(R.id.tv_sunrise_time)
        tvSunset = findViewById(R.id.tv_sunset_time)
        tvUvIndex = findViewById(R.id.tv_uv_index)
        tvUvIndexDesc = findViewById(R.id.tv_uv_index_desc)

        setDetailLocationData()

        val linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        forecastDailyList = arrayListOf()

        rv7DaysForecasts = findViewById(R.id.rv_7_day_forecasts)
        rv7DaysForecasts.setHasFixedSize(true)
        rv7DaysForecasts.layoutManager = linearLayoutManager

        tvSeeDetailAirQuality.setOnClickListener {
            // seeDetailAirQuality() <- use this if want to show AirQualityDialogFragment
            showDialogDetailAirQuality()
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
    }

    private fun setDetailLocationData() {
        // get location data from MainActivity
        val defaultLatLong = intent.getStringExtra(DEFAULT_LOC_KEY)
        val selectedLatLong = intent.getStringExtra(SELECTED_LOC_KEY)

        // set location data
        if (selectedLatLong.isNullOrEmpty()) {
            getDailyForecastData(defaultLatLong.toString())
        } else {
            getDailyForecastData(selectedLatLong.toString())
        }
    }

    private fun getDailyForecastData(location: String) {
        lifecycleScope.launch {
            try {
                val client = ApiConfig.getApiService().getForecastData(
                    apiKey = BuildConfig.WEATHER_API_KEY,
                    location = location,
                    days = "5",
                    airQuality = "yes",
                    alerts = "no"
                )
                client.enqueue(object : Callback<ForecastResponse> {
                    override fun onResponse(
                        call: Call<ForecastResponse>,
                        response: Response<ForecastResponse>
                    ) {
                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                // city name
                                val locationName = responseBody.location.name
                                val locationRegion = responseBody.location.region
                                tvCityNameDailyForecast.text = getString(
                                    R.string.location_format,
                                    locationName,
                                    locationRegion
                                )

                                // min max temperature
                                val minTemperature =
                                    responseBody.forecast.forecastday[0].day.mintempC.toString()
                                        .substringBefore(".") + "°C"
                                val maxTemperature =
                                    responseBody.forecast.forecastday[0].day.maxtempC.toString()
                                        .substringBefore(".") + "°C"
                                tvMinMaxTemperature.text =
                                    "Min:$minTemperature    Max:$maxTemperature"

                                // air quality index
                                val airQualityIndex =
                                    responseBody.current.airQuality.usEpaIndex

                                lifecycleScope.launch {
                                    val aqiResult = withContext(Dispatchers.Default) {
                                        when (airQualityIndex) {
                                            in 0..50 -> "Good"
                                            in 51..100 -> "Moderate"
                                            in 101..150 -> "Unhealthy for Sensitive Groups"
                                            in 151..200 -> "Unhealthy"
                                            in 201..300 -> "Very Unhealthy"
                                            in 301..500 -> "Hazardous"
                                            else -> "Invalid data"
                                        }
                                    }
                                    tvEpaIndex.text = "$airQualityIndex-$aqiResult"
                                }

                                // sunrise
                                val sunriseTime =
                                    responseBody.forecast.forecastday[0].astro.sunrise
                                tvSunrise.text = sunriseTime

                                // sunset
                                val sunsetTime =
                                    responseBody.forecast.forecastday[0].astro.sunset
                                tvSunset.text = sunsetTime

                                // uv index
                                val uvIndex = responseBody.forecast.forecastday[0].day.uv
                                val uvIndexDouble = uvIndex.toString().toDouble()

                                lifecycleScope.launch {
                                    val uvIndexResult = withContext(Dispatchers.Default) {
                                        when {
                                            uvIndexDouble <= 2.99 -> "Low"
                                            uvIndexDouble <= 5.99 -> "Moderate"
                                            uvIndexDouble <= 7.99 -> "High"
                                            uvIndexDouble <= 10.99 -> "Very High"
                                            uvIndexDouble >= 11.0 -> "Extreme"
                                            else -> "Unknown"
                                        }
                                    }
                                    tvUvIndex.text = uvIndex.toString().substringBefore(".")
                                    tvUvIndexDesc.text = uvIndexResult
                                    Log.d("UV INDEX", "uvIndexResult: $uvIndexDouble")
                                }

                                // forecast daily
                                val getForecastDayList = responseBody.forecast.forecastday
                                forecastDailyList = getForecastDayList
                                forecastDailyAdapter = ForecastDailyAdapter(
                                    context = this@LocationDetailActivity,
                                    forecastDailyList = forecastDailyList
                                )
                                rv7DaysForecasts.adapter = forecastDailyAdapter
                            }
                        }
                    }

                    override fun onFailure(
                        call: Call<ForecastResponse>,
                        t: Throwable
                    ) {
                        Log.e("LocationDetailActivity", "onFailure: ${t.message}", t)
                        Toast.makeText(
                            this@LocationDetailActivity,
                            "Failed to load data. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                })
            } catch (e: Exception) {
                Log.e("error", "getDailyForecastData: ${e.message}")
            }
        }
    }


    private fun showDialogDetailAirQuality() {
        val builder = AlertDialog.Builder(this@LocationDetailActivity)
        val dialogView = layoutInflater.inflate(R.layout.fragment_air_quality_detail, null)

        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)

        tvCo = dialogView.findViewById(R.id.tv_co)
        tvNo2 = dialogView.findViewById(R.id.tv_no2)
        tvO3 = dialogView.findViewById(R.id.tv_o3)
        tvSo2 = dialogView.findViewById(R.id.tv_so2)
        tvPm25 = dialogView.findViewById(R.id.tv_pm25)
        tvPm10 = dialogView.findViewById(R.id.tv_pm10)
        btnClose = dialogView.findViewById(R.id.btn_close)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Use this dialog to remove empty spaces below close button
        val window = dialog.window
        val layoutParams = window?.attributes
        layoutParams?.width = WindowManager.LayoutParams.MATCH_PARENT
        window?.attributes = layoutParams

        seeDetailAirQuality(tvCo, tvNo2, tvO3, tvSo2, tvPm25, tvPm10)
    }

    private fun seeDetailAirQuality(
        tvCo: TextView,
        tvNo2: TextView,
        tvO3: TextView,
        tvSo2: TextView,
        tvPm25: TextView,
        tvPm10: TextView
    ) {
        // get location data from MainActivity
        val defaultLatLong = intent.getStringExtra(DEFAULT_LOC_KEY)
        val selectedLatLong = intent.getStringExtra(SELECTED_LOC_KEY)

        // set location data
        if (selectedLatLong.isNullOrEmpty()) {
            getAqiData(defaultLatLong.toString(), tvCo, tvNo2, tvO3, tvSo2, tvPm25, tvPm10)
        } else {
            getAqiData(selectedLatLong.toString(), tvCo, tvNo2, tvO3, tvSo2, tvPm25, tvPm10)
        }

//        USE THIS CODE BELOW IF WANT TO USE AirQualityDialogFragment
//
        // get location data from MainActivity
//        val defaultLatLong = intent.getStringExtra(DEFAULT_LOC_KEY)
//        val selectedLatLong = intent.getStringExtra(SELECTED_LOC_KEY)

        // send location data to AirQualityDialogFragment
        if (selectedLatLong.isNullOrEmpty()) {
            val dialog = AirQualityDialogFragment()
            val defaultLatLongBundle = Bundle()
            defaultLatLongBundle.putString(DATA_KEY, defaultLatLong)

            dialog.arguments = defaultLatLongBundle
            dialog.show(supportFragmentManager, SEND_KEY)
        } else {
            val dialog = AirQualityDialogFragment()
            val selectedLatLongBundle = Bundle()
            selectedLatLongBundle.putString(DATA_KEY, selectedLatLong)

            dialog.arguments = selectedLatLongBundle
            dialog.show(supportFragmentManager, SEND_KEY)
        }
    }

    private fun getAqiData(
        location: String,
        tvCo: TextView,
        tvNo2: TextView,
        tvO3: TextView,
        tvSo2: TextView,
        tvPm25: TextView,
        tvPm10: TextView
    ) {
        lifecycleScope.launch {
            try {
                val client = ApiConfig.getApiService().getForecastData(
                    apiKey = BuildConfig.WEATHER_API_KEY,
                    location = location,
                    days = "1",
                    airQuality = "yes",
                    alerts = "no"
                )
                client.enqueue(object : Callback<ForecastResponse> {
                    override fun onResponse(
                        call: Call<ForecastResponse>,
                        response: Response<ForecastResponse>
                    ) {
                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                tvCo.text = responseBody.current.airQuality.co.toString()
                                tvNo2.text = responseBody.current.airQuality.no2.toString()
                                tvO3.text = responseBody.current.airQuality.o3.toString()
                                tvSo2.text = responseBody.current.airQuality.so2.toString()
                                tvPm25.text = responseBody.current.airQuality.pm25.toString()
                                tvPm10.text = responseBody.current.airQuality.pm10.toString()
                            }
                        }
                    }

                    override fun onFailure(
                        call: Call<ForecastResponse>,
                        t: Throwable
                    ) {
                        Log.e("AirQualityDialogFragment", "onFailure: ${t.message}", t)
                        Toast.makeText(
                            this@LocationDetailActivity,
                            "Failed to load data. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                })
            } catch (e: Exception) {
                Log.e("error", "getDailyForecastData: ${e.message}")
            }
        }
    }

    companion object {
        const val SELECTED_LOC_KEY = "1"
        const val DEFAULT_LOC_KEY = "0"
        const val DATA_KEY = "100"
        const val SEND_KEY = "300"
    }
}