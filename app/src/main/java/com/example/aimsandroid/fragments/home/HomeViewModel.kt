package com.example.aimsandroid.fragments.home

import RotateBitmap
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.example.aimsandroid.database.BillOfLading
import com.example.aimsandroid.database.TripStatus
import com.example.aimsandroid.database.TripWithWaypoints
import com.example.aimsandroid.database.getDatabase
import com.example.aimsandroid.repository.LocationRepository
import com.example.aimsandroid.repository.TripRepository
import com.example.aimsandroid.utils.FileLoaderListener
import com.example.aimsandroid.utils.OnSaveListener
import com.example.aimsandroid.utils.TripStatusCode
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.mapping.Map
import getBolBitmapPath
import getSignatureBitmapPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sortWaypointBySeqNum
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

/*
* Android view model that handles the logic for overlying home view
* */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var map: Map? = null
    private var _currentTripId: Long = -1L
    private var prefs: SharedPreferences = application.getSharedPreferences("com.example.aimsandroid", Context.MODE_PRIVATE)
    private var driverId: String? = prefs.getString("driverId", "D1")
    private val locationRepository = LocationRepository(application)
    val tripRepository = TripRepository(application)
    private val _currentTripCompleted = MutableLiveData<Boolean>(false)
    val currentTripCompleted: LiveData<Boolean>
        get() = _currentTripCompleted

    var currentTrip: LiveData<TripWithWaypoints> = tripRepository.getTripWithWaypointsByTripId(_currentTripId)
    val latitude = locationRepository.latitude
    val longitude = locationRepository.longitude

    //method to recenter map when button is clicked
    fun recenterMap() {
        try{
            if(!this.latitude.value?.equals(0.0)!! && !this.longitude.value?.equals(0.0)!!) {
                this.map?.setCenter(GeoCoordinate(latitude.value!!, longitude.value!!), Map.Animation.BOW, map?.maxZoomLevel!!*0.75, 0.0f, 0.1f)
            }
        }
        catch (e: Exception) {
            Log.w("aimsDebug", "GPS Signal Lost")
        }
    }

    //method to find next waypoint
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

    //method to save bill of lading form, bill of lading picture, and signature in database
    suspend fun saveForm(billOfLading: BillOfLading, bolBitmap: Bitmap?, signatureBitmap: Bitmap?, onSaveListener: OnSaveListener) {
        viewModelScope.launch {
            onSaveListener.onSaving()
            withContext(Dispatchers.IO){
                saveBitmaps(bolBitmap, signatureBitmap, billOfLading)
                tripRepository.insertBillOfLading(billOfLading)
                resolveNextWaypoint()
                if(checkCurrentTripIsCompleted()) {
                    val currentTripId = currentTrip.value?.trip?.tripId
                    currentTripId?.let {
                        tripRepository.setTripStatus(TripStatus(it, true))
                        if(signatureBitmap!=null){
                            onTripEvent(it, TripStatusCode.TRIP_DONE)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        removeCurrentTrip()
                        onSaveListener.onTripCompleted()
                    }
                } else {
                    withContext(Dispatchers.Main){
                        onSaveListener.onSave()
                    }
                }
            }
        }
    }

    //method to check if current trip has been completed
    private suspend fun checkCurrentTripIsCompleted(): Boolean {
        var waypoints = currentTrip.value?.waypoints
        if(waypoints!=null){
            waypoints = sortWaypointBySeqNum(waypoints)
            var complete = true
            for(waypoint in waypoints){
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

    //method to remove current trip context when trip has been completed
    private fun removeCurrentTrip() {
        prefs.edit().putLong("currentTripId", -1L).apply()
        currentTrip = tripRepository.getTripWithWaypointsByTripId(-1L)
        _currentTripCompleted.value = true
    }

    //method to signify current trip removed event has been complete
    fun onCurrentTripRemoved() {
        _currentTripCompleted.value = false
    }

    //method to save bitmaps
    private suspend fun saveBitmaps(bolBitmap: Bitmap?, signatureBitmap: Bitmap?, billOfLading: BillOfLading) {
        saveBolBitmap(bolBitmap, billOfLading)
        saveSignatureBitmap(signatureBitmap, billOfLading)
    }

    //method to save bill of lading bitmap
    suspend fun saveBolBitmap(bolBitmap: Bitmap?, billOfLading: BillOfLading){
        if(bolBitmap!=null){
            withContext(Dispatchers.IO){
                try {
                    val filePath =  getApplication<Application>().getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE).absolutePath + "/AIMS/bol/"
                    val dir = File(filePath)
                    if(!dir.exists()) {
                        dir.mkdirs()
                    }
                    val file = File(dir, getBolBitmapPath(billOfLading.tripIdFk, billOfLading.wayPointSeqNum, driverId.toString()))
                    val fOut = FileOutputStream(file)
                    val rotatedBolBitmap = RotateBitmap(bolBitmap, 90.0f)
                    rotatedBolBitmap.compress(Bitmap.CompressFormat.JPEG, 75, fOut)
                    fOut.flush()
                    fOut.close()
                    Log.i("aimsDebugDataPersist", "BOL image saved at path ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.w("aimsDebugDataPersist", "Error while saving BOL image: $e")
                }
            }
        }
    }

    //method to save signature bitmap
    suspend fun saveSignatureBitmap(signatureBitmap: Bitmap?, billOfLading: BillOfLading){
        if(signatureBitmap!=null){
            withContext(Dispatchers.IO){
                try{
                    val filePath = getApplication<Application>().getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE).absolutePath + "/AIMS/signature/"
                    val dir = File(filePath)
                    if(!dir.exists()) {
                        dir.mkdirs()
                    }
                    val file = File(dir, getSignatureBitmapPath(billOfLading.tripIdFk, billOfLading.wayPointSeqNum, driverId.toString()))
                    val fOut = FileOutputStream(file)
                    signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
                    fOut.flush()
                    fOut.close()
                    Log.i("aimsDebugDataPersist", "Signature image saved at path ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.w("aimsDebugDataPersist", "Error while saving signature image: $e")
                }
            }
        }
    }

    //method to get saved signature uri from local file system
    suspend fun getSignatureUri(tripIdFk: Long, wayPointSeqNum: Long, fileLoaderListener: FileLoaderListener){
        withContext(Dispatchers.IO) {
            try {
                val filePath = getApplication<Application>().getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE).absolutePath + "/AIMS/signature/"
                val dir = File(filePath)
                val file = File(dir, getSignatureBitmapPath(tripIdFk, wayPointSeqNum, driverId.toString()))
                fileLoaderListener.onSuccess(file.toUri())
            } catch (e: Exception) {
                fileLoaderListener.onError(e.toString())
            }
        }
    }

    //method to get saved bill of lading uri from local file system
    suspend fun getBolUri(tripIdFk: Long, wayPointSeqNum: Long, fileLoaderListener: FileLoaderListener){
        withContext(Dispatchers.IO) {
            try {
                val filePath = getApplication<Application>().getDir(Environment.DIRECTORY_PICTURES, Context.MODE_PRIVATE).absolutePath + "/AIMS/bol/"
                val dir = File(filePath)
                val file = File(dir, getBolBitmapPath(tripIdFk, wayPointSeqNum, driverId.toString()))
                fileLoaderListener.onSuccess(file.toUri())
            } catch (e: Exception) {
                fileLoaderListener.onError(e.toString())
            }
        }
    }

    //method to send trip event to the dispatcher or save it in the app
    fun onTripEvent(tripId: Long, tripStatusCode: TripStatusCode){
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                tripRepository.onTripEvent(tripId, tripStatusCode)
            }
        }
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