package com.calibre.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the MainActivity.
 *
 * This class is responsible for preparing and managing the data for the UI.
 * It handles the logic for UI interactions, such as button clicks.
 */
class MainViewModel : ViewModel() {

    // A private mutable shared flow to send one-time events to the UI.
    private val _events = MutableSharedFlow<Event>()
    // A public flow that the UI can observe for events.
    val events = _events.asSharedFlow()

    /**
     * Called when the "Import Library" button is clicked.
     * This function emits an event to trigger the Storage Access Framework
     * directory picker in the Activity.
     */
    fun onImportLibraryClicked() {
        viewModelScope.launch {
            _events.emit(Event.RequestImportDirectoryPicker)
        }
    }

    /**
     * Called when the "Export Library" button is clicked.
     * This function emits an event to trigger the Storage Access Framework
     * directory picker in the Activity for selecting an export location.
     */
    fun onExportLibraryClicked() {
        viewModelScope.launch {
            _events.emit(Event.RequestExportDirectoryPicker)
        }
    }

    /**
     * Sealed class representing the one-time events that can be sent from this ViewModel.
     */
    sealed class Event {
        /**
         * An event to signal that the directory picker should be opened for import.
         */
        object RequestImportDirectoryPicker : Event()

        /**
         * An event to signal that the directory picker should be opened for export.
         */
        object RequestExportDirectoryPicker : Event()
    }
}
