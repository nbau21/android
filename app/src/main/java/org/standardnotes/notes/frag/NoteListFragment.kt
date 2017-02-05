package org.standardnotes.notes.frag

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.frag_note_list.*
import org.joda.time.format.DateTimeFormat
import org.standardnotes.notes.NoteActivity
import org.standardnotes.notes.R
import org.standardnotes.notes.SApplication
import org.standardnotes.notes.comms.Crypt
import org.standardnotes.notes.comms.data.Note
import org.standardnotes.notes.comms.data.SyncItems
import org.standardnotes.notes.comms.data.UploadSyncItems
import org.standardnotes.notes.dpToPixels
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class NoteListFragment : Fragment() {


    private val REQ_EDIT_NOTE: Int = 1

    val adapter: Adapter by lazy { Adapter() }

    var notes = ArrayList<Note>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.frag_note_list, container, false)
        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            // Horzontal divider lines between each item
            val paint = Paint()

            init {
                paint.color = resources.getColor(R.color.feint_gray)
            }

            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
                super.onDraw(c, parent, state)
                val left = parent.paddingLeft + 16.dpToPixels().toFloat()
                val right = parent.width - parent.paddingRight.toFloat()
                val childCount = parent.childCount
                for (i in 0..childCount - 1) {
                    val child = parent.getChildAt(i)
                    val params = child
                            .layoutParams as RecyclerView.LayoutParams
                    val top = child.bottom + params.bottomMargin.toFloat()
                    val bottom = top + 1.dpToPixels()
                    c.drawRect(left, top, right, bottom, paint)
                }
            }
        })
        swipeRefreshLayout.setOnRefreshListener { sync() }
        notes = ArrayList(SApplication.instance!!.noteStore.notesList)
        sync()
        list.adapter = adapter
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_EDIT_NOTE) {
                notes = ArrayList(SApplication.instance!!.noteStore.notesList)
                adapter.notifyDataSetChanged()
                if (SApplication.instance!!.noteStore.notesToSaveCount() > 0) {
                    sync()
                }
            }
//        }
    }

    // TODO move this out to some manager class
    fun sync() {
        swipeRefreshLayout.isRefreshing = true
        val uploadSyncItems = UploadSyncItems()
        uploadSyncItems.syncToken = SApplication.instance!!.noteStore.syncToken
        val dirtyItems = SApplication.instance!!.noteStore.toSave
        dirtyItems
                .map { Crypt.encrypt(it) }
                .forEach { uploadSyncItems.items.add(it) }
        SApplication.instance!!.comms.api.sync(uploadSyncItems).enqueue(object : Callback<SyncItems> {
            override fun onResponse(call: Call<SyncItems>, response: Response<SyncItems>) {
                notes.clear()
                SApplication.instance!!.noteStore.putItems(response.body())
                notes = ArrayList(SApplication.instance!!.noteStore.notesList)
                swipeRefreshLayout.isRefreshing = false
                adapter.notifyDataSetChanged()
            }

            override fun onFailure(call: Call<SyncItems>, t: Throwable) {
                Toast.makeText(activity, "Failed to sync", Toast.LENGTH_SHORT).show()
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    fun startNewNote() {
        startActivityForResult(Intent(activity, NoteActivity::class.java), REQ_EDIT_NOTE)
    }

    inner class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var note: Note? = null
            get
            set(value) {
                field = value
                title.text = note?.title
                date.text = DateTimeFormat.shortDateTime().print(note?.updatedAt)
                var noteText = note?.text ?: ""
                noteText = noteText.substring(0, Math.min(256, noteText.length))
                noteText.replace('\n', ' ')
                text.text = noteText
                synced.visibility = if (note?.dirty == true) View.VISIBLE else View.INVISIBLE
            }
        private val title: TextView = itemView.findViewById(R.id.title) as TextView
        private val date: TextView = itemView.findViewById(R.id.date) as TextView
        private val text: TextView = itemView.findViewById(R.id.text) as TextView
        private val synced: View = itemView.findViewById(R.id.synced)

        init {
            itemView.setOnClickListener {
                val intent: Intent = Intent(activity, NoteActivity::class.java)
                intent.putExtra("noteId", note?.uuid)
                startActivityForResult(intent, REQ_EDIT_NOTE)
            }
            itemView.setOnLongClickListener {
                val popup = PopupMenu(activity, itemView)
                popup.menu.add("Delete")
                popup.setOnMenuItemClickListener {
                    SApplication.instance!!.noteStore.deleteItem(note!!.uuid)
                    notes = ArrayList(SApplication.instance!!.noteStore.notesList)
                    adapter.notifyDataSetChanged()
                    sync()
                    true
                }
                popup.show()
                true
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<NoteHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): NoteHolder {
            return NoteHolder(LayoutInflater.from(activity).inflate(R.layout.item_note, parent, false))
        }

        override fun getItemCount(): Int {
            return notes.count()
        }

        override fun onBindViewHolder(holder: NoteHolder, position: Int) {
            val note: Note = notes[position]
            holder.note = note
        }

    }

}