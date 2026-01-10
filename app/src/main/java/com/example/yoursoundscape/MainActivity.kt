package com.example.yoursoundscape

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yoursoundscape.data.AppDatabase
import com.example.yoursoundscape.data.Note
import com.example.yoursoundscape.databinding.ActivityMainBinding
import com.example.yoursoundscape.ui.NotesAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.widget.doAfterTextChanged


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NotesAdapter
    private var activePlayer: MediaPlayer? = null
    private var activeDialog: BottomSheetDialog? = null
    private var allNotes: List<com.example.yoursoundscape.data.Note> = emptyList()
    private enum class SortMode { NEWEST, OLDEST, LONGEST }
    private var sortMode: SortMode = SortMode.NEWEST


    private val addOrEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch { loadNotes() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        adapter = NotesAdapter(
            onClick = { note -> showNotePreview(note) },
            onLongClick = { note -> showManageMenu(note) }
        )

        binding.notesRecycler.layoutManager = LinearLayoutManager(this)
        binding.notesRecycler.adapter = adapter

        // --- Search / filtry / sort ---
        binding.etSearch.doAfterTextChanged {
            applyFilters()
        }

        binding.swOnlyWithPhoto.setOnCheckedChangeListener { _, _ ->
            applyFilters()
        }

        binding.swOnlyLongAudio.setOnCheckedChangeListener { _, _ ->
            applyFilters()
        }

        binding.spSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                sortMode = when (position) {
                    1 -> SortMode.OLDEST
                    2 -> SortMode.LONGEST
                    else -> SortMode.NEWEST
                }
                applyFilters()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnClearFilters.setOnClickListener {
            binding.etSearch.setText("")
            binding.swOnlyWithPhoto.isChecked = false
            binding.swOnlyLongAudio.isChecked = false
            binding.spSort.setSelection(0)
            sortMode = SortMode.NEWEST
            applyFilters()
        }

        // --- Add note ---
        binding.addNoteFab.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            addOrEditLauncher.launch(intent)
        }

        lifecycleScope.launch { loadNotes() }
    }


    private suspend fun loadNotes() {
        val dao = com.example.yoursoundscape.data.AppDatabase.getInstance(this).noteDao()
        allNotes = withContext(Dispatchers.IO) { dao.getAll() }
        applyFilters()
    }

    // --- LONG PRESS MENU: Edytuj / Usuń ---

    private fun showManageMenu(note: Note) {
        val items = arrayOf("Edytuj", "Usuń")

        AlertDialog.Builder(this)
            .setTitle("Notatka")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openEdit(note.id)
                    1 -> confirmDelete(note)
                }
            }
            .show()
    }

    private fun openEdit(noteId: Int) {
        val intent = Intent(this, AddNoteActivity::class.java).apply {
            putExtra(AddNoteActivity.EXTRA_NOTE_ID, noteId)
        }
        addOrEditLauncher.launch(intent)
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Usunąć notatkę?")
            .setMessage("Usunie notatkę oraz pliki audio i zdjęcie.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                lifecycleScope.launch { deleteNote(note) }
            }
            .show()
    }

    private suspend fun deleteNote(note: Note) {
        val dao = AppDatabase.getInstance(this).noteDao()

        withContext(Dispatchers.IO) {
            dao.delete(note)
            deleteFileIfExists(note.audioPath)
            deleteFileIfExists(note.imagePath)
        }

        Toast.makeText(this, "Usunięto", Toast.LENGTH_SHORT).show()
        loadNotes()
    }

    private fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return

        if (path.startsWith("content://")) {
            runCatching { contentResolver.delete(Uri.parse(path), null, null) }
            return
        }

        runCatching {
            val f = File(path)
            if (f.exists()) f.delete()
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()

        val onlyWithPhoto = binding.swOnlyWithPhoto.isChecked
        val onlyLongAudio = binding.swOnlyLongAudio.isChecked

        var filtered = allNotes

        // Search po tytule
        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.title.lowercase().contains(query) }
        }

        // Tylko ze zdjęciem
        if (onlyWithPhoto) {
            filtered = filtered.filter { !it.imagePath.isNullOrBlank() }
        }

        // Tylko audio > 30s (czyli co najmniej 31 sekund)
        if (onlyLongAudio) {
            filtered = filtered.filter { it.durationSeconds > 30 }
        }

        // Sortowanie
        filtered = when (sortMode) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortMode.OLDEST -> filtered.sortedBy { it.createdAt }
            SortMode.LONGEST -> filtered.sortedByDescending { it.durationSeconds }
        }

        adapter.submitList(filtered)

        val isEmpty = filtered.isEmpty()
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.notesRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }


    // --- PREVIEW (bez zmian, zostawiamy) ---

    private fun showNotePreview(note: com.example.yoursoundscape.data.Note) {
        // ✅ Autostop: jeśli wcześniej był otwarty inny podgląd / grało audio
        activePlayer?.release()
        activePlayer = null
        activeDialog?.dismiss()
        activeDialog = null

        val dialog = BottomSheetDialog(this)
        activeDialog = dialog

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_note_preview, null)
        dialog.setContentView(view)

        val titleText = view.findViewById<TextView>(R.id.previewTitle)
        val imageView = view.findViewById<ImageView>(R.id.previewImage)
        val noImageText = view.findViewById<TextView>(R.id.noImageText)

        val playPauseBtn = view.findViewById<MaterialButton>(R.id.playPauseBtn)
        val seekBar = view.findViewById<android.widget.SeekBar>(R.id.audioSeekBar)
        val timeText = view.findViewById<TextView>(R.id.audioTimeText)
        val audioErrorText = view.findViewById<TextView>(R.id.audioErrorText)
        val audioContainer = view.findViewById<View>(R.id.audioContainer)

        titleText.text = note.title

        // --- Zdjęcie ---
        if (!note.imagePath.isNullOrBlank()) {
            val bmp = BitmapFactory.decodeFile(note.imagePath)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                imageView.visibility = View.VISIBLE
                noImageText.visibility = View.GONE
            } else {
                imageView.visibility = View.GONE
                noImageText.visibility = View.VISIBLE
            }
        } else {
            imageView.visibility = View.GONE
            noImageText.visibility = View.VISIBLE
        }

        // --- Audio: walidacja pliku ---
        val path = note.audioPath
        val fileOk = path.isNotBlank() && java.io.File(path).exists()

        if (!fileOk) {
            // ✅ Obsługa błędów (plan)
            audioErrorText.visibility = View.VISIBLE
            playPauseBtn.visibility = View.GONE
            seekBar.visibility = View.GONE
            timeText.visibility = View.GONE

            Toast.makeText(this, "Nagranie niedostępne", Toast.LENGTH_SHORT).show()
            dialog.show()
            return
        } else {
            audioErrorText.visibility = View.GONE
            playPauseBtn.visibility = View.VISIBLE
            seekBar.visibility = View.VISIBLE
            timeText.visibility = View.VISIBLE
        }

        // --- MediaPlayer + UI ---
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var isPlaying = false
        var userSeeking = false

        fun formatMs(ms: Int): String {
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format("%02d:%02d", m, s)
        }

        fun updateTimeUi(currentMs: Int, totalMs: Int) {
            timeText.text = "${formatMs(currentMs)} / ${formatMs(totalMs)}"
            if (!userSeeking && totalMs > 0) {
                val progress = (currentMs * 100 / totalMs).coerceIn(0, 100)
                seekBar.progress = progress
            }
        }

        val player = MediaPlayer()
        activePlayer = player

        try {
            player.setDataSource(path)
            player.prepare()
        } catch (e: Exception) {
            activePlayer?.release()
            activePlayer = null

            audioErrorText.visibility = View.VISIBLE
            playPauseBtn.visibility = View.GONE
            seekBar.visibility = View.GONE
            timeText.visibility = View.GONE

            Toast.makeText(this, "Nagranie niedostępne", Toast.LENGTH_SHORT).show()
            dialog.show()
            return
        }

        val totalMs = player.duration.coerceAtLeast(0)
        updateTimeUi(0, totalMs)
        playPauseBtn.text = "Odtwórz"

        val tick = object : Runnable {
            override fun run() {
                val p = activePlayer ?: return
                if (p.isPlaying) {
                    updateTimeUi(p.currentPosition, totalMs)
                    handler.postDelayed(this, 250)
                }
            }
        }

        player.setOnCompletionListener {
            isPlaying = false
            playPauseBtn.text = "Odtwórz"
            seekBar.progress = 0
            updateTimeUi(0, totalMs)
            handler.removeCallbacks(tick)
        }

        playPauseBtn.setOnClickListener {
            val p = activePlayer ?: return@setOnClickListener

            if (!isPlaying) {
                p.start()
                isPlaying = true
                playPauseBtn.text = "Pauza"
                handler.post(tick)
            } else {
                p.pause()
                isPlaying = false
                playPauseBtn.text = "Odtwórz"
                handler.removeCallbacks(tick)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val p = activePlayer ?: return
                val newPos = (progress * totalMs / 100).coerceIn(0, totalMs)
                updateTimeUi(newPos, totalMs)
            }

            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                val p = activePlayer ?: return
                val progress = sb?.progress ?: 0
                val newPos = (progress * totalMs / 100).coerceIn(0, totalMs)
                p.seekTo(newPos)
                userSeeking = false
                updateTimeUi(p.currentPosition, totalMs)
            }
        })

        // ✅ Autostop: zamknięcie dialogu zatrzymuje odtwarzanie
        dialog.setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            activePlayer?.release()
            activePlayer = null
            activeDialog = null
        }

        dialog.show()
    }
}
