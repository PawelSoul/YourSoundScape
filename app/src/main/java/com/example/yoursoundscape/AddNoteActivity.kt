package com.example.yoursoundscape

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.yoursoundscape.data.AppDatabase
import com.example.yoursoundscape.data.Note
import com.example.yoursoundscape.databinding.ActivityAddNoteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AddNoteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }

    private lateinit var binding: ActivityAddNoteBinding

    private var noteId: Int? = null
    private var originalNote: Note? = null

    private var audioPath: String? = null
    private var imagePath: String? = null

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var durationSeconds: Int = 0

    private var pendingCameraFile: File? = null

    private val REQ_RECORD_AUDIO = 1001

    // ✅ Galeria
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val newPath = copyUriToAppFile(uri)
                replaceImagePath(newPath)
            }
        }

    // ✅ Aparat (zapis do pliku)
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                val f = pendingCameraFile
                if (f != null && f.exists()) {
                    replaceImagePath(f.absolutePath)
                } else {
                    Toast.makeText(this, "Nie udało się zapisać zdjęcia", Toast.LENGTH_SHORT).show()
                }
            } else {
                // użytkownik anulował
                pendingCameraFile = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1).takeIf { it != -1 }

        binding.titleEdit.doAfterTextChanged { updateSaveEnabled() }

        binding.stopBtn.isEnabled = false

        binding.recordBtn.setOnClickListener {
            if (hasMicPermission()) startRecording() else requestMicPermission()
        }

        binding.stopBtn.setOnClickListener { stopRecording() }

        // ✅ Zamiast samej galerii: wybór Galeria / Aparat
        binding.addPhotoBtn.setOnClickListener { showImageSourceDialog() }

        binding.removePhotoBtn.setOnClickListener {
            val old = imagePath
            imagePath = null
            binding.photoPreview.setImageDrawable(null)
            binding.photoPreview.visibility = View.GONE
            binding.photoStatusText.text = "Brak zdjęcia"
            binding.removePhotoBtn.isEnabled = false

            if (!old.isNullOrBlank()) deleteFileIfExists(old)
            updateSaveEnabled()
        }

        binding.saveBtn.setOnClickListener { saveOrUpdate() }

        binding.cancelBtn.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (noteId != null) {
            enterEditMode(noteId!!)
        } else {
            updateSaveEnabled()
        }
    }

    private fun showImageSourceDialog() {
        val items = arrayOf("Galeria", "Aparat")
        AlertDialog.Builder(this)
            .setTitle("Dodaj zdjęcie")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> openCamera()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }

        // sprawdź czy w ogóle jest aplikacja aparatu
        val cameraIntent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "Brak aplikacji aparatu na urządzeniu", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val dir = getExternalFilesDir("images") ?: filesDir
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            pendingCameraFile = file

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Nie można uruchomić aparatu: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }


    private fun replaceImagePath(newPath: String) {
        val old = imagePath
        imagePath = newPath

        showPhotoPreview(newPath)
        binding.photoStatusText.text = "Dodano zdjęcie"
        binding.photoPreview.visibility = View.VISIBLE
        binding.removePhotoBtn.isEnabled = true

        if (!old.isNullOrBlank() && old != newPath) deleteFileIfExists(old)
        updateSaveEnabled()
    }

    private fun enterEditMode(id: Int) {
        // audio bez zmian => ukrywamy nagrywanie
        binding.recordBtn.visibility = View.GONE
        binding.stopBtn.visibility = View.GONE
        binding.audioStatusText.text = "Audio: bez zmian"

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AddNoteActivity).noteDao()
            val note = withContext(Dispatchers.IO) { dao.getById(id) }

            if (note == null) {
                Toast.makeText(this@AddNoteActivity, "Nie znaleziono notatki", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            originalNote = note
            binding.titleEdit.setText(note.title)

            audioPath = note.audioPath
            durationSeconds = note.durationSeconds

            imagePath = note.imagePath
            if (!imagePath.isNullOrBlank()) {
                showPhotoPreview(imagePath!!)
                binding.photoStatusText.text = "Dodano zdjęcie"
                binding.photoPreview.visibility = View.VISIBLE
                binding.removePhotoBtn.isEnabled = true
            } else {
                binding.photoPreview.visibility = View.GONE
                binding.photoStatusText.text = "Brak zdjęcia"
                binding.removePhotoBtn.isEnabled = false
            }

            binding.saveBtn.text = "Zaktualizuj"
            updateSaveEnabled()
        }
    }

    private fun updateSaveEnabled() {
        val titleOk = !binding.titleEdit.text.isNullOrBlank()
        val audioOk = audioPath != null
        binding.saveBtn.isEnabled = titleOk && audioOk
    }

    private fun saveOrUpdate() {
        val title = binding.titleEdit.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            Toast.makeText(this, "Podaj tytuł", Toast.LENGTH_SHORT).show()
            return
        }
        if (audioPath == null) {
            Toast.makeText(this, "Dodaj nagranie", Toast.LENGTH_SHORT).show()
            return
        }

        val dao = AppDatabase.getInstance(this).noteDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val edit = originalNote
                if (edit == null) {
                    // ADD
                    dao.insert(
                        Note(
                            title = title,
                            audioPath = audioPath!!,
                            imagePath = imagePath,
                            createdAt = System.currentTimeMillis(),
                            durationSeconds = durationSeconds
                        )
                    )
                } else {
                    // EDIT (audio bez zmian)
                    dao.update(
                        edit.copy(
                            title = title,
                            imagePath = imagePath
                        )
                    )
                }
            }

            Toast.makeText(this@AddNoteActivity, "Zapisano", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQ_RECORD_AUDIO -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording()
                else binding.audioStatusText.text = "Brak uprawnień do mikrofonu"
            }

            REQ_CAMERA -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted) openCamera()
                else Toast.makeText(this, "Brak uprawnień do aparatu", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecording() {
        val dir = getExternalFilesDir(null) ?: filesDir
        recordingFile = File(dir, "note_${System.currentTimeMillis()}.m4a")

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(recordingFile!!.absolutePath)
            prepare()
            start()
        }

        recordingStartMs = System.currentTimeMillis()

        binding.audioStatusText.text = "Nagrywanie..."
        binding.recordBtn.isEnabled = false
        binding.stopBtn.isEnabled = true
        updateSaveEnabled()
    }

    private fun stopRecording() {
        val r = recorder ?: return
        try {
            r.stop()
        } catch (_: Exception) {
        } finally {
            r.release()
            recorder = null
        }

        val file = recordingFile
        if (file != null && file.exists() && file.length() > 0) {
            audioPath = file.absolutePath
            durationSeconds =
                ((System.currentTimeMillis() - recordingStartMs) / 1000)
                    .toInt()
                    .coerceAtLeast(1)
            binding.audioStatusText.text = "Gotowe: ${durationSeconds}s"
        } else {
            audioPath = null
            binding.audioStatusText.text = "Nagranie nie powstało"
        }

        binding.recordBtn.isEnabled = true
        binding.stopBtn.isEnabled = false
        updateSaveEnabled()
    }

    override fun onStop() {
        super.onStop()
        if (recorder != null) stopRecording()
    }

    private fun copyUriToAppFile(uri: Uri): String {
        val dir = getExternalFilesDir("images") ?: filesDir
        val outFile = File(dir, "img_${System.currentTimeMillis()}.jpg")

        contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(outFile).use { output ->
                if (input != null) input.copyTo(output)
            }
        }
        return outFile.absolutePath
    }

    private fun showPhotoPreview(path: String) {
        val bmp = BitmapFactory.decodeFile(path)
        if (bmp != null) {
            binding.photoPreview.setImageBitmap(bmp)
            binding.photoPreview.visibility = View.VISIBLE
        } else {
            binding.photoPreview.visibility = View.GONE
            binding.photoStatusText.text = "Nie udało się wczytać zdjęcia"
        }
    }

    private fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val f = File(path)
            if (f.exists()) f.delete()
        }
    }
    private val REQ_CAMERA = 2001

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQ_CAMERA
        )
    }
}
