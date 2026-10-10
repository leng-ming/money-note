package com.local.moneynote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        BudgetCarryoverEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetCarryoverDao(): BudgetCarryoverDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * v1 → v2：分类表加入 parent_id，支持二级分类，并给预置的一级分类补上常用细分。
         *
         * 这是纯增量迁移：只 ALTER 加一列 + 插入新的二级分类行，
         * 老账单、老账户、老预算一条都不会动。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parent_id INTEGER")
                seedSubCategories(db)
            }
        }

        /**
         * v2 → v3：新增「预算转结」表。纯建表，不动任何已有数据。
         *
         * 列定义必须和 [BudgetCarryoverEntity] 完全一致，否则 Room 校验 schema 时会抛
         * IllegalStateException: Migration didn't properly handle ...
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budget_carryovers` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`category_id` INTEGER, " +
                        "`from_year` INTEGER NOT NULL, " +
                        "`from_month` INTEGER NOT NULL, " +
                        "`to_year` INTEGER NOT NULL, " +
                        "`to_month` INTEGER NOT NULL, " +
                        "`amount_cents` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v3 → v4：支持「转账」。
         *
         * 加了 to_account_id 和 fee_cents 两列，同时把 category_id 改成**可空**
         * （转账不属于任何分类）。
         *
         * SQLite 不支持修改列的可空性，所以只能走标准的「重建表」三步：
         * 建新表 → 把老数据搬过去 → 删老表改名。**数据一条都不会丢**。
         * 表结构必须和 [TransactionEntity] 完全一致，否则 Room 校验 schema 会直接抛异常。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`amount_cents` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`account_id` INTEGER NOT NULL, " +
                        "`to_account_id` INTEGER, " +
                        "`fee_cents` INTEGER NOT NULL DEFAULT 0, " +
                        "`category_id` INTEGER, " +
                        "`note` TEXT NOT NULL, " +
                        "`occurred_at` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "`exclude_from_stats` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `transactions_new` (" +
                        "`id`, `amount_cents`, `kind`, `account_id`, `to_account_id`, `fee_cents`, " +
                        "`category_id`, `note`, `occurred_at`, `created_at`, `updated_at`, `exclude_from_stats`) " +
                        "SELECT `id`, `amount_cents`, `kind`, `account_id`, NULL, 0, " +
                        "`category_id`, `note`, `occurred_at`, `created_at`, `updated_at`, `exclude_from_stats` " +
                        "FROM `transactions`"
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

                // 索引跟着一起重建，不然查询会退化成全表扫描
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at` " +
                        "ON `transactions` (`occurred_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_category_id` " +
                        "ON `transactions` (`category_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_account_id` " +
                        "ON `transactions` (`account_id`)"
                )
            }
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
                seedSubCategories(db)
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

        /** 一级分类 → 常用二级细分的预置表 */
        private val SUB_CATEGORY_SEEDS: List<Triple<TxKind, String, List<String>>> = listOf(
            Triple(TxKind.EXPENSE, "餐饮", listOf("早餐", "午餐", "晚餐", "奶茶", "咖啡", "零食", "水果", "外卖", "烟酒")),
            Triple(TxKind.EXPENSE, "交通", listOf("公交地铁", "打车", "加油", "停车", "过路费", "共享单车")),
            Triple(TxKind.EXPENSE, "购物", listOf("日用品", "服饰", "数码", "美妆", "家居")),
            Triple(TxKind.EXPENSE, "居住", listOf("房租", "水电", "燃气", "物业", "宽带")),
            Triple(TxKind.EXPENSE, "通讯", listOf("话费", "流量")),
            Triple(TxKind.EXPENSE, "娱乐", listOf("电影", "游戏", "旅游", "运动", "KTV")),
            Triple(TxKind.EXPENSE, "医疗", listOf("挂号", "药品", "体检")),
            Triple(TxKind.EXPENSE, "教育", listOf("书籍", "课程", "培训")),
            Triple(TxKind.EXPENSE, "人情", listOf("红包", "礼物", "请客")),
            Triple(TxKind.INCOME, "理财", listOf("利息", "基金", "股票"))
        )

        /**
         * 给一级分类挂上二级细分。二级分类继承一级的图标与配色，免得记账页的颜色花掉。
         * 幂等：同一父分类下已存在的细分不会重复插入（首次建库和 v1→v2 迁移都会调用它）。
         */
        private fun seedSubCategories(db: SupportSQLiteDatabase) {
            SUB_CATEGORY_SEEDS.forEach { (kind, parentName, children) ->
                var parentId = -1L
                var iconKey = "more"
                var colorHex = "#FF757575"

                db.query(
                    "SELECT id, icon_key, color_hex FROM categories " +
                        "WHERE name = ? AND kind = ? AND parent_id IS NULL LIMIT 1",
                    arrayOf(parentName, kind.name)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        parentId = cursor.getLong(0)
                        iconKey = cursor.getString(1) ?: "more"
                        colorHex = cursor.getString(2) ?: "#FF757575"
                    }
                }
                if (parentId < 0L) return@forEach

                children.forEachIndexed { index, childName ->
                    var exists = false
                    db.query(
                        "SELECT COUNT(*) FROM categories WHERE parent_id = ? AND name = ?",
                        arrayOf(parentId, childName)
                    ).use { c ->
                        if (c.moveToFirst()) exists = c.getInt(0) > 0
                    }
                    if (exists) return@forEachIndexed

                    db.execSQL(
                        "INSERT INTO categories (name, kind, parent_id, icon_key, color_hex, sort_order, archived, is_system) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 0, 1)",
                        arrayOf(childName, kind.name, parentId, iconKey, colorHex, index)
                    )
                }
            }
        }
    }
}
