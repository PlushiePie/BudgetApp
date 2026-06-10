package com.budget.app

import android.app.DatePickerDialog
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
import com.budget.app.data.Category
import com.budget.app.data.Saving
import com.budget.app.data.Transaction
import com.budget.app.databinding.*
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var budgetManager: BudgetManager
    private lateinit var transactionAdapter: TransactionAdapter

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var currentCategories = listOf<Category>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        budgetManager = BudgetManager(this)

        setupRecyclerViews()
        setupClickListeners()

        lifecycleScope.launch {
            budgetManager.loadInitialData()
            loadData()
        }

        // Проверяем, нужно ли показать все траты
        if (intent.getBooleanExtra("show_all_transactions", false)) {
            showAllTransactionsDialog()
        }

        // Проверяем, нужно ли экспортировать CSV
        if (intent.getBooleanExtra("export_csv", false)) {
            exportToCSV()
        }
    }

    private fun setupRecyclerViews() {
        transactionAdapter = TransactionAdapter(
            onDeleteClick = { transaction ->
                lifecycleScope.launch {
                    budgetManager.deleteTransaction(transaction)
                    loadData()
                    Toast.makeText(this@MainActivity, "Трата удалена", Toast.LENGTH_SHORT).show()
                }
            },
            onCompleteClick = { transaction ->
                lifecycleScope.launch {
                    budgetManager.markTransactionCompleted(transaction)
                    loadData()
                    Toast.makeText(this@MainActivity, "Трата отмечена как выполненная", Toast.LENGTH_SHORT).show()
                }
            },
            onItemClick = { transaction ->
                showTransactionDetailsDialog(transaction)
            }
        )
        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvRecentTransactions.adapter = transactionAdapter
    }

    private fun setupClickListeners() {
        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }

        binding.btnSavings.setOnClickListener {
            showSavingsDialog()
        }

        binding.btnAnalytics.setOnClickListener {
            val intent = Intent(this, MonthlyAnalyticsActivity::class.java)
            startActivity(intent)
        }

        binding.btnSetMonthlyBudget.setOnClickListener {
            showSetMonthlyBudgetDialog()
        }

        binding.btnCategories.setOnClickListener {
            val intent = Intent(this, CategoriesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            budgetManager.getAllCategories().collect { categories ->
                currentCategories = categories
                updatePieChart(categories)
                updateTotalUI()
            }
        }

        lifecycleScope.launch {
            budgetManager.getAllTransactions().collect { transactions ->
                transactionAdapter.submitList(transactions.take(5))
            }
        }
    }

    private fun updatePieChart(categories: List<Category>) {
        lifecycleScope.launch {
            val allTransactions = budgetManager.getAllTransactions().firstOrNull() ?: emptyList()

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            // Фильтруем траты только за текущий месяц
            val currentMonthTransactions = allTransactions.filter { transaction ->
                val transCalendar = Calendar.getInstance()
                transCalendar.time = transaction.date
                transCalendar.get(Calendar.MONTH) == currentMonth &&
                        transCalendar.get(Calendar.YEAR) == currentYear
            }

            // Сумма трат за текущий месяц
            val totalCurrentMonth = currentMonthTransactions.sumOf { it.amount }

            // Группируем по категориям
            val categorySpent = mutableMapOf<String, Double>()
            for (transaction in currentMonthTransactions) {
                categorySpent[transaction.category] = categorySpent.getOrDefault(transaction.category, 0.0) + transaction.amount
            }

            val entries = mutableListOf<PieEntry>()
            val colors = mutableListOf<Int>()

            val colorPalette = listOf(
                android.graphics.Color.parseColor("#FF6B6B"),
                android.graphics.Color.parseColor("#4ECDC4"),
                android.graphics.Color.parseColor("#45B7D1"),
                android.graphics.Color.parseColor("#96CEB4"),
                android.graphics.Color.parseColor("#FFEAA7"),
                android.graphics.Color.parseColor("#DDA0DD"),
                android.graphics.Color.parseColor("#FFB347"),
                android.graphics.Color.parseColor("#779ECB")
            )

            var index = 0
            for ((categoryName, amount) in categorySpent) {
                if (amount > 0) {
                    val category = categories.find { it.name == categoryName }
                    val icon = category?.icon ?: "📌"
                    entries.add(PieEntry(amount.toFloat(), "$icon $categoryName"))
                    colors.add(colorPalette[index % colorPalette.size])
                    index++
                }
            }

            if (entries.isNotEmpty()) {
                val dataSet = PieDataSet(entries, "Траты за этот месяц")
                dataSet.colors = colors
                dataSet.valueTextSize = 16f
                dataSet.valueTypeface = null
                dataSet.setDrawIcons(false)
                dataSet.valueTextColor = android.graphics.Color.BLACK

                val pieData = PieData(dataSet)
                pieData.setValueFormatter(PercentFormatter())
                pieData.setValueTextSize(16f)

                binding.pieChart.data = pieData
                binding.pieChart.description.isEnabled = false
                binding.pieChart.isDrawHoleEnabled = true
                binding.pieChart.setHoleColor(android.graphics.Color.TRANSPARENT)
                binding.pieChart.setDrawEntryLabels(true)
                binding.pieChart.setEntryLabelTextSize(16f)
                binding.pieChart.setEntryLabelColor(android.graphics.Color.BLACK)
                // Устанавливаем сумму за текущий месяц в центр диаграммы
                binding.pieChart.setCenterText("${totalCurrentMonth.toInt()} ₽")
                binding.pieChart.setCenterTextSize(18f)
                binding.pieChart.animateY(1000)
                binding.pieChart.invalidate()
            } else {
                binding.pieChart.clear()
                binding.pieChart.setNoDataText("Нет трат за этот месяц")
                binding.pieChart.invalidate()
            }
        }
    }

    private suspend fun updateTotalUI() {
        val allTransactions = budgetManager.getAllTransactions().firstOrNull() ?: emptyList()

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Фильтруем траты только за текущий месяц
        val currentMonthTransactions = allTransactions.filter { transaction ->
            val transCalendar = Calendar.getInstance()
            transCalendar.time = transaction.date
            transCalendar.get(Calendar.MONTH) == currentMonth &&
                    transCalendar.get(Calendar.YEAR) == currentYear
        }

        val totalSpentThisMonth = currentMonthTransactions.sumOf { it.amount }
        val totalBudget = budgetManager.getTotalBudget()
        val percent = if (totalBudget > 0) ((totalSpentThisMonth / totalBudget) * 100).toInt() else 0

        binding.tvTotalInfo.text = "Итого за месяц: ${totalSpentThisMonth.toInt()} ₽ из ${totalBudget.toInt()} ₽ ($percent%)"
        binding.progressTotal.progress = percent
    }

    private fun showAddTransactionDialog() {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        var selectedDate = Date()

        val categories = currentCategories.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerCategory.adapter = adapter

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
            .setTitle("Добавить трату")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val amountText = dialogBinding.etAmount.text.toString()
                val amount = parseAmount(amountText)
                val category = dialogBinding.spinnerCategory.selectedItem.toString()
                val comment = dialogBinding.etComment.text.toString()

                if (amount > 0) {
                    lifecycleScope.launch {
                        budgetManager.addTransaction(amount, category, comment, selectedDate)
                        loadData()
                        Toast.makeText(this@MainActivity, "Трата добавлена", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Введите корректную сумму > 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun parseAmount(input: String): Double {
        val trimmed = input.trim().lowercase().replace(",", ".")
        return when {
            trimmed.endsWith("к") || trimmed.endsWith("т") -> {
                val num = trimmed.dropLast(1).toDoubleOrNull()
                if (num != null) num * 1000 else 0.0
            }
            else -> trimmed.toDoubleOrNull() ?: 0.0
        }
    }

    private fun showSetMonthlyBudgetDialog() {
        val input = EditText(this)
        input.hint = "Общий бюджет на месяц"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Установить бюджет на месяц")
            .setMessage("Текущий общий бюджет: ${currentCategories.sumOf { it.budget }.toInt()} ₽")
            .setView(input)
            .setPositiveButton("Установить") { _, _ ->
                val newBudget = input.text.toString().toDoubleOrNull()
                if (newBudget != null && newBudget > 0) {
                    lifecycleScope.launch {
                        budgetManager.setMonthlyBudget(newBudget)
                        loadData()
                        Toast.makeText(this@MainActivity, "Бюджет установлен: ${newBudget.toInt()} ₽", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAllTransactionsDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Все траты")
            .setPositiveButton("Закрыть", null)
            .create()

        lifecycleScope.launch {
            budgetManager.getAllTransactions().collect { transactions ->
                val items = transactions.map { transaction ->
                    val status = if (transaction.isCompleted) "✅" else "⏳"
                    "${status} ${transaction.amount.toInt()} ₽ | ${transaction.category} | ${dateFormat.format(transaction.date)}"
                }.toTypedArray()

                val listView = ListView(this@MainActivity)
                listView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, items)

                listView.setOnItemClickListener { _, _, position, _ ->
                    val transaction = transactions[position]
                    showTransactionDetailsDialog(transaction)
                    dialog.dismiss()
                }

                dialog.setView(listView)
                dialog.show()
            }
        }
    }

    private fun showTransactionDetailsDialog(transaction: Transaction) {
        val status = if (transaction.isCompleted) "✅ Выполнена" else "⏳ Ожидает"
        val message = """
            
            ═══════════════════════════
            
            📁 Категория: ${transaction.category}
            💰 Сумма: ${transaction.amount.toInt()} ₽
            📅 Дата: ${dateFormat.format(transaction.date)}
            📝 Комментарий: ${transaction.comment.ifEmpty { "—" }}
            🏷 Статус: $status
            
            ═══════════════════════════
            
        """.trimIndent()

        // Создаем диалог
        val dialog = AlertDialog.Builder(this)
            .setTitle("📋 ДЕТАЛИ ТРАТЫ")
            .setMessage(message)
            .create()

        // Создаем кастомный layout для кнопок после создания диалога
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(50, 10, 50, 20)
        }

        // Кнопка "Удалить"
        val deleteButton = Button(this).apply {
            text = "❌ Удалить"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#E53935"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 5, 0, 5) }
            setOnClickListener {
                lifecycleScope.launch {
                    budgetManager.deleteTransaction(transaction)
                    loadData()
                    Toast.makeText(this@MainActivity, "Трата удалена", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        // Кнопка "Отметить выполненной"
        val completeButton = Button(this).apply {
            text = if (transaction.isCompleted) "✅ Уже выполнена" else "✅ Отметить выполненной"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 5, 0, 5) }
            isEnabled = !transaction.isCompleted
            if (!transaction.isCompleted) {
                setOnClickListener {
                    lifecycleScope.launch {
                        budgetManager.markTransactionCompleted(transaction)
                        loadData()
                        Toast.makeText(this@MainActivity, "Трата отмечена как выполненная", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }

        // Кнопка "Закрыть"
        val closeButton = Button(this).apply {
            text = "🔙 Закрыть"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#607D8B"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 5, 0, 5) }
            setOnClickListener {
                dialog.dismiss()
            }
        }

        buttonLayout.addView(deleteButton)
        buttonLayout.addView(completeButton)
        buttonLayout.addView(closeButton)

        // Добавляем кнопки в диалог
        dialog.setView(buttonLayout)

        dialog.show()

        // Увеличиваем размер текста сообщения
        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.textSize = 18f
        messageView?.gravity = android.view.Gravity.CENTER
    }

    private fun showSavingsDialog() {
        lifecycleScope.launch {
            try {
                val savings = budgetManager.getAllSavings().firstOrNull() ?: emptyList()

                if (savings.isEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("💰 Цели")
                        .setMessage("Нет добавленных целей\n\nНажмите 'Добавить цель' чтобы создать")
                        .setPositiveButton("➕ Добавить цель") { _, _ ->
                            showAddSavingDialog()
                        }
                        .setNegativeButton("Закрыть", null)
                        .show()
                } else {
                    val items = savings.map { saving ->
                        val percent = if (saving.targetAmount > 0) {
                            ((saving.currentAmount / saving.targetAmount) * 100).toInt()
                        } else 0
                        "${saving.icon} ${saving.name}\n${saving.currentAmount.toInt()} / ${saving.targetAmount.toInt()} ₽ ($percent%)"
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("💰 Цели")
                        .setItems(items) { _, which ->
                            val saving = savings[which]
                            showSavingActionsDialog(saving)
                        }
                        .setPositiveButton("➕ Добавить цель") { _, _ ->
                            showAddSavingDialog()
                        }
                        .setNegativeButton("Закрыть", null)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Ошибка")
                    .setMessage("Ошибка загрузки: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showAddSavingDialog() {
        val dialogBinding = DialogAddSavingBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
            .setTitle("Добавить цель")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val target = dialogBinding.etTarget.text.toString().toDoubleOrNull()
                val icon = dialogBinding.etIcon.text.toString().ifEmpty { "💰" }

                if (name.isNotEmpty() && target != null && target > 0) {
                    lifecycleScope.launch {
                        try {
                            budgetManager.addSaving(name, target, icon)
                            Toast.makeText(this@MainActivity, "Цель добавлена", Toast.LENGTH_SHORT).show()
                            loadData()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSavingActionsDialog(saving: Saving) {
        val options = arrayOf("💰 Пополнить", "🗑 Удалить")
        AlertDialog.Builder(this)
            .setTitle("${saving.icon} ${saving.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddToSavingDialog(saving)
                    1 -> {
                        lifecycleScope.launch {
                            try {
                                budgetManager.deleteSaving(saving)
                                loadData()
                                Toast.makeText(this@MainActivity, "Цель удалена", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Ошибка удаления", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun showAddToSavingDialog(saving: Saving) {
        val input = EditText(this)
        input.hint = "Сумма пополнения"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Пополнить: ${saving.icon} ${saving.name}")
            .setMessage("Текущая сумма: ${saving.currentAmount.toInt()} / ${saving.targetAmount.toInt()} ₽")
            .setView(input)
            .setPositiveButton("Пополнить") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    lifecycleScope.launch {
                        try {
                            budgetManager.addToSaving(saving.name, amount)
                            loadData()
                            Toast.makeText(this@MainActivity, "Пополнено на ${amount.toInt()} ₽", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Ошибка пополнения", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportToCSV() {
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
                Toast.makeText(this@MainActivity, "Нет трат для экспорта", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@MainActivity, "✅ Отчёт сохранён: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Adapter for Transactions
    inner class TransactionAdapter(
        private val onDeleteClick: (Transaction) -> Unit,
        private val onCompleteClick: (Transaction) -> Unit,
        private val onItemClick: (Transaction) -> Unit
    ) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

        private var transactions = listOf<Transaction>()

        fun submitList(list: List<Transaction>) {
            transactions = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return TransactionViewHolder(view)
        }

        override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
            val transaction = transactions[position]
            val status = if (transaction.isCompleted) "✅" else "⏳"
            val comment = if (transaction.comment.isNotEmpty()) " | ${transaction.comment}" else ""

            holder.text1.text = "$status ${transaction.amount.toInt()} ₽ | ${transaction.category}"
            holder.text2.text = "${dateFormat.format(transaction.date)}$comment"

            if (!transaction.isCompleted) {
                holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
            } else {
                holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            holder.itemView.setOnClickListener {
                onItemClick(transaction)
            }

            holder.itemView.setOnLongClickListener {
                showTransactionContextMenu(transaction)
                true
            }
        }

        private fun showTransactionContextMenu(transaction: Transaction) {
            val options = mutableListOf<String>()
            if (!transaction.isCompleted) options.add("Отметить как выполненную")
            options.add("Удалить")
            options.add("Отмена")

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Действия")
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        "Отметить как выполненную" -> onCompleteClick(transaction)
                        "Удалить" -> onDeleteClick(transaction)
                    }
                }
                .show()
        }

        override fun getItemCount() = transactions.size

        inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1 = itemView.findViewById<TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<TextView>(android.R.id.text2)
        }
    }
}