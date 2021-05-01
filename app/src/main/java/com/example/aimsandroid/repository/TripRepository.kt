package com.example.aimsandroid.repository

import API_KEY
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aimsandroid.database.*
import com.example.aimsandroid.network.Network
import com.example.aimsandroid.utils.FetchApiEventListener
import com.example.aimsandroid.utils.TripStatusCode
import getCurrentDateTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.net.UnknownHostException

class TripRepository(application: Application) {
    private val prefs = application.getSharedPreferences("com.example.aimsandroid", Context.MODE_PRIVATE)
    val driverId = prefs.getString("driverId", "D1")!!.trim()
    val database = getDatabase(application, driverId)
    val trips = database.tripDao.getTripsWithWaypoints()
    fun getTripWithWaypointsByTripId(tripId: Long) = database.tripDao.getTripWithWaypointsByTripId(tripId)
    suspend fun getTripByTripId(tripId: Long) = database.tripDao.getTrip(tripId)
    suspend fun insertTrip(trip: Trip) = database.tripDao.insertTrip(trip)
    suspend fun setTripStatus(tripStatus: TripStatus) = database.tripDao.setTripStatus(tripStatus)
    suspend fun getTripStatus(tripId: Long) = database.tripDao.getTripStatus(tripId)
    suspend fun insertWaypoint(wayPoint: WayPoint) = database.tripDao.insertWaypoint(wayPoint)
    fun getBillOfLading(seqNum: Long, owningTripId: Long) = database.tripDao.getBillOfLading(seqNum, owningTripId)
    suspend fun getWaypointWithBillOfLading(seqNum: Long, owningTripId: Long) = database.tripDao.getWayPointWithBillOfLading(seqNum, owningTripId)
    suspend fun insertAllTrips(trips: List<Trip>) = database.tripDao.insertAllTrips(trips)
    suspend fun insertAllWaypoints(waypoints:List<WayPoint>) = database.tripDao.insertAllWaypoints(waypoints)

    suspend fun insertBillOfLading(billOfLading: BillOfLading) {
        withContext(Dispatchers.IO) {
            val waypointWithBillOfLading = getWaypointWithBillOfLading(billOfLading.wayPointSeqNum, billOfLading.tripIdFk)
            if(waypointWithBillOfLading.waypoint != null) {
                if(waypointWithBillOfLading.waypoint.waypointTypeDescription == "Source") {
                    insertSourceBillOfLading(billOfLading)
                } else {
                    insertSiteBillOfLading(billOfLading)
                }
            }
        }
    }

    private suspend fun insertSourceBillOfLading(billOfLading: BillOfLading) {
        if(billOfLading.complete == true) {
            try {
                //TODO check status code 1000 on response
                putTripProductPickupAsync(
                    driverId,
                    "170",
                    "27",
                    //TODO implement product id
                    "1175",
                    billOfLading.billOfLadingNumber.toString(),
                    billOfLading.loadingStarted.toString(),
                    billOfLading.loadingEnded.toString(),
                    billOfLading.grossQuantity.toString(),
                    billOfLading.netQuantity.toString(),
                    API_KEY
                ).await()
                billOfLading.synced = true
                Log.i("aimsDebugRepository", "Sent bill of lading: $billOfLading")
            } catch (e: UnknownHostException) {
                billOfLading.synced = false
                Log.w("aimsDebugRepository", "No internet connection, saving for later: $billOfLading")
            } catch (e: Exception) {
                billOfLading.synced = false
                Log.w("aimsDebugRepository", "Unexpected error occurred while sending: $billOfLading")
            } finally {
                database.tripDao.insertBillOfLading(billOfLading)
            }
        } else {
            Log.i("aimsDebugRepository", "Updated waypoint information")
            database.tripDao.insertBillOfLading(billOfLading)
        }
    }

    private suspend fun insertSiteBillOfLading(billOfLading: BillOfLading){
        //TODO implement this with api
        billOfLading.synced = true
        database.tripDao.insertBillOfLading(billOfLading)
    }

    suspend fun onTripEvent(tripId: Long, tripStatusCode: TripStatusCode) {
        withContext(Dispatchers.IO){
            val tripEvent = TripEvent(
                0L,
                driverId,
                tripId,
                tripStatusCode.getStatusCode().trim(),
                tripStatusCode.getStatusMessage().trim(),
                getCurrentDateTimeString().trim(),
                false
            )
            try{
                //TODO check status code 1000 on response
                putTripEventStatusAsync(
                    tripEvent.driverId,
                    tripEvent.tripId.toString(),
                    tripEvent.statusCode,
                    tripEvent.statusMessage,
                    tripEvent.datetime,
                    API_KEY
                ).await()
                tripEvent.synced = true
                Log.i("aimsDebugRepository", "Sent trip status: $tripEvent")
            } catch(e: UnknownHostException){
                Log.w("aimsDebugRepository", "No internet connection, saving for later: $tripEvent")
            } catch (e: Exception){
                Log.w("aimsDebugRepository", "Unexpected error while saving: $tripEvent")
            } finally {
                database.tripDao.insertTripEvent(tripEvent)
            }
        }
    }

    suspend fun refreshTrips(fetchApiEventListener: FetchApiEventListener) {
        withContext(Dispatchers.IO){
            try {
                val response = Network.dispatcher.getTripsAsync(driverId, API_KEY).await()
                val data =response.data
                val responseStatus = data.responseStatus
                val tripSections = data.tripSections
                val trips = ArrayList<Trip>()
                val waypoints = ArrayList<WayPoint>()
                if(tripSections!=null){
                    for(tripSection in tripSections){
                        val trip = tripSection.getTrip()
                        val waypoint = tripSection.getWaypoint()
                        trips.add(trip)
                        waypoints.add(waypoint)
                    }
                    insertAllTrips(trips)
                    insertAllWaypoints(waypoints)
                }
                fetchApiEventListener.onSuccess()
            } catch (e: Exception){
                fetchApiEventListener.onError(e.toString())
            }
        }
    }
    suspend fun getUnSyncedBillOfLading() = database.tripDao.getUnSyncedBillOfLading()
    suspend fun getUnSyncedTripEvents() = database.tripDao.getUnSyncedTripEvents()
    suspend fun insertBillOfLadingNoNetwork(billOfLading: BillOfLading) = database.tripDao.insertBillOfLading(billOfLading)
    suspend fun insertTripEventNoNetwork(tripEvent: TripEvent) = database.tripDao.insertTripEvent(tripEvent)

    fun putTripProductPickupAsync(
        driverId: String,
        tripId: String,
        sourceId: String,
        productId: String,
        bolNum: String,
        startTime: String,
        endTime: String,
        grossQty: String,
        netQty: String,
        API_KEY: String
    ) = Network.dispatcher.putTripProductPickupAsync(
        driverId.trim(),
        tripId.trim(),
        sourceId.trim(),
        productId.trim(),
        bolNum.trim(),
        startTime.trim(),
        endTime.trim(),
        grossQty.trim(),
        netQty.trim(),
        API_KEY
    )

    fun putTripEventStatusAsync(
        driverId: String,
        tripId: String,
        statusCode: String,
        statusMessage: String,
        statusDate: String,
        API_KEY: String
    ) = Network.dispatcher.putTripEventStatusAsync(
        driverId.trim(),
        tripId.trim(),
        statusCode.trim(),
        statusMessage.trim(),
        statusDate.trim(),
        API_KEY
    )
}