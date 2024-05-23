import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
    id("com.strumenta.antlr-kotlin")
}

val generateKotlinGrammarSource by tasks.registering(AntlrKotlinTask::class) {
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(project.file("src/jvmMain/antlr")) {
        include("**/*.g4")
    }

    val package_name: String = "dev.toastbits.kje.grammar"
    packageName = "dev.toastbits.kje.grammar"

    arguments = listOf("-visitor")

    val directory: String = "generated-antlr/${package_name.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(directory).get().asFile
}

kotlin {
    jvm()

    linuxX64()
    linuxArm64()
    mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
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

tasks.withType<KotlinCompile> {
    dependsOn(generateKotlinGrammarSource)
}

// mavenPublishing {
//     coordinates("dev.toastbits", "kje", "0.0.1")

//     publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
//     signAllPublications()

//     configure(KotlinMultiplatform(
//         javadocJar = JavadocJar.Dokka("dokkaHtml"),
//         sourcesJar = true
//     ))

//     pom {
//         name.set("kjna")
//         description.set("")
//         url.set("https:/github.com/toasterofbread/kje")
//         inceptionYear.set("2024")

//         licenses {
//             license {
//                 name.set("Apache-2.0")
//                 url.set("https://www.apache.org/licenses/LICENSE-2.0")
//             }
//         }
//         developers {
//             developer {
//                 id.set("toasterofbread")
//                 name.set("Talo Halton")
//                 email.set("talohalton@gmail.com")
//                 url.set("https://github.com/toasterofbread")
//             }
//         }
//         scm {
//             connection.set("https://github.com/toasterofbread/kmp-template.git")
//             url.set("https://github.com/toasterofbread/kmp-template")
//         }
//         issueManagement {
//             system.set("Github")
//             url.set("https://github.com/toasterofbread/kmp-template/issues")
//         }
//     }
// }
