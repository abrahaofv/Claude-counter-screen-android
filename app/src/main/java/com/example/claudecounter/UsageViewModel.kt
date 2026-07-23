package com.example.claudecounter

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)

    val usageState: StateFlow<SessionManager.UsageState> = sessionManager.usageState
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.usageState.value)

    fun refresh() {
        val orgId = sessionManager.orgId ?: return
        val cookie = sessionManager.sessionCookie ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = ClaudeApiService.fetchUsage(orgId, cookie)
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
