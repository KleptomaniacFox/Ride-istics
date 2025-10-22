// model


package com.example.rideistics.ui.notifications

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID


class NotificationsViewModel(application: Application) : AndroidViewModel(application) {



    companion object {
        private const val TAG = "NotificationsViewModel"
        private const val PROFILE_JSON_FILENAME = "user_profile.json"
        private const val IMAGE_DIRECTORY_NAME = "profile_images"

        // JSON Keys
        private const val KEY_IMAGE_PATH = "profile_image_path"
        private const val KEY_FULL_NAME = "profile_full_name"
        private const val KEY_EMAIL = "profile_email"
        private const val KEY_PHONE = "profile_phone"
        private const val KEY_VEHICLE_NAME = "profile_vehicle_name"
        const val USER_DOCUMENTS_DIRECTORY_NAME = "user_documents"
    }


    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _profileImagePath = MutableLiveData<String?>() // Stores path to image in internal storage

    private val _fullName = MutableLiveData<String?>()
    val fullName: LiveData<String?> = _fullName

    private val _email = MutableLiveData<String?>()
    val email: LiveData<String?> = _email

    private val _phoneNumber = MutableLiveData<String?>()
    val phoneNumber: LiveData<String?> = _phoneNumber

    private val _vehicleName = MutableLiveData<String?>()
    val vehicleName: LiveData<String?> = _vehicleName

