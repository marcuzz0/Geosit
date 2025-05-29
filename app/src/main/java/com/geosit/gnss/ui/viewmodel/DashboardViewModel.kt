package com.geosit.gnss.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.geosit.gnss.data.connection.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {
    
    val connectionState = connectionManager.connectionState
    val gnssPosition = connectionManager.currentPosition
}
