package com.cognifide.gradle.common.tasks

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.CommonTask
import com.cognifide.gradle.common.build.ProgressIndicator
import com.cognifide.gradle.common.utils.Formats
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

@ExperimentalStdlibApi
open class JarTask : Jar(), CommonTask {

    @Internal
    final override val common = CommonExtension.of(project)

    @Internal
    var copyProgress: ProgressIndicator.() -> Unit = {
        update("Creating JAR file: ${archiveFileName.get()} (${Formats.fileSize(archiveFile.get().asFile)})")
    }

    init {
        isZip64 = true
    }

    @TaskAction
    override fun copy() {
        common.progressIndicator {
            updater(copyProgress)
            super.copy()
            logger.info("JAR file created: ${archiveFile.get().asFile}")
        }
    }
}
