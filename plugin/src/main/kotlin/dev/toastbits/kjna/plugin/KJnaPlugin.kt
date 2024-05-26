package dev.toastbits.kjna.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin

class KJnaPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply {
            apply(DownloadTaskPlugin::class.java)
        }

        project.tasks.create(KJnaGenerateTask.NAME, KJnaGenerateTask::class.java).apply {
            group = "KJna"
        }
    }
}
