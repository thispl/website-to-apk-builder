package com.example.websitetoapk
import android.content.Intent
import java.io.ByteArrayInputStream
import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.example.websitetoapk.ui.theme.WebsiteToAPKTheme
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import java.io.File
import javax.net.ssl.*
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import android.content.ContentValues
import android.provider.MediaStore
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat



class MainActivity : ComponentActivity() {

    var fileCallback: ValueCallback<Array<Uri>>? = null

    val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_channel",
                "PDF Downloads",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS) // 👈 ADD HERE
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) permissionLauncher.launch(notGranted.toTypedArray())
    }


    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_DOMAIN = "saved_domain"
//        const val DEFAULT_URL = "https://103.103.9.11/app"
        const val DEFAULT_URL = "https://erp.shaherunited.com/app"
    }

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestAppPermissions()
        enableEdgeToEdge()
        setContent {
            WebsiteToAPKTheme {
                DomainBasedWebView { webViewRef = it }
            }
        }
    }

    override fun onBackPressed() {
        webViewRef?.takeIf { it.canGoBack() }?.goBack() ?: super.onBackPressed()
    }
}

@Composable
fun DomainBasedWebView(onWebViewCreated: (WebView) -> Unit) {
    val context = LocalContext.current
    var domain by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        domain = prefs.getString(MainActivity.KEY_DOMAIN, MainActivity.DEFAULT_URL)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    } else {
        WebViewScreen(domain!!, onWebViewCreated)
    }
}

@Composable
fun WebViewScreen(url: String, onWebViewCreated: (WebView) -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var isRefreshing by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { isRefreshing = true; webView?.reload() }
        ) {
            AndroidView(
                factory = {
                    WebView(it).apply {
                        webView = this
                        onWebViewCreated(this)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->

                            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

                            val cookie = CookieManager.getInstance().getCookie(url)

                            val request = DownloadManager.Request(Uri.parse(url)).apply {
                                setMimeType(mimeType)
                                addRequestHeader("User-Agent", userAgent)
                                if (cookie != null) addRequestHeader("Cookie", cookie) // 🔥 THIS IS THE FIX
                                setTitle(fileName)
                                setDescription("Downloading file...")
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                                allowScanningByMediaScanner()
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            }

                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            dm.enqueue(request)

                            Toast.makeText(context, "Downloading file", Toast.LENGTH_SHORT).show()
                        }



                        webChromeClient = object : WebChromeClient() {

                            // 1. Handle Camera & Microphone (WebRTC)
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                // This grants the website permission to use the camera/mic
                                // provided the Android app already has the system permission.
                                activity?.runOnUiThread {
                                    request?.grant(request.resources)
                                }
                            }

                            // 2. Handle Location (Geolocation API)
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                // This grants the website permission to access location
                                callback?.invoke(origin, true, false)
                            }

                            // Keep your existing file chooser logic here
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                activity?.fileCallback?.onReceiveValue(null)
                                activity?.fileCallback = filePathCallback
                                fileChooserParams?.createIntent()?.let {
                                    activity?.fileChooserLauncher?.launch(it)
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {

                            // 🔥 Catch normal URL navigations (printview, download_pdf)
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {

                                val url = request?.url.toString()

                                if (url.contains("/printview") || url.contains("download_pdf")) {

                                    val uri = Uri.parse(url)

                                    val doctype = uri.getQueryParameter("doctype")
                                    val name = uri.getQueryParameter("name") ?: "document"
                                    val format = uri.getQueryParameter("format") ?: "Standard"
                                    val noLetterhead = uri.getQueryParameter("no_letterhead") ?: "0"

                                    val pdfUrl =
                                        if (url.contains("download_pdf")) {
                                            url
                                        } else {
                                            "${uri.scheme}://${uri.host}/api/method/frappe.utils.print_format.download_pdf" +
                                                    "?doctype=$doctype&name=$name&format=$format&no_letterhead=$noLetterhead"
                                        }

                                    downloadPdfWithCookies(view, pdfUrl, "$name.pdf")
                                    return true
                                }

                                return false
                            }

                            // 🔥 Catch redirected PDF (very important for IP sites)
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {

                                val url = request?.url.toString()

                                if (url.endsWith(".pdf", true)) {
                                    downloadPdfWithCookies(view, url, "document.pdf")

                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream("".toByteArray())
                                    )
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isRefreshing = false

                                view?.evaluateJavascript(
                                    """
(function() {
    var style = document.createElement('style');
    style.innerHTML = `
        /* App centering */
        #app {
            display: flex !important;
            justify-content: center !important;
            align-items: center !important;
            height: 100vh !important;
            width: 100vw !important;
            overflow: hidden !important;
        }

        /* 🔥 Frappe Dropdown height fix */
        .awesomplete > ul {
            max-height: 500px !important;
            overflow-y: auto !important;
            font-size: 18px !important;
        }

        .awesomplete > ul > li {
            padding: 5px !important;
        }

        /* Select2 dropdown (Link fields) */
        .select2-results__options {
            max-height: 400px !important;
            font-size: 18px !important;
        }

        .select2-results__option {
            padding: 14px 18px !important;
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
                                handler?.proceed()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                isRefreshing = false
                            }

                            // ✅ Common function to download PDF with session cookie
                            private fun downloadPdfWithCookies(view: WebView?, pdfUrl: String, fileName: String) {

                                Thread {
                                    try {
                                        val cookie = CookieManager.getInstance().getCookie(pdfUrl)

                                        val trustAllCerts = arrayOf<TrustManager>(
                                            object : X509TrustManager {
                                                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                                                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                                            }
                                        )

                                        val sslContext = SSLContext.getInstance("SSL")
                                        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                                        val url = URL(pdfUrl)
                                        val connection = url.openConnection() as HttpsURLConnection
                                        connection.sslSocketFactory = sslContext.socketFactory
                                        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }

                                        connection.setRequestProperty("Cookie", cookie)
                                        connection.connect()

                                        val input = connection.inputStream

                                        // ✅ Save using MediaStore (Android 10+ safe)
                                        val resolver = view!!.context.contentResolver
                                        val contentValues = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                        }

                                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)!!
                                        val outputStream = resolver.openOutputStream(uri)!!

                                        input.copyTo(outputStream)

                                        outputStream.close()
                                        input.close()

                                        // ✅ Toast + Notification
                                        view.post {
                                            val context = view.context

                                            // Toast
                                            Toast.makeText(context, "PDF Downloaded", Toast.LENGTH_SHORT).show()

                                            // Open PDF when notification tapped
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }

                                            val pendingIntent = PendingIntent.getActivity(
                                                context,
                                                0,
                                                intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                            )

                                            val notification = NotificationCompat.Builder(context, "download_channel")
                                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                                .setContentTitle("PDF Downloaded")
                                                .setContentText(fileName)
                                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                .setContentIntent(pendingIntent)
                                                .setAutoCancel(true)
                                                .build()

                                            NotificationManagerCompat.from(context)
                                                .notify(System.currentTimeMillis().toInt(), notification)
                                        }

                                    } catch (e: Exception) {
                                        view?.post {
                                            Toast.makeText(view.context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
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
