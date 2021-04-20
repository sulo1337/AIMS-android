package com.example.aimsandroid.fragments.trips

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.aimsandroid.R
import com.example.aimsandroid.databinding.FragmentTripsBinding
import com.example.aimsandroid.fragments.trips.detaildialog.TripsDetailDialog
import com.example.aimsandroid.utils.FetchApiEventListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class TripsFragment : Fragment() {

    private lateinit var viewModel: TripsViewModel
    private lateinit var binding: FragmentTripsBinding
    private lateinit var prefs: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTripsBinding.inflate(inflater);
        val tripsViewModelFactory = TripsViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, tripsViewModelFactory).get(TripsViewModel::class.java)
        prefs = requireActivity().getSharedPreferences("com.example.aimsandroid", Context.MODE_PRIVATE)
        val fragmentTitle = binding.fragmentTitle
        fragmentTitle.setText(getString(R.string.trips_toolbar_title));
        binding.lifecycleOwner = this
        binding.tripsRecyclerView.adapter = TripsAdapter(TripsAdapter.TripsClickListener{
            lifecycleScope.launch{
                val dialog: DialogFragment = TripsDetailDialog.newInstance(it)
                dialog.show(childFragmentManager, "tripsDetailDialog")
            }
        }, prefs)
        viewModel.trips.observe(viewLifecycleOwner, Observer{
            val adapter = binding.tripsRecyclerView.adapter as TripsAdapter
            adapter.submitList(it)
        })
        viewModel.refreshing.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshContainer.isRefreshing = it
        })
        binding.swipeRefreshContainer.setOnRefreshListener {
            viewModel.refreshTrips(object : FetchApiEventListener{
                override fun onSuccess() {
                    Snackbar.make(requireView(), "Trips Up-to-date", Snackbar.LENGTH_SHORT).setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE).setBackgroundTint(
                        Color.rgb(75,181,67)
                    ).show()
                }

                override fun onError(error: String) {
                    Snackbar.make(requireView(), "No internet connection", Snackbar.LENGTH_SHORT).setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE).setBackgroundTint(
                        Color.rgb(220,20,60)).show()
                }
            })
        }
        return binding.root;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refreshTrips(object : FetchApiEventListener{
            override fun onSuccess() {}
            override fun onError(error: String) {}
        })
    }
}