import java.io.FileInputStream
import java.util.Properties

// AGP 9 부터 코틀린 지원이 내장이라 org.jetbrains.kotlin.android 를 붙이지 않는다.
plugins {
    alias(libs.plugins.android.application)
}

// 웹뷰가 띄울 주소. 도메인이 바뀌어도 코드를 고치지 않는다.
// ./gradlew assembleRelease -Pgommit.webUrl=https://다른도메인
val webUrl: String = (findProperty("gommit.webUrl") as String?) ?: "https://go-mmit.site"

// 릴리즈 서명. app/keystore.properties 는 커밋되지 않는다.
// 파일이 없으면 서명 설정 없이 진행한다 - 팀원이 클론해도 assembleDebug 는 그대로 돈다.
val keystoreProperties: Properties? =
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { keystoreFile ->
        Properties().also { props -> FileInputStream(keystoreFile).use(props::load) }
    }

android {
    namespace = "com.gommit.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gommit.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "WEB_URL", "\"$webUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 2.1MB -> 0.3MB. 실기기 확인 후 켰다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
}
