package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Down : RuntimeDefaultTask() {

    init {
        description = "Turns off all runtimes."
    }

    companion object {
        const val NAME = "down"
    }
}
