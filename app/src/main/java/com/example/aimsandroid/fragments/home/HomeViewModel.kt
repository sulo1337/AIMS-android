package com.example.aimsandroid.fragments.home

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.lifecycle.*
import com.example.aimsandroid.database.BillOfLading
import com.example.aimsandroid.database.TripStatus
import com.example.aimsandroid.database.TripWithWaypoints
import com.example.aimsandroid.database.getDatabase
import com.example.aimsandroid.repository.LocationRepository
import com.example.aimsandroid.repository.TripRepository
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.mapping.Map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sortWaypointBySeqNum
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var map: Map? = null
    private var _currentTripId: Long = -1L
    private var prefs: SharedPreferences = application.getSharedPreferences("com.example.aimsandroid", Context.MODE_PRIVATE)
    private val locationRepository = LocationRepository(application)
    private val database = getDatabase(application)
    val tripRepository = TripRepository(database)
    private val _currentTripCompleted = MutableLiveData<Boolean>(false)
    val currentTripCompleted: LiveData<Boolean>
        get() = _currentTripCompleted

    var currentTrip: LiveData<TripWithWaypoints> = tripRepository.getTripWithWaypointsByTripId(_currentTripId)
    val latitude = locationRepository.latitude
    val longitude = locationRepository.longitude

    fun recenterMap() {
        try{
            if(!this.latitude.value?.equals(0.0)!! && !this.longitude.value?.equals(0.0)!!) {
                this.map?.setCenter(GeoCoordinate(latitude.value!!, longitude.value!!), Map.Animation.BOW, map?.maxZoomLevel!!*0.75, 0.0f, 0.1f)
            }
        }
        catch (e: Exception) {
            Log.i("aimsDebug", "no location info")
        }
    }

    suspend fun resolveNextWaypoint() {
        var waypoints = currentTrip.value?.waypoints
        if(waypoints != null) {
            waypoints = sortWaypointBySeqNum(waypoints)
            var nextWaypointId = -1L
            for(waypoint in waypoints){
                val waypointWithBillOfLading = tripRepository.getWaypointWithBillOfLading(waypoint.seqNum, waypoint.owningTripId)
                if(waypointWithBillOfLading.billOfLading == null){
                    nextWaypointId = waypoint.seqNum
                    break
                } else if(waypointWithBillOfLading.billOfLading.complete == false) {
                    nextWaypointId = waypoint.seqNum
                    break
                }
            }
            prefs.edit().putLong("nextWaypointSeqNumber", nextWaypointId).apply()
        }
    }

    suspend fun saveForm(billOfLading: BillOfLading, bolBitmap: Bitmap, signatureBitmap: Bitmap?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                tripRepository.insertBillOfLading(billOfLading)
                saveBitmaps(bolBitmap, signatureBitmap, billOfLading)
                resolveNextWaypoint()
                if(checkCurrentTripIsCompleted()) {
                    val currentTripId = currentTrip.value?.trip?.tripId
                    currentTripId?.let {
                        tripRepository.setTripStatus(TripStatus(it, true))
                    }
                    withContext(Dispatchers.Main) {
                        removeCurrentTrip()
                    }
                }
            }
        }
    }

    private suspend fun checkCurrentTripIsCompleted(): Boolean {
        var waypoints = currentTrip.value?.waypoints
        if(waypoints!=null){
            waypoints = sortWaypointBySeqNum(waypoints)
            var complete = true
            for(waypoint in waypoints){
                Log.i("aimsDebug", waypoint.toString())
                val waypointWithBillOfLading = tripRepository.getWaypointWithBillOfLading(waypoint.seqNum, waypoint.owningTripId)
                if(waypointWithBillOfLading.billOfLading == null) {
                    complete = false
                    break
                } else if(waypointWithBillOfLading.billOfLading.complete == false) {
                    complete = false
                    break
                }
            }
            return complete
        } else {
            return false
        }
    }

    private fun removeCurrentTrip() {
        prefs.edit().putLong("currentTripId", -1L).apply()
        currentTrip = tripRepository.getTripWithWaypointsByTripId(-1L)
        _currentTripCompleted.value = true
    }

    fun onCurrentTripRemoved() {
        _currentTripCompleted.value = false
    }

    private suspend fun saveBitmaps(bolBitmap: Bitmap, signatureBitmap: Bitmap?, billOfLading: BillOfLading) {
        saveBolBitmap(bolBitmap, billOfLading)
        saveSignatureBitmap(signatureBitmap, billOfLading)
    }

    suspend fun saveBolBitmap(bolBitmap: Bitmap, billOfLading: BillOfLading){
        withContext(Dispatchers.IO){
            try {
                val filePath =  getApplication<Application>().getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE).absolutePath + "/AIMS/bol/"
                val dir = File(filePath)
                if(!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "bol_"+billOfLading.tripIdFk.toString()+"_"+billOfLading.wayPointSeqNum.toString()+".jpeg")
                val fOut = FileOutputStream(file)
                bolBitmap.compress(Bitmap.CompressFormat.JPEG, 75, fOut)
                fOut.flush()
                fOut.close()
                Log.i("aimsDebug_fh", file.absolutePath+" saved")
            } catch (e: Exception) {
                Log.i("aimsDebug_fh", e.toString())
            }
        }
    }

    suspend fun saveSignatureBitmap(signatureBitmap: Bitmap?, billOfLading: BillOfLading){

    }

    init {
        _currentTripId = prefs.getLong("currentTripId", -1L)
        currentTrip =  tripRepository.getTripWithWaypointsByTripId(_currentTripId)
        viewModelScope.launch {
            if(checkCurrentTripIsCompleted()) {
                removeCurrentTrip()
            }
        }
        //TODO
        latitude.observeForever {
            map?.positionIndicator?.isVisible = true;
        }

        //TODO
        longitude.observeForever {
            map?.positionIndicator?.isVisible = true;
        }
    }
}