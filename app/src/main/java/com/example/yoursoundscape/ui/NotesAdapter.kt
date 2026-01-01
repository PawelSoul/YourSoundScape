package com.example.yoursoundscape.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.yoursoundscape.data.Note
import com.example.yoursoundscape.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onClick: (Note) -> Unit
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NoteVH(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            // Tytuł: #id + tytuł
            binding.noteTitle.text = "#${note.id} ${note.title}"

            // Podtytuł: data + czas nagrania
            val date = dateFormat.format(Date(note.createdAt))
            binding.noteSubtitle.text = "$date • ${note.durationSeconds}s"

            // Miniatura zdjęcia (jeśli jest)
            if (!note.imagePath.isNullOrBlank()) {
                val bmp = BitmapFactory.decodeFile(note.imagePath)
                if (bmp != null) {
                    binding.noteImage.setImageBitmap(bmp)
                } else {
                    binding.noteImage.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                binding.noteImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            // Klik w element
            binding.root.setOnClickListener { onClick(note) }
        }
    }
}
