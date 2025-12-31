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
            Log.d("YourSoundScape", "Clicked note id=${note.id}")
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
}