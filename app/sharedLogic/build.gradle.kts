plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    js {
        outputModuleName = "sharedLogic"
        browser()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
