package com.example.aimsandroid.fragments.profile

import PHONE_NUMBER
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.aimsandroid.MapDownloadActivity
import com.example.aimsandroid.R
import com.example.aimsandroid.SplashActivity
import com.example.aimsandroid.databinding.AlertAboutAppBinding
import com.example.aimsandroid.databinding.FragmentProfileBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.jakewharton.processphoenix.ProcessPhoenix
import getGreeting
import getLoader


class ProfileFragment : Fragment() {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    private lateinit var viewModel: ProfileViewModel
    private lateinit var binding: FragmentProfileBinding
    private lateinit var loader: AlertDialog
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentProfileBinding.inflate(inflater);
        return binding.root;
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val viewModelFactory = ProfileViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, viewModelFactory).get(ProfileViewModel::class.java)
        loader = getLoader(requireActivity())
        refreshInfo()
        binding.logoutContainer.setOnClickListener {
            handleLogout()
        }
        binding.downloadMapContainer.setOnClickListener {
            handleDownloadMap()
        }
        binding.aboutContainer.setOnClickListener {
            handleAbout()
        }
    }

    fun handleLogout() {
        if(internetIsConnected()) {
            viewModel.logout(object : LogoutEventListener{
                override fun onLogoutStarted() {
                    loader.show()
                }

                override fun onLogoutComplete() {
                    Handler(Looper.getMainLooper()).postDelayed({
                        loader.hide()
                        ProcessPhoenix.triggerRebirth(context, Intent(requireContext(), SplashActivity::class.java))
                    }, 1000)
                }
            })
        } else {
            Toast.makeText(requireContext(), "Logout disabled while offline", Toast.LENGTH_LONG).show()
        }
    }

    fun handleDownloadMap() {
        startActivity(Intent(requireContext(), MapDownloadActivity::class.java))
    }

    fun handleAbout() {
        val alertAboutAppBinding = AlertAboutAppBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setCancelable(true)
            .setView(alertAboutAppBinding.root)
            .setPositiveButton("Help") { dialogInterface: DialogInterface, i: Int ->
                TedPermission.with(requireContext())
                    .setPermissionListener(object: PermissionListener {
                        override fun onPermissionGranted() {
                            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel: $PHONE_NUMBER")))
                        }

                        override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
                        }
                    })
                    .setDeniedMessage("Please allow phone call permission for help.")
                    .setGotoSettingButtonText("Open App Settings")
                    .setPermissions(
                        Manifest.permission.CALL_PHONE
                    )
                    .check()
            }
            .setNegativeButton("Close"){dialog: DialogInterface?, i: Int ->
                dialog?.dismiss()
            }
            .show()
    }

    fun refreshInfo() {
        val driverName = viewModel.getDriverName()
        driverName?.let{
            val greetingText = "${getGreeting()},\n ${it.trim()}"
            binding.driverName.text = greetingText
        }
    }

    fun internetIsConnected(): Boolean {
        return try {
            val command = "ping -c 1 google.com"
            Runtime.getRuntime().exec(command).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    interface LogoutEventListener{
        fun onLogoutStarted()
        fun onLogoutComplete()
    }
}