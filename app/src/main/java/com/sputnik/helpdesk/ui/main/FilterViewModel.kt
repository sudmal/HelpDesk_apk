package com.sputnik.helpdesk.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sputnik.helpdesk.data.prefs.Prefs

class FilterViewModel : ViewModel() {
    val selectedServiceTypeName = MutableLiveData<String?>(null)
    val selectedTerritoryId = MutableLiveData<Int?>(null)

    // Emitting any value triggers all TicketsFragment instances to re-sync from server
    val syncTrigger = MutableLiveData<Unit>()
    fun requestSync() { syncTrigger.value = Unit }

    // Updated after each successful foreground sync; shown in the header as "Обновлено в HH:mm"
    val lastSyncTime = MutableLiveData<String?>(null)

    // Sort order for ticket lists; initialized from Prefs in MainActivity
    val sortOrder = MutableLiveData(Prefs.SORT_BY_TIME)
}
