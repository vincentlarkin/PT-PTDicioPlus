import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Bundles data/build/dicio.sqlite (built by `python3 -I tools/build_dictionary.py`) into generated assets,
 * plus dicio.version, a stamp the on-device installer compares to decide whether to reinstall.
 */
abstract class BundleDictionary : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val database: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val src = database.get().asFile
        val dir = outputDir.get().asFile.resolve("dictionary").apply { mkdirs() }
        src.copyTo(dir.resolve("dicio.sqlite"), overwrite = true)
        dir.resolve("dicio.version").writeText("${src.length()}-${src.lastModified()}\n${src.length()}\n")
    }
}
