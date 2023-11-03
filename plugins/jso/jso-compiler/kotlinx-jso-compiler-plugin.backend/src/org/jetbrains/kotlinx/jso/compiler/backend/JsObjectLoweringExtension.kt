/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlinx.jso.compiler.backend

import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectAnnotations
import org.jetbrains.kotlinx.jso.compiler.resolve.JsSimpleObjectPluginKey
import org.jetbrains.kotlinx.jso.compiler.resolve.StandardIds

private class MoveExternalInlineFunctionsWithBodiesOutside(private val context: IrPluginContext) : DeclarationTransformer {
    private val jsFunction = context.referenceFunctions(StandardIds.JS_FUNCTION_ID).single()
    private val EXPECTED_ORIGIN = IrDeclarationOrigin.GeneratedByPlugin(JsSimpleObjectPluginKey)

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        val file = declaration.file
        val parent = declaration.parentClassOrNull

        if (parent == null || declaration !is IrSimpleFunction || declaration.origin != EXPECTED_ORIGIN) return null

        declaration.isExternal = false
        declaration.parent = file
        file.declarations.add(declaration)
        declaration.body = context.irFactory.createBlockBody(declaration.startOffset, declaration.endOffset).apply {
            statements += IrReturnImpl(
                declaration.startOffset,
                declaration.endOffset,
                declaration.returnType,
                declaration.symbol,
                IrCallImpl(
                    declaration.startOffset,
                    declaration.endOffset,
                    declaration.returnType,
                    jsFunction,
                    0,
                    1,
                ).apply {
                    val jsObject = buildString {
                        append('{')
                        declaration.valueParameters.forEachIndexed { i, it ->
                            append(it.name.identifier)
                            append(':')
                            append(it.name.identifier)
                            if (i != declaration.valueParameters.lastIndex) append(',')
                        }
                        append('}')
                    }
                    putValueArgument(0, jsObject.toIrConst(context.irBuiltIns.stringType))
                }
            )
        }

        return emptyList()
    }
}

open class JsObjectLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        MoveExternalInlineFunctionsWithBodiesOutside(pluginContext).lower(moduleFragment)
    }
}
