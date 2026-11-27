package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.NucleoLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Núcleo Chat", appName)
  }

  @Test
  fun `logger initializes and records session log file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    NucleoLogger.init(context)
    NucleoLogger.i("Test", "Teste de log do Núcleo Chat")
    NucleoLogger.flushNowBlocking()

    val publicPath = NucleoLogger.getPublicFolderDisplayPath()
    assertTrue(publicPath.startsWith("Downloads/NucleoChat/nucleo_log_"))
    val internalFile = NucleoLogger.getInternalLogFile()
    assertTrue(internalFile != null && internalFile.exists() && internalFile.readText().contains("Teste de log do Núcleo Chat"))
  }
}
