package com.local.moneynote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "money_note.db"
                )
                    .addCallback(SeedCallback)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * 首次建库时写入默认账户与分类。
         * 这里刻意用原始 SQL 而不是 DAO —— 在 onCreate 回调里调 DAO 会触发数据库重复打开而死锁。
         */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedAccounts(db)
                seedCategories(db)
            }

            private fun seedAccounts(db: SupportSQLiteDatabase) {
                val accounts = listOf(
                    Triple("现金", "CASH", "#FF43A047"),
                    Triple("微信", "WECHAT", "#FF07C160"),
                    Triple("支付宝", "ALIPAY", "#FF1677FF"),
                    Triple("银行卡", "BANK", "#FF5C6BC0")
                )
                accounts.forEachIndexed { index, (name, type, color) ->
                    db.execSQL(
                        "INSERT INTO accounts (name, type, initial_balance_cents, color_hex, sort_order, archived, created_at) " +
                            "VALUES (?, ?, 0, ?, ?, 0, ?)",
                        arrayOf(name, type, color, index, System.currentTimeMillis())
                    )
                }
            }

            private fun seedCategories(db: SupportSQLiteDatabase) {
                // name, kind, iconKey, color
                val cats = listOf(
                    // ---- 支出 ----
                    listOf("餐饮", "EXPENSE", "food", "#FFEF6C00"),
                    listOf("交通", "EXPENSE", "transport", "#FF1E88E5"),
                    listOf("购物", "EXPENSE", "shopping", "#FFD81B60"),
                    listOf("居住", "EXPENSE", "home", "#FF6D4C41"),
                    listOf("通讯", "EXPENSE", "phone", "#FF00897B"),
                    listOf("娱乐", "EXPENSE", "game", "#FF8E24AA"),
                    listOf("医疗", "EXPENSE", "medical", "#FFE53935"),
                    listOf("教育", "EXPENSE", "edu", "#FF3949AB"),
                    listOf("人情", "EXPENSE", "gift", "#FFF4511E"),
                    listOf("其他", "EXPENSE", "more", "#FF757575"),
                    // ---- 收入 ----
                    listOf("工资", "INCOME", "salary", "#FF2E7D32"),
                    listOf("奖金", "INCOME", "bonus", "#FF00897B"),
                    listOf("兼职", "INCOME", "parttime", "#FF1E88E5"),
                    listOf("理财", "INCOME", "invest", "#FFEF6C00"),
                    listOf("红包", "INCOME", "redpacket", "#FFD81B60"),
                    listOf("其他", "INCOME", "more", "#FF757575")
                )
                cats.forEachIndexed { index, c ->
                    db.execSQL(
                        "INSERT INTO categories (name, kind, icon_key, color_hex, sort_order, archived, is_system) " +
                            "VALUES (?, ?, ?, ?, ?, 0, 1)",
                        arrayOf(c[0], c[1], c[2], c[3], index % 10)
                    )
                }
            }
        }
    }
}
