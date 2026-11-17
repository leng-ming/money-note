package com.local.moneynote.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.local.moneynote.data.BackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 可选的本地自动备份。
 *
 * 不引入 WorkManager、也不申请任何新权限：用户自己挑一个目录（SAF 授权，可持久化），
 * App 每次启动时看一眼「距上次备份超过一天没有」，超了就顺手导一份进那个目录。
 *
 * 为什么用「启动时检查」而不是定时任务：记账 App 本来就是每天会打开几次的，
 * 定时唤醒反而更费电、还要多一套 JobScheduler 的兼容处理。
 * 代价是长期不开 App 就不会备份 —— 但那种情况下本来也没有新账要备份。
 *
 * 目录里只留最近 [KEEP] 份，不至于把用户的网盘撑爆。
 */
object AutoBackup {

    private const val PREF = "moneynote_autobackup"
    private const val KEY_DIR = "dir_uri"
    private const val KEY_LAST = "last_at"

    /** 保留最近几份 */
    private const val KEEP = 7

    /** 两次自动备份的最小间隔：一天 */
    private const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** 只清理自己生成的文件，用户手动放进来的东西一概不碰 */
    private const val NAME_PREFIX = "记账自动备份-"

    fun dirUri(context: Context): Uri? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_DIR, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDir(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIR, uri?.toString())
            .apply()
    }

    fun lastAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    /** 关了自动备份就把上次时间也清掉，重新打开时会立刻备份一份 */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * 到点就备份一份。
     *
     * @return 写出的文件名；不需要备份（没设置目录 / 还没到一天）时返回 null
     */
    suspend fun maybeRun(context: Context, payload: BackupPayload): String? =
        withContext(Dispatchers.IO) {
            val dir = dirUri(context) ?: return@withContext null
            val now = System.currentTimeMillis()
            if (now - lastAt(context) < MIN_INTERVAL_MS) return@withContext null

            val json = Backup.toJson(payload)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
            val name = "$NAME_PREFIX$stamp.json"

            val ok = runCatching {
                val doc = DocumentsContract.createDocument(
                    context.contentResolver,
                    dir,
                    "application/json",
                    name
                ) ?: error("建不了备份文件")
                val out = context.contentResolver.openOutputStream(doc)
                    ?: error("打不开输出流")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                trim(context, dir)
            }.isSuccess

            if (!ok) return@withContext null

            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST, now)
                .apply()
            name
        }

    /** 只保留最近 KEEP 份自己生成的文件 */
    private fun trim(context: Context, dir: Uri) {
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                dir,
                DocumentsContract.getTreeDocumentId(dir)
            )
            val resolver = context.contentResolver
            // name / lastModified / documentUri
            val mine = mutableListOf<Triple<String, Long, Uri>>()

            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (!name.startsWith(NAME_PREFIX)) continue
                    mine.add(
                        Triple(
                            name,
                            c.getLong(2),
                            DocumentsContract.buildDocumentUriUsingTree(dir, id)
                        )
                    )
                }
            }

            mine.sortedByDescending { it.second }
                .drop(KEEP)
                .forEach { (_, _, uri) ->
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                }
        }
    }
}
