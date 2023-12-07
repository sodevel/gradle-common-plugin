package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

@ExperimentalStdlibApi
open class Await : RuntimeDefaultTask() {

    init {
        description = "Await for healthy condition of all runtimes."
    }

    companion object {
        const val NAME = "await"
    }
}
