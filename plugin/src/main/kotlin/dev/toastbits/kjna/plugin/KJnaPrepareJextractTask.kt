package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import java.io.File
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.Files.getPosixFilePermissions
import java.nio.file.Files.setPosixFilePermissions
import de.undercouch.gradle.tasks.download.DownloadExtension

open class KJnaPrepareJextractTask: DefaultTask() {
    @InputFile
    @Optional
    var jextract_binary: File? = null

    @Input
    @Optional
    var jextract_archive_url: String = getPlatformJextractUrl()

    @Input
    @Optional
    var jextract_archive_exe_path: String = getPlatformJextractArchiveExePath()

    @OutputDirectory
    @Optional
    var jextract_archive_extract_directory: File = project.rootProject.layout.buildDirectory.file("jextract").get().asFile

    @OutputFile
    lateinit var final_jextract_binary: File

    init {
        project.afterEvaluate {
            final_jextract_binary = getFinalJextractBinaryFile()
        }
    }

    @TaskAction
    fun prepareJextract() {
        final_jextract_binary = getJextractBinary()
    }

    @Internal
    fun getFinalJextractBinaryFile(): File = getSpecifiedOrSystemJextractBinary() ?: jextract_archive_extract_directory.resolve(jextract_archive_exe_path)

    private fun getSpecifiedOrSystemJextractBinary(): File? {
        jextract_binary?.also {
            return it
        }

        // TODO | Get system binary
        return null
    }

    private fun getJextractBinary(): File {
        getSpecifiedOrSystemJextractBinary()?.also { file ->
            if (!file.isFile()) {
                throw GradleException("No Jextract binary exists at '$file'")
            }

            return file
        }

        val binary: File = jextract_archive_extract_directory.resolve(jextract_archive_exe_path)
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
        const val NAME: String = "kjnaPrepareJextract"
    }
}

private fun getPlatformJextractUrl(): String {
    val arch: String = System.getProperty("os.arch").lowercase()
    val os: OperatingSystem = OperatingSystem.current()
    
    if (arch == "x86_64" || arch == "amd64") {
        if (os.isLinux()) {
            return "https://download.java.net/java/early_access/jextract/22/4/openjdk-22-jextract+4-30_linux-x64_bin.tar.gz"
        }
        else if (os.isWindows()) {
            return "https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_windows-x64_bin.tar.gz"
        }
        else if (os.isMacOsX()) {
            return "https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_macos-x64_bin.tar.gz"
        }
    }
    else if (arch == "arm64" || arch == "aarch64") {
        if (os.isMacOsX()) {
            return "https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_macos-aarch64_bin.tar.gz"
        }
    }

    throw RuntimeException("OS/arch combination not supported by Jextract ($os - $arch)")
}

private fun getPlatformJextractArchiveExePath(): String {
    val os: OperatingSystem = OperatingSystem.current()
    if (os.isLinux()) {
        return "jextract-22/jextract"
    }
    else if (os.isWindows()) {
        return "jextract-22/bin/jextract.bat"
    }
    else if (os.isMacOsX()) {
        TODO()
    }

    throw RuntimeException("OS not supported by Jextract ($os)")
}
