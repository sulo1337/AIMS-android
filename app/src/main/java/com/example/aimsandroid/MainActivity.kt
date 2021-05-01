package com.example.aimsandroid

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.aimsandroid.service.ForegroundService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gu.toolargetool.TooLargeTool
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.jakewharton.processphoenix.ProcessPhoenix


class MainActivity : AppCompatActivity() {
    var PERMISSION_ALL = 1
    private lateinit var foregroundServiceIntent: Intent
    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false);
        val navController = findNavController(R.id.defaultNavHostFragment)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation_bar)
        bottomNavigationView.setupWithNavController(navController)
        bottomNavigationView.setOnNavigationItemReselectedListener {  }
        foregroundServiceIntent = Intent(this, ForegroundService::class.java)
        foregroundServiceIntent.setAction("Online")
        checkLogin()
        if(!ForegroundService.isRunning()){
            startForegroundService(foregroundServiceIntent)
        }
        TooLargeTool.startLogging(this.application)
    }

    private fun setupForegroundService() {

    }

    private fun checkLogin() {
        val prefs: SharedPreferences = application.getSharedPreferences("com.example.aimsandroid", Context.MODE_PRIVATE)
        val driverId = prefs.getString("driverId", null)
        val driverKey = prefs.getString("driverKey", null)
        if(driverId == null || driverKey == null) {
            startLoginActivity()
            finish()
        } else {
            TedPermission.with(this)
                .setPermissionListener(object: PermissionListener{
                    override fun onPermissionGranted() {
                    }

                    override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                        Toast.makeText(applicationContext, "Background location permission denied", Toast.LENGTH_SHORT).show()
                    }
                })
                .setDeniedMessage("Please allow location permission.\n\nPlease select \"Allow all the time\" for location to enable background navigation.")
                .setGotoSettingButtonText("Open App Settings")
                .setPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                .check()
        }
    }

    private fun startLoginActivity() {
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
    }

    private fun restartApp() {
        ProcessPhoenix.triggerRebirth(applicationContext)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outPersistentState.clear()
        outState.clear()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean{
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.className)) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        if(foregroundServiceIntent!=null) {
            stopService(foregroundServiceIntent)
        }
        super.onDestroy()
    }
}