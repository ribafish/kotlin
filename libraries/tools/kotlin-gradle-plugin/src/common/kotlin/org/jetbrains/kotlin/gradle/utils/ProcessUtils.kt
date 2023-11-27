/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

import org.gradle.api.logging.Logger
import kotlin.concurrent.thread

internal fun runCommand(
    command: List<String>,
    logger: Logger? = null,
    fallback: ((retCode: Int, output: String) -> String?)? = null,
    errorHandler: ((retCode: Int, output: String, process: Process) -> String?)? = null,
    processConfiguration: ProcessBuilder.() -> Unit = { }
): String {
    val process = ProcessBuilder(command).apply {
        this.processConfiguration()
    }.start()

    var inputText = ""
    var errorText = ""

    val inputThread = thread {
        inputText = process.inputStream.use {
            it.reader().readText()
        }
    }

    val errorThread = thread {
        errorText = process.errorStream.use {
            it.reader().readText()
        }
    }

    inputThread.join()
    errorThread.join()

    val retCode = process.waitFor()
    logger?.info(
        """
            |Information about "${command.joinToString(" ")}" call:
            |
            |${inputText}
        """.trimMargin()
    )

    if (retCode != 0 && fallback != null) {
        val fallbackText = fallback(retCode, inputText.ifBlank { errorText })
        if (fallbackText != null) {
            return fallbackText
        }
    }

    check(retCode == 0) {
        errorHandler?.invoke(retCode, inputText.ifBlank { errorText }, process)
            ?: """
                |Executing of '${command.joinToString(" ")}' failed with code $retCode and message: 
                |
                |$inputText
                |
                |$errorText
                |
                """.trimMargin()
    }

    return inputText
}
