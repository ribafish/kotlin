/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.descriptors.FirBuiltInsPackageFragment
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.descriptors.FirPackageFragmentDescriptor
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import java.util.concurrent.ConcurrentHashMap

class Fir2IrClassifierGenerator(
    private val components: Fir2IrComponents,
    private val moduleDescriptor: FirModuleDescriptor,
) : Fir2IrComponents by components {
    // -------------------------------------------- Builtins --------------------------------------------

    private val fragmentCache: ConcurrentHashMap<FqName, ExternalPackageFragments> = ConcurrentHashMap()
    private val builtInsFragmentCache: ConcurrentHashMap<FqName, IrExternalPackageFragment> = ConcurrentHashMap()

    private class ExternalPackageFragments(
        val fragmentForDependencies: IrExternalPackageFragment,
        val fragmentForPrecompiledBinaries: IrExternalPackageFragment,
    )

    // TODO: probably this method should be somehow restricted to use only from builtins
    //   Maybe it's worth to move it into separate component along with fragments cache
    fun findDependencyClassByClassId(classId: ClassId): IrClassSymbol? {
        val firSymbol = session.symbolProvider.getClassLikeSymbolByClassId(classId) ?: return null
        require(firSymbol.origin == FirDeclarationOrigin.Library || firSymbol.origin == FirDeclarationOrigin.BuiltIns)

        val firClassSymbol = firSymbol as? FirRegularClassSymbol ?: return null
        val signature = signatureComposer.composeSignature(firClassSymbol.fir)
        val irClass = symbolTable.declareClassIfNotExists(firClassSymbol, signature) { symbol ->
            val firClass = firClassSymbol.fir
            Fir2IrLazyClass(
                components,
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                firClass.irOrigin(session.firProvider),
                firClass,
                symbol
            ).apply {
                prepareTypeParameters()
                parent = when (val outerClassId = classId.outerClassId) {
                    null -> getIrExternalPackageFragment(classId.packageFqName)
                    else -> findDependencyClassByClassId(outerClassId)!!.owner
                }
            }
        }

        return irClass.symbol
    }

    private fun getIrExternalOrBuiltInsPackageFragment(fqName: FqName, firOrigin: FirDeclarationOrigin): IrExternalPackageFragment {
        val isBuiltIn = fqName in StandardNames.BUILT_INS_PACKAGE_FQ_NAMES
        return if (isBuiltIn) getIrBuiltInsPackageFragment(fqName) else getIrExternalPackageFragment(fqName, firOrigin)
    }

    private fun getIrBuiltInsPackageFragment(fqName: FqName): IrExternalPackageFragment {
        return builtInsFragmentCache.getOrPut(fqName) {
            createExternalPackageFragment(FirBuiltInsPackageFragment(fqName, moduleDescriptor))
        }
    }

    fun getIrExternalPackageFragment(
        fqName: FqName,
        firOrigin: FirDeclarationOrigin = FirDeclarationOrigin.Library,
    ): IrExternalPackageFragment {
        val fragments = fragmentCache.getOrPut(fqName) {
            ExternalPackageFragments(
                fragmentForDependencies = createExternalPackageFragment(fqName, FirModuleDescriptor(session, moduleDescriptor.builtIns)),
                fragmentForPrecompiledBinaries = createExternalPackageFragment(fqName, moduleDescriptor)
            )
        }
        // Make sure that external package fragments have a different module descriptor. The module descriptors are compared
        // to determine if objects need regeneration because they are from different modules.
        // But keep original module descriptor for the fragments coming from parts compiled on the previous incremental step
        return when (firOrigin) {
            FirDeclarationOrigin.Precompiled -> fragments.fragmentForPrecompiledBinaries
            else -> fragments.fragmentForDependencies
        }
    }

    private fun createExternalPackageFragment(fqName: FqName, moduleDescriptor: FirModuleDescriptor): IrExternalPackageFragment {
        return createExternalPackageFragment(FirPackageFragmentDescriptor(fqName, moduleDescriptor))
    }

    private fun createExternalPackageFragment(packageFragmentDescriptor: PackageFragmentDescriptor): IrExternalPackageFragment {
        val symbol = IrExternalPackageFragmentSymbolImpl(packageFragmentDescriptor)
        return IrExternalPackageFragmentImpl(symbol, packageFragmentDescriptor.fqName)
    }

    // -------------------------------------------- Main --------------------------------------------

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
                }
            }
        }
        irClass.parent = parent
        return irClass
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
            when (parentSymbol) {
                is IrClassifierSymbol -> symbolTable.declareGlobalTypeParameter(typeParameter.symbol, signature, typeParameterFactory)
                else -> symbolTable.declareScopedTypeParameter(typeParameter.symbol, signature, typeParameterFactory)
            }
        }
        irTypeParameter.superTypes = typeParameter.bounds.map { it.toIrType() }
        irTypeParameter.parent = parentSymbol.owner as IrDeclarationParent
        return irTypeParameter
    }

    internal fun processTypeParameters(firOwner: FirTypeParameterRefsOwner, irOwner: IrTypeParametersContainer) {
        irOwner.typeParameters = firOwner.typeParameters.mapIndexedNotNull { index, typeParameter ->
            if (typeParameter !is FirTypeParameter) return@mapIndexedNotNull null
            createIrTypeParameter(typeParameter, index, irOwner.symbol)
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
