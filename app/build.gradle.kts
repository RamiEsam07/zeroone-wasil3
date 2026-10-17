
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

// Read local configuration without placing credentials directly in source code.
val supabaseLocalProperties = Properties().apply {
  val localPropertiesFile = rootProject.file("local.properties")

  if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { input ->
      load(input)
    }
  }
}

val supabaseUrl =
  supabaseLocalProperties.getProperty("SUPABASE_URL", "").trim()

val supabasePublishableKey =
  supabaseLocalProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "").trim()

// Release keystore path; release signing still requires a real keystore and passwords.
val keystorePath = supabaseLocalProperties.getProperty(
  "KEYSTORE_PATH",
  "upload-keystore.jks"
).trim()

android {
  namespace = "com.example"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.aistudio.wasildelivery.kpqmzx"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    buildConfigField(
      "String",
      "SUPABASE_URL",
      "\"$supabaseUrl\""
    )

    buildConfigField(
      "String",
      "SUPABASE_PUBLISHABLE_KEY",
      "\"$supabasePublishableKey\""
    )
  }

  signingConfigs {
    create("release") {
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Android and Jetpack Compose
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.coil.compose)
  // Lifecycle and navigation
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Local database
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  // Networking and serialization
  implementation(libs.converter.moshi)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Google Credential Manager dependencies are defined in the version catalog.
  // Enable them when we implement Google sign-in.
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)

  // Optional dependencies, currently not enabled:
  // implementation(libs.accompanist.permissions)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  // implementation(libs.androidx.datastore.preferences)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.play.services.location)

  // Unit tests
  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  // Instrumented tests
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  // Debug tools
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
