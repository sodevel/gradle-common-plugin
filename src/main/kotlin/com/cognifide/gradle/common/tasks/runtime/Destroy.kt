package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Destroy : RuntimeDefaultTask() {

    init {
        description = "Destroys all runtimes."
    }

    companion object {
        const val NAME = "destroy"
    }
}
