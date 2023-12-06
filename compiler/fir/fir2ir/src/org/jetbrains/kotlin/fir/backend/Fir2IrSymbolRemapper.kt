/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol

class Fir2IrSymbolRemapper {
    private val symbolsMap: MutableMap<IrSymbol, IrSymbol> = mutableMapOf()
    private var initialized = false

    fun remapFunctionSymbol(symbol: IrSimpleFunctionSymbol): IrSimpleFunctionSymbol = remap(symbol)
    fun remapPropertySymbol(symbol: IrPropertySymbol): IrPropertySymbol = remap(symbol)

    private inline fun <reified S : IrSymbol> remap(symbol: S): S {
        return symbolsMap[symbol] as S? ?: symbol
    }

    @RequiresOptIn
    annotation class SymbolRemapperInternals

    @SymbolRemapperInternals
    fun initializeSymbolsMap(map: Map<IrSymbol, IrSymbol>) {
        if (initialized) {
            error("Symbols remapper can be initialized only once")
        }
        initialized = true
        symbolsMap.putAll(map)
    }
}
