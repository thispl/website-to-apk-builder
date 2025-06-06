package com.example.websitetoapk

import android.content.Context
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.websitetoapk.ui.theme.WebsiteToAPKTheme
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_DOMAIN = "saved_domain"
        const val DEFAULT_URL = "http://115.244.98.51/app"
    }

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebsiteToAPKTheme {
                DomainBasedWebView(
                    onWebViewCreated = { webView -> webViewRef = webView }
                )
            }
        }
    }

    override fun onBackPressed() {
        webViewRef?.let {
            if (it.canGoBack()) {
                it.goBack()
                return
            }
        }
        super.onBackPressed()
    }
}

@Composable
fun DomainBasedWebView(onWebViewCreated: (WebView) -> Unit) {
    val context = LocalContext.current
    var domain by remember { mutableStateOf<String?>(null) }
    var isLoadingDomain by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        domain = prefs.getString(MainActivity.KEY_DOMAIN, MainActivity.DEFAULT_URL)
        isLoadingDomain = false
    }

    if (isLoadingDomain) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        domain?.let {
            WebViewScreen(
                url = it,
                onWebViewCreated = onWebViewCreated,
                onChangeDomain = {
                    context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().remove(MainActivity.KEY_DOMAIN).apply()
                    domain = MainActivity.DEFAULT_URL
                }
            )
        }
    }
}

@Composable
fun WebViewScreen(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onChangeDomain: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = {
                isRefreshing = true
                webViewRef?.reload()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        onWebViewCreated(this)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        requestFocus()
                        isFocusable = true
                        isFocusableInTouchMode = true

                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isRefreshing = false

                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = `
                                            #app {
                                                display: flex !important;
                                                justify-content: center !important;
                                                align-items: center !important;
                                                height: 100vh !important;
                                                width: 100vw !important;
                                                overflow: hidden !important;
                                            }
                                            .splash, .splash-screen, .spinner, .loading {
                                                position: fixed !important;
                                                top: 50% !important;
                                                left: 50% !important;
                                                transform: translate(-50%, -50%) !important;
                                                z-index: 9999 !important;
                                            }
                                            .awesomplete > ul {
                                                max-height: 300px !important;
                                                overflow-y: auto !important;
                                                z-index: 9999 !important;
                                            }
                                            .dropdown-menu {
                                                max-height: 300px !important;
                                                overflow-y: auto !important;
                                                z-index: 9999 !important;
                                            }
                                            .modal, .dropdown, .awesomplete {
                                                z-index: 9999 !important;
                                            }
                                        `;
                                        document.head.appendChild(style);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.proceed() // ⚠️ Use only for testing, not production
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                isRefreshing = false
                            }
                        }

                        settings.userAgentString =
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.96 Safari/537.36"

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        loadUrl(url)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 35.dp)
            )
        }
    }
}
