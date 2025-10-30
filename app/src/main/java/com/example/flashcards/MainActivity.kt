package com.example.flashcards

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

import android.net.Uri
import android.os.Bundle
import android.view.* import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
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
        groups[value].cards.forEach { it.isFlipped = false }
        cardAdapter.updateCards(groups[value].cards)
        // Обновляем Spinner в Action Bar при смене группы (если он уже создан)
        if (supportActionBar?.customView is Spinner) {
            (supportActionBar?.customView as Spinner).setSelection(value)
        }
    }

    private lateinit var groupSpinner: Spinner
    private lateinit var recyclerView: RecyclerView

    private lateinit var cardAdapter: CardAdapter

    // ... (весь код exportFileLauncher, importFileLauncher, swipeToDeleteCallback) ...
    // (Код SAF и Свайпа для удаления остается без изменений, я его скрою для краткости)
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

            groups[currentGroupIndex].cards.removeAt(position)
            cardAdapter.notifyItemRemoved(position)

            Snackbar.make(this@MainActivity.recyclerView, "Dell card?", Snackbar.LENGTH_LONG)
                .setAction("Restore") {
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
                        if (event != DISMISS_EVENT_ACTION && deletedCard != null) {
                            deletedCard = null
                            deletedPosition = -1
                            saveGroupsToFile()
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
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(recyclerView)

        setupGroupSpinner()

        // === ПЕРЕМЕЩЕНИЕ SPINNER В ACTION BAR ===
        val actionBar = supportActionBar
        if (actionBar != null) {
            val spinnerInActionBar = Spinner(actionBar.themedContext)

            spinnerInActionBar.adapter = groupSpinner.adapter
            spinnerInActionBar.onItemSelectedListener = groupSpinner.onItemSelectedListener

            actionBar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionBar.customView = spinnerInActionBar
            actionBar.setDisplayShowTitleEnabled(false)

            spinnerInActionBar.setOnLongClickListener {
                showGroupOptionsDialog()
                true
            }
        }
    }

    // === РЕАЛИЗАЦИЯ МЕНЮ ACTION BAR ===
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // ✅ ОБРАБОТЧИК НОВОЙ КНОПКИ
            R.id.action_flip_all -> {
                flipAllCards()
                true
            }
            R.id.action_add_card -> {
                showAddCardDialog()
                true
            }
            R.id.action_new_group -> {
                showNewGroupDialog()
                true
            }
            R.id.action_export -> {
                exportFileLauncher.launch("flashcards.json")
                true
            }
            R.id.action_import -> {
                importFileLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ✅ НОВАЯ ФУНКЦИЯ ДЛЯ ПЕРЕВОРОТА КАРТОЧЕК
    private fun flipAllCards() {
        if (groups.isEmpty() || groups[currentGroupIndex].cards.isEmpty()) {
            Toast.makeText(this, "Нет карточек для переворота", Toast.LENGTH_SHORT).show()
            return
        }

        // Переворачиваем все карточки в текущей группе
        groups[currentGroupIndex].cards.forEach { card ->
            card.isFlipped = !card.isFlipped
        }

        // Обновляем весь список в адаптере
        cardAdapter.notifyDataSetChanged()
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
                if (position != currentGroupIndex) {
                    currentGroupIndex = position
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showGroupOptionsDialog() {
        if (groups.isEmpty()) return
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
                        (supportActionBar?.customView as? Spinner)?.adapter = groupSpinner.adapter
                        (supportActionBar?.customView as? Spinner)?.setSelection(currentGroupIndex)
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

                (supportActionBar?.customView as? Spinner)?.adapter = groupSpinner.adapter
                (supportActionBar?.customView as? Spinner)?.setSelection(currentGroupIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === Управление карточками ===

    private fun showAddCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_card_edit, null)
        val frontInput = dialogView.findViewById<EditText>(R.id.editTextFront)
        val backInput = dialogView.findViewById<EditText>(R.id.editTextBack)

        val dialog = AlertDialog.Builder(this)
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
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.show()
    }

    private fun showEditCardDialog(position: Int) {
        val card = groups[currentGroupIndex].cards[position]
        val dialogView = layoutInflater.inflate(R.layout.dialog_card_edit, null)
        val frontInput = dialogView.findViewById<EditText>(R.id.editTextFront)
        val backInput = dialogView.findViewById<EditText>(R.id.editTextBack)

        frontInput.setText(card.front)
        backInput.setText(card.back)

        val dialog = AlertDialog.Builder(this)
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
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.show()
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

                    (supportActionBar?.customView as? Spinner)?.adapter = groupSpinner.adapter
                    (supportActionBar?.customView as? Spinner)?.setSelection(currentGroupIndex)
                } else {
                    Toast.makeText(this, "The group already exists", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === Сохранение/Загрузка ===

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