plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="com.simulagamer.filedesk"; compileSdk=35
 defaultConfig { applicationId="com.simulagamer.filedesk"; minSdk=26; targetSdk=35; versionCode=15; versionName="1.5.0-beta1" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation(platform("androidx.compose:compose-bom:2025.02.00"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.documentfile:documentfile:1.0.1")
}
