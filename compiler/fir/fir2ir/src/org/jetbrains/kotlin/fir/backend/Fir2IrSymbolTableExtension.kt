/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.fir.signaturer.FirBasedSignatureComposer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.shouldNotBeCalled
import org.jetbrains.kotlin.utils.threadLocal

class Fir2IrSymbolTableExtension(table: SymbolTable, val signatureComposer: FirBasedSignatureComposer) : SymbolTableExtension<
        FirBasedSymbol<*>, FirClassSymbol<*>, FirTypeAliasSymbol, FirScriptSymbol, FirFunctionSymbol<*>,
        FirConstructorSymbol, FirPropertySymbol, FirVariableSymbol<*>, FirEnumEntrySymbol, FirValueParameterSymbol, FirTypeParameterSymbol
        >(table) {


    private val valueParameterSlice: SymbolTableSlice.Scoped<FirValueParameterSymbol, IrValueParameter, IrValueParameterSymbol> by threadLocal {
        SymbolTableSlice.Scoped(lock)
    }

    private val variableSlice: SymbolTableSlice.Scoped<FirVariableSymbol<*>, IrVariable, IrVariableSymbol> by threadLocal {
        SymbolTableSlice.Scoped(lock)
    }

    override fun MutableList<SymbolTableSlice.Scoped<*, *, *>>.initializeScopedSlices() {
        add(valueParameterSlice)
        add(variableSlice)
    }

    // ------------------------------------ signature ------------------------------------

    override fun calculateSignature(declaration: FirBasedSymbol<*>): IdSignature? {
        return when (declaration) {
            is FirClassSymbol<*> -> signatureComposer.composeSignature(declaration.fir)
            is FirScriptSymbol -> signatureComposer.composeSignature(declaration.fir)
            is FirConstructorSymbol -> signatureComposer.composeSignature(declaration.fir)
            else -> error("Signature can not be calculated for this declaration: ${declaration::class.simpleName}")
        }
    }

    override fun calculateEnumEntrySignature(declaration: FirEnumEntrySymbol): IdSignature? {
        return signatureComposer.composeSignature(declaration.fir)
    }

    @Deprecated("should not be called", level = DeprecationLevel.HIDDEN)
    override fun calculateFieldSignature(declaration: FirVariableSymbol<*>): IdSignature? {
        shouldNotBeCalled()
    }

    // ------------------------------------ script ------------------------------------

    override fun defaultScriptFactory(startOffset: Int, endOffset: Int, script: FirScriptSymbol, symbol: IrScriptSymbol): IrScript {
        TODO("Not yet implemented")
    }

    // ------------------------------------ class ------------------------------------

    fun declareClass(declaration: FirClassSymbol<*>, signature: IdSignature?, classFactory: (IrClassSymbol) -> IrClass): IrClass {
        return declare(
            declaration,
            classSlice,
            SymbolTable::declareClass,
            { createClassSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    fun declareClassIfNotExists(declaration: FirClassSymbol<*>, signature: IdSignature?, classFactory: (IrClassSymbol) -> IrClass): IrClass {
        return declareIfNotExist(
            declaration,
            classSlice,
            SymbolTable::declareClassIfNotExists,
            { createClassSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceClass(declaration: FirClassSymbol<*>, signature: IdSignature?): IrClassSymbol {
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

    fun declareConstructor(
        declaration: FirConstructorSymbol,
        signature: IdSignature?,
        classFactory: (IrConstructorSymbol) -> IrConstructor,
    ): IrConstructor {
        return declare(
            declaration,
            constructorSlice,
            SymbolTable::declareConstructor,
            { createConstructorSymbol(declaration, it) },
            classFactory,
            specificCalculateSignature = { signature }
        )
    }

    fun declareConstructorIfNotExists(
        declaration: FirConstructorSymbol,
        signature: IdSignature?,
        constructorFactory: (IrConstructorSymbol) -> IrConstructor,
    ): IrConstructor {
        return declareIfNotExist(
            declaration,
            constructorSlice,
            SymbolTable::declareConstructorIfNotExists,
            { createConstructorSymbol(declaration, it) },
            constructorFactory,
            specificCalculateSignature = { signature }
        )
    }

    @OptIn(SymbolTableInternals::class)
    fun referenceConstructor(declaration: FirConstructorSymbol, signature: IdSignature?): IrConstructorSymbol {
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
        declaration: FirVariableSymbol<*>,
        type: IrType,
        visibility: DescriptorVisibility?,
        symbol: IrFieldSymbol,
    ): IrField {
        TODO("Not yet implemented")
    }

    fun declareField(declaration: FirVariableSymbol<*>, signature: IdSignature?, classFactory: (IrFieldSymbol) -> IrField): IrField {
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
    fun referenceField(declaration: FirVariableSymbol<*>, signature: IdSignature?): IrFieldSymbol {
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

    fun declarePropertyIfNotExists(
        declaration: FirPropertySymbol,
        signature: IdSignature?,
        propertyFactory: (IrPropertySymbol) -> IrProperty,
    ): IrProperty {
        return declareIfNotExist(
            declaration,
            propertySlice,
            SymbolTable::declarePropertyIfNotExists,
            { createPropertySymbol(declaration, it) },
            propertyFactory,
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

    @Deprecated("Should not be called", level = DeprecationLevel.HIDDEN)
    override fun referenceProperty(declaration: FirPropertySymbol): IrPropertySymbol {
        shouldNotBeCalled()
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

    fun declareFunction(
        declaration: FirFunctionSymbol<*>,
        signature: IdSignature?,
        functionFactory: (IrSimpleFunctionSymbol) -> IrSimpleFunction,
    ): IrSimpleFunction {
        require(declaration !is FirConstructorSymbol)
        return declare(
            declaration,
            functionSlice,
            SymbolTable::declareSimpleFunction,
            { createFunctionSymbol(declaration, it) },
            functionFactory,
            specificCalculateSignature = { signature }
        )
    }

    fun declareFunctionIfNotExists(
        declaration: FirFunctionSymbol<*>,
        signature: IdSignature?,
        functionFactory: (IrSimpleFunctionSymbol) -> IrSimpleFunction,
    ): IrSimpleFunction {
        return declareIfNotExist(
            declaration,
            functionSlice,
            SymbolTable::declareSimpleFunctionIfNotExists,
            { createFunctionSymbol(declaration, it) },
            functionFactory,
            specificCalculateSignature = { signature }
        )
    }


    @OptIn(SymbolTableInternals::class)
    fun referenceFunction(declaration: FirFunctionSymbol<*>, signature: IdSignature?): IrFunctionSymbol {
        require(declaration !is FirConstructorSymbol)
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

    @Deprecated("Should not be called", level = DeprecationLevel.HIDDEN)
    override fun referenceSimpleFunction(declaration: FirFunctionSymbol<*>): IrSimpleFunctionSymbol {
        shouldNotBeCalled()
    }

    // ------------------------------------ type parameter ------------------------------------

    fun declareGlobalTypeParameter(
        declaration: FirTypeParameterSymbol,
        signature: IdSignature?,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter,
    ): IrTypeParameter {
        return declare(
            declaration,
            globalTypeParameterSlice,
            SymbolTable::declareGlobalTypeParameter,
            { createTypeParameterSymbol(declaration, it) },
            typeParameterFactory,
            specificCalculateSignature = { signature }
        )
    }

    // TODO: add proper forward referencing of general type parameters (without knowing is parameter global or scoped)
    @OptIn(SymbolTableInternals::class)
    fun referenceGlobalTypeParameter(declaration: FirTypeParameterSymbol, signature: IdSignature?): IrTypeParameterSymbol {
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

    fun declareScopedTypeParameter(
        declaration: FirTypeParameterSymbol,
        signature: IdSignature?,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter,
    ): IrTypeParameter {
        return declareScopedTypeParameter(declaration, typeParameterFactory, specificCalculateSignature = { signature })
    }

    override fun referenceTypeParameter(declaration: FirTypeParameterSymbol): IrTypeParameterSymbol {
        return when (val container = declaration.containingDeclarationSymbol) {
            is FirClassLikeSymbol<*> -> {
                val containerSignature = signatureComposer.composeSignature(container.fir)
                val signature = signatureComposer.composeTypeParameterSignature(container.typeParameterSymbols.indexOf(declaration), containerSignature)
                referenceGlobalTypeParameter(declaration, signature)
            }
            else -> referenceScopedTypeParameter(declaration)
        }
    }

    override fun defaultTypeParameterFactory(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirTypeParameterSymbol,
        symbol: IrTypeParameterSymbol,
    ): IrTypeParameter {
        shouldNotBeCalled()
    }

    // ------------------------------------ value parameter ------------------------------------

    // TODO: consider moving to common class
    override fun referenceValueParameter(declaration: FirValueParameterSymbol): IrValueParameterSymbol {
        return valueParameterSlice.referenced(declaration) {
            error("Undefined parameter referenced: $declaration\n${valueParameterSlice.dump()}")
        }
    }

    fun declareValueParameter(
        declaration: FirValueParameterSymbol,
        valueParameterFactory: (IrValueParameterSymbol) -> IrValueParameter,
    ): IrValueParameter {
        return valueParameterSlice.declareLocal(declaration, { IrValueParameterSymbolImpl() }, valueParameterFactory)
    }

    // ------------------------------------ variable ------------------------------------

    fun referenceVariable(declaration: FirVariableSymbol<*>): IrVariableSymbol {
        return variableSlice.referenced(declaration) {
            error("Undefined variable referenced: $declaration\n${variableSlice.dump()}")
        }
    }

    fun declareVariable(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        declaration: FirVariableSymbol<*>,
        name: Name,
        type: IrType,
        isVar: Boolean,
        isConst: Boolean,
        isLateinit: Boolean,
    ): IrVariable {
        return variableSlice.declareLocal(declaration, { IrVariableSymbolImpl() }) { symbol ->
            IrVariableImpl(
                startOffset, endOffset, origin, symbol, name, type,
                isVar, isConst, isLateinit
            )
        }
    }

}
