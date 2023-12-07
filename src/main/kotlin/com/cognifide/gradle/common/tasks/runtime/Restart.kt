package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Restart : RuntimeDefaultTask() {

    init {
        description = "Turns off then on all runtimes."
    }

    companion object {
        const val NAME = "restart"
    }
}
