/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class Fir2IrClassifierGenerator(private val components: Fir2IrComponents) : Fir2IrComponents by components {
    fun createIrClass(regularClass: FirRegularClass, parent: IrDeclarationParent): IrClass {
        val visibility = regularClass.visibility
        val modality = when (regularClass.classKind) {
            ClassKind.ENUM_CLASS -> regularClass.enumClassModality()
            ClassKind.ANNOTATION_CLASS -> Modality.OPEN
            else -> regularClass.modality ?: Modality.FINAL
        }
        val signature = signatureComposer.composeSignature(regularClass)
        val irClass = regularClass.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareClass(regularClass.symbol, signature) { symbol ->
                irFactory.createClass(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = regularClass.computeIrOrigin(),
                    name = regularClass.name,
                    visibility = components.visibilityConverter.convertToDescriptorVisibility(visibility),
                    symbol = symbol,
                    kind = regularClass.classKind,
                    modality = modality,
                    isExternal = regularClass.isExternal,
                    isCompanion = regularClass.isCompanion,
                    isInner = regularClass.isInner,
                    isData = regularClass.isData,
                    isValue = regularClass.isInline,
                    isExpect = regularClass.isExpect,
                    isFun = regularClass.isFun
                ).apply {
                    metadata = FirMetadataSource.Class(regularClass)
                    superTypes = regularClass.superTypeRefs.map { it.toIrType() }
                }
            }
        }
        irClass.parent = parent
        processTypeParameters(regularClass, irClass)
        setThisReceiver(irClass, regularClass.typeParameters)
        return irClass
    }

    private fun setThisReceiver(irClass: IrClass, typeParameters: List<FirTypeParameterRef>) {
        symbolTable.enterScope(irClass)
        val typeArguments = typeParameters.map {
            val irTypeParameterSymbol = symbolTable.referenceTypeParameter(it.symbol)
            IrSimpleTypeImpl(irTypeParameterSymbol, false, emptyList(), emptyList())
        }
        irClass.thisReceiver = irClass.declareThisReceiverParameter(
            thisType = IrSimpleTypeImpl(irClass.symbol, false, typeArguments, emptyList()),
            thisOrigin = IrDeclarationOrigin.INSTANCE_RECEIVER
        )
        symbolTable.leaveScope(irClass)
    }

    fun createIrAnonymousObject(
        anonymousObject: FirAnonymousObject,
        visibility: Visibility = Visibilities.Local,
        name: Name = SpecialNames.NO_NAME_PROVIDED,
        irParent: IrDeclarationParent? = null
    ): IrClass {
        val irAnonymousObject = anonymousObject.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareClass(anonymousObject.symbol, signature = null) {
                irFactory.createClass(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = IrDeclarationOrigin.DEFINED,
                    name = name,
                    visibility = components.visibilityConverter.convertToDescriptorVisibility(visibility),
                    symbol = IrClassSymbolImpl(),
                    kind = anonymousObject.classKind,
                    modality = Modality.FINAL,
                ).apply {
                    metadata = FirMetadataSource.Class(anonymousObject)
                    superTypes = anonymousObject.superTypeRefs.map { it.toIrType() }
                }

            }
        }
        if (irParent != null) {
            irAnonymousObject.parent = irParent
        }
        return irAnonymousObject
    }

    fun createTypeAlias(typeAlias: FirTypeAlias, parent: IrDeclarationParent): IrTypeAlias {
        val signature = signatureComposer.composeSignature(typeAlias)
        return typeAlias.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareTypeAlias(typeAlias.symbol, signature) { symbol ->
                val irTypeAlias = irFactory.createTypeAlias(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = IrDeclarationOrigin.DEFINED,
                    name = typeAlias.name,
                    visibility = components.visibilityConverter.convertToDescriptorVisibility(typeAlias.visibility),
                    symbol = symbol,
                    isActual = typeAlias.isActual,
                    expandedType = typeAlias.expandedTypeRef.toIrType(),
                ).apply {
                    this.parent = parent
                    // TODO: where we should handle type parameters?
                    //   Here or in Fir2IrDeclarationsConverter?
                    // setTypeParameters(typeAlias)
                }
                irTypeAlias
            }
        }
    }

    fun createIrTypeParameter(
        typeParameter: FirTypeParameter,
        index: Int,
        parentSymbol: IrSymbol,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT
    ): IrTypeParameter {
        require(index >= 0)
        val origin = typeParameter.computeIrOrigin()
        val irTypeParameter = typeParameter.convertWithOffsets { startOffset, endOffset ->
            val signature = signatureComposer.composeTypeParameterSignature(
                index, parentSymbol.signature
            )
            val typeParameterFactory = { symbol: IrTypeParameterSymbol ->
                irFactory.createTypeParameter(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = typeParameter.name,
                    symbol = symbol,
                    variance = typeParameter.variance,
                    index = index,
                    isReified = typeParameter.isReified,
                )
            }
            symbolTable.declareGlobalTypeParameter(typeParameter.symbol, signature, typeParameterFactory)
        }
        irTypeParameter.superTypes = typeParameter.bounds.map { it.toIrType(typeOrigin) }
        irTypeParameter.parent = parentSymbol.owner as IrDeclarationParent
        return irTypeParameter
    }

    internal fun processTypeParameters(
        firOwner: FirTypeParameterRefsOwner,
        irOwner: IrTypeParametersContainer,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT,
    ) {
        irOwner.typeParameters = firOwner.typeParameters.mapIndexedNotNull { index, typeParameter ->
            if (typeParameter !is FirTypeParameter) return@mapIndexedNotNull null
            createIrTypeParameter(typeParameter, index, irOwner.symbol, typeOrigin)
        }
    }

    fun createIrEnumEntry(
        enumEntry: FirEnumEntry,
        irParent: IrClass,
        predefinedOrigin: IrDeclarationOrigin? = null,
    ): IrEnumEntry {
        return enumEntry.convertWithOffsets { startOffset, endOffset ->
            val signature = signatureComposer.composeSignature(enumEntry)
            symbolTable.declareEnumEntry(enumEntry.symbol, signature) { symbol ->
                val origin = enumEntry.computeIrOrigin(predefinedOrigin)
                irFactory.createEnumEntry(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = enumEntry.name,
                    symbol = symbol,
                ).apply {
                    parent = irParent
                }
            }
        }
    }

    fun createIrAnonymousInitializer(
        anonymousInitializer: FirAnonymousInitializer,
        irParent: IrClass,
    ): IrAnonymousInitializer = convertCatching(anonymousInitializer) {
        return anonymousInitializer.convertWithOffsets { startOffset, endOffset ->
            irFactory.createAnonymousInitializer(
                startOffset, endOffset, IrDeclarationOrigin.DEFINED,
                IrAnonymousInitializerSymbolImpl()
            ).apply {
                parent = irParent
            }
        }
    }

    // -------------------------------------------- Utilities --------------------------------------------

    // TODO: those functions for enums looks suspicious
    private fun FirRegularClass.enumClassModality(): Modality {
        return when {
            declarations.any { it is FirCallableDeclaration && it.modality == Modality.ABSTRACT } -> {
                Modality.ABSTRACT
            }
            declarations.none { it is FirEnumEntry && isEnumEntryWhichRequiresSubclass(it) } -> {
                Modality.FINAL
            }
            hasAbstractMembersInScope() -> {
                Modality.ABSTRACT
            }
            else -> {
                Modality.OPEN
            }
        }
    }

    private fun isEnumEntryWhichRequiresSubclass(enumEntry: FirEnumEntry): Boolean {
        val initializer = enumEntry.initializer
        return initializer is FirAnonymousObjectExpression && initializer.anonymousObject.declarations.any { it !is FirConstructor }
    }

    private fun FirRegularClass.hasAbstractMembersInScope(): Boolean {
        val scope = unsubstitutedScope(session, scopeSession, withForcedTypeCalculator = false, memberRequiredPhase = null)
        val names = scope.getCallableNames()
        var hasAbstract = false
        for (name in names) {
            scope.processFunctionsByName(name) {
                if (it.isAbstract) {
                    hasAbstract = true
                }
            }
            if (hasAbstract) return true
            scope.processPropertiesByName(name) {
                if (it.isAbstract) {
                    hasAbstract = true
                }
            }
            if (hasAbstract) return true
        }
        return false
    }
}
