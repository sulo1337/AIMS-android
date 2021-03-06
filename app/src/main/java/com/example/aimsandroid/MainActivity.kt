package com.example.aimsandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.aimsandroid.service.ForegroundService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gu.toolargetool.TooLargeTool
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import getStackTraceString

/**
 * This class is an Android activity class to create main activity in the application
 */
class MainActivity : AppCompatActivity() {
    private lateinit var foregroundServiceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(
                "aimsDebugException",
                "Uncaught exception in ${t.name} ${getStackTraceString(e)}"
            )
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show()
        }
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false);
        val navController = findNavController(R.id.defaultNavHostFragment)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation_bar)
        bottomNavigationView.setupWithNavController(navController)
        bottomNavigationView.setOnNavigationItemReselectedListener {  }
        foregroundServiceIntent = Intent(this, ForegroundService::class.java)
        foregroundServiceIntent.setAction("Online")
            if(!ForegroundService.isRunning()) {
                startForegroundService(foregroundServiceIntent)
            }
    }

    //This method checks necessary permissions for the app
    private fun checkPermissions() {
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

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outPersistentState.clear()
        outState.clear()
    }

    override fun onDestroy() {
        stopService(foregroundServiceIntent)
        super.onDestroy()
    }
}