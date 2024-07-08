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

            implementation(libs.androidx.preference)
            implementation(libs.dec)
            implementation(libs.maxmind.db)
        }


        iosMain.dependencies { }

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


        ndk{
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        val VERSION_CODE = 1
        val VERSION_NAME = "1.0.0"
        val APPLICATION_ID = namespace

        buildConfigField("int", "VERSION_CODE", VERSION_CODE.toString())
        buildConfigField("String", "VERSION_NAME", "\"${VERSION_NAME}\"")
        buildConfigField("String", "APPLICATION_ID", "\"${APPLICATION_ID}\"")
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets{
        named("main") {
            // assets for oui.txt -> bssid -> manufacturer
            assets.srcDir("src/androidMain/assets")
            // jnilibs -> Native code C++
            jniLibs.srcDir("src/androidMain/jni")
            // res -> Android resources
            res.srcDir("src/androidMain/res")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures{
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }

        debug {
//            // Application only
//            applicationIdSuffix = ".debug"
//            versionNameSuffix = "-beta"
            isPseudoLocalesEnabled = true

            if (project.hasProperty("doNotStrip")) {
                androidComponents{
                    onVariants(selector().withBuildType("debug")){
                        packaging.jniLibs.keepDebugSymbols.add("**/libpcapd.so")
                        packaging.jniLibs.keepDebugSymbols.add("**/libcapture.so")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.androidx.core)
//    implementation(libs.okhttp3)
}
