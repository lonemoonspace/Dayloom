import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
val hasReleaseKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { keystoreProps.getProperty(it)?.isNotBlank() == true } &&
    keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).isFile } == true
// An incomplete keystore.properties must not fail configuration (that would break test/IDE sync too);
// only assembleRelease checks it, see afterEvaluate below.
// keystore.properties 不完整时不能在配置阶段报错（会连带 test 与 IDE 同步失败）；只在 assembleRelease 时检查，见下方 afterEvaluate。

android {
    namespace = "io.github.lonemoonspace.dayloom"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.lonemoonspace.dayloom"
        minSdk = 33
        targetSdk = 36
        // YYYYMMDDNN, fixed per release so builds on the same day or with a wrong clock stay predictable.
        // 按 YYYYMMDDNN 固定，避免同一天多次构建或系统时间错误导致升级/降级不可预测。
        versionCode = 2026100901
        versionName = "0.0.1"
        // The APK is public: never put API keys in it. Keys are entered in the app's settings only.
        // APK 公开可下载：不放任何 API Key，Key 只在 App 设置页填写。
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates locales_config.xml from the values-* folders so system settings can offer per-app language.
        // 按 values-* 目录自动生成 locales_config.xml，系统设置里才能给本 App 单独选语言。
        generateLocaleConfig = true
    }

    lint {
        // versionCode is a YYYYMMDDNN date, far below Integer.MAX_VALUE and Google Play's limit.
        // versionCode 是 YYYYMMDDNN 日期编码，远低于 Integer.MAX_VALUE 与 Google Play 上限。
        disable += "HighAppVersionCode"
        // A string missing in one language must fail the build, not ship half-translated.
        // 任一语言缺字符串都要让构建失败，不能带着半份翻译发布。
        error += listOf("MissingTranslation", "ExtraTranslation")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Unit tests run on the Debug variant only; Release would run the same tests twice for no new information.
// 单元测试只跑 Debug 变体；Release 变体跑的是同一批用例，只是白白多一倍耗时。
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

afterEvaluate {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        doFirst {
            check(hasReleaseKey) {
                "assembleRelease needs a valid keystore.properties; see keystore.properties.example. " +
                    "/ assembleRelease 需要有效的 keystore.properties，参见 keystore.properties.example。"
            }
        }
    }

    // Local gate, identical to CI: unit tests + Android Lint + Debug build. Must be green before every commit.
    // 本地门禁，与 CI 相同：单元测试 + Android Lint + Debug 构建。每次提交前必须全绿。
    tasks.register("verify") {
        group = "verification"
        description = "test + lintDebug + assembleDebug"
        dependsOn("test", "lintDebug", "assembleDebug")
    }

    // Used by the release workflows: gate, signed APK named by version, archived R8 mapping and SHA256SUMS in release/.
    // 发版 workflow 使用：门禁、按版本命名的签名 APK、归档的 R8 mapping，以及 release/ 下的 SHA256SUMS。
    tasks.register("publishVersionedRelease") {
        dependsOn("verify", "assembleRelease")
        doLast {
            val version = android.defaultConfig.versionName ?: error("versionName missing")
            val source = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
            check(source.isFile) { "release APK not found: ${source.absolutePath}" }
            val outputDir = rootProject.file("release").apply { mkdirs() }
            val target = File(outputDir, "Dayloom-v$version.apk")
            source.copyTo(target, overwrite = true)

            // Without the mapping, crash stack traces from R8-shrunk builds cannot be retraced; fail rather than ship without it.
            // 没有 mapping 就无法还原 R8 混淆后的崩溃堆栈；宁可失败，也不发一个没有 mapping 的包。
            val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
            check(mapping.isFile) { "R8 mapping not found: ${mapping.absolutePath} (was minification turned off?)" }
            mapping.copyTo(File(outputDir, "mapping-v$version.txt"), overwrite = true)

            val sums = outputDir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .sortedBy { it.name }
                .joinToString("") { apk ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    "$digest  ${apk.name}\n"
                }
            File(outputDir, "SHA256SUMS").writeText(sums)
            logger.lifecycle("Published $target")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Blur behind the glass top and bottom bars. / 玻璃顶栏与底栏背后的模糊。
    implementation(libs.haze)
    // Drag-to-reorder for home cards. / 首页卡片的拖动排序。
    implementation(libs.reorderable)
    // Lunar dates and solar terms for the calendar module, behind its LunarProvider. / 日历模块的农历与节气，藏在 LunarProvider 后面。
    implementation(libs.lunar)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
