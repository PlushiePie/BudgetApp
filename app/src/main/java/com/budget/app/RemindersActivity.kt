package com.budget.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budget.app.data.AppDatabase
import com.budget.app.data.Reminder
import com.budget.app.databinding.ActivityRemindersBinding
import com.budget.app.databinding.DialogAddTransactionBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemindersBinding
    private lateinit var db: AppDatabase
    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var budgetManager: BudgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        budgetManager = BudgetManager(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()

        loadReminders()

        // Проверяем напоминания на сегодня
        checkTodayReminders()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(
            onDeleteClick = { reminder ->
                lifecycleScope.launch {
                    db.reminderDao().deleteReminder(reminder)
                    loadReminders()
                    Toast.makeText(this@RemindersActivity, "Платеж удален", Toast.LENGTH_SHORT).show()
                }
            },
            onApplyClick = { reminder ->
                showAddTransactionFromReminder(reminder)
            }
        )
        binding.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.rvReminders.adapter = reminderAdapter
    }

    private fun setupClickListeners() {
        binding.btnAddReminder.setOnClickListener {
            showAddReminderDialog()
        }
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            db.reminderDao().getAllReminders().collect { reminders ->
                reminderAdapter.submitList(reminders)
            }
        }
    }

    private fun checkTodayReminders() {
        lifecycleScope.launch {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val reminders = db.reminderDao().getRemindersList()
            val todayReminders = reminders.filter { it.dayOfMonth == today && it.isActive }

            for (reminder in todayReminders) {
                showReminderNotification(reminder)
            }
        }
    }

    private fun showReminderNotification(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("⏰ Напоминание")
            .setMessage("${reminder.title}\nСумма: ${reminder.amount.toInt()} ₽\nКатегория: ${reminder.category}\n\nДобавить трату?")
            .setPositiveButton("Добавить") { _, _ ->
                showAddTransactionFromReminder(reminder)
            }
            .setNegativeButton("Напомнить позже", null)
            .setNeutralButton("Отключить") { _, _ ->
                lifecycleScope.launch {
                    val updated = reminder.copy(isActive = false)
                    db.reminderDao().updateReminder(updated)
                    loadReminders()
                    Toast.makeText(this@RemindersActivity, "Напоминание отключено", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAddTransactionFromReminder(reminder: Reminder) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        var selectedDate = Date()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        // Получаем категории
        lifecycleScope.launch {
            val categories = budgetManager.getAllCategories().firstOrNull() ?: emptyList()
            val categoryNames = categories.map { it.name }.toTypedArray()
            val adapter = ArrayAdapter(this@RemindersActivity, android.R.layout.simple_spinner_item, categoryNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogBinding.spinnerCategory.adapter = adapter

            // Выбираем нужную категорию
            val categoryIndex = categoryNames.indexOf(reminder.category)
            if (categoryIndex >= 0) {
                dialogBinding.spinnerCategory.setSelection(categoryIndex)
            }
        }

        dialogBinding.etAmount.setText(reminder.amount.toInt().toString())
        dialogBinding.etComment.setText(reminder.title)
        dialogBinding.tvDate.text = dateFormat.format(selectedDate)

        dialogBinding.btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = selectedDate
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate = Calendar.getInstance().apply {
                        set(year, month, day)
                    }.time
                    dialogBinding.tvDate.text = dateFormat.format(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Добавить трату: ${reminder.title}")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val amount = dialogBinding.etAmount.text.toString().toDoubleOrNull() ?: reminder.amount
                val category = dialogBinding.spinnerCategory.selectedItem.toString()
                val comment = dialogBinding.etComment.text.toString()

                lifecycleScope.launch {
                    budgetManager.addTransaction(amount, category, comment, selectedDate)
                    Toast.makeText(this@RemindersActivity, "Трата добавлена", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddReminderDialog() {
        val inputTitle = EditText(this)
        inputTitle.hint = "Название"
        inputTitle.filters = arrayOf(InputFilter.LengthFilter(15))

        val inputAmount = EditText(this)
        inputAmount.hint = "Сумма (₽)"
        inputAmount.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputAmount.filters = arrayOf(InputFilter.LengthFilter(7))

        val inputDay = EditText(this)
        inputDay.hint = "Число месяца (1-31)"
        inputDay.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputDay.filters = arrayOf(InputFilter.LengthFilter(2))

        // Получаем категории для выбора
        val categorySpinner = Spinner(this)
        var categoriesList = listOf<String>()

        lifecycleScope.launch {
            val categories = budgetManager.getAllCategories().firstOrNull() ?: emptyList()
            categoriesList = categories.map { it.name }
            val adapter = ArrayAdapter(this@RemindersActivity, android.R.layout.simple_spinner_item, categoriesList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(inputTitle)
            addView(inputAmount)
            addView(inputDay)
            addView(categorySpinner)
        }

        AlertDialog.Builder(this)
            .setTitle("⏰ Добавить платеж")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val title = inputTitle.text.toString().trim()
                val amount = inputAmount.text.toString().toDoubleOrNull()
                val day = inputDay.text.toString().toIntOrNull()
                val category = if (categoriesList.isNotEmpty() && categorySpinner.selectedItemPosition >= 0) {
                    categorySpinner.selectedItem.toString()
                } else "Еда"

                if (title.isNotEmpty() && amount != null && day != null && day in 1..31) {
                    lifecycleScope.launch {
                        val reminder = Reminder(
                            title = title,
                            amount = amount,
                            category = category,
                            dayOfMonth = day,
                            isActive = true
                        )
                        db.reminderDao().insertReminder(reminder)
                        loadReminders()
                        Toast.makeText(this@RemindersActivity, "Платеж добавлен на $day число", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Заполните все поля (день 1-31)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Адаптер для списка напоминаний
    inner class ReminderAdapter(
        private val onDeleteClick: (Reminder) -> Unit,
        private val onApplyClick: (Reminder) -> Unit
    ) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

        private var reminders = listOf<Reminder>()

        fun submitList(list: List<Reminder>) {
            reminders = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reminder = reminders[position]

            holder.text1.text = "⏰ ${reminder.title} - ${reminder.amount.toInt()} ₽"
            holder.text2.text = "Категория: ${reminder.category} | Число: ${reminder.dayOfMonth}-го"

            holder.itemView.setOnClickListener {
                onApplyClick(reminder)
            }
            holder.itemView.setOnLongClickListener {
                onDeleteClick(reminder)
                true
            }
        }

        override fun getItemCount() = reminders.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}