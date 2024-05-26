import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.JavadocJar
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
    id("com.strumenta.antlr-kotlin")
}

val generateKotlinGrammarSource by tasks.registering(AntlrKotlinTask::class) {
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(project.file("src/jvmMain/antlr")) {
        include("**/*.g4")
    }

    val package_name: String = "dev.toastbits.kjna.grammar"
    packageName = "dev.toastbits.kjna.grammar"

    arguments = listOf("-visitor")

    val directory: String = "generated-antlr/${package_name.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(directory).get().asFile
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting

        val jvmMain by getting {
            kotlin {
                srcDir(generateKotlinGrammarSource.get().outputDirectory!!)
            }

            dependencies {
                val antlr_kotlin_version: String = extra["antlrKotlin.version"] as String
                implementation("com.strumenta:antlr-kotlin-runtime:$antlr_kotlin_version")
            }
        }
    }
}

tasks.matching { it.name.lowercase().contains("sourcesjar") || it.name.contains("compile") }.all {
    dependsOn(generateKotlinGrammarSource)
}

tasks.withType<KotlinCompile> {
    dependsOn(generateKotlinGrammarSource)
}

mavenPublishing {
    val project_version: String = extra["project.version"] as String
    coordinates("dev.toastbits.kjna", "library", project_version)

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("KJna library")
        description.set("TODO")
        url.set("https:/github.com/toasterofbread/KJna")
        inceptionYear.set("2024")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("toasterofbread")
                name.set("Talo Halton")
                email.set("talohalton@gmail.com")
                url.set("https://github.com/toasterofbread")
            }
        }
        scm {
            connection.set("https://github.com/toasterofbread/KJna.git")
            url.set("https://github.com/toasterofbread/KJna")
        }
        issueManagement {
            system.set("Github")
            url.set("https://github.com/toasterofbread/KJna/issues")
        }
    }
}
