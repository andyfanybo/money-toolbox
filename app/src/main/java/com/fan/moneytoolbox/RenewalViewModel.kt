package com.fan.moneytoolbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fan.moneytoolbox.data.RenewalItem
import com.fan.moneytoolbox.data.RenewalRepository
import com.fan.moneytoolbox.notify.RenewalNotifier
import com.fan.moneytoolbox.notify.RenewalScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RenewalViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RenewalRepository(app)
    val items: StateFlow<List<RenewalItem>> = repo.itemsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { RenewalScheduler.restoreAll(getApplication()) }
    }

    fun save(item: RenewalItem) {
        viewModelScope.launch {
            repo.upsert(item)?.let { RenewalScheduler.cancel(getApplication(), it) }
            RenewalNotifier.cancel(getApplication(), item)
            RenewalScheduler.schedule(getApplication(), item)
        }
    }

    fun markPaid(id: String) {
        viewModelScope.launch {
            val (old, updated) = repo.markPaid(id, LocalDate.now()) ?: return@launch
            RenewalScheduler.cancel(getApplication(), old)
            RenewalNotifier.cancel(getApplication(), old)
            RenewalScheduler.schedule(getApplication(), updated)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val old = repo.delete(id) ?: return@launch
            RenewalScheduler.cancel(getApplication(), old)
            RenewalNotifier.cancel(getApplication(), old)
        }
    }
}
