/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class DataFrameLikeReturnTypeInjector(session: FirSession) : FirExpressionResolutionExtension(session) {
    companion object {
        val DF_CLASS_ID: ClassId = ClassId.topLevel(FqName.fromSegments(listOf("DataFrame")))
    }

    override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
        val callReturnType = functionCall.coneTypeSafe<ConeClassLikeType>() ?: return emptyList()
        if (callReturnType.classId != DF_CLASS_ID) return emptyList()
        val rootMarker = callReturnType.typeArguments[0]
        if (rootMarker !is ConeClassLikeType) {
            return emptyList()
        }
        val symbol = rootMarker.toRegularClassSymbol(session) ?: return emptyList()
        return symbol.declarationSymbols
            .filterIsInstance<FirPropertySymbol>()
            .filter { it.resolvedReturnType.classId?.shortClassName?.asString()?.startsWith("Scope") ?: false }
            .map { it.resolvedReturnType }
    }
}