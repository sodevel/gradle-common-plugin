package com.cognifide.gradle.common.file.resolver

import com.cognifide.gradle.common.CommonExtension

@ExperimentalStdlibApi
open class FileResolver(common: CommonExtension) : Resolver<FileGroup>(common) {

    override fun createGroup(name: String) = FileGroup(this, name)
}
