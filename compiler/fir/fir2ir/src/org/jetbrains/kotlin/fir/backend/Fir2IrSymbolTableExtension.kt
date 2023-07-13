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
        FirBasedSymbol<*>, FirClassSymbol<*>, FirTypeAliasSymbol, FirScriptSymbol, FirFunctionSymbol<*>,
        FirConstructorSymbol, FirPropertySymbol, FirFieldSymbol, FirEnumEntrySymbol, FirValueParameterSymbol, FirTypeParameterSymbol
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
    override fun calculateEnumEntrySignature(declaration: FirEnumEntrySymbol): IdSignature? {
        shouldNotBeCalled()
    }

    @Deprecated("should not be called", level = DeprecationLevel.HIDDEN)
    override fun calculateFieldSignature(declaration: FirFieldSymbol): IdSignature? {
        shouldNotBeCalled()
    }

    // ------------------------------------ script ------------------------------------

    override fun defaultScriptFactory(startOffset: Int, endOffset: Int, script: FirScriptSymbol, symbol: IrScriptSymbol): IrScript {
        TODO("Not yet implemented")
    }

    // ------------------------------------ class ------------------------------------

    fun declareClass(declaration: FirClassSymbol<*>, signature: IdSignature, classFactory: (IrClassSymbol) -> IrClass): IrClass {
        return declare(
            declaration,
            classSlice,
            SymbolTable::declareClass,
            { createClassSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceClass(declaration: FirClassSymbol<*>, signature: IdSignature): IrClassSymbol {
        return reference(
            declaration,
            classSlice,
            SymbolTable::referenceClassImpl,
            ::createClassSymbol,
            ::createPublicClassSymbol,
            ::createPrivateClassSymbol,
            specificCalculateSignature = { signature }
        )
    }

    // ------------------------------------ constructor ------------------------------------

    fun declareConstructor(declaration: FirConstructorSymbol, signature: IdSignature, classFactory: (IrConstructorSymbol) -> IrConstructor): IrConstructor {
        return declare(
            declaration,
            constructorSlice,
            SymbolTable::declareConstructor,
            { createConstructorSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceConstructor(declaration: FirConstructorSymbol, signature: IdSignature): IrConstructorSymbol {
        return reference(
            declaration,
            constructorSlice,
            SymbolTable::referenceConstructorImpl,
            ::createConstructorSymbol,
            ::createPublicConstructorSymbol,
            ::createPrivateConstructorSymbol,
            specificCalculateSignature = { signature }
        )
    }

    // ------------------------------------ enum entry ------------------------------------

    fun declareEnumEntry(declaration: FirEnumEntrySymbol, signature: IdSignature?, classFactory: (IrEnumEntrySymbol) -> IrEnumEntry): IrEnumEntry {
        return declare(
            declaration,
            enumEntrySlice,
            SymbolTable::declareEnumEntry,
            { createEnumEntrySymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceEnumEntry(declaration: FirEnumEntrySymbol, signature: IdSignature?): IrEnumEntrySymbol {
        return reference(
            declaration,
            enumEntrySlice,
            SymbolTable::referenceEnumEntryImpl,
            ::createEnumEntrySymbol,
            ::createPublicEnumEntrySymbol,
            ::createPrivateEnumEntrySymbol,
            specificCalculateSignature = { signature }
        )
    }

    override fun defaultEnumEntryFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        enumEntry: FirEnumEntrySymbol,
        symbol: IrEnumEntrySymbol,
    ): IrEnumEntry {
        TODO("Not yet implemented")
    }

    // ------------------------------------ field ------------------------------------

    override fun defaultFieldFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirFieldSymbol,
        type: IrType,
        visibility: DescriptorVisibility?,
        symbol: IrFieldSymbol,
    ): IrField {
        TODO("Not yet implemented")
    }

    fun declareField(declaration: FirFieldSymbol, signature: IdSignature?, classFactory: (IrFieldSymbol) -> IrField): IrField {
        return declare(
            declaration,
            fieldSlice,
            SymbolTable::declareField,
            { createFieldSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceField(declaration: FirFieldSymbol, signature: IdSignature?): IrFieldSymbol {
        return reference(
            declaration,
            fieldSlice,
            SymbolTable::referenceFieldImpl,
            ::createFieldSymbol,
            ::createPublicFieldSymbol,
            ::createPrivateFieldSymbol,
            specificCalculateSignature = { signature }
        )
    }

    // ------------------------------------ property ------------------------------------

    fun declareProperty(declaration: FirPropertySymbol, signature: IdSignature?, classFactory: (IrPropertySymbol) -> IrProperty): IrProperty {
        return declare(
            declaration,
            propertySlice,
            SymbolTable::declareProperty,
            { createPropertySymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceProperty(declaration: FirPropertySymbol, signature: IdSignature?): IrPropertySymbol {
        return reference(
            declaration,
            propertySlice,
            SymbolTable::referencePropertyImpl,
            ::createPropertySymbol,
            ::createPublicPropertySymbol,
            ::createPrivatePropertySymbol,
            specificCalculateSignature = { signature }
        )
    }

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

    fun declareTypeAlias(declaration: FirTypeAliasSymbol, signature: IdSignature?, classFactory: (IrTypeAliasSymbol) -> IrTypeAlias): IrTypeAlias {
        return declare(
            declaration,
            typeAliasSlice,
            SymbolTable::declareTypeAlias,
            { createTypeAliasSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceTypeAlias(declaration: FirTypeAliasSymbol, signature: IdSignature?): IrTypeAliasSymbol {
        return reference(
            declaration,
            typeAliasSlice,
            SymbolTable::referenceTypeAliasImpl,
            ::createTypeAliasSymbol,
            ::createPublicTypeAliasSymbol,
            ::createPrivateTypeAliasSymbol,
            specificCalculateSignature = { signature }
        )
    }

    // ------------------------------------ function ------------------------------------

    fun declareFunction(declaration: FirFunctionSymbol<*>, signature: IdSignature?, classFactory: (IrFunctionSymbol) -> IrSimpleFunction): IrSimpleFunction {
        return declare(
            declaration,
            functionSlice,
            SymbolTable::declareSimpleFunction,
            { createFunctionSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceFunction(declaration: FirFunctionSymbol<*>, signature: IdSignature?): IrFunctionSymbol {
        return reference(
            declaration,
            functionSlice,
            SymbolTable::referenceSimpleFunctionImpl,
            ::createFunctionSymbol,
            ::createPublicFunctionSymbol,
            ::createPrivateFunctionSymbol,
            specificCalculateSignature = { signature }
        )
    }

    // ------------------------------------ type parameter ------------------------------------

    fun declareTypeParameter(declaration: FirTypeParameterSymbol, signature: IdSignature?, classFactory: (IrTypeParameterSymbol) -> IrTypeParameter): IrTypeParameter {
        return declare(
            declaration,
            globalTypeParameterSlice,
            SymbolTable::declareGlobalTypeParameter,
            { createTypeParameterSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceTypeParameter(declaration: FirTypeParameterSymbol, signature: IdSignature?): IrTypeParameterSymbol {
        return reference(
            declaration,
            globalTypeParameterSlice,
            SymbolTable::referenceTypeParameterImpl,
            ::createTypeParameterSymbol,
            ::createPublicTypeParameterSymbol,
            ::createPrivateTypeParameterSymbol,
            specificCalculateSignature = { signature }
        )
    }

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
