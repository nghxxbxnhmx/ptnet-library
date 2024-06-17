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

//    listOf(
//        iosX64(),
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach {
//        it.binaries.framework {
//            baseName = "shared"
//            isStatic = true
//        }
//    }


    iosX64() {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.gson)
            implementation(libs.jnbPing)
        }
        androidMain.dependencies {
            implementation(libs.gson)
            implementation(libs.okhttp3)
            implementation(libs.compose.ui)
            implementation(libs.dnsJava)
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
    sourceSets{
        named("main") {
            assets.srcDir("src/androidMain/assets")
        }
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.androidx.core)
//    implementation(libs.okhttp3)
}
