plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version: einzige Quelle. Für ein Release hier erhöhen und denselben Tag pushen (v0.1.0).
val lumenVersion = "0.1.0"

// Private Werte aus local.properties (nie im Repo).
val localProps = rootProject.file("local.properties").takeIf { it.exists() }?.readLines().orEmpty()
fun localProp(key: String): String? =
    localProps.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "de.tb.lumen"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.tb.lumen"
        minSdk = 28
        targetSdk = 35
        // 0.1.0 → 100, 1.2.3 → 10203: jede neue Version installiert sich über die alte.
        versionCode = lumenVersion.split('.').fold(0) { acc, part -> acc * 100 + part.toInt() }
        versionName = System.getenv("VERSION_SUFFIX")?.let { "$lumenVersion-$it" } ?: lumenVersion
        // Private Standardwerte aus local.properties (nie im Repo). Lassen sich auch zur Laufzeit
        // per ADB setzen, siehe Config.kt:
        //   tmdb.key=…          TMDB-Schlüssel (Folgenbild/-nummer)
        //   calendar.urls=a,b   veröffentlichte Kalender-Links (webcal://…)
        buildConfigField("String", "TMDB_KEY", "\"${localProp("tmdb.key").orEmpty()}\"")
        buildConfigField("String", "CALENDAR_URLS", "\"${localProp("calendar.urls").orEmpty()}\"")
    }

    // Release-Schlüssel für veröffentlichte APKs: in CI aus GitHub-Secrets (Umgebung, siehe
    // .github/workflows/release.yml), lokal aus local.properties:
    //   signing.store=C:/pfad/lumen-release.jks
    //   signing.password=…
    //   signing.alias=lumen
    // Fehlt er, wird mit dem Debug-Key signiert – reicht zum Ausprobieren, aber Updates einer
    // offiziellen Version lassen sich dann nicht darüber installieren.

    val keystore = (System.getenv("LUMEN_KEYSTORE") ?: localProp("signing.store"))?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("LUMEN_KEYSTORE_PASSWORD") ?: localProp("signing.password")
                keyAlias = System.getenv("LUMEN_KEY_ALIAS") ?: localProp("signing.alias")
                keyPassword = System.getenv("LUMEN_KEY_PASSWORD") ?: localProp("signing.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        // Gewollt: beide werden per ADB vergeben bzw. braucht ein Launcher (alle Apps auflisten).
        disable += setOf("ProtectedPermissions", "QueryAllPackagesPermission")
        // Bestehende Altlasten sind eingefroren; CI schlägt nur bei NEUEN Fehlern an.
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        // android.*-Aufrufe in Unit-Tests liefern Standardwerte statt zu werfen.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Installiert die mitgelieferten Baseline Profiles von Compose auch beim Sideloading.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    // Echtes org.json – die Android-Version ist in Unit-Tests nur ein leerer Stub.
    testImplementation("org.json:json:20240303")
}
