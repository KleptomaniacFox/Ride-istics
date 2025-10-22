// fragment


package com.example.rideistics.ui.notifications

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.rideistics.R // Ensure this R file is correct for your project
import com.example.rideistics.databinding.FragmentNotificationsBinding // Your ViewBinding class
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlin.io.path.exists

class NotificationsFragment : Fragment() {

    companion object {
        private const val TAG = "NotificationsFragment"
    }

    // At the top of your NotificationsFragment class, with other launchers
    private val pickDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // THIS BLOCK IS EXECUTED WHEN THE USER SELECTS A FILE (OR CANCELS)
            uri?.let { selectedUri -> // 'selectedUri' is the Uri of the document the user picked
                val fileName = getFileNameFromUri(selectedUri)
                val customSubfolder = "MyReports" // As we defined earlier

                // HERE is where your app takes action with the selected document:
                // It calls the ViewModel to handle the saving process.
                viewModel.handleSelectedDocument(selectedUri, fileName, customSubfolder)

                Toast.makeText(requireContext(), "Document '${fileName ?: "selected"}' processing for folder '$customSubfolder'.", Toast.LENGTH_LONG).show()
            }
        }

    // Helper function to get filename from Uri (add this in the Fragment)
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        // Requires: import android.provider.OpenableColumns
        requireActivity().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }


    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationsViewModel

    // ActivityResultLauncher for picking an image
    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            // Pass the original content URI to the ViewModel.
            // ViewModel will handle copying to internal storage and updating its state.
            viewModel.setSelectedImageUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(NotificationsViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupClickListeners()
        setupTextWatchers()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.imageButton2.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Optional: Add a way to clear the image
        binding.imageButton2.setOnLongClickListener {
            viewModel.setSelectedImageUri(null) // Request ViewModel to clear the image
            // Toast for clearing is now handled by the ViewModel's toastMessage LiveData
            true // Consume the long click
        }

//        binding.buttonUploadDocument.setOnClickListener {
//            Toast.makeText(context, "Upload document clicked (implement me!)", Toast.LENGTH_SHORT).show()
//            // TODO: Implement document upload logic
//        }
//
//        binding.buttonViewDocuments.setOnClickListener {
//            Toast.makeText(context, "View documents clicked (implement me!)", Toast.LENGTH_SHORT).show()
//            // TODO: Implement view documents logic
//        }

        binding.buttonUploadDocument.setOnClickListener {
            // Launch the document picker, allow any type of file
            pickDocumentLauncher.launch(arrayOf("*/*"))
            // You can restrict MIME types if needed, e.g., arrayOf("application/pdf", "image/*")
        }

        binding.buttonViewDocuments.setOnClickListener {
            val documentsDir = File(
                requireContext().filesDir,
                NotificationsViewModel.USER_DOCUMENTS_DIRECTORY_NAME
            )
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }

            // It's better to try to open the directory itself.
            // FileProvider is needed to get a content:// URI for the directory.
            val authority = "${requireContext().packageName}.fileprovider"
            try {
                Log.d(TAG, "Attempting to get URI for documentsDir: ${documentsDir.absolutePath}") // <--- ADDED LOG HERE
                val documentsDirUri = FileProvider.getUriForFile(requireContext(), authority, documentsDir)

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(documentsDirUri, "*/*") // Standard type for opening folders
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // May be needed depending on context

                // Verify that the intent will resolve to an activity
                if (intent.resolveActivity(requireActivity().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "No app found to view the documents folder.", Toast.LENGTH_LONG).show()
                    // Fallback: Try opening the first file in the directory as a sample if no folder viewer
                    // Or show a list of files within your app if you prefer more control.
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "FileProvider authority error or path issue: ", e)
                Toast.makeText(context, "Error setting up folder view.", Toast.LENGTH_LONG).show()
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No app found to view the documents folder.", Toast.LENGTH_LONG).show()
            }
        }

    }

    private fun observeViewModel() {
        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            Log.d(TAG, "Observer received URI: $uri") // Corrected TAG usage
            val placeholder = R.drawable.ic_baseline_person_24
            if (uri != null) {
                try {
                    binding.imageButton2.setImageURI(null) // Reset image first
                    binding.imageButton2.setImageURI(uri)   // Set the new URI
                    binding.imageButton2.requestLayout()    // Ask for a re-layout
                    binding.imageButton2.invalidate()       // Ask for a redraw
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting image URI: $uri", e) // Corrected TAG usage
                    binding.imageButton2.setImageResource(placeholder) // Fallback to placeholder on error
                }
            } else {
                binding.imageButton2.setImageResource(placeholder)
                binding.imageButton2.requestLayout()    // Also for placeholder
                binding.imageButton2.invalidate()       // Also for placeholder
            }
        }


        viewModel.fullName.observe(viewLifecycleOwner) { name ->
            if (binding.editTextName.text.toString() != name) {
                binding.editTextName.setText(name ?: "")
            }
        }

        viewModel.email.observe(viewLifecycleOwner) { emailValue ->
            if (binding.editTextEmail.text.toString() != emailValue) {
                binding.editTextEmail.setText(emailValue ?: "")
            }
        }

        viewModel.phoneNumber.observe(viewLifecycleOwner) { phone ->
            if (binding.editTextPhone.text.toString() != phone) {
                binding.editTextPhone.setText(phone ?: "")
            }
        }

        viewModel.vehicleName.observe(viewLifecycleOwner) { vehicle ->
            if (binding.editTextVehicleName.text.toString() != vehicle) {
                binding.editTextVehicleName.setText(vehicle ?: "")
            }
        }

        // Observer for Toast messages
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown() // Notify ViewModel that message has been shown
            }
        }
    }

    private fun setupTextWatchers() {
        binding.editTextName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateFullName(s.toString())
            }
        })

        binding.editTextEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateEmail(s.toString())
            }
        })

        binding.editTextPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updatePhoneNumber(s.toString())
            }
        })

        binding.editTextVehicleName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateVehicleName(s.toString())
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to prevent memory leaks
    }
}
