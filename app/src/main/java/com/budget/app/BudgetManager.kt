package com.budget.app

import android.content.Context
import com.budget.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.*
import java.util.concurrent.TimeUnit

class BudgetManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)

    fun getAllCategories(): Flow<List<Category>> = database.categoryDao().getAllCategories()

    fun getAllTransactions(): Flow<List<Transaction>> = database.transactionDao().getAllTransactions()

    fun getAllSavings(): Flow<List<Saving>> = database.savingDao().getAllSavings()

    suspend fun refreshData() {
        // Принудительно обновляем данные из БД
        database.categoryDao().getCategoriesList()
        database.transactionDao().getTransactionsList()
        database.savingDao().getSavingsList()
    }

    suspend fun addTransaction(amount: Double, categoryName: String, comment: String, date: Date) {
        if (amount <= 0) return

        val transaction = Transaction(
            amount = amount,
            category = categoryName,
            comment = comment,
            date = date,
            isCompleted = false
        )
        database.transactionDao().insertTransaction(transaction)

        val categories = database.categoryDao().getCategoriesList()
        val category = categories.find { it.name == categoryName }
        category?.let {
            database.categoryDao().updateCategorySpent(categoryName, it.spent + amount)
        }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        database.transactionDao().deleteTransaction(transaction)
        val categories = database.categoryDao().getCategoriesList()
        val category = categories.find { it.name == transaction.category }
        category?.let {
            val newSpent = (it.spent - transaction.amount).coerceAtLeast(0.0)
            database.categoryDao().updateCategorySpent(transaction.category, newSpent)
        }
    }

    suspend fun markTransactionCompleted(transaction: Transaction) {
        if (!transaction.isCompleted) {
            database.transactionDao().markTransactionCompleted(transaction.id)
        }
    }

    suspend fun updateBudget(categoryName: String, newBudget: Double) {
        if (newBudget > 0) {
            database.categoryDao().updateCategoryBudget(categoryName, newBudget)
        }
    }

    suspend fun setMonthlyBudget(totalAmount: Double) {
        if (totalAmount <= 0) return

        val categories = database.categoryDao().getCategoriesList()
        val currentTotal = categories.sumOf { it.budget }
        if (currentTotal == 0.0) return

        var remainingBudget = totalAmount
        for (i in categories.indices) {
            val proportion = categories[i].budget / currentTotal
            var newBudget = totalAmount * proportion
            if (i == categories.size - 1) {
                newBudget = remainingBudget
            }
            database.categoryDao().updateCategoryBudget(categories[i].name, newBudget.coerceAtLeast(0.0))
            remainingBudget -= newBudget
        }
    }

    suspend fun addSaving(name: String, targetAmount: Double, icon: String) {
        if (targetAmount <= 0 || name.isEmpty()) return
        val saving = Saving(name = name, targetAmount = targetAmount, currentAmount = 0.0, icon = icon)
        database.savingDao().insertSaving(saving)
    }

    suspend fun addToSaving(savingName: String, amount: Double) {
        if (amount <= 0) return
        database.savingDao().addToSaving(savingName, amount)
    }

    suspend fun deleteSaving(saving: Saving) {
        database.savingDao().deleteSaving(saving)
    }

    suspend fun getTotalBudget(): Double {
        val categories = database.categoryDao().getCategoriesList()
        return categories.sumOf { it.budget }
    }

    suspend fun getTotalSpent(): Double {
        val categories = database.categoryDao().getCategoriesList()
        return categories.sumOf { it.spent }
    }

    suspend fun getAverageSpentPerDay(): Double {
        val transactions = database.transactionDao().getTransactionsList()
        if (transactions.isEmpty()) return 0.0

        val dates = transactions.map { it.date }
        val firstDate = dates.minOrNull() ?: return 0.0
        val lastDate = dates.maxOrNull() ?: return 0.0
        val days = TimeUnit.DAYS.convert(lastDate.time - firstDate.time, TimeUnit.MILLISECONDS) + 1
        val totalSpent = getTotalSpent()
        return if (days > 0) totalSpent / days else 0.0
    }

    suspend fun getMostExpensiveCategory(): Pair<String, Double>? {
        val categories = database.categoryDao().getCategoriesList()
        if (categories.isEmpty()) return null
        return categories.maxByOrNull { it.spent }?.let {
            if (it.spent > 0) it.name to it.spent else null
        }
    }

    suspend fun getCategoryPercentage(categoryName: String): Double {
        val total = getTotalSpent()
        if (total == 0.0) return 0.0
        val categories = database.categoryDao().getCategoriesList()
        val category = categories.find { it.name == categoryName }
        return (category?.spent ?: 0.0) / total * 100
    }

    suspend fun getMonthEndForecast(): Double {
        val today = Date()
        val calendar = Calendar.getInstance()
        calendar.time = today
        val lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, lastDayOfMonth)
        val daysLeft = TimeUnit.DAYS.convert(calendar.time.time - today.time, TimeUnit.MILLISECONDS) + 1

        val avgPerDay = getAverageSpentPerDay()
        val projectedSpending = avgPerDay * daysLeft
        val remainingBudget = getTotalBudget() - getTotalSpent()
        return remainingBudget - projectedSpending
    }

    suspend fun searchTransactionsByComment(searchText: String): List<Transaction> {
        return if (searchText.isEmpty()) {
            database.transactionDao().getTransactionsList()
        } else {
            database.transactionDao().searchTransactions(searchText)
        }
    }

    suspend fun loadInitialData() {
        val categories = database.categoryDao().getCategoriesList()
        if (categories.isEmpty()) {
            val defaultCategories = listOf(
                Category(name = "Еда", budget = 15000.0, spent = 0.0, icon = "🍔"),
                Category(name = "Транспорт", budget = 5000.0, spent = 0.0, icon = "🚗"),
                Category(name = "Развлечения", budget = 5000.0, spent = 0.0, icon = "🎬"),
                Category(name = "Жильё", budget = 15000.0, spent = 0.0, icon = "🏠"),
                Category(name = "Здоровье", budget = 5000.0, spent = 0.0, icon = "💊")
            )
            for (category in defaultCategories) {
                database.categoryDao().insertCategory(category)
            }
        }
    }
}