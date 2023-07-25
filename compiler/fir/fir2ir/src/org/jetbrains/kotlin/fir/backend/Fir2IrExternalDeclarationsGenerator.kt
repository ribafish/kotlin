/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.isJavaOrEnhancement
import org.jetbrains.kotlin.fir.descriptors.FirBuiltInsPackageFragment
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.descriptors.FirPackageFragmentDescriptor
import org.jetbrains.kotlin.fir.lazy.*
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrExternalPackageFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.IrExternalPackageFragmentSymbolImpl
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.withScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.util.concurrent.ConcurrentHashMap

class Fir2IrExternalDeclarationsGenerator(
    private val components: Fir2IrComponents,
    private val moduleDescriptor: FirModuleDescriptor,
) : Fir2IrComponents by components {
    private val fragmentCache: ConcurrentHashMap<FqName, ExternalPackageFragments> = ConcurrentHashMap()
    private val builtInsFragmentCache: ConcurrentHashMap<FqName, IrExternalPackageFragment> = ConcurrentHashMap()

    private class ExternalPackageFragments(
        val fragmentForDependencies: IrExternalPackageFragment,
        val fragmentForPrecompiledBinaries: IrExternalPackageFragment,
    )

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

    fun findDependencyClassByClassId(classId: ClassId): IrClassSymbol? {
        val firSymbol = session.symbolProvider.getClassLikeSymbolByClassId(classId) ?: return null
        require(firSymbol.origin.isExternal) {
            "Bruh: ${firSymbol.origin}, ${firSymbol.fir.render()}"
        }

        val firClassSymbol = firSymbol as? FirRegularClassSymbol ?: return null
        val signature = signatureComposer.composeSignature(firClassSymbol.fir)
        val irParent = when (val outerClassId = classId.outerClassId) {
            null -> getIrExternalPackageFragment(classId.packageFqName)
            else -> findDependencyClassByClassId(outerClassId)!!.owner
        }
        return getOrCreateLazyClass(firClassSymbol, signature, irParent)
    }

    fun getOrCreateLazyClass(
        classSymbol: FirRegularClassSymbol,
        signature: IdSignature?,
        irParent: IrDeclarationParent,
    ): IrClassSymbol {
        val irClass = symbolTable.declareClassIfNotExists(classSymbol, signature) { symbol ->
            val klass = classSymbol.fir
            Fir2IrLazyClass(
                components,
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                klass.irOrigin(session.firProvider),
                klass,
                symbol
            ).apply {
                parent = irParent
                processTypeParameters()
            }
        }
        return irClass.symbol
    }

    fun getOrCreateLazyConstructor(
        constructorSymbol: FirConstructorSymbol,
        signature: IdSignature?,
        irParent: IrDeclarationParent,
    ): IrConstructorSymbol {
        val irConstructor = symbolTable.declareConstructorIfNotExists(constructorSymbol, signature) { symbol ->
            val constructor = constructorSymbol.fir
            Fir2IrLazyConstructor(
                components,
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                constructor.computeExternalOrigin(irParent),
                constructor,
                symbol
            ).apply {
                parent = irParent
                processTypeParameters()
            }
        }
        return irConstructor.symbol
    }

    fun getOrCreateLazySimpleFunction(
        functionSymbol: FirNamedFunctionSymbol,
        signature: IdSignature?,
        irParent: IrDeclarationParent,
    ): IrSimpleFunctionSymbol {
        val irFunction = symbolTable.declareFunctionIfNotExists(functionSymbol, signature) { symbol ->
            val function = functionSymbol.fir
            val isFakeOverride =
                function.isSubstitutionOrIntersectionOverride &&
                        function.dispatchReceiverClassLookupTagOrNull() !=
                        function.originalForSubstitutionOverride?.dispatchReceiverClassLookupTagOrNull()
            Fir2IrLazySimpleFunction(
                components,
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                function.computeExternalOrigin(irParent),
                function,
                (irParent as? Fir2IrLazyClass)?.fir,
                symbol,
                isFakeOverride,
                irParent
            ).apply {
                processTypeParameters()
            }
        }
        return irFunction.symbol
    }

    fun getOrCreateLazyProperty(
        propertySymbol: FirPropertySymbol,
        signature: IdSignature?,
        irParent: IrDeclarationParent,
    ): IrPropertySymbol {
        val irProperty = symbolTable.declarePropertyIfNotExists(propertySymbol, signature) { symbol ->
            val property = propertySymbol.fir
            val isFakeOverride =
                property.isSubstitutionOrIntersectionOverride &&
                        property.dispatchReceiverClassLookupTagOrNull() !=
                        property.originalForSubstitutionOverride?.dispatchReceiverClassLookupTagOrNull()
            Fir2IrLazyProperty(
                components,
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                property.computeExternalOrigin(irParent),
                property,
                (irParent as? Fir2IrLazyClass)?.fir,
                symbol,
                isFakeOverride
            ).apply {
                parent = irParent
            }
        }
        return irProperty.symbol
    }

    private fun Fir2IrTypeParametersContainer.processTypeParameters() {
        symbolTable.withScope(this) {
            prepareTypeParameters()
        }
    }

    // TODO: probably worth to leave only part about external declaration stub
    private fun FirCallableDeclaration.computeExternalOrigin(
        irParent: IrDeclarationParent
    ): IrDeclarationOrigin {
        val parentOrigin = when (irParent) {
            is IrDeclaration -> irParent.origin
            is IrExternalPackageFragment -> IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB
            else -> error("Unsupported declaration: ${irParent::class.simpleName}")
        }
        return when {
            this.isIntersectionOverride || this.isSubstitutionOverride -> IrDeclarationOrigin.FAKE_OVERRIDE
            parentOrigin == IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB && this.isJavaOrEnhancement -> {
                IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            }
            this.origin is FirDeclarationOrigin.Plugin -> IrDeclarationOrigin.GeneratedByPlugin((this.origin as FirDeclarationOrigin.Plugin).key)
            else -> parentOrigin
        }
    }

    fun getIrClassSymbolForNotFoundClass(classLikeLookupTag: ConeClassLikeLookupTag): IrClassSymbol {
        val classId = classLikeLookupTag.classId
        val signature = IdSignature.CommonSignature(
            packageFqName = classId.packageFqName.asString(),
            declarationFqName = classId.relativeClassName.asString(),
            id = 0,
            mask = 0,
            description = null,
        )

        val parentId = classId.outerClassId
        val parentClass = parentId?.let { getIrClassSymbolForNotFoundClass(it.toLookupTag()) }
        val irParent = parentClass?.owner ?: getIrExternalPackageFragment(classId.packageFqName)

        val irClass = symbolTable.declareClassIfNotExists(signature) {
            irFactory.createClass(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB,
                name = classId.shortClassName,
                visibility = DescriptorVisibilities.DEFAULT_VISIBILITY,
                symbol = it,
                kind = ClassKind.CLASS,
                modality = Modality.FINAL,
            ).apply {
                parent = irParent
            }
        }
        return irClass.symbol
    }
}

val FirDeclarationOrigin.isExternal: Boolean
    get() = this == FirDeclarationOrigin.Library || this == FirDeclarationOrigin.BuiltIns || this is FirDeclarationOrigin.Java
