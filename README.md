# KJna

A Gradle plugin for Kotlin Multiplatform projects which generates binding code for common-module access to native libraries. Supports Kotlin/Native using the built-in cinterop system and Kotlin/JVM using [Jextract](https://github.com/openjdk/jextract).

KJna currently supports the following Kotlin platforms:
- JVM
- Linux x86_64
- Linux ARM64
- Windows x86_64

See the [releases](https://github.com/toasterofbread/kjna/releases) page for a list of available versions.

> [!WARNING]
> This library is experimental. Several features such as nested pointers and function pointers with arguments are untested and/or not implemented.

## Setup

### Installation


1. Add the Maven Central repository to your dependency resolution configuration
```
repositories {
    mavenCentral()
}
```

2. Add the KJna Gradle plugin

```
plugins {
    id("dev.toastbits.kjna").version("<version>")
}
```

3. Add the KJna runtime dependency
```
dependencies {
    implementation("dev.toastbits.kjna:runtime:<version>")
}
```

### JDK version

Jextract relies on [Java 22's foreign memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html), so consumers of this library must be compiled using JDK 22 or greater (or JDK 21 with preview features enabled).

**To use JDK 22**

```
kotlin {
    jvmToolchain(22)
}
```

**To use JDK 21 with preview features**

```
tasks.withType<JavaCompile>() {
    options.compilerArgs.add("--enable-preview")
}

kotlin {
    jvmToolchain(21)
}
```

### Build files

Due to a KMP limitation, Java source files generated by Jextract are stored in `/<project>/src/jvmMain/java` (by default). You might want to exclude this directory from source control.

All other generated files are stored in `/<project>/build/kjna`.

Unless specified by the user, Jextract is downloaded automatically and stored in `/<root-project>/build/jextract`.

## Configuration

See the [sample project](/sample/build.gradle.kts) for a working example using mpv.

```
kotlin {
    jvm() // 'withJava()' is called automatically by KJna

    val native_targets: List<KotlinNativeTarget> =
        listOf(
            linuxX64(),
            ...
        )

    kjna {
        generate {
            // Use to override Jextract's library loading method (see sample)
            override_jextract_loader = true

            packages(native_targets) {
                add("kjna.libmpv") {

                    // Disabled packages will have no function implementations
                    // Use the 'isAvailable()' companion method to check at runtime
                    enabled = true

                    addHeader(
                        "mpv/client.h", // Header path
                        "MpvClient" // Generated class name (gen.libmpv.LibMpv in this case)
                    )

                    libraries = listOf("mpv")
                }

                ...
            }
        }
    }
}
```

## Additional credits

- [ANTLR](https://github.com/antlr/antlr4)
- [Kotlin support for ANTLR](https://github.com/Strumenta/antlr-kotlin) by [Strumenta](https://github.com/Strumenta)
- [Gradle download task](https://github.com/michel-kraemer/gradle-download-task) by [Michel Krämer](https://github.com/michel-kraemer)
- [Plexus archiver](https://github.com/codehaus-plexus/plexus-archiver) by [Codehaus](https://github.com/codehaus-plexus)
