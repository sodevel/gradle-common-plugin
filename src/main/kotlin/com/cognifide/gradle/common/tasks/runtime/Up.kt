package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Up : RuntimeDefaultTask() {

    init {
        description = "Turns on all runtimes."
    }

    companion object {
        const val NAME = "up"
    }
}
