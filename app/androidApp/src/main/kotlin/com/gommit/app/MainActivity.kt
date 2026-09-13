package com.gommit.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // onPermissionRequest 와 런타임 권한 결과 사이에 요청을 들고 있는 자리
    private var pendingCameraRequest: PermissionRequest? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingCameraRequest ?: return@registerForActivityResult
            pendingCameraRequest = null
            if (granted) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                request.deny()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).also { setContentView(it) }
        configureWebView()
        applySystemBarInsets()

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        val redirect = oauthRedirectOf(intent)
        when {
            savedInstanceState != null -> webView.restoreState(savedInstanceState)
            redirect != null -> loadOAuthCallback(redirect)
            else -> webView.loadUrl(BuildConfig.WEB_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthRedirectOf(intent)?.let(::loadOAuthCallback)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // -----------------------------------------------------------------------
    // 웹뷰
    // -----------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.settings.apply {
            javaScriptEnabled = true

            // localStorage 가 여기 달려 있다. 끄면 RT 가 저장되지 않아 앱을 껐다 켤 때마다 로그아웃된다
            domStorageEnabled = true

            // 인증 촬영의 카메라 미리보기가 사용자 제스처 없이 시작된다
            mediaPlaybackRequiresUserGesture = false

            userAgentString = "$userAgentString $APP_USER_AGENT_SUFFIX"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                if (url.host == webHost) return false
                openOutside(url)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    request.deny()
                    return
                }
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    return
                }
                pendingCameraRequest = request
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // targetSdk 35 부터 시스템 바 뒤로 그려진다. 웹뷰를 상태바와 내비게이션 바 안쪽으로 민다
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // -----------------------------------------------------------------------
    // 우리 도메인 밖
    // -----------------------------------------------------------------------

    // 구글이 임베디드 웹뷰의 OAuth 요청을 차단한다. 밖으로 나가는 주소는 전부 시스템 브라우저로 뺀다
    private fun openOutside(url: Uri) {
        val intent = if (url.scheme == "http" || url.scheme == "https") {
            CustomTabsIntent.Builder().build().intent.setData(url)
        } else {
            Intent(Intent.ACTION_VIEW, url)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 열 수 있는 앱이 없으면 아무것도 하지 않는다
        }
    }

    // -----------------------------------------------------------------------
    // 소셜 로그인 복귀
    //
    // Custom Tab 에서 돌아온 gommit://oauth/callback/{provider}?code=...&state=app:...
    // 을 웹뷰의 https 콜백 경로로 그대로 옮긴다. state 는 접두어까지 손대지 않는다
    // -----------------------------------------------------------------------

    private fun oauthRedirectOf(intent: Intent?): Uri? {
        val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
        if (data.scheme != CALLBACK_SCHEME || data.host != CALLBACK_HOST) return null
        return data
    }

    private fun loadOAuthCallback(redirect: Uri) {
        val segments = redirect.pathSegments
        if (segments.size != 2 || segments[0] != CALLBACK_PATH) return

        val provider = segments[1]
        if (provider !in OAUTH_PROVIDERS) return

        val query = redirect.encodedQuery.orEmpty()
        val target = buildString {
            append(BuildConfig.WEB_URL)
            append("/oauth/")
            append(provider)
            append("/callback")
            if (query.isNotEmpty()) {
                append('?')
                append(query)
            }
        }
        webView.loadUrl(target)
    }

    private val webHost: String? by lazy { BuildConfig.WEB_URL.toUri().host }

    companion object {
        private const val APP_USER_AGENT_SUFFIX = "Gommit-App/1.0"

        private const val CALLBACK_SCHEME = "gommit"
        private const val CALLBACK_HOST = "oauth"
        private const val CALLBACK_PATH = "callback"

        private val OAUTH_PROVIDERS = setOf("google", "naver")
    }
}
