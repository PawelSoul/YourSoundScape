package com.example.yoursoundscape.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.yoursoundscape.data.Note
import com.example.yoursoundscape.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteVH>() {

    private val items = mutableListOf<Note>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun submitList(newItems: List<Note>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteVH {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteVH(binding)
    }

    override fun onBindViewHolder(holder: NoteVH, position: Int) {
        holder.bind(items[position], onClick, onLongClick, dateFormat)
    }

    override fun getItemCount(): Int = items.size

    class NoteVH(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            note: Note,
            onClick: (Note) -> Unit,
            onLongClick: (Note) -> Unit,
            dateFormat: SimpleDateFormat
        ) {
            binding.noteTitle.text = note.title

            val dateText = dateFormat.format(Date(note.createdAt))
            val durText = if (note.durationSeconds > 0) "${note.durationSeconds}s" else "—"
            binding.noteSubtitle.text = "$dateText • $durText"

            // --- Miniatura / placeholder ---
            val imgPath = note.imagePath
            if (imgPath.isNullOrBlank()) {
                binding.noteImage.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.noteImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
            } else {
                val bmp = BitmapFactory.decodeFile(imgPath)
                if (bmp != null) {
                    binding.noteImage.setImageBitmap(bmp)
                    binding.noteImage.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    binding.noteImage.setImageResource(android.R.drawable.ic_menu_gallery)
                    binding.noteImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }

            // --- Klik / Long press ---
            binding.root.setOnClickListener { onClick(note) }
            binding.root.setOnLongClickListener {
                onLongClick(note)
                true
            }

            // ✅ Delikatna animacja pojawienia (tylko raz na ViewHolder, żeby nie migało przy scrollu)
            val alreadyAnimated = binding.root.getTag(binding.root.id) as? Boolean ?: false
            if (!alreadyAnimated) {
                binding.root.alpha = 0f
                binding.root.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .start()
                binding.root.setTag(binding.root.id, true)
            } else {
                binding.root.alpha = 1f
            }
        }
    }
}
