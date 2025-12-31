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

class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding

    // Na razie „mock” — potem tu wpiszemy realne ścieżki z MediaRecorder / aparatu
    private var audioPath: String? = null
    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleEdit.doAfterTextChanged { updateSaveEnabled() }

        binding.recordBtn.setOnClickListener {
            // TU potem będzie start nagrywania
            audioPath = "audio_${System.currentTimeMillis()}.m4a"
            binding.audioStatusText.text = "Gotowe: nagranie przygotowane"
            binding.recordBtn.isEnabled = false
            binding.stopBtn.isEnabled = true
            updateSaveEnabled()
        }

        binding.stopBtn.setOnClickListener {
            // TU potem będzie stop nagrywania
            binding.recordBtn.isEnabled = true
            binding.stopBtn.isEnabled = false
            Toast.makeText(this, "Zatrzymano nagranie", Toast.LENGTH_SHORT).show()
            updateSaveEnabled()
        }

        binding.addPhotoBtn.setOnClickListener {
            // TU potem będzie kamera
            imagePath = "img_${System.currentTimeMillis()}.jpg"
            binding.photoStatusText.text = "Dodano zdjęcie (mock)"
            binding.removePhotoBtn.isEnabled = true
            // preview na razie ukryty, bo nie mamy realnego bitmapa
        }

        binding.removePhotoBtn.setOnClickListener {
            imagePath = null
            binding.photoStatusText.text = "Brak zdjęcia"
            binding.removePhotoBtn.isEnabled = false
            binding.photoPreview.visibility = android.view.View.GONE
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
                        durationSeconds = 0
                    )
                )
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
