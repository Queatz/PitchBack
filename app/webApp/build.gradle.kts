import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        outputModuleName = "saigon"
        browser {
            commonWebpackConfig {
                outputFileName = "saigon.js"
                cssSupport {
                    enabled.set(true)
                }
                // webpack-dev-server only serves the JS bundle unless we also expose
                // processed resources (index.html + styles.css). Without this, GET /
                // returns Express's "Cannot GET /".
                devServer = KotlinWebpackConfig.DevServer(
                    port = 8081,
                    static = mutableListOf(
                        layout.buildDirectory
                            .dir("processedResources/js/main")
                            .get()
                            .asFile
                            .absolutePath,
                    ),
                )
            }
        }
        binaries.executable()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(compose.html.core)
            implementation(compose.runtime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
