/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import kotlin.reflect.KClass

abstract class FirFunctionCallRefinementExtension(session: FirSession) : FirExtension(session) {
    companion object {
        val NAME = FirExtensionPointName("FunctionCallRefinementExtension")
    }

    final override val name: FirExtensionPointName
        get() = NAME


    final override val extensionType: KClass<out FirExtension> = FirFunctionCallRefinementExtension::class

    /**
     * @return null if plugin is not interested in a [symbol]
     */
    abstract fun intercept(callInfo: CallInfo, symbol: FirBasedSymbol<*>): FirBasedSymbol<*>?

    abstract fun transform(call: FirFunctionCall, originalSymbol: FirBasedSymbol<*>): FirFunctionCall

    fun interface Factory : FirExtension.Factory<FirFunctionCallRefinementExtension>
}

val FirExtensionService.callRefinementExtensions: List<FirFunctionCallRefinementExtension> by FirExtensionService.registeredExtensions()

internal class OriginalCallData(val originalSymbol: FirBasedSymbol<*>, val extension: FirFunctionCallRefinementExtension)

internal object OriginalCallDataKey : FirDeclarationDataKey()

internal var FirDeclaration.originalCallData: OriginalCallData? by FirDeclarationDataRegistry.data(OriginalCallDataKey)