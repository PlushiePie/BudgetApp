package com.budget.app

import android.content.Intent
import android.os.Bundle
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
        categoryAdapter = CategoryListAdapter(
            onEditClick = { category ->
                showEditBudgetDialog(category)
            },
            onDeleteClick = { category ->
                showDeleteCategoryDialog(category)
            }
        )
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
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            budgetManager.getAllCategories().collect { categories ->
                categoryAdapter.submitList(categories)
            }
        }
    }

    private fun showAddCategoryDialog() {
        val inputName = EditText(this)
        inputName.hint = "Название категории"

        val inputBudget = EditText(this)
        inputBudget.hint = "Бюджет (₽)"
        inputBudget.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val inputIcon = EditText(this)
        inputIcon.hint = "Иконка (эмодзи)"
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

                if (name.isNotEmpty() && budget != null && budget > 0) {
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
                } else {
                    Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditBudgetDialog(category: Category) {
        val input = EditText(this)
        input.hint = "Новый бюджет"
        input.setText(category.budget.toInt().toString())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Редактировать бюджет: ${category.icon} ${category.name}")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newBudget = input.text.toString().toDoubleOrNull()
                if (newBudget != null && newBudget > 0) {
                    lifecycleScope.launch {
                        budgetManager.updateBudget(category.name, newBudget)
                        loadCategories()
                        Toast.makeText(this@CategoriesActivity, "Бюджет обновлён", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
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

    // Адаптер для списка категорий с прогресс-баром
    inner class CategoryListAdapter(
        private val onEditClick: (Category) -> Unit,
        private val onDeleteClick: (Category) -> Unit
    ) : RecyclerView.Adapter<CategoryListAdapter.ViewHolder>() {

        private var categories = listOf<Category>()

        fun submitList(list: List<Category>) {
            categories = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val percent = if (category.budget > 0) {
                ((category.spent / category.budget) * 100).toInt().coerceIn(0, 100)
            } else 0

            holder.tvName.text = "${category.icon} ${category.name}"
            holder.tvAmount.text = "${category.spent.toInt()} / ${category.budget.toInt()} ₽"
            holder.progressBar.progress = percent

            val color = when {
                percent >= 100 -> android.graphics.Color.parseColor("#FF4444")
                percent >= 80 -> android.graphics.Color.parseColor("#FFA500")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)

            holder.itemView.setOnClickListener { onEditClick(category) }
            holder.itemView.setOnLongClickListener {
                onDeleteClick(category)
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