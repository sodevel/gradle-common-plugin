package com.cognifide.gradle.common

import com.cognifide.gradle.common.build.BuildScope
import com.cognifide.gradle.common.build.ObjectFactory
import com.cognifide.gradle.common.build.Parallel
import com.cognifide.gradle.common.build.ProgressCountdown
import com.cognifide.gradle.common.build.ProgressIndicator
import com.cognifide.gradle.common.build.ProgressLogger
import com.cognifide.gradle.common.build.PropertyParser
import com.cognifide.gradle.common.build.Retry
import com.cognifide.gradle.common.build.ServiceAccessor
import com.cognifide.gradle.common.file.FileWatcher
import com.cognifide.gradle.common.file.resolver.FileResolver
import com.cognifide.gradle.common.file.transfer.FileTransferManager
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.file.transfer.sftp.SftpFileTransfer
import com.cognifide.gradle.common.file.transfer.smb.SmbFileTransfer
import com.cognifide.gradle.common.health.HealthChecker
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.java.JavaSupport
import com.cognifide.gradle.common.mvn.MvnInvoker
import com.cognifide.gradle.common.notifier.NotifierFacade
import com.cognifide.gradle.common.tasks.TaskFacade
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.Patterns
import com.cognifide.gradle.common.utils.capitalizeChar
import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.common.zip.ZipFile
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

@ExperimentalStdlibApi
@Suppress("TooManyFunctions")
open class CommonExtension(val project: Project) {

    val logger = project.logger

    /**
     * Allows to read project property specified in command line and system property as a fallback.
     */
    val prop = PropertyParser(project)

    /**
     * Reduces boilerplate related to lazy configuration API
     */
    val obj = ObjectFactory(project)

    /**
     * Accessor for internal Gradle services.
     */
    val services = ServiceAccessor(project)

    /**
     * Define settings for file transfer facade which allows to perform basic file operations on remote servers
     * like uploading and downloading files.
     *
     * Supports multiple protocols: HTTP, SFTP, SMB and other supported by JVM.
     */
    val fileTransfer = FileTransferManager(this)

    /**
     * Configures file transfer facade.
     */
    fun fileTransfer(options: FileTransferManager.() -> Unit) = fileTransfer.using(options)

    /**
     * Provides API for displaying interactive notification during running build tasks.
     */
    val notifier = NotifierFacade.of(this)

    fun notifier(configurer: NotifierFacade.() -> Unit) = notifier.using(configurer)

    val tasks = TaskFacade(project)

    /**
     * Allows to register tasks with hooks working nicely with task configuration avoidance.
     */
    fun tasks(configurer: TaskFacade.() -> Unit) {
        tasks.apply(configurer)
    }

    /**
     * Configure Java for running AEM instance and compilation.
     */
    val javaSupport by lazy { JavaSupport(this) }

    fun javaSupport(options: JavaSupport.() -> Unit) = javaSupport.using(options)

    /**
     * Show asynchronous 0 indicator with percentage while performing some action.
     */
    fun <T> progress(total: Int, action: ProgressIndicator.() -> T): T = progress(total.toLong(), action)

    /**
     * Show asynchronous progress indicator with percentage while performing some action.
     */
    fun <T> progress(total: Long, action: ProgressIndicator.() -> T): T {
        return ProgressIndicator(project).apply { this.total = total }.launch(action)
    }

    fun <T> progress(action: ProgressIndicator.() -> T) = progressIndicator(action)

    /**
     * Show asynchronous progress indicator while performing some action.
     */
    fun <T> progressIndicator(action: ProgressIndicator.() -> T): T = ProgressIndicator(project).launch(action)

    /**
     * Show synchronous progress logger while performing some action.
     */
    fun <T> progressLogger(action: ProgressLogger.() -> T): T = ProgressLogger.of(project).launch(action)

    /**
     * Grab user input interactively.
     */
    val userInput by lazy { ServiceAccessor(project).get<UserInputHandler>() }

    /**
     * Wait some time after performing asynchronous operation.
     */
    fun waitFor(time: Long) = progressCountdown(time)

    /**
     * Show synchronous progress countdown / time to wait after performing asynchronous operation.
     */
    fun progressCountdown(time: Long) = progressCountdown { this.time = time }

    /**
     * Show synchronous progress countdown / time to wait after performing asynchronous operation.
     */
    fun progressCountdown(options: ProgressCountdown.() -> Unit) = ProgressCountdown(project).apply(options).run()

    /**
     * Determine temporary directory for particular service (any name).
     */
    fun temporaryFile(path: String): File = temporaryDir.resolve(path)

    /**
     * Get or compute MD5 checksum of file interactively.
     */
    fun checksumFile(file: File, recalculate: Boolean = false): String = progress {
        step = "Calculating checksum"
        message = "File '${file.name}'"
        Formats.checksum(file, recalculate)
    }

    /**
     * Predefined temporary directory.
     */
    val temporaryDir: File get() = project.buildDir.resolve(TEMPORARY_DIR)

    /**
     * Get recent file from directory
     */
    fun recentFile(dirPath: String, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS): File = recentFile(project.file(dirPath), filePatterns)

    /**
     * Get recent file from directory
     */
    fun recentFile(dir: File, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS): File = recentFileProvider(dir, filePatterns).orNull
        ?: throw CommonException("No recent files available in directory '$dir' matching file pattern(s): $filePatterns!")

    /**
     * Get recent file from directory
     */
    fun recentFileProvider(dirPath: String, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS) = recentFileProvider(project.file(dirPath), filePatterns)

