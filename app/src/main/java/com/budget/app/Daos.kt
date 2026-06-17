package com.budget.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesList(): List<Category>

    @Insert
    suspend fun insertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("UPDATE categories SET spent = :spent WHERE name = :name")
    suspend fun updateCategorySpent(name: String, spent: Double)

    @Query("UPDATE categories SET budget = :budget WHERE name = :name")
    suspend fun updateCategoryBudget(name: String, budget: Double)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getTransactionsList(): List<Transaction>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("UPDATE transactions SET isCompleted = 1 WHERE id = :id")
    suspend fun markTransactionCompleted(id: Long)

    @Query("SELECT * FROM transactions WHERE comment LIKE '%' || :searchText || '%' ORDER BY date DESC")
    suspend fun searchTransactions(searchText: String): List<Transaction>
}

@Dao
interface SavingDao {
    @Query("SELECT * FROM savings")
    fun getAllSavings(): Flow<List<Saving>>

    @Query("SELECT * FROM savings")
    suspend fun getSavingsList(): List<Saving>

    @Insert
    suspend fun insertSaving(saving: Saving)

    @Delete
    suspend fun deleteSaving(saving: Saving)

    @Update
    suspend fun updateSaving(saving: Saving)

    @Query("UPDATE savings SET currentAmount = currentAmount + :amount WHERE name = :name")
    suspend fun addToSaving(name: String, amount: Double)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY dayOfMonth")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    suspend fun getRemindersList(): List<Reminder>

    @Insert
    suspend fun insertReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Update
    suspend fun updateReminder(reminder: Reminder)
}