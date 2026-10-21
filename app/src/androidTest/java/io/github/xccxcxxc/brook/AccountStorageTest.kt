package io.github.xccxcxxc.brook

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xccxcxxc.brook.data.Account
import io.github.xccxcxxc.brook.data.AccountType
import io.github.xccxcxxc.brook.data.Prefs
import io.github.xccxcxxc.brook.data.SecretBox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 账号凭据在真机/模拟器上的存法（真实的 Android Keystore，Robolectric 没有）：
 * 磁盘上不留明文密码和 token；重启 App 后还能用；旧版明文账号升级后自动迁移、保持登录。
 * 每次 new 一个 [Prefs] = 模拟一次冷启动（重新从磁盘读）。
 */
@RunWith(AndroidJUnit4::class)
class AccountStorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsDir get() = File(context.applicationInfo.dataDir, "shared_prefs")

    private fun diskText(): String = prefsDir.listFiles().orEmpty().filter { it.isFile }.joinToString("\n") { it.readText() }

    @Before
    fun setUp() {
        Prefs(context).saveAccount(null)
    }

    @After
    fun tearDown() {
        Prefs(context).saveAccount(null)
        Graph.get().prefs.saveAccount(null) // App 单例里的内存状态也清掉，别影响后面的测试
    }

    @Test
    fun tokenIsEncryptedOnDiskAndSurvivesRestart() {
        Prefs(context).saveAccount(Account(AccountType.FRESHRSS, "https://rss.example.com/api/greader.php", "alice"), "SECRET-TOKEN-123")

        assertFalse("磁盘上不能有明文 token", diskText().contains("SECRET-TOKEN-123"))

        val restarted = Prefs(context)
        val account = restarted.account.value
        assertNotNull(account)
        assertEquals("alice", account!!.username)
        assertEquals("https://rss.example.com/api/greader.php", account.apiBase)
        assertEquals("SECRET-TOKEN-123", restarted.authToken())
    }

    @Test
    fun legacyPlaintextAccountIsMigratedAndStaysLoggedIn() {
        // 旧版的存法：账号、明文密码、明文 token 都在 brook.xml 里
        context.getSharedPreferences("brook", Context.MODE_PRIVATE).edit(commit = true) {
            putString("acc_type", AccountType.MINIFLUX.name)
            putString("acc_base", "http://192.168.1.10:8080")
            putString("acc_user", "bob")
            putString("acc_pass", "OLD-PASSWORD-456")
            putString("acc_token", "OLD-TOKEN-789")
            putString("acc_desc", "家里")
        }

        Prefs(context) // 升级后第一次启动

        val text = diskText()
        assertFalse("旧的明文密码要删掉", text.contains("OLD-PASSWORD-456"))
        assertFalse("旧的明文 token 要删掉", text.contains("OLD-TOKEN-789"))

        val restarted = Prefs(context) // 再启动一次：从迁移后的文件读
        val account = restarted.account.value
        assertNotNull("升级后仍然是登录状态", account)
        assertEquals(AccountType.MINIFLUX, account!!.type)
        assertEquals("bob", account.username)
        assertEquals("家里", account.description)
        assertEquals("OLD-TOKEN-789", restarted.authToken())
    }

    @Test
    fun logoutRemovesAccount() {
        Prefs(context).saveAccount(Account(AccountType.FRESHRSS, "https://rss.example.com/api/greader.php", "alice"), "TOKEN-X")
        Prefs(context).saveAccount(null)

        val restarted = Prefs(context)
        assertNull(restarted.account.value)
        assertEquals("", restarted.authToken())
        assertFalse(diskText().contains("alice"))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = SecretBox.encrypt("hello")
        assertTrue(sealed.startsWith("v1:"))
        assertEquals("hello", SecretBox.decrypt(sealed))
        val chars = sealed.toCharArray()
        val i = chars.size / 2 // 改密文中间的一个字符
        chars[i] = if (chars[i] == 'A') 'B' else 'A'
        assertNull(SecretBox.decrypt(String(chars)))
        assertNull(SecretBox.decrypt("plain-text"))
    }
}
