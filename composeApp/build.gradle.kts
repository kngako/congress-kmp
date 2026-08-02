import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

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
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
//    jvm()
    
    sourceSets {
        androidMain.dependencies {
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



            implementation(libs.lightning.kmp.app)
            implementation(libs.lightning.kmp.core)

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
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
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

// JitPack rewrites the version inside a published .module file, and in doing so drops the classifier
// from every artifact name -- so lightning-kmp-app's apple targets advertise their host-specific
// metadata as `library-<target>-<ver>.jar` when it is actually served as `...-<ver>-metadata.jar`.
// The klib and the aar are unaffected (android compiles either way); this only bites the metadata
// compilations, which is what every shared ios source set resolves through. Restore the classifier.
abstract class RestoreJitPackClassifier @Inject constructor(
    private val variant: String,
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val id = context.details.id
        context.details.withVariant(variant) {
            withFiles {
                removeAllFiles()
                addFile("${id.name}-${id.version}-metadata.jar")
            }
        }
    }
}

dependencies {
    components {
        mapOf(
            "library-iosarm64" to "iosArm64MetadataElements-published",
            "library-iossimulatorarm64" to "iosSimulatorArm64MetadataElements-published",
        ).forEach { (module, variant) ->
            withModule<RestoreJitPackClassifier>("com.github.kngako.lightning-kmp-app:$module") {
                params(variant)
            }
        }
    }

    debugImplementation(libs.compose.uiTooling)
//    ksp(libs.androidx.room3.compiler)
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
//    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
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
