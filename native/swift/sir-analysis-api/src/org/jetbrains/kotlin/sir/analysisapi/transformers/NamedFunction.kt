/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.analysisapi.transformers

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.symbolPointerOfType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.sir.SirForeignFunction
import org.jetbrains.kotlin.sir.SirOrigin
import org.jetbrains.kotlin.sir.builder.buildForeignFunction
import java.lang.IllegalStateException

fun KtNamedFunction.toForeignFunction(): SirForeignFunction? {
    val names = fqName?.pathSegments()
        ?: return null
    val pointer: KtSymbolPointer<KtFunctionLikeSymbol> = analyze(this) {
        symbolPointerOfType()
    }
    return buildForeignFunction {
        origin = SirOrigin.KotlinEntity.Function(
            name = { names.toListString() },
            parameters = {
                analyze(this@toForeignFunction) {
                    val function = pointer.restoreSymbol()
                        ?: throw IllegalStateException("could not restore symbol")
                    function.valueParameters.map { it.toSirParam() }
                }
            },
            returnType = {
                analyze(this@toForeignFunction) {
                    val function = pointer.restoreSymbol()
                        ?: throw IllegalStateException("could not restore symbol")
                    SirOrigin.ExternallyDefined(
                        name = function.returnType.toString()
                    )
                }
            },
        )
    }
}

private fun KtValueParameterSymbol.toSirParam(): SirOrigin.KotlinEntity.Parameter = SirOrigin.KotlinEntity.Parameter(
    name = name.toString(),
    type = SirOrigin.ExternallyDefined(name = returnType.toString())
)

private fun List<Name>.toListString() = map { it.asString() }
