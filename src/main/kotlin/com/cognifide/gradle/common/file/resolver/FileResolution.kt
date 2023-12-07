package com.cognifide.gradle.common.file.resolver

import org.gradle.api.file.FileTree
import java.io.File

@ExperimentalStdlibApi
open class FileResolution(val group: FileGroup, val id: String, private val resolver: (FileResolution) -> File) {

    private val common = group.resolver.common

    val dir get() = group.resolver.downloadDir.get().asFile.resolve(id)

    val file: File by lazy { thenOperations.fold(resolver(this)) { f, o -> o(f) } }

    private var thenOperations = mutableListOf<FileResolution.(File) -> File>()

    /**
     * Resolve file immediatelly.
     */
    fun resolve() = file

    /**
     * Perform operation on resolved file, but do not change it path (work in-place).
     */
    fun use(operation: FileResolution.(File) -> Unit) {
        then { operation(it); it }
    }

    /**
     * Perform operation on resolved file with ability to change it path.
     */
    fun then(operation: FileResolution.(File) -> File) {
        thenOperations.add(operation)
    }

    // DSL for 'then' and 'use' methods

    /**
     * Copy source file to target only if it does not exist.
     */
    fun copyFile(source: File, target: File) {
        if (!target.exists()) {
            common.logger.info("Copying resolved file '$source' to file '$target'")

            source.copyTo(target)
        }
    }

    /**
     * Copy source file to target directory only if target file does not exist.
     */
    fun copyToDirectory(source: File, targetDir: File) {
        val targetFile = File(targetDir, source.name)
        if (!targetFile.exists()) {
            source.copyTo(targetFile)
        }
    }

    /**
     * Read files from ZIP/TAR archive.
     */
    fun archiveTree(archive: File): FileTree = when (archive.extension) {
        "zip" -> common.project.zipTree(archive)
        else -> common.project.tarTree(archive)
    }

    /**
     * Read single file from ZIP/TAR archive.
     */
    fun archiveFile(archive: File, entryPattern: String): File = archiveTree(archive)
        .matching { it.include(entryPattern) }.singleFile

    /**
     * Read files from ZIP/TAR archive.
     */
    fun archiveFiles(archive: File, entriesPattern: String): Sequence<File> = archiveTree(archive)
        .matching { it.include(entriesPattern) }.asSequence()

    /**
     * Extract & copy single archive file and copy it to target location only if it does not exist.
     */
    fun copyArchiveFile(archive: File, entryPattern: String, target: File) = target.apply {
        if (!exists()) {
            val archiveFile = archiveFile(archive, entryPattern)
            common.logger.info("Copying resolved archive file '$archiveFile' to '$this'")
            archiveFile.copyTo(this)
        }
    }

    /**
     * Extract & copy archive files and copy them to target directory only if it each file does not exist.
     */
    fun copyArchiveFiles(archive: File, entriesPattern: String, targetDir: File) {
        archiveFiles(archive, entriesPattern).forEach { archiveFile ->
            File(targetDir, archiveFile.name).apply {
                if (!exists()) {
                    common.logger.info("Copying resolved archive file '$archiveFile' to '$this'")
                    archiveFile.copyTo(this)
                }
            }
        }
    }
}
