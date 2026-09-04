import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
//    alias(libs.plugins.androidx.room)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sqldelight)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    
    // Declared only on a mac, mirroring the gate lightning-kmp-app's :library applies to its own ios
    // targets. Down the composite chain secp256k1-kmp declares a libsecp256k1 cinterop, which makes
    // gradle switch off klib cross compilation for apple targets, so on a linux/windows host nothing
    // in the build tree offers an ios variant of fr.acinq.phoenix:lightning-kmp-app. Declaring these
    // targets anyway leaves every ios compilation unable to resolve it and fails the build outright.
    // Building an ios binary needs a mac regardless.
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }
    
//    jvm()
    
    sourceSets {
        androidMain.dependencies {
            // The android secp256k1 natives. Nothing pulls these in transitively: lightning-kmp-core
            // publishes no android variant, so the android target resolves it to the jvm one, which
            // asks for secp256k1-kmp-jni-jvm and gets desktop .so/.dylib/.dll files that cannot load
            // on a device. The app has to name the android artifact itself.
            //
            // Resolved from source via the composite build, not from Maven Central -- bitcoin-kmp's
            // settings.gradle.kts substitutes this coordinate for secp256k1-kmp's :jni:android. That
            // matters: the stock artifact has no ChillDKG module, so ChillDKG would compile and then
            // fail with UnsatisfiedLinkError. The version is never resolved; substitution matches on
            // group:name.
            implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.24.0")

            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime.ktx)
//            implementation(libs.androidx.room.sqlite.wrapper)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.okhttp.coroutines)

            implementation(libs.sqldelight.android.driver)
        }
        commonMain.dependencies {
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)

//            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation (libs.compose.material.icons.core)
            implementation (libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)


            implementation(libs.kermit)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.cbor)


            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)



//            implementation(libs.lightning.kmp.core)

            // resolved via the lightning-kmp-app composite build (see settings.gradle.kts)
            implementation("fr.acinq.phoenix:lightning-kmp-app:1.0.0")

            implementation(libs.navigation.compose)

            implementation(libs.okio)

            implementation(libs.qrose)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            implementation(libs.vitorpamplona.quartz)

            implementation("com.ionspin.kotlin:bignum:0.3.10")

            implementation("no.synth:kmp-zip:0.8.0")
            implementation("no.synth:kmp-zip-okio:0.8.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        // Only exists when the ios targets above were declared; the default hierarchy template
        // creates this source set from them.
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            iosMain.dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }
    }
}

android {
    namespace = "press.mantra.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "press.mantra.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
//    ksp(libs.androidx.room3.compiler)
    add("kspAndroid", libs.androidx.room3.compiler)
    // These configurations only exist when the ios targets are declared, which the kotlin block
    // above does only on a mac.
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
//        add("kspIosX64", libs.androidx.room.compiler)
        add("kspIosArm64", libs.androidx.room3.compiler)
    }
//    add("kspJvm", libs.androidx.room3.compiler)
    // Add any other platform target you use in your project, for example kspDesktop
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

sqldelight {
    databases {
        create("ChannelsDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/channelsdb")
        }
        create("PaymentsDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/paymentsdb")
        }
        create("AppDatabase") {
            packageName.set("fr.acinq.phoenix.db.sqldelight")
            srcDirs.from("src/commonMain/sqldelight/appdb")
        }
    }
}

compose.desktop {
    application {
        mainClass = "press.mantra.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "press.mantra.desktop"
            packageVersion = "1.0.0"
        }
    }
}
