plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":plugin"))
}

kotlin {
    sourceSets {
        val main by getting {
            dependencies {
                implementation(project(":library"))

                val ktor_version: String = extra["ktor.version"] as String
                implementation("io.ktor:ktor-client-core:$ktor_version")
            }
        }
    }
}
