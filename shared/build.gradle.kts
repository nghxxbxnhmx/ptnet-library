plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)

}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.gson)
            implementation(libs.jnbPing)
//            implementation(libs.compose.ui.tooling)
//            implementation(libs.compose.ui.tooling.preview)

        }
        androidMain.dependencies {
            implementation(libs.gson)
            implementation(libs.okhttp3)
            implementation(libs.compose.ui)
        }
        iosMain.dependencies {  }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

}

android {
    namespace = "com.ftel.ptnetlibrary"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.gson)
//    implementation(libs.okhttp3)
}
