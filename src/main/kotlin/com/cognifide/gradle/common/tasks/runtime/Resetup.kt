package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Resetup : RuntimeDefaultTask() {

    init {
        description = "Destroys then sets up all runtimes."
    }

    companion object {
        const val NAME = "resetup"
    }
}
