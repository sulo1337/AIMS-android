package com.example.aimsandroid.homefragment

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

@SuppressLint("MissingPermission")
class HomeViewModel(locationManager: LocationManager) : ViewModel() {
    private val _latitude = MutableLiveData<Double>()
    val latitude: LiveData<Double>
        get() = _latitude

    var prevLatitude: Double = 0.00000001

    private val _longitude = MutableLiveData<Double>()
    val longitude: LiveData<Double>
        get() = _longitude

    var prevLongitude: Double = 0.00000001

    private val _locationChanged = MutableLiveData<Boolean>()
    val locationChanged: LiveData<Boolean>
        get() = _locationChanged

    private val locationListener: LocationListener

    init {
        _latitude.value = 0.00000000
        _longitude.value = 0.00000000
        prevLatitude = 0.00000001
        prevLongitude = 0.00000001
        locationListener =  MyLocationListener(this)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200,  0.02f, locationListener)
    }

    fun onLocationChanged(location: Location) {
        prevLatitude = _latitude.value!!
        prevLongitude = _longitude.value!!
        _latitude.value = location.latitude
        _longitude.value = location.longitude
        _locationChanged.value = true
    }

    fun doneOnLocationChanged() {
        _locationChanged.value = false
    }

    class MyLocationListener(argViewModel: HomeViewModel): LocationListener {
        private val viewModel = argViewModel

        override fun onLocationChanged(location: Location) {
            viewModel.onLocationChanged(location)
        }
    }
}

