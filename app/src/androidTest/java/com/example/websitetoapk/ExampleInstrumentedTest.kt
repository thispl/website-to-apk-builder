package com.example.websitetoapk

import android.net.Uri
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.websitetoapk", appContext.packageName)
    }

    @Test
    fun loginWithoutViewportFitsWebView() {
        val webView = AtomicReference<WebView>()
        val layout = AtomicReference<String>()
        val html = """
            <!doctype html><html><head><title>Login</title></head><body>
            <main id="page-login"><div id="app" style="padding:16px">
            <form><input aria-label="Email"></form>
            </div></main></body></html>
        """.trimIndent()

        composeRule.setContent {
            WebViewScreen("data:text/html;charset=utf-8,${Uri.encode(html)}") { webView.set(it) }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            webView.get()?.let { view ->
                view.post {
                    view.evaluateJavascript(
                        """
                        (function() {
                            var viewport = document.querySelector('meta[name="viewport"]');
                            if (!viewport) return null;
                            var app = document.getElementById('app').getBoundingClientRect();
                            return viewport.content === 'width=device-width, initial-scale=1'
                                && document.documentElement.scrollWidth === window.innerWidth
                                && app.right <= window.innerWidth
                                && app.height < window.innerHeight;
                        })()
                        """.trimIndent()
                    ) { result -> if (result != "null") layout.set(result) }
                }
            }
            layout.get() != null
        }
        assertEquals("true", layout.get())
    }
}