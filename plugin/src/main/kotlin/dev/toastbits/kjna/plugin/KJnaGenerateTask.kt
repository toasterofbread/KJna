package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import de.undercouch.gradle.tasks.download.DownloadExtension
import org.gradle.internal.os.OperatingSystem
import java.nio.file.Files.getPosixFilePermissions
import java.nio.file.Files.setPosixFilePermissions
import java.nio.file.attribute.PosixFilePermission
import java.io.File
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import dev.toastbits.kjna.c.CHeaderParser

open class KJnaGenerateTask: DefaultTask() {
    @OutputDirectory
    @Optional
    var output_directory: File? = null

    @Input
    @Optional
    var packages: List<KJnaGeneratePackageConfiguration> = emptyList()

    @Input
    @Optional
    var include_dirs: List<String> =
        listOf(
            "/usr/include/",
            "/usr/local/include/",
            "/usr/include/linux/"
        )

    @InputFile
    @Optional
    var jextract_binary: File? = null

    @Input
    @Optional
    var jextract_archive_url: String = "https://download.java.net/java/early_access/jextract/22/4/openjdk-22-jextract+4-30_linux-x64_bin.tar.gz"

    @Input
    @Optional
    var jextract_archive_dirname: String = "jextract-22"

    @OutputDirectory
    @Optional
    var jextract_archive_extract_directory: File = project.layout.buildDirectory.file(".jextract").get().getAsFile()

    init {
        description = "TODO"
    }

    @TaskAction
    fun generate() {
        var jextract: File = getJextractBinary()
        println("Jextract loaded: $jextract")

        // executeJextractCommand(jextract, listOf("--help"))
    }

    private fun executeJextractCommand(binary: File, args: List<String>) {
        val process: Process = Runtime.getRuntime().exec((listOf(binary.absolutePath) + args).toTypedArray())

        val result: Int = process.waitFor()
        if (result != 0) {
            throw GradleException("Jextract failed ($result).\nExecutable: $binary\nArgs: $args")
        }
    }

    private fun getJextractBinary(): File {
        jextract_binary?.also { file ->
            if (!file.isFile()) {
                throw GradleException("No Jextract binary exists at '$file'")
            }

            return file
        }

        val binary: File = jextract_archive_extract_directory.resolve(jextract_archive_dirname).resolve("jextract")
        if (binary.isFile()) {
            return binary
        }

        val download_dest: File = jextract_archive_extract_directory.resolve("jextract.tar.gz")

        project.extensions.getByType(DownloadExtension::class.java).run { download ->
            download.src(jextract_archive_url)
            download.dest(download_dest)
            download.onlyIfModified(true)
        }

        val unarchiver: TarGZipUnArchiver = TarGZipUnArchiver()
        unarchiver.sourceFile = download_dest
        unarchiver.destDirectory = jextract_archive_extract_directory
        unarchiver.extract()

        download_dest.delete()

        if (!binary.isFile()) {
            throw GradleException("Jextract successfully downloaded and extracted, but no binary found at '$binary'")
        }

        if (OperatingSystem.current().isUnix()) {
            val permissions: MutableSet<PosixFilePermission> = getPosixFilePermissions(binary.toPath())
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
            setPosixFilePermissions(binary.toPath(), permissions)
        }

        return binary
    }

    companion object {
        const val NAME: String = "generateKJnaBindings"
    }
}

// afterEvaluate {
//     for (task in listOf("compileKotlinJvm", "jvmSourcesJar")) {
//         tasks.getByName(task) {
//             dependsOn(tasks.jextract)
//         }
//     }
// }

