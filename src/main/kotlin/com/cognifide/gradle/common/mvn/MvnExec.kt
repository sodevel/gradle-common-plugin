package com.cognifide.gradle.common.mvn

import com.cognifide.gradle.common.CommonDefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

@ExperimentalStdlibApi
open class MvnExec : CommonDefaultTask() {

    @Internal
    val invoker = MvnInvoker(common)

    fun invoker(options: MvnInvoker.() -> Unit) {
        invoker.apply(options)
    }

    @TaskAction
    fun exec() {
        invoker.invoke()
    }
}
