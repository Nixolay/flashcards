package com.example.flashcards

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*

class MainActivity : AppCompatActivity() {

    private val groups = mutableListOf<FlashcardGroup>()
    private var currentGroupIndex = 0
        set(value) {
            field = value
            title = "Card: ${groups[value].name}"
            cardAdapter.updateCards(groups[value].cards)
        }

    private lateinit var groupSpinner: Spinner
    private lateinit var recyclerView: RecyclerView

    // ✅ Изменённые типы переменных
    private lateinit var addButton: ImageButton
    private lateinit var newGroupButton: ImageButton
    private lateinit var exportButton: ImageButton
    private lateinit var importButton: ImageButton

    private lateinit var cardAdapter: CardAdapter

    // === SAF: Экспорт с выбором места ===
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    val json = Gson().toJson(groups)
                    stream.write(json.toByteArray())
                }
                Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // === SAF: Импорт с выбором файла ===
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = InputStreamReader(stream)
                    val gson = Gson()
                    val type = object : TypeToken<List<FlashcardGroup>>() {}.type
                    val loaded = gson.fromJson<List<FlashcardGroup>>(reader, type) ?: emptyList()
                    groups.clear()
                    groups.addAll(loaded)
                    groups.forEach { group -> group.cards.forEach { it.isFlipped = false } }
                    setupGroupSpinner()
                    currentGroupIndex = 0
                    Toast.makeText(this, "Imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error import", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // === Свайп для удаления ===
    private val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private var deletedCard: Flashcard? = null
        private var deletedPosition = -1

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            deletedCard = groups[currentGroupIndex].cards[position]
            deletedPosition = position

            // Удаляем из списка и обновляем UI
            groups[currentGroupIndex].cards.removeAt(position)
            cardAdapter.notifyItemRemoved(position)

            // Показываем Snackbar с отменой
            Snackbar.make(recyclerView, "Dell card?", Snackbar.LENGTH_LONG)
                .setAction("Restore") {
                    // Восстанавливаем карточку
                    if (deletedCard != null) {
                        groups[currentGroupIndex].cards.add(deletedPosition, deletedCard!!)
                        cardAdapter.notifyItemInserted(deletedPosition)
                        deletedCard = null
                        deletedPosition = -1
                    }
                }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        // Если пользователь НЕ нажал "Отмена" — окончательно удаляем
                        if (event != DISMISS_EVENT_ACTION && deletedCard != null) {
                            deletedCard = null
                            deletedPosition = -1
                            saveGroupsToFile() // Сохраняем изменения
                        }
                    }
                })
                .show()
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView
            val icon: Drawable? = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_delete)
            val background = ColorDrawable(Color.RED)

            val iconMargin = (itemView.height - icon!!.intrinsicHeight) / 2
            val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
            val iconBottom = iconTop + icon.intrinsicHeight

            if (dX > 0) {
                val iconLeft = itemView.left + iconMargin
                val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
            } else if (dX < 0) {
                val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
            } else {
                background.setBounds(0, 0, 0, 0)
            }

            background.draw(c)
            icon.draw(c)
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        groupSpinner = findViewById(R.id.groupSpinner)
        recyclerView = findViewById(R.id.recyclerView)

        // ✅ Теперь это ImageButtons
        addButton = findViewById(R.id.addButton)
        newGroupButton = findViewById(R.id.newGroupButton)
        exportButton = findViewById(R.id.exportButton)
        importButton = findViewById(R.id.importButton)

        loadGroupsFromFile()
        if (groups.isEmpty()) {
            groups.add(FlashcardGroup("Default"))
        }

        cardAdapter = CardAdapter(
            cards = groups[currentGroupIndex].cards,
            onCardClick = { position ->
                val card = groups[currentGroupIndex].cards[position]
                card.isFlipped = !card.isFlipped
                cardAdapter.notifyItemChanged(position)
            },
            onCardLongClick = { position ->
                showEditCardDialog(position)
            }
        )

        recyclerView.adapter = cardAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(recyclerView) // 👈 подключаем свайп

        setupGroupSpinner()

        // ✅ Добавляем обработчик долгого нажатия на Spinner
        groupSpinner.setOnLongClickListener {
            showGroupOptionsDialog()
            true
        }

        addButton.setOnClickListener { showAddCardDialog() }
        newGroupButton.setOnClickListener { showNewGroupDialog() }
        exportButton.setOnClickListener { exportFileLauncher.launch("flashcards.json") }
        importButton.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json"))
        }

        // Подсказки при долгом нажатии на кнопки
        addButton.setOnLongClickListener { Toast.makeText(this, "Добавить карточку", Toast.LENGTH_SHORT).show(); true }
        newGroupButton.setOnLongClickListener { Toast.makeText(this, "Создать группу", Toast.LENGTH_SHORT).show(); true }
        exportButton.setOnLongClickListener { Toast.makeText(this, "Экспортировать", Toast.LENGTH_SHORT).show(); true }
        importButton.setOnLongClickListener { Toast.makeText(this, "Импортировать", Toast.LENGTH_SHORT).show(); true }
    }

    // === Управление группами ===

    private fun setupGroupSpinner() {
        val groupNames = groups.map { it.name }
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            groupNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 16f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.textSize = 16f
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        groupSpinner.adapter = adapter
        groupSpinner.setSelection(currentGroupIndex)
        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentGroupIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showGroupOptionsDialog() {
        val options = arrayOf("Edit", "Remove")
        AlertDialog.Builder(this)
            .setTitle("Group: ${groups[currentGroupIndex].name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditGroupNameDialog()
                    1 -> showDeleteGroupDialog()
                }
            }
            .show()
    }

    private fun showEditGroupNameDialog() {
        val editText = EditText(this)
        editText.setText(groups[currentGroupIndex].name)
        editText.selectAll()

        AlertDialog.Builder(this)
            .setTitle("Edit name")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != groups[currentGroupIndex].name) {
                    if (groups.none { it.name == newName }) {
                        groups[currentGroupIndex].name = newName
                        setupGroupSpinner()
                        saveGroupsToFile()
                    } else {
                        Toast.makeText(this, "Group already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Restore", null)
            .show()
    }

    private fun showDeleteGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Remove group?")
            .setMessage("Are you sure you want to delete the group '${groups[currentGroupIndex].name}'?")
            .setPositiveButton("Remove") { _, _ ->
                val groupName = groups[currentGroupIndex].name
                groups.removeAt(currentGroupIndex)

                if (groups.isEmpty()) {
                    groups.add(FlashcardGroup("Default"))
                    currentGroupIndex = 0
                } else if (currentGroupIndex >= groups.size) {
                    currentGroupIndex = groups.size - 1
                }

                setupGroupSpinner()
                cardAdapter.updateCards(groups[currentGroupIndex].cards)
                saveGroupsToFile()
                Toast.makeText(this, "Group '$groupName' removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === Управление карточками ===

    private fun showAddCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_card_edit, null)
        val frontInput = dialogView.findViewById<EditText>(R.id.editTextFront)
        val backInput = dialogView.findViewById<EditText>(R.id.editTextBack)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("New card")
            .setPositiveButton("Add") { _, _ ->
                val front = frontInput.text.toString().trim()
                val back = backInput.text.toString().trim()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    groups[currentGroupIndex].cards.add(Flashcard(front, back))
                    cardAdapter.notifyItemInserted(groups[currentGroupIndex].cards.size - 1)
                    saveGroupsToFile()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCardDialog(position: Int) {
        val card = groups[currentGroupIndex].cards[position]
        val dialogView = layoutInflater.inflate(R.layout.dialog_card_edit, null)
        val frontInput = dialogView.findViewById<EditText>(R.id.editTextFront)
        val backInput = dialogView.findViewById<EditText>(R.id.editTextBack)

        frontInput.setText(card.front)
        backInput.setText(card.back)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Edit card")
            .setPositiveButton("Save") { _, _ ->
                val front = frontInput.text.toString().trim()
                val back = backInput.text.toString().trim()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    groups[currentGroupIndex].cards[position].front = front
                    groups[currentGroupIndex].cards[position].back = back
                    cardAdapter.notifyItemChanged(position)
                    saveGroupsToFile()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewGroupDialog() {
        val editText = EditText(this)
        editText.hint = "New group"
        AlertDialog.Builder(this)
            .setTitle("Create group")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty() && groups.none { it.name == name }) {
                    groups.add(FlashcardGroup(name))
                    setupGroupSpinner()
                    currentGroupIndex = groups.lastIndex
                    saveGroupsToFile()
                } else {
                    Toast.makeText(this, "The group already exists", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === Автоматическое сохранение/загрузка в Android/media/... ===

    private fun getFlashcardsFile(): File {
        val mediaDirs = getExternalMediaDirs()
        if (mediaDirs.isNotEmpty() && mediaDirs[0] != null) {
            val dir = mediaDirs[0]!!
            dir.mkdirs()
            return File(dir, "flashcards.json")
        }
        return File(getExternalFilesDir(null), "flashcards.json")
    }

    private fun saveGroupsToFile() {
        try {
            val file = getFlashcardsFile()
            FileWriter(file).use { it.write(Gson().toJson(groups)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadGroupsFromFile() {
        try {
            val file = getFlashcardsFile()
            if (file.exists()) {
                InputStreamReader(file.inputStream()).use { reader ->
                    val gson = Gson()
                    val type = object : TypeToken<List<FlashcardGroup>>() {}.type
                    val loaded = gson.fromJson<List<FlashcardGroup>>(reader, type) ?: emptyList()
                    groups.clear()
                    groups.addAll(loaded)
                    groups.forEach { group -> group.cards.forEach { it.isFlipped = false } }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        saveGroupsToFile()
    }
}