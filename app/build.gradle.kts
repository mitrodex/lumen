plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.tb.lumen"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.tb.lumen"
        minSdk = 28
        targetSdk = 35
        // CI setzt beides aus dem Git-Tag (v1.2.3) bzw. der Build-Nummer.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1-dev"
        // Private Standardwerte aus local.properties (nie im Repo). Lassen sich auch zur Laufzeit
        // per ADB setzen, siehe Config.kt:
        //   tmdb.key=…          TMDB-Schlüssel (Folgenbild/-nummer)
        //   calendar.urls=a,b   veröffentlichte Kalender-Links (webcal://…)
        val local = rootProject.file("local.properties").takeIf { it.exists() }?.readLines().orEmpty()
        fun prop(key: String) = local.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim().orEmpty()
        buildConfigField("String", "TMDB_KEY", "\"${prop("tmdb.key")}\"")
        buildConfigField("String", "CALENDAR_URLS", "\"${prop("calendar.urls")}\"")
    }

    // Release-Schlüssel für veröffentlichte APKs (GitHub-Secrets, siehe .github/workflows/release.yml).
    // Fehlt er, wird mit dem Debug-Key signiert – reicht zum Sideloaden auf den eigenen Stick.
    val keystore = System.getenv("LUMEN_KEYSTORE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("LUMEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUMEN_KEY_ALIAS")
                keyPassword = System.getenv("LUMEN_KEY_PASSWORD")
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
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Installiert die mitgelieferten Baseline Profiles von Compose auch beim Sideloading.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    // Echtes org.json – die Android-Version ist in Unit-Tests nur ein leerer Stub.
    testImplementation("org.json:json:20240303")
}
