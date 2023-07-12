/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.utils.addToStdlib.shouldNotBeCalled

class Fir2IrSymbolTableExtension(table: SymbolTable) : SymbolTableExtension<
        FirBasedSymbol<*>, FirClassSymbol<*>, FirTypeAliasSymbol, FirScriptSymbol, FirNamedFunctionSymbol,
        FirConstructorSymbol, FirPropertySymbol, FirValueParameterSymbol, FirTypeParameterSymbol
        >(table) {
    override fun MutableList<SymbolTableSlice.Scoped<*, *, *>>.initializeScopedSlices() {
        TODO("Not yet implemented")
    }

    // ------------------------------------ signature ------------------------------------

    @Deprecated("should not be called", level = DeprecationLevel.HIDDEN)
    override fun calculateSignature(declaration: FirBasedSymbol<*>): IdSignature? {
        shouldNotBeCalled()
    }

    @Deprecated("should not be called", level = DeprecationLevel.HIDDEN)
    override fun calculateEnumEntrySignature(declaration: FirClassSymbol<*>): IdSignature? {
        shouldNotBeCalled()
    }

    @Deprecated("should not be called", level = DeprecationLevel.HIDDEN)
    override fun calculateFieldSignature(declaration: FirPropertySymbol): IdSignature? {
        shouldNotBeCalled()
    }

    // ------------------------------------ script ------------------------------------

    override fun defaultScriptFactory(startOffset: Int, endOffset: Int, script: FirScriptSymbol, symbol: IrScriptSymbol): IrScript {
        TODO("Not yet implemented")
    }

    // ------------------------------------ class ------------------------------------

    // ------------------------------------ constructor ------------------------------------

    // ------------------------------------ enum entry ------------------------------------

    override fun defaultEnumEntryFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        enumEntry: FirClassSymbol<*>,
        symbol: IrEnumEntrySymbol,
    ): IrEnumEntry {
        TODO("Not yet implemented")
    }

    // ------------------------------------ field ------------------------------------

    override fun defaultFieldFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirPropertySymbol,
        type: IrType,
        visibility: DescriptorVisibility?,
        symbol: IrFieldSymbol,
    ): IrField {
        TODO("Not yet implemented")
    }

    // ------------------------------------ property ------------------------------------

    override fun defaultPropertyFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirPropertySymbol,
        isDelegated: Boolean,
        symbol: IrPropertySymbol,
    ): IrProperty {
        TODO("Not yet implemented")
    }

    // ------------------------------------ typealias ------------------------------------

    // ------------------------------------ function ------------------------------------

    // ------------------------------------ type parameter ------------------------------------

    override fun defaultTypeParameterFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirTypeParameterSymbol,
        symbol: IrTypeParameterSymbol,
    ): IrTypeParameter {
        TODO("Not yet implemented")
    }
}
