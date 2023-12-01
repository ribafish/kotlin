/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.analysisapi.transformers

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.sir.SirForeignFunction
import org.jetbrains.kotlin.sir.SirOrigin
import org.jetbrains.kotlin.sir.builder.buildForeignFunction

internal fun KtNamedFunction.toForeignFunction(): SirForeignFunction? {
    val names = fqName?.pathSegments()
        ?: return null
    return buildForeignFunction {
        origin = SirOrigin.KotlinEntity.Function(
            fqName = { names.toListString() },
            parameters = {
                analyze(this@toForeignFunction) {
                    val function = this@toForeignFunction.getFunctionLikeSymbol()
                    function.valueParameters.map { it.toSirParam() }
                }
            },
            returnType = {
                analyze(this@toForeignFunction) {
                    val function = this@toForeignFunction.getFunctionLikeSymbol()
                    SirOrigin.KotlinEntity.KotlinType(name = function.returnType.toString())
                }
            },
        )
    }
}

private fun KtValueParameterSymbol.toSirParam(): SirOrigin.KotlinEntity.Parameter = SirOrigin.KotlinEntity.Parameter(
    name = name.toString(),
    type = SirOrigin.KotlinEntity.KotlinType(name = returnType.toString())
)

private fun List<Name>.toListString() = map { it.asString() }