    /**
     * Get recent file from directory
     */
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun recentFileProvider(dir: File, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS): Provider<File> = project.fileTree(dir)
        .matching { it.include(filePatterns) }.elements
        .map { files -> files.map { it.asFile } }
        .map { files -> files.maxByOrNull { it.lastModified() } }

    /**
     * Get recent files built in directories as file collection.
     */
    fun recentFiles(vararg dirs: File, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS): ConfigurableFileCollection {
        return recentFiles(dirs.asIterable(), filePatterns)
    }

    /**
     * Get recent files built in directories as file collection.
     */
    fun recentFiles(dirs: Iterable<File>, filePatterns: Iterable<String> = RECENT_FILE_PATTERNS): ConfigurableFileCollection {
        return providedFiles(dirs.map { recentFileProvider(it, filePatterns) })
    }

    /**
     * Returns file collection with providers returning any value (avoids exception).
     */
    fun providedFiles(vararg providers: Provider<File>) = providedFiles(providers.asIterable())

    /**
     * Returns file collection with providers returning any value (avoids exception).
     */
    fun providedFiles(providers: Iterable<Provider<File>>) = obj.files { from(obj.provider { providers.mapNotNull { it.orNull } }) }

    /**
     * Factory method for configuration object determining how operation should be retried.
     */
    fun retry(options: Retry.() -> Unit = {}) = Retry(this).apply(options)

    /**
     * React on file changes under configured directories.
     */
    fun watchFiles(options: FileWatcher.() -> Unit) {
        FileWatcher(this).apply(options).start()
    }

    /**
     * Resolve single file from defined repositories or by using defined file transfers.
     */
    fun resolveFile(value: Any) = resolveFile { get(value) }

    /**
     * Resolve single file from defined repositories or by using defined file transfers.
     */
    fun resolveFile(options: FileResolver.() -> Unit) = resolveFiles(options).firstOrNull()
        ?: throw CommonException("File not resolved!")

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(vararg values: Any): List<File> = resolveFiles(values.asIterable())

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(values: Iterable<Any>): List<File> = resolveFiles { values.forEach { get(it) } }

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(options: FileResolver.() -> Unit): List<File> = FileResolver(this).apply(options).files

    /**
     * Perform any HTTP requests to external endpoints.
     */
    fun <T> http(consumer: HttpClient.() -> T) = HttpClient(this).run(consumer)

    /**
     * Download files using HTTP protocol using custom settings.
     */
    fun <T> httpFile(consumer: HttpFileTransfer.() -> T) = fileTransfer.factory.http(consumer)

    /**
     * Transfer files using over SFTP protocol using custom settings.
     */
    fun <T> sftpFile(consumer: SftpFileTransfer.() -> T) = fileTransfer.factory.sftp(consumer)

    /**
     * Transfer files using over SMB protocol using custom settings.
     */
    fun <T> smbFile(consumer: SmbFileTransfer.() -> T) = fileTransfer.factory.smb(consumer)

    /**
     * Utility to work with ZIP files (even big ones)
     */
    fun zip(file: File) = ZipFile(file)

    /**
     * Utility to work with ZIP files (even big ones)
     */
    fun zip(path: String) = zip(project.file(path))

    fun healthCheck(options: HealthChecker.() -> Unit) = HealthChecker(this).apply(options).start()

    /**
     * Invoke Maven process.
     */
    fun mvn(options: MvnInvoker.() -> Unit) = MvnInvoker(this).apply(options).invoke()

    // Utilities (to use without imports)

    val parallel = Parallel

    val formats = Formats

    val patterns = Patterns

    val buildScope = BuildScope.of(project)

    @ExperimentalStdlibApi
    companion object {

        const val NAME = "common"

        const val TEMPORARY_DIR = "tmp"

        val RECENT_FILE_PATTERNS = listOf("*.jar", "*.zip", "*.war", "*.ear")

        private val PLUGIN_IDS = listOf(CommonPlugin.ID)

        fun of(project: Project): CommonExtension {
            return project.extensions.findByType(CommonExtension::class.java)
                ?: throw CommonException("${project.displayName.capitalizeChar()} must have at least one of following plugins applied: $PLUGIN_IDS")
        }
    }
}

@ExperimentalStdlibApi
val Project.common get() = CommonExtension.of(project)

@Synchronized
fun Project.pluginProject(id: String): Project? = when {
    plugins.hasPlugin(id) -> this
    else -> rootProject.allprojects.firstOrNull { it.plugins.hasPlugin(id) }
}

@Synchronized
fun Project.pluginProjects(id: String): List<Project> = rootProject.allprojects.filter { it.plugins.hasPlugin(id) }

val Project.pathPrefix get() = if (project.rootProject == project) ":" else "${project.path}:"

fun <T : Task> Project.whenEvaluated(task: TaskProvider<T>, configurer: T.() -> Unit) {
    afterEvaluate {
        task.configure { configurer(it) }
    }
}

fun <T : Task> Project.whenEvaluatedAll(task: TaskProvider<T>, configurer: T.() -> Unit) {
    gradle.projectsEvaluated {
        task.configure { configurer(it) }
    }
}

fun <T : Task> Project.whenGraphReady(task: TaskProvider<T>, configurer: T.(TaskExecutionGraph) -> Unit) {
    gradle.taskGraph.whenReady { graph ->
        task.configure { task ->
            if (graph.hasTask(task)) {
                configurer(task, graph)
            }
        }
    }
}

@ExperimentalStdlibApi
fun <T : Task> Project.checkForce(task: TaskProvider<T>) = whenGraphReady(task) {
    if (!common.prop.force) {
        throw CommonException("Unable to run unsafe task '$path' without param '-P${PropertyParser.FORCE_PROP}'!")
    }
}