    // For Toast messages
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        loadProfileData()
    }

    // Call this after the toast is shown
    fun onToastMessageShown() {
        _toastMessage.value = null
    }

    // In NotificationsViewModel.kt

    suspend fun copyDocumentToInternalStorage(
        context: Context,
        documentUri: Uri,
        originalFileName: String?,
        customSubfolderName: String // Added customSubfolderName parameter
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Create the base user documents directory
                val baseDocumentsDir = File(context.filesDir, USER_DOCUMENTS_DIRECTORY_NAME)
                if (!baseDocumentsDir.exists()) {
                    baseDocumentsDir.mkdirs()
                }

                // Create the custom subfolder within the base documents directory
                val customSubfolderDir = File(baseDocumentsDir, customSubfolderName)
                if (!customSubfolderDir.exists()) {
                    customSubfolderDir.mkdirs()
                }

                // Sanitize filename or create a unique one
                val fileName = originalFileName?.replace("[^a-zA-Z0-9._-]".toRegex(), "_") ?: "document_${UUID.randomUUID()}"
                var destinationFile = File(customSubfolderDir, fileName) // Save in the custom subfolder
                var counter = 1
                // Handle potential filename conflicts by appending a number
                while (destinationFile.exists()) {
                    val nameWithoutExtension = fileName.substringBeforeLast('.')
                    val extension = fileName.substringAfterLast('.', "")
                    destinationFile = File(customSubfolderDir, "${nameWithoutExtension}_${counter++}${if (extension.isNotEmpty()) ".$extension" else ""}")
                }

                context.contentResolver.openInputStream(documentUri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Document copied to: ${destinationFile.absolutePath} in subfolder '$customSubfolderName'")
                destinationFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error copying document to internal storage in subfolder '$customSubfolderName'", e)
                null
            }
        }
    }



    // Function to be called from Fragment
    fun handleSelectedDocument(documentUri: Uri, originalFileName: String?, customSubfolderName: String) {
        viewModelScope.launch { // Launches a coroutine for background work
            val context = getApplication<Application>().applicationContext
            // THIS IS THE ACTUAL SAVING
            val savedPath = copyDocumentToInternalStorage(context, documentUri, originalFileName, customSubfolderName)
            if (savedPath != null) {
                Log.i(TAG, "Document saved successfully into '$customSubfolderName' at $savedPath")
            } else {
                Log.e(TAG, "Failed to save document into '$customSubfolderName'.")
            }
        }
    }


    private fun loadProfileData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                val profileFile = File(context.filesDir, PROFILE_JSON_FILENAME)
                if (profileFile.exists()) {
                    try {
                        val jsonString = profileFile.readText()
                        val jsonObject = JSONObject(jsonString)

                        _fullName.postValue(jsonObject.optString(KEY_FULL_NAME, null))
                        _email.postValue(jsonObject.optString(KEY_EMAIL, null))
                        _phoneNumber.postValue(jsonObject.optString(KEY_PHONE, null))
                        _vehicleName.postValue(jsonObject.optString(KEY_VEHICLE_NAME, null))

                        val imagePath = jsonObject.optString(KEY_IMAGE_PATH, null)
                        _profileImagePath.postValue(imagePath)
                        if (imagePath != null) {
                            val imageFile = File(imagePath)
                            if (imageFile.exists()) {
                                _selectedImageUri.postValue(Uri.fromFile(imageFile))
                            } else {
                                _selectedImageUri.postValue(null)
                                _profileImagePath.postValue(null) // Clear invalid path
                            }
                        } else {
                            _selectedImageUri.postValue(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading profile data from JSON", e)
                        clearAllData() // Reset if JSON is corrupt or unreadable
                    }
                } else {
                    Log.i(TAG, "Profile JSON file does not exist. Starting fresh.")
                    clearAllData() // Initialize LiveData to null/empty on main thread
                }
            }
        }
    }


    private fun saveProfileData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                val profileFile = File(context.filesDir, PROFILE_JSON_FILENAME)
                val jsonObject = JSONObject()
                try {
                    jsonObject.put(KEY_FULL_NAME, _fullName.value)
                    jsonObject.put(KEY_EMAIL, _email.value)
                    jsonObject.put(KEY_PHONE, _phoneNumber.value)
                    jsonObject.put(KEY_VEHICLE_NAME, _vehicleName.value)
                    jsonObject.put(KEY_IMAGE_PATH, _profileImagePath.value) // Save the path

                    profileFile.writeText(jsonObject.toString(4)) // Use 4 for pretty print
                    Log.d(TAG, "Profile data saved to ${profileFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving profile data to JSON", e)
                }
            }
        }
    }

    private suspend fun copyImageToInternalStorage(contentUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            try {
                val imageDir = File(context.filesDir, IMAGE_DIRECTORY_NAME)
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                // Use a consistent filename or a unique one if you expect multiple images over time
                // For a single profile picture, a consistent name is fine and simplifies deletion.
                val extension = context.contentResolver.getType(contentUri)?.substringAfterLast('/') ?: "jpg"
                val fileName = "profile_image.$extension"
                val destinationFile = File(imageDir, fileName)

                context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Image copied to: ${destinationFile.absolutePath}")
                destinationFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error copying image to internal storage", e)
                null
            }
        }
    }

    private suspend fun deleteImageFromInternalStorage(filePath: String?) {
        if (filePath == null) return
        withContext(Dispatchers.IO) {
            try {
                val fileToDelete = File(filePath)
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d(TAG, "Deleted image: $filePath")
                    } else {
                        Log.w(TAG, "Failed to delete image: $filePath")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting image: $filePath", e)
            }
        }
    }

    fun setSelectedImageUri(contentUri: Uri?) {
        // If user cancelled the picker, do nothing.
        if (contentUri == null) {
            return
        }

        viewModelScope.launch {
            val oldImagePath = _profileImagePath.value
            val newImagePath = copyImageToInternalStorage(contentUri) // contentUri is not null here

            if (newImagePath != null) {
                // Successfully copied new image
                if (oldImagePath != null && oldImagePath != newImagePath) { // Should not happen with fixed name "profile_image.ext"
                    deleteImageFromInternalStorage(oldImagePath)
                }
                _profileImagePath.postValue(newImagePath)
                _selectedImageUri.postValue(Uri.fromFile(File(newImagePath)))
                _toastMessage.postValue("Profile pic updated !")
                saveProfileData() // Save profile since image path has changed
            } else {
                // Failed to copy new image
                _toastMessage.postValue("Failed to update profile pic.")
                // Don't save profile data, as no change to image path occurred
            }
        }
    }

    fun clearProfileImage() {
        viewModelScope.launch {
            val oldImagePath = _profileImagePath.value
            if (oldImagePath != null) {
                deleteImageFromInternalStorage(oldImagePath)
            }
            _profileImagePath.postValue(null)
            _selectedImageUri.postValue(null)
            _toastMessage.postValue("Profile pic cleared.")
            saveProfileData() // Save profile since image path is now null
        }
    }

    private fun clearAllData() {
        // To be called from background thread
        _fullName.postValue(null)
        _email.postValue(null)
        _phoneNumber.postValue(null)
        _vehicleName.postValue(null)
        _profileImagePath.postValue(null)
        _selectedImageUri.postValue(null)
    }
    private fun clearAllDataOnMainThread() {
        // To be called from main thread (e.g. if file doesn't exist initially)
        _fullName.value = null
        _email.value = null
        _phoneNumber.value = null
        _vehicleName.value = null
        _profileImagePath.value = null
        _selectedImageUri.value = null
    }


    fun updateFullName(name: String?) {
        if (_fullName.value != name) {
            _fullName.value = name
            saveProfileData()
        }
    }

    fun updateEmail(emailValue: String?) {
        if (_email.value != emailValue) {
            _email.value = emailValue
            saveProfileData()
        }
    }

    fun updatePhoneNumber(phone: String?) {
        if (_phoneNumber.value != phone) {
            _phoneNumber.value = phone
            saveProfileData()
        }
    }

    fun updateVehicleName(vehicle: String?) {
        if (_vehicleName.value != vehicle) {
            _vehicleName.value = vehicle
            saveProfileData()
        }
    }
}
