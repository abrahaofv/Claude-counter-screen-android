package com.example.claudecounter

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)

    val usageState: StateFlow<SessionManager.UsageState> = sessionManager.usageState
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.usageState.value)

    fun refresh() {
        val orgId = sessionManager.orgId ?: return
        val context = getApplication<Application>()
        WebViewUsageFetcher.getInstance().fetchUsage(context, orgId) { json, error ->
            val result = ClaudeApiService.parseUsageResponse(json, error)
            if (result.success && result.data != null) {
                sessionManager.clearError()
                sessionManager.updateUsage(result.data)
            } else if (result.isApiBlocked) {
                sessionManager.setApiBlocked(true, result.errorMessage)
            } else {
                sessionManager.setApiBlocked(false, result.errorMessage)
            }
        }
    }

    fun logout() {
        sessionManager.clearAuth()
        // Stop service
        getApplication<Application>().stopService(
            Intent(getApplication(), UsagePollingService::class.java)
        )
    }
}
