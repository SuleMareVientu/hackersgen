plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.publish)
}

kotlin {
    // Android target (JVM-based, consumed by the Android sceneview module)
    jvm("android")


    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib")
            api(libs.kotlin.math)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
