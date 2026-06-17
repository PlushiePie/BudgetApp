package com.budget.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budget.app.data.Transaction
import com.budget.app.databinding.ActivityMonthlyAnalyticsBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonthlyAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonthlyAnalyticsBinding
    private lateinit var budgetManager: BudgetManager
    private lateinit var transactionAdapter: MonthlyTransactionAdapter

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("ru"))
    private val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    private var currentCalendar = Calendar.getInstance()
    private var allTransactions = listOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        budgetManager = BudgetManager(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()

        loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = MonthlyTransactionAdapter()
        binding.rvMonthlyTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvMonthlyTransactions.adapter = transactionAdapter
    }

    private fun setupClickListeners() {
        binding.btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthDisplay()
        }

        binding.btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthDisplay()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            budgetManager.getAllTransactions().collect { transactions ->
                allTransactions = transactions
                updateMonthDisplay()
            }
        }
    }

    private fun updateMonthDisplay() {
        val monthYear = monthFormat.format(currentCalendar.time)
        binding.tvMonthYear.text = monthYear.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val year = currentCalendar.get(Calendar.YEAR)
        val month = currentCalendar.get(Calendar.MONTH)

        val monthlyTransactions = allTransactions.filter { transaction ->
            val cal = Calendar.getInstance()
            cal.time = transaction.date
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }

        updatePieChart(monthlyTransactions)
        updateStatistics(monthlyTransactions)
        transactionAdapter.submitList(monthlyTransactions)
    }

    private fun updatePieChart(transactions: List<Transaction>) {
        val categoryMap = mutableMapOf<String, Double>()

        for (transaction in transactions) {
            categoryMap[transaction.category] = categoryMap.getOrDefault(transaction.category, 0.0) + transaction.amount
        }

        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        val originalValues = mutableMapOf<Int, Double>()

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

        // Применяем логарифмическое масштабирование
        val maxAmount = categoryMap.values.maxOrNull() ?: 0.0
        val useLogScale = maxAmount > 50000

        var index = 0
        for ((category, amount) in categoryMap) {
            if (amount > 0) {
                val fullLabel = category

                val displayValue = if (useLogScale && amount > 0) {
                    val logValue = Math.log10(amount + 1) * 1000
                    if (logValue < 500f) 500f else logValue
                } else {
                    amount.toFloat()
                }

                entries.add(PieEntry(displayValue.toFloat(), fullLabel))
                originalValues[index] = amount
                colors.add(colorPalette[index % colorPalette.size])
                index++
            }
        }

        if (entries.isNotEmpty()) {
            val dataSet = PieDataSet(entries, "Траты по категориям")
            dataSet.colors = colors
            dataSet.valueTextSize = 14f
            dataSet.valueTextColor = android.graphics.Color.BLACK

            // Полностью скрываем цены на диаграмме
            dataSet.valueTextSize = 0f
            dataSet.setDrawValues(false)

            // Оставляем только названия категорий
            binding.pieChartMonth.setDrawEntryLabels(true)
            binding.pieChartMonth.setEntryLabelTextSize(12f)
            binding.pieChartMonth.setEntryLabelColor(android.graphics.Color.BLACK)

            val pieData = PieData(dataSet)
            pieData.setValueFormatter(PercentFormatter())

            binding.pieChartMonth.data = pieData
            binding.pieChartMonth.description.isEnabled = false
            binding.pieChartMonth.isDrawHoleEnabled = true
            binding.pieChartMonth.setHoleColor(android.graphics.Color.TRANSPARENT)

            // В центре сумма
            val totalSpent = transactions.sumOf { it.amount }
            binding.pieChartMonth.setCenterText("${totalSpent.toInt()} ₽")
            binding.pieChartMonth.setCenterTextSize(16f)

            // Добавляем кликабельность на диаграмму
            binding.pieChartMonth.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    if (e != null && e is PieEntry) {
                        val label = e.label
                        val index = entries.indexOf(e)
                        val realValue = originalValues[index] ?: e.value.toDouble()

                        AlertDialog.Builder(this@MonthlyAnalyticsActivity)
                            .setTitle("📊 Детали категории")
                            .setMessage("$label\n\n💰 Сумма: ${realValue.toInt()} ₽")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                override fun onNothingSelected() {
                    // Ничего не делаем
                }
            })

            binding.pieChartMonth.animateY(1000)
            binding.pieChartMonth.invalidate()
        } else {
            binding.pieChartMonth.clear()
            binding.pieChartMonth.setNoDataText("Нет трат за этот месяц")
            binding.pieChartMonth.invalidate()
        }
    }

    private fun updateStatistics(transactions: List<Transaction>) {
        val totalSpent = transactions.sumOf { it.amount }

        val daysWithTransactions = transactions.map {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(it.date)
        }.distinct().size

        val avgPerDay = if (daysWithTransactions > 0) totalSpent / daysWithTransactions else 0.0

        val categoryMap = mutableMapOf<String, Double>()
        for (transaction in transactions) {
            categoryMap[transaction.category] = categoryMap.getOrDefault(transaction.category, 0.0) + transaction.amount
        }

        val mostExpensive = categoryMap.maxByOrNull { it.value }

        binding.tvTotalSpentMonth.text = "💰 Всего потрачено: ${totalSpent.toInt()} ₽"
        binding.tvAvgPerDay.text = "📊 В день в среднем: ${String.format("%.2f", avgPerDay)} ₽"

        if (mostExpensive != null) {
            binding.tvMostExpensive.text = "🔥 Самая затратная: ${mostExpensive.key} (${mostExpensive.value.toInt()} ₽)"
        } else {
            binding.tvMostExpensive.text = "🔥 Самая затратная: —"
        }
    }

    // Адаптер для списка транзакций
    inner class MonthlyTransactionAdapter : RecyclerView.Adapter<MonthlyTransactionAdapter.ViewHolder>() {

        private var transactions = listOf<Transaction>()

        fun submitList(list: List<Transaction>) {
            transactions = list.sortedByDescending { it.date }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val transaction = transactions[position]
            val status = if (transaction.isCompleted) "✅" else "⏳"
            val comment = if (transaction.comment.isNotEmpty()) " | ${transaction.comment}" else ""

            holder.text1.text = "$status ${transaction.amount.toInt()} ₽ | ${transaction.category}"
            holder.text2.text = "${dateFormat.format(transaction.date)}$comment"
        }

        override fun getItemCount() = transactions.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1 = itemView.findViewById<TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<TextView>(android.R.id.text2)
        }
    }
}