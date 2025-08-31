package com.calibre

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calibre.databinding.ActivityMainBinding
import com.calibre.ui.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The main entry point for the Calibre Android application.
 * This activity is responsible for hosting the primary UI and coordinating
 * the main user actions, such as starting the library import process.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Modern way to handle activity results.
    // This launcher opens the system's directory picker for IMPORT.
    private val importDirectoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // The user has selected a directory for import.
            startImporterService(uri)
            Toast.makeText(this, "Import started...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Import directory selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // This launcher opens the system's directory picker for EXPORT.
    private val exportDirectoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // The user has selected a directory for export.
            startExporterService(uri)
            Toast.makeText(this, "Export started...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Export directory selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startExporterService(directoryUri: Uri) {
        val intent = Intent(this, com.calibre.service.ExporterService::class.java).apply {
            action = com.calibre.service.ExporterService.ACTION_START_EXPORT
            putExtra(com.calibre.service.ExporterService.EXTRA_OUTPUT_DIRECTORY_URI, directoryUri)
        }
        startService(intent)
    }

    private fun startImporterService(directoryUri: Uri) {
        val intent = Intent(this, com.calibre.service.ImporterService::class.java).apply {
            action = com.calibre.service.ImporterService.ACTION_START_IMPORT
            putExtra(com.calibre.service.ImporterService.EXTRA_DIRECTORY_URI, directoryUri)
        }
        startService(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModelEvents()
    }

    private fun setupClickListeners() {
        binding.importLibraryButton.setOnClickListener {
            viewModel.onImportLibraryClicked()
        }
        binding.exportLibraryButton.setOnClickListener {
            viewModel.onExportLibraryClicked()
        }
    }

    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is MainViewModel.Event.RequestImportDirectoryPicker -> {
                        importDirectoryPickerLauncher.launch(null)
                    }
                    is MainViewModel.Event.RequestExportDirectoryPicker -> {
                        exportDirectoryPickerLauncher.launch(null)
                    }
                }
            }
        }
    }
}
