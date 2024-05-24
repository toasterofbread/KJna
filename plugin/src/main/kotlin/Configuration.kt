import org.gradle.api.Project
import dev.toastbits.kjna.plugin.*

class KJnaConfiguration(
    private val generate_task: KJnaGenerateTask
) {
    fun generate(configure: KJnaGenerateTask.() -> Unit) {
        configure(generate_task)
    }
}

fun Project.kjna(configure: KJnaConfiguration.() -> Unit) {
    val generate_task: KJnaGenerateTask = tasks.getByName(KJnaGenerateTask.NAME) as KJnaGenerateTask
    configure(KJnaConfiguration(generate_task))
}
