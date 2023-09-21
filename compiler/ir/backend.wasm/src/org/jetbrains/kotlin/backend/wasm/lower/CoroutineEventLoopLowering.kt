/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.isExported
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irComposite
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.WasmTarget

// This pass needed to call coroutines event loop run after exported functions calls
// @JsExport
// fun someExportedMethod() {
//     println("hello world")
// }
//
// converts into
//
// @JsExport
// fun someExportedMethod() {
//     try {
//         println("hello world")
//     } finally {
//         processCoroutineEvents()
//     }
// }

internal class CoroutineEventLoopLowering(val context: WasmBackendContext) : FileLoweringPass {
    private val processCoroutineEvents get() = context.wasmSymbols.processCoroutineEvents
    private val isWasi = context.configuration.get(JSConfigurationKeys.WASM_TARGET, WasmTarget.JS) == WasmTarget.WASI

    private fun processExportFunction(irFunction: IrFunction) {
        val body = irFunction.body ?: return
        if (body is IrBlockBody && body.statements.isEmpty()) return

        val bodyType = when (body) {
            is IrExpressionBody -> body.expression.type
            is IrBlockBody -> context.irBuiltIns.unitType
            else -> TODO(this::class.qualifiedName!!)
        }

        with(context.createIrBuilder(irFunction.symbol)) {
            val tryBody = irComposite {
                when (body) {
                    is IrBlockBody -> body.statements.forEach { +it }
                    is IrExpressionBody -> +body.expression
                    else -> TODO(this::class.qualifiedName!!)
                }
            }

            val tryWrap = irTry(
                type = bodyType,
                tryResult = tryBody,
                catches = emptyList(),
                finallyExpression = irCall(processCoroutineEvents)
            )

            when (body) {
                is IrExpressionBody -> body.expression = tryWrap
                is IrBlockBody -> with(body.statements) {
                    clear()
                    add(tryWrap)
                }
                else -> TODO(this::class.qualifiedName!!)
            }
        }
    }

    override fun lower(irFile: IrFile) {
        if (!isWasi) return
        for (declaration in irFile.declarations) {
            if (declaration is IrFunction && declaration.isExported()) {
                processExportFunction(declaration)
            }
        }
    }
}