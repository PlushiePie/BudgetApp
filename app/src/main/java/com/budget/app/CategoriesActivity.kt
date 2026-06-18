package com.budget.app

import android.content.Intent
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
import com.budget.app.data.Category
import com.budget.app.databinding.ActivityCategoriesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var budgetManager: BudgetManager
    private lateinit var categoryAdapter: CategoryListAdapter
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        budgetManager = BudgetManager(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()

        loadCategories()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryListAdapter()
        binding.rvCategoriesList.layoutManager = LinearLayoutManager(this)
        binding.rvCategoriesList.adapter = categoryAdapter
    }

    private fun setupClickListeners() {
        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        binding.btnViewAllTransactions.setOnClickListener {
            showAllTransactionsDialog()
        }

        binding.btnExport.setOnClickListener {
            showExportDialog()
        }

        binding.btnReminders.setOnClickListener {
            val intent = Intent(this, RemindersActivity::class.java)
            startActivity(intent)
        }

        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            // Получаем категории
            budgetManager.getAllCategories().collect { categories ->
                // Получаем все траты
                val allTransactions = budgetManager.getAllTransactions().firstOrNull() ?: emptyList()

                // Фильтруем траты за текущий месяц
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)

                val currentMonthTransactions = allTransactions.filter { transaction ->
                    val transCalendar = Calendar.getInstance()
                    transCalendar.time = transaction.date
                    transCalendar.get(Calendar.MONTH) == currentMonth &&
                            transCalendar.get(Calendar.YEAR) == currentYear
                }

                // Группируем по категориям
                val monthlySpent = mutableMapOf<String, Double>()
                for (transaction in currentMonthTransactions) {
                    monthlySpent[transaction.category] = monthlySpent.getOrDefault(transaction.category, 0.0) + transaction.amount
                }

                categoryAdapter.submitList(categories, monthlySpent)
            }
        }
    }

    private fun showAddCategoryDialog() {
        val inputName = EditText(this)
        inputName.hint = "Название категории"
        inputName.filters = arrayOf(InputFilter.LengthFilter(15))

        val inputBudget = EditText(this)
        inputBudget.hint = "Бюджет (₽) (макс. 10 000 000)"
        inputBudget.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputBudget.filters = arrayOf(InputFilter.LengthFilter(8))

        val inputIcon = EditText(this)
        inputIcon.hint = "Иконка (эмодзи) (макс. 5 символов)"
        inputIcon.filters = arrayOf(InputFilter.LengthFilter(5))
        inputIcon.setText("📌")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(inputName)
            addView(inputBudget)
            addView(inputIcon)
        }

        AlertDialog.Builder(this)
            .setTitle("➕ Новая категория")
            .setView(layout)
            .setPositiveButton("Добавить") { _, _ ->
                val name = inputName.text.toString().trim()
                val budget = inputBudget.text.toString().toDoubleOrNull()
                val icon = inputIcon.text.toString().ifEmpty { "📌" }

                if (name.isNotEmpty() && budget != null && budget > 0 && budget <= 10000000) {
                    lifecycleScope.launch {
                        try {
                            val db = AppDatabase.getDatabase(this@CategoriesActivity)
                            val newCategory = Category(
                                name = name,
                                budget = budget,
                                spent = 0.0,
                                icon = icon
                            )
                            db.categoryDao().insertCategory(newCategory)
                            loadCategories()
                            Toast.makeText(this@CategoriesActivity, "Категория добавлена", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@CategoriesActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (budget != null && budget > 10000000) {
                    Toast.makeText(this, "Бюджет не может превышать 10 000 000 ₽", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditCategoryDialog(category: Category) {
        val inputName = EditText(this)
        inputName.hint = "Название категории"
        inputName.setText(category.name)
        inputName.filters = arrayOf(InputFilter.LengthFilter(15))

        val inputBudget = EditText(this)
        inputBudget.hint = "Бюджет (₽) (макс. 10 000 000)"
        inputBudget.setText(category.budget.toInt().toString())
        inputBudget.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputBudget.filters = arrayOf(InputFilter.LengthFilter(8))

        val inputIcon = EditText(this)
        inputIcon.hint = "Иконка (эмодзи) (макс. 5 символов)"
        inputIcon.setText(category.icon)
        inputIcon.filters = arrayOf(InputFilter.LengthFilter(5))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(inputName)
            addView(inputBudget)
            addView(inputIcon)
        }

        AlertDialog.Builder(this)
            .setTitle("✏️ Редактировать категорию")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newBudget = inputBudget.text.toString().toDoubleOrNull()
                val newIcon = inputIcon.text.toString().ifEmpty { "📌" }

                if (newName.isNotEmpty() && newBudget != null && newBudget > 0 && newBudget <= 10000000) {
                    lifecycleScope.launch {
                        try {
                            val db = AppDatabase.getDatabase(this@CategoriesActivity)

                            // Обновляем название категории (если изменилось)
                            if (newName != category.name) {
                                // Обновляем все траты с новым названием
                                val transactions = db.transactionDao().getTransactionsList()
                                for (transaction in transactions) {
                                    if (transaction.category == category.name) {
                                        val updatedTransaction = transaction.copy(category = newName)
                                        db.transactionDao().updateTransaction(updatedTransaction)
                                    }
                                }
                            }

                            // Обновляем саму категорию
                            val updatedCategory = category.copy(
                                name = newName,
                                budget = newBudget,
                                icon = newIcon
                            )
                            db.categoryDao().updateCategory(updatedCategory)

                            loadCategories()
                            Toast.makeText(this@CategoriesActivity, "Категория обновлена", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@CategoriesActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (newBudget != null && newBudget > 10000000) {
                    Toast.makeText(this, "Бюджет не может превышать 10 000 000 ₽", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Удалить категорию?")
            .setMessage("${category.icon} ${category.name}\nВсе траты в этой категории тоже будут удалены!")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(this@CategoriesActivity)
                        db.categoryDao().deleteCategory(category)
                        val transactions = db.transactionDao().getTransactionsList()
                        val transactionsToDelete = transactions.filter { it.category == category.name }
                        for (transaction in transactionsToDelete) {
                            db.transactionDao().deleteTransaction(transaction)
                        }
                        loadCategories()
                        Toast.makeText(this@CategoriesActivity, "Категория удалена", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@CategoriesActivity, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCategoryActionsDialog(category: Category) {
        val options = arrayOf("✏️ Редактировать категорию", "🗑 Удалить категорию", "❌ Отмена")

        AlertDialog.Builder(this)
            .setTitle("${category.icon} ${category.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditCategoryDialog(category)
                    1 -> showDeleteCategoryDialog(category)
                    2 -> { /* Отмена - ничего не делаем */ }
                }
            }
            .show()
    }

    private fun showAllTransactionsDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📋 Все траты")
            .setPositiveButton("Закрыть", null)
            .create()

        lifecycleScope.launch {
            budgetManager.getAllTransactions().collect { transactions ->
                val items = transactions.map { transaction ->
                    val status = if (transaction.isCompleted) "✅" else "⏳"
                    "${status} ${transaction.amount.toInt()} ₽ | ${transaction.category} | ${dateFormat.format(transaction.date)}"
                }.toTypedArray()

                val listView = ListView(this@CategoriesActivity)
                listView.adapter = ArrayAdapter(this@CategoriesActivity, android.R.layout.simple_list_item_1, items)

                // Только просмотр, без возможности изменения
                listView.isClickable = false
                listView.isFocusable = false

                dialog.setView(listView)
                dialog.show()
            }
        }
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this)
            .setTitle("📄 Экспорт данных")
            .setMessage("Вы действительно хотите экспортировать все траты в CSV файл?")
            .setPositiveButton("Да, экспортировать") { _, _ ->
                performExport()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performExport() {
        lifecycleScope.launch {
            val transactions = budgetManager.getAllTransactions().firstOrNull() ?: emptyList()

            if (transactions.isEmpty()) {
                Toast.makeText(this@CategoriesActivity, "Нет трат для экспорта", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val fileName = "отчёт_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val content = StringBuilder()
            content.append("Дата;Категория;Сумма;Комментарий;Статус\n")

            for (transaction in transactions) {
                content.append("${dateFormat.format(transaction.date)};${transaction.category};${transaction.amount};${transaction.comment};${if (transaction.isCompleted) "Выполнено" else "Ожидает"}\n")
            }

            try {
                val file = java.io.File(getExternalFilesDir(null), fileName)
                file.writeText(content.toString())
                Toast.makeText(this@CategoriesActivity, "✅ Отчёт сохранён: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@CategoriesActivity, "❌ Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showHelpDialog() {
        val message = """
            📌 КАК ПОЛЬЗОВАТЬСЯ:
            
            1️⃣ Добавление трат
            Нажмите на синюю кнопку ➕ внизу экрана.
            Заполните: сумму, категорию, комментарий и дату.
            
            2️⃣ Категории
            Создавайте свои категории (налоги, подарки и т.д.)
            Нажмите на категорию - можно изменить бюджет.
            Долгое нажатие - удалить категорию.
            
            3️⃣ Бюджет
            На главном экране кнопка "Установить бюджет на месяц".
            Сумма распределяется автоматически по категориям.
            
            4️⃣ Регулярные платежи
            Добавьте напоминания для регулярных трат.
            В нужный день приложение напомнит о платеже.
            
            5️⃣ Цели
            Копите деньги на мечту! 
            Создайте цель и пополняйте её.
            
            6️⃣ Аналитика
            Смотрите траты по месяцам и категориям.
            Нажимайте на сектора диаграммы для деталей.
            
            7️⃣ Экспорт
            Сохраняйте все траты в CSV файл.
            
            ⚠️ ЛИМИТЫ:
            • Максимальная сумма траты: 
              10 000 000 ₽
            • Бюджет категории: 10 000 000 ₽
            • Общий бюджет: 10 000 000 ₽
            • Сумма цели: 10 000 000 ₽
            • Пополнение цели: 10 000 000 ₽
            • Комментарий: до 12 символов
            • Название категории: до 15 символов
            • Название цели: до 20 символов
            
            Приятного использования! 🎉
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("❓ Помощь")
            .setMessage(message)
            .setPositiveButton("Понятно! ✅", null)
            .show()
    }

    // Адаптер для списка категорий с прогресс-баром (только за текущий месяц)
    inner class CategoryListAdapter() : RecyclerView.Adapter<CategoryListAdapter.ViewHolder>() {

        private var categories = listOf<Category>()
        private var monthlySpent = mutableMapOf<String, Double>()

        fun submitList(list: List<Category>, spent: Map<String, Double>) {
            categories = list
            monthlySpent = spent.toMutableMap()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val spentThisMonth = monthlySpent[category.name] ?: 0.0
            val percent = if (category.budget > 0) {
                ((spentThisMonth / category.budget) * 100).toInt().coerceIn(0, 100)
            } else 0

            holder.tvName.text = "${category.icon} ${category.name}"
            holder.tvAmount.text = "${spentThisMonth.toInt()} / ${category.budget.toInt()} ₽"
            holder.progressBar.progress = percent

            val color = when {
                percent >= 100 -> android.graphics.Color.parseColor("#FF4444")
                percent >= 80 -> android.graphics.Color.parseColor("#FFA500")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)

            holder.itemView.setOnClickListener {
                showCategoryActionsDialog(category)
            }
            holder.itemView.setOnLongClickListener {
                showCategoryActionsDialog(category)
                true
            }
        }

        override fun getItemCount() = categories.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
            val tvAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
            val progressBar: ProgressBar = itemView.findViewById(R.id.progressCategory)
        }
    }
}