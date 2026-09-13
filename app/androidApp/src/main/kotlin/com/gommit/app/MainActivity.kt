package com.gommit.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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

        // 웹뷰를 그릇에 담는다. 웹뷰 자체에 패딩을 주면 뷰 패딩은 걸리는데 웹 뷰포트가
        // 안 줄어서 콘텐츠가 상태바 뒤로 그대로 그려진다. 그릇이 줄어야 웹뷰도 줄어든다
        webView = WebView(this)
        val root = FrameLayout(this).apply { addView(webView) }
        setContentView(root)

        configureWebView()
        applySystemBarInsets(root)

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

            // 원격 URL 만 로드한다. file:// 과 content:// 를 쓸 일이 없다.
            // allowFileAccess 는 targetSdk 30 부터 이미 false 지만 명시해 둔다.
            allowFileAccess = false
            allowContentAccess = false

            userAgentString = "$userAgentString $APP_USER_AGENT_SUFFIX"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                if (originOf(url) == webOrigin) return false
                openOutside(url)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // 요청한 프레임이 우리 origin 이 아니면 거절한다.
                // 이게 없으면 iframe 이나 리다이렉트로 들어온 남의 페이지가 카메라를 가져간다.
                if (originOf(request.origin) != webOrigin) {
                    request.deny()
                    return
                }
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
                // 앞선 요청이 아직 권한 대화상자를 기다리고 있으면 그것부터 닫는다.
                // 덮어쓰기만 하면 그 요청은 grant 도 deny 도 못 받고 페이지가 영영 기다린다
                pendingCameraRequest?.deny()
                pendingCameraRequest = request
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // targetSdk 35 부터 시스템 바 뒤로 그려진다. 웹뷰를 상태바와 내비게이션 바 안쪽으로 민다.
    // 상단은 숨통을 조금 더 준다 - 이유는 dimens.xml
    private fun applySystemBarInsets(root: View) {
        val breathingRoom = resources.getDimensionPixelSize(R.dimen.web_top_breathing_room)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            view.updatePadding(bars.left, bars.top + breathingRoom, bars.right, bars.bottom)
            insets
        }
    }

    // -----------------------------------------------------------------------
    // 우리 도메인 밖
    // -----------------------------------------------------------------------

    // 구글이 임베디드 웹뷰의 OAuth 요청을 차단한다. 밖으로 나가는 주소는 전부 시스템 브라우저로 뺀다
    private fun openOutside(url: Uri) {
        if (url.scheme == "http" || url.scheme == "https") {
            try {
                CustomTabsIntent.Builder().build().launchUrl(this, url)
                return
            } catch (_: ActivityNotFoundException) {
                // Custom Tabs 를 지원하는 브라우저가 없다. 아래 ACTION_VIEW 로 내려간다
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
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

    private val webOrigin: String? by lazy { originOf(BuildConfig.WEB_URL.toUri()) }

    // scheme://host[:port] 만 남긴다. 경로나 끝의 / 가 붙어 와도 같은 값이 나온다
    private fun originOf(uri: Uri?): String? {
        val scheme = uri?.scheme ?: return null
        val authority = uri.authority ?: return null
        return "$scheme://$authority"
    }

    companion object {
        private const val APP_USER_AGENT_SUFFIX = "Gommit-App/1.0"

        private const val CALLBACK_SCHEME = "gommit"
        private const val CALLBACK_HOST = "oauth"
        private const val CALLBACK_PATH = "callback"

        private val OAUTH_PROVIDERS = setOf("google", "naver")
    }
}
