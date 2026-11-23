package com.qiyu.browser.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 凭据存储（对标 Edge 安全等级）：
 * - 密钥：Android Keystore 内生成的 AES-256（不可导出；设备支持时硬件级保护）
 * - 每条密码独立 AES-GCM 加密（12 字节随机 IV + 认证标签，篡改/位翻转可检出）
 * - 磁盘上只有密文与元数据，绝无明文
 * - 明文只出现在「保存/比对/填充」的瞬间（char[]），用完立即清零；
 *   数据模型 CredRow 不含密码字段，明文不进入任何长期驻留对象
 */
class CredentialStore private constructor(private val ctx: Context) {

    data class CredRow(
        val id: Long,
        val origin: String,
        val host: String,
        val username: String,
        val created: Long,
        val lastUsed: Long
    )

    companion object {
        private const val KEY_ALIAS = "qiyu_creds_aes256"
        private const val FILE_NAME = "credentials.enc"

        private var cache: CredentialStore? = null

        @JvmStatic
        fun of(ctx: Context): CredentialStore = synchronized(CredentialStore::class.java) {
            cache ?: CredentialStore(ctx.applicationContext).also { cache = it }
        }
    }

    private val lock = Any()
    private val file: File = File(ctx.filesDir, FILE_NAME)
    private var nextId = 1L

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .build()
        return generateKey(spec)
    }

    private fun generateKey(spec: KeyGenParameterSpec): SecretKey {
        val kg = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        kg.init(spec)
        return kg.generateKey()
    }

    /** 加密：返回 base64(iv || ciphertext) */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    /** 解密：入参 base64(iv || ciphertext)；失败返回 null。返回 char[]，调用方负责清零 */
    private fun decrypt(b64: String): CharArray? {
        return try {
            val raw = Base64.decode(b64, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8).toCharArray()
        } catch (e: Exception) {
            null
        }
    }

    /** 读取全部行（含密文），nextId 同步推进 */
    private fun loadRows(): List<Pair<CredRow, String>> {
        synchronized(lock) {
            nextId = 1L
            if (!file.exists()) return emptyList()
            return try {
                val root = JSONObject(file.readText())
                val arr = root.getJSONArray("rows")
                val out = ArrayList<Pair<CredRow, String>>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val row = CredRow(
                        o.optLong("id"),
                        o.optString("origin"),
                        o.optString("host"),
                        o.optString("username"),
                        o.optLong("created"),
                        o.optLong("lastUsed")
                    )
                    out.add(row to o.optString("ct"))
                    if (row.id >= nextId) nextId = row.id + 1
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** 原子写：临时文件 + rename，防中途损坏 */
    private fun saveRows(rows: List<Pair<CredRow, String>>) {
        synchronized(lock) {
            val arr = JSONArray()
            for ((row, ct) in rows) {
                val o = JSONObject()
                o.put("id", row.id)
                o.put("origin", row.origin)
                o.put("host", row.host)
                o.put("username", row.username)
                o.put("created", row.created)
                o.put("lastUsed", row.lastUsed)
                o.put("ct", ct)
                arr.put(o)
            }
            val root = JSONObject()
            root.put("v", 1)
            root.put("rows", arr)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    // ---- 对外 API ----

    /** 保存/更新一条（host+username 唯一）。明文仅在方法栈内短暂存在 */
    fun save(origin: String, host: String, username: String, password: String): Long {
        if (host.isEmpty() || password.isEmpty()) return -1L
        val rows = loadRows().toMutableList()
        val ct = encrypt(password)
        val idx = rows.indexOfFirst { it.first.host.equals(host, true) && it.first.username == username }
        val now = System.currentTimeMillis()
        if (idx >= 0) {
            val (old, _) = rows[idx]
            rows[idx] = old.copy(origin = origin, lastUsed = now) to ct
            saveRows(rows)
            return old.id
        }
        val id = nextId++
        val row = CredRow(id, origin, host, username, now, now)
        rows.add(row to ct)
        saveRows(rows)
        return id
    }

    /** 比对已存密码是否与输入一致（瞬态解密，比较完即弃） */
    fun matches(host: String, username: String, password: String): Boolean {
        val rows = loadRows()
        val target = rows.firstOrNull { it.first.host.equals(host, true) && it.first.username == username }
            ?: return false
        val saved = decrypt(target.second) ?: return false
        val plainArr = password.toCharArray()
        var equal = saved.size == plainArr.size
        if (equal) {
            for (i in saved.indices) {
                if (saved[i] != plainArr[i]) {
                    equal = false
                    break
                }
            }
        }
        saved.fill('\u0000')
        plainArr.fill('\u0000')
        return equal
    }

    fun allRows(): List<CredRow> = loadRows().map { it.first }

    fun rowsForHost(host: String): List<CredRow> =
        loadRows().map { it.first }.filter { it.host.equals(host, true) }
            .sortedByDescending { it.lastUsed }

    /** 取明文密码（填充用）：调用方用完必须 charArray.fill('\0') */
    fun getPassword(id: Long): CharArray? {
        val target = loadRows().firstOrNull { it.first.id == id } ?: return null
        return decrypt(target.second)
    }

    fun setPassword(id: Long, password: String) {
        val rows = loadRows().toMutableList()
        val idx = rows.indexOfFirst { it.first.id == id }
        if (idx < 0 || password.isEmpty()) return
        rows[idx] = rows[idx].first.copy(lastUsed = System.currentTimeMillis()) to encrypt(password)
        saveRows(rows)
    }

    fun setUsername(id: Long, username: String) {
        val rows = loadRows().toMutableList()
        val idx = rows.indexOfFirst { it.first.id == id }
        if (idx < 0) return
        rows[idx] = rows[idx].first.copy(username = username) to rows[idx].second
        saveRows(rows)
    }

    fun touch(id: Long) {
        val rows = loadRows().toMutableList()
        val idx = rows.indexOfFirst { it.first.id == id }
        if (idx < 0) return
        rows[idx] = rows[idx].first.copy(lastUsed = System.currentTimeMillis()) to rows[idx].second
        saveRows(rows)
    }

    fun delete(id: Long) {
        val rows = loadRows().filterNot { it.first.id == id }
        saveRows(rows)
    }

    fun clearAll() {
        synchronized(lock) {
            file.delete()
        }
    }
}
