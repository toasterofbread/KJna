import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
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
                
                val json_version: String = extra["kotlinx.serialization.json.version"] as String
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$json_version")
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

    configure(KotlinMultiplatform(
        javadocJar = JavadocJar.Dokka("dokkaHtml"),
        sourcesJar = true
    ))

    pom {
        name.set("KJna Library")
        description.set("Classes for binding file generation used by the KJna Gradle plugin")
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

tasks.withType<DokkaTaskPartial>().configureEach {
    moduleName.set("KJna Library")
}

tasks.withType<AbstractDokkaLeafTask>().configureEach {
    dependsOn("generateKotlinGrammarSource")
}
