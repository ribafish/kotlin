/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlinx.jso.compiler.backend

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbolInternals
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlinx.jso.compiler.resolve.JsSimpleObjectPluginKey

private object MoveExternalInlineFunctionsWithBodiesOutside : BodyLoweringPass {
    private val EXPECTED_ORIGIN = IrDeclarationOrigin.GeneratedByPlugin(JsSimpleObjectPluginKey)

    @OptIn(IrSymbolInternals::class)
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        val file = container.file
        val parent = container.parent as? IrClass

        if (parent != null && container is IrSimpleFunction && container.origin == EXPECTED_ORIGIN) {
            container.dispatchReceiverParameter = null
            container.isExternal = false
            container.parent = file
            parent.declarations.remove(container)
            file.declarations.add(container)
        }

        irBody.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol.owner.origin == EXPECTED_ORIGIN) {
                    expression.dispatchReceiver = null
                }
                return super.visitCall(expression)
            }
        })
    }
}

open class JsObjectLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        MoveExternalInlineFunctionsWithBodiesOutside.lower(moduleFragment)
    }
}
