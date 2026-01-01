package com.example.yoursoundscape

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.yoursoundscape.data.AppDatabase
import com.example.yoursoundscape.data.Note

import androidx.recyclerview.widget.LinearLayoutManager

import com.example.yoursoundscape.databinding.ActivityMainBinding
import com.example.yoursoundscape.ui.NotesAdapter

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import android.graphics.BitmapFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NotesAdapter
    private val addNoteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch { loadNotes() }
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1) ViewBinding zamiast setContentView(R.layout...)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2) Zostawiamy Twoje insets (żeby nie było pod status bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 3) Adapter + RecyclerView
        adapter = NotesAdapter { note ->
            showNotePreview(note)
        }

        binding.notesRecycler.layoutManager = LinearLayoutManager(this)
        binding.notesRecycler.adapter = adapter

        val dao = AppDatabase.getInstance(this).noteDao()

        // 4) (tylko na start) dodajemy 3 testowe notatki, jeśli baza pusta
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val existing = dao.getAll()
                if (existing.isEmpty()) {
                    repeat(3) { i ->
                        dao.insert(
                            Note(
                                title = "Testowa notatka #${i + 1}",
                                audioPath = "test/path_${System.currentTimeMillis()}_$i.m4a",
                                imagePath = null,
                                createdAt = System.currentTimeMillis(),
                                durationSeconds = 10 + i
                            )
                        )
                    }
                }
            }
            loadNotes()
        }

        binding.addNoteFab.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            addNoteLauncher.launch(intent)
        }
    }

    private suspend fun loadNotes() {
        val dao = AppDatabase.getInstance(this).noteDao()
        val notes = withContext(Dispatchers.IO) { dao.getAll() }
        adapter.submitList(notes)
    }

    private fun showNotePreview(note: com.example.yoursoundscape.data.Note) {
        val dialog = BottomSheetDialog(this)

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_note_preview, null)
        dialog.setContentView(view)

        val titleText = view.findViewById<TextView>(R.id.previewTitle)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)
        val noImageText = view.findViewById<TextView>(R.id.noImageText)
        val playPauseBtn = view.findViewById<MaterialButton>(R.id.playPauseBtn)

        titleText.text = note.title

        // Zdjęcie
        if (!note.imagePath.isNullOrBlank()) {
            val bmp = BitmapFactory.decodeFile(note.imagePath)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                imageView.visibility = android.view.View.VISIBLE
                noImageText.visibility = android.view.View.GONE
            } else {
                imageView.visibility = android.view.View.GONE
                noImageText.visibility = android.view.View.VISIBLE
            }
        } else {
            imageView.visibility = android.view.View.GONE
            noImageText.visibility = android.view.View.VISIBLE
        }

        // Audio (play/pause)
        var player: MediaPlayer? = null
        var isPlaying = false

        fun stopPlayer() {
            player?.release()
            player = null
            isPlaying = false
            playPauseBtn.text = "Odtwórz"
        }

        playPauseBtn.setOnClickListener {
            if (!isPlaying) {
                try {
                    if (player == null) {
                        player = MediaPlayer().apply {
                            setDataSource(note.audioPath)
                            prepare()
                            setOnCompletionListener {
                                stopPlayer()
                            }
                        }
                    }
                    player?.start()
                    isPlaying = true
                    playPauseBtn.text = "Pauza"
                } catch (e: Exception) {
                    stopPlayer()
                    playPauseBtn.text = "Błąd odtwarzania"
                }
            } else {
                player?.pause()
                isPlaying = false
                playPauseBtn.text = "Odtwórz"
            }
        }

        dialog.setOnDismissListener {
            stopPlayer()
        }

        dialog.show()
    }

}