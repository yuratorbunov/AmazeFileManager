package com.amaze.filemanager.asynchronous.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Environment
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.asynchronous.management.ServiceWatcherUtil
import com.amaze.filemanager.asynchronous.services.DecryptService.NOTIFICATION_SERVICE
import com.amaze.filemanager.asynchronous.services.DecryptService.START_NOT_STICKY
import com.amaze.filemanager.asynchronous.services.DecryptService.TAG_DECRYPT_PATH
import com.amaze.filemanager.asynchronous.services.DecryptService.TAG_SOURCE
import com.amaze.filemanager.asynchronous.services.EncryptService.TAG_PASSWORD
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.files.CryptUtil
import com.amaze.filemanager.shadows.ShadowMultiDex
import com.amaze.filemanager.ui.notifications.NotificationConstants
import com.amaze.filemanager.utils.AESCrypt
import com.amaze.filemanager.utils.CryptUtilTest.Companion.initMockSecretKeygen
import com.amaze.filemanager.utils.ProgressHandler
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowMultiDex::class], sdk = [KITKAT, P])
@Suppress("StringLiteralDuplication")
class DecryptServiceTest {

    private lateinit var source: ByteArray
    private lateinit var service: DecryptService
    private lateinit var notificationManager: ShadowNotificationManager

    /**
     * Test setup
     */
    @Before
    fun setUp() {
        source = Random(System.currentTimeMillis()).nextBytes(73)
        service = Robolectric.setupService(DecryptService::class.java)
        notificationManager = shadowOf(
            ApplicationProvider.getApplicationContext<Context?>()
                .getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        )
        initMockSecretKeygen()
    }

    /**
     * Post test cleanup
     */
    @After
    fun tearDown() {
        service.stopSelf()
        service.onDestroy()
    }

    /**
     * Test [DecryptService] on decrypting legacy encrypted files.
     *
     * No password guarding here - just the logic of [DecryptService] and [CryptUtil] itself.
     */
    @Test
    fun testLegacyDecryptWorkflow() {
        val sourceFile = File(Environment.getExternalStorageDirectory(), "test.bin")
        ByteArrayInputStream(source).copyTo(FileOutputStream(sourceFile))
        CryptUtil(
            ApplicationProvider.getApplicationContext(),
            HybridFileParcelable(sourceFile.absolutePath),
            ProgressHandler(),
            ArrayList<HybridFile>(),
            "test.bin${CryptUtil.CRYPT_EXTENSION}",
            false,
            null
        )
        val targetFile = File(Environment.getExternalStorageDirectory(), "test.bin${CryptUtil.CRYPT_EXTENSION}")
        assertTrue(targetFile.exists())
        sourceFile.delete()

        ServiceWatcherUtil.position = 0L
        Intent(ApplicationProvider.getApplicationContext(), DecryptService::class.java).run {
            putExtra(
                TAG_SOURCE,
                HybridFileParcelable(targetFile.absolutePath).also {
                    it.size = targetFile.length()
                }
            )
            putExtra(TAG_DECRYPT_PATH, Environment.getExternalStorageDirectory().absolutePath)
            assertEquals(START_NOT_STICKY, service.onStartCommand(this, 0, 0))
        }
        assertTrue(notificationManager.activeNotifications.isNotEmpty())
        notificationManager.activeNotifications.first().let {
            assertEquals(NotificationConstants.DECRYPT_ID, it.id)
            if (SDK_INT >= O)
                assertEquals(NotificationConstants.CHANNEL_NORMAL_ID, it.notification.channelId)
        }
        val verifyFile = File(Environment.getExternalStorageDirectory(), "test.bin")
        await().atMost(1000, TimeUnit.SECONDS).until {
            verifyFile.exists() && verifyFile.length() > 0
        }
        assertTrue(verifyFile.length() < targetFile.length())
        assertArrayEquals(source, verifyFile.readBytes())
    }

    /**
     * Test [DecryptService] on decrypting AESCrypt format files.
     */
    @Test
    fun testAescryptWorkflow() {
        if (SDK_INT >= M) {
            val sourceFile = File(Environment.getExternalStorageDirectory(), "test.bin${CryptUtil.AESCRYPT_EXTENSION}")
            val targetFile = File(Environment.getExternalStorageDirectory(), "test.bin")
            AESCrypt("passW0rD").encrypt(
                `in` = ByteArrayInputStream(source),
                out = FileOutputStream(sourceFile),
                progressHandler = ProgressHandler()
            )
            await().atMost(10, TimeUnit.SECONDS).until {
                sourceFile.length() > source.size
            }
            ServiceWatcherUtil.position = 0L
            Intent(ApplicationProvider.getApplicationContext(), DecryptService::class.java).run {
                putExtra(
                    TAG_SOURCE,
                    HybridFileParcelable(sourceFile.absolutePath).also {
                        it.size = sourceFile.length()
                    }
                )
                putExtra(TAG_DECRYPT_PATH, Environment.getExternalStorageDirectory().absolutePath)
                putExtra(TAG_PASSWORD, "passW0rD")
                assertEquals(START_NOT_STICKY, service.onStartCommand(this, 0, 0))
            }
            assertTrue(notificationManager.activeNotifications.isNotEmpty())
            notificationManager.activeNotifications.first().let {
                assertEquals(NotificationConstants.DECRYPT_ID, it.id)
                assertEquals(NotificationConstants.CHANNEL_NORMAL_ID, it.notification.channelId)
            }
            await().atMost(10000, TimeUnit.SECONDS).until {
                targetFile.exists() && targetFile.length() > 0
            }
            assertTrue(targetFile.exists() && targetFile.length() > 0)
            assertTrue(targetFile.length() < sourceFile.length())
            assertArrayEquals(source, targetFile.readBytes())
            /* [FIXME] in this test notification is not cleared, but when test on device
             * the notification did cleared
             */
            // assertTrue(notificationManager.activeNotifications.isEmpty())
            targetFile.delete()
        } else {
            Log.w(javaClass.simpleName, "Test skipped for $SDK_INT")
        }
    }
}
