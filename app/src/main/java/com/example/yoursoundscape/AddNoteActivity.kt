package com.example.yoursoundscape

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.yoursoundscape.data.AppDatabase
import com.example.yoursoundscape.data.Note
import com.example.yoursoundscape.databinding.ActivityAddNoteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.FileOutputStream
import android.graphics.BitmapFactory


class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding

    // Na razie „mock” — potem tu wpiszemy realne ścieżki z MediaRecorder / aparatu
    private var audioPath: String? = null
    private var imagePath: String? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var durationSeconds: Int = 0
    private val REQ_RECORD_AUDIO = 1001
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val savedPath = copyUriToAppFile(uri)
                imagePath = savedPath
                showPhotoPreview(savedPath)

                binding.photoStatusText.text = "Dodano zdjęcie"
                binding.photoPreview.visibility = android.view.View.VISIBLE
                binding.removePhotoBtn.isEnabled = true

                updateSaveEnabled()
            }
        }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleEdit.doAfterTextChanged { updateSaveEnabled() }

        binding.recordBtn.setOnClickListener {
            if (hasMicPermission()) startRecording() else requestMicPermission()
        }

        binding.stopBtn.setOnClickListener {
            stopRecording()
        }


        binding.addPhotoBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")

        }

        binding.removePhotoBtn.setOnClickListener {
            imagePath = null
            binding.photoPreview.setImageDrawable(null)
            binding.photoPreview.visibility = android.view.View.GONE
            binding.photoStatusText.text = "Brak zdjęcia"
            binding.removePhotoBtn.isEnabled = false
            updateSaveEnabled()
        }

        binding.saveBtn.setOnClickListener { saveNote() }

        binding.cancelBtn.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        updateSaveEnabled()
    }

    private fun updateSaveEnabled() {
        val titleOk = !binding.titleEdit.text.isNullOrBlank()
        val audioOk = audioPath != null
        binding.saveBtn.isEnabled = titleOk && audioOk
    }

    private fun saveNote() {
        val title = binding.titleEdit.text?.toString()?.trim().orEmpty()
        val audio = audioPath

        if (title.isBlank()) {
            Toast.makeText(this, "Podaj tytuł", Toast.LENGTH_SHORT).show()
            return
        }
        if (audio == null) {
            Toast.makeText(this, "Dodaj nagranie", Toast.LENGTH_SHORT).show()
            return
        }

        val dao = AppDatabase.getInstance(this).noteDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.insert(
                    Note(
                        title = title,
                        audioPath = audio,
                        imagePath = imagePath,
                        createdAt = System.currentTimeMillis(),
                        durationSeconds = durationSeconds
                    )
                )
            }
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

        if (requestCode == REQ_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) startRecording()
            else binding.audioStatusText.text = "Brak uprawnień do mikrofonu"
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
            durationSeconds = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt().coerceAtLeast(1)
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
            binding.photoPreview.visibility = android.view.View.VISIBLE
        } else {
            binding.photoPreview.visibility = android.view.View.GONE
            binding.photoStatusText.text = "Nie udało się wczytać zdjęcia"
        }
    }

}
