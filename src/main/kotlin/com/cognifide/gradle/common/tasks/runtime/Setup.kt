package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Setup : RuntimeDefaultTask() {

    init {
        description = "Sets up all runtimes."
    }

    companion object {
        const val NAME = "setup"
    }
}
