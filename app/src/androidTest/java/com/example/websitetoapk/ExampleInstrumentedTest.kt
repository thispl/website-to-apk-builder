package com.example.websitetoapk

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
            <!doctype html><html><head><title>Login</title>
            <style>
                .navbar > .container { display:flex; align-items:center; justify-content:space-between; }
                .navbar-brand { white-space:nowrap; margin-right:16px; }
            </style></head><body>
            <nav class="navbar navbar-expand-lg"><div class="container">
            <a class="navbar-brand"><div style="width:100%;display:flex;padding:6px 20px">
            <div>SHAYA POLYMERS LIMITED</div>
            <div id="tagline" style="white-space:nowrap;margin-left:450px">
            <span style="font-size:20px">Innovation ..</span>
            <span style="font-size:20px">Excellence ..</span>
            </div>
            </div></a><button class="navbar-toggler">Menu</button>
            </div></nav>
            <main id="page-login"><div id="app" style="padding:16px">
            <form><input aria-label="Email"></form>
            </div></main></body></html>
        """.trimIndent()

        composeRule.setContent {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                WebViewScreen("data:text/html;charset=utf-8,${Uri.encode(html)}") { webView.set(it) }
            }
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
                            var tagline = document.getElementById('tagline').getBoundingClientRect();
                            var menu = document.querySelector('.navbar-toggler').getBoundingClientRect();
                            return viewport.content === 'width=device-width, initial-scale=1'
                                && document.documentElement.scrollWidth === window.innerWidth
                                && app.right <= window.innerWidth
                                && app.height < window.innerHeight
                                && tagline.right <= menu.left
                                && menu.right <= window.innerWidth;
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