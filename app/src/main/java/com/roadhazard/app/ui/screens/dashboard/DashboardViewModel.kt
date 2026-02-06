package com.roadhazard.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadhazard.app.data.model.Report
import com.roadhazard.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()
    
    // UI State for collapse/expand
    private val _isReportsExpanded = MutableStateFlow(true)
    val isReportsExpanded: StateFlow<Boolean> = _isReportsExpanded.asStateFlow()

    init {
        fetchReports()
    }

    private fun fetchReports() {
        viewModelScope.launch {
            repository.getReports()
                .catch { e ->
                    e.printStackTrace()
                    // Handle error state if needed
                }
                .collect { reportList ->
                    _reports.value = reportList
                }
        }
    }
    
    fun toggleReportsExpansion() {
        _isReportsExpanded.value = !_isReportsExpanded.value
    }
}
