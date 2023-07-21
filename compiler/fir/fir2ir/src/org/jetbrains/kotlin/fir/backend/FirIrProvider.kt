/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData.SymbolKind
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData.SymbolKind.*
import org.jetbrains.kotlin.backend.common.serialization.kind
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(SymbolInternals::class)
class FirIrProvider(val components: Fir2IrComponents) : IrProvider {
    private val symbolProvider = components.session.symbolProvider
    val symbolTable = components.symbolTable
    private val externalDeclarationsGenerator = components.externalDeclarationsGenerator

    override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {
        if (symbol.isBound) return symbol.owner as IrDeclaration
        if (symbol is IrFieldSymbol) {
            return findFieldViaProperty(symbol)
        }
        val signature = symbol.signature ?: return null
        return getDeclarationForSignature(signature, symbol.kind())
    }

    private fun findFieldViaProperty(fieldSymbol: IrFieldSymbol): IrField? {
        val propertySymbol = symbolTable.getPropertySymbolForField(fieldSymbol) ?: return null
        val property = getDeclaration(propertySymbol) as IrProperty? ?: return null
        val field = property.backingField
        require(field?.symbol == fieldSymbol)
        return field
    }

    private fun getDeclarationForSignature(signature: IdSignature, kind: SymbolKind): IrDeclaration? = when (signature) {
        is IdSignature.AccessorSignature -> getDeclarationForAccessorSignature(signature)
        is IdSignature.CompositeSignature -> getDeclarationForCompositeSignature(signature, kind)
        is IdSignature.CommonSignature -> getDeclarationForCommonSignature(signature, kind)
        else -> error("Unexpected signature kind: $signature")
    }

    private fun getDeclarationForAccessorSignature(signature: IdSignature.AccessorSignature): IrDeclaration? {
        val property = getDeclarationForSignature(signature.propertySignature, PROPERTY_SYMBOL) as? IrProperty ?: return null
        return when (signature.accessorSignature.shortName) {
            property.getter?.name?.asString() -> property.getter
            property.setter?.name?.asString() -> property.setter
            else -> null
        }
    }

    private fun getDeclarationForCompositeSignature(signature: IdSignature.CompositeSignature, kind: SymbolKind): IrDeclaration? {
        if (kind == TYPE_PARAMETER_SYMBOL) {
            val container = (getDeclarationForSignature(signature.container, CLASS_SYMBOL)
                ?: getDeclarationForSignature(signature.container, FUNCTION_SYMBOL)
                ?: getDeclarationForSignature(signature.container, PROPERTY_SYMBOL)
                    ) as IrTypeParametersContainer
            val localSignature = signature.inner as IdSignature.LocalSignature
            return container.typeParameters[localSignature.index()]
        }
        return getDeclarationForSignature(signature.nearestPublicSig(), kind)
    }

    private fun getDeclarationForCommonSignature(signature: IdSignature.CommonSignature, kind: SymbolKind): IrDeclaration? {
        val packageFqName = FqName(signature.packageFqName)
        val nameSegments = signature.nameSegments
        val topName = Name.identifier(nameSegments[0])

        return if (nameSegments.size == 1) {
            val packageFragment = components.externalDeclarationsGenerator.getIrExternalPackageFragment(packageFqName)
            val candidates = when (kind) {
                CLASS_SYMBOL, TYPEALIAS_SYMBOL -> listOfNotNull(symbolProvider.getClassLikeSymbolByClassId(ClassId(packageFqName, topName)))
                else -> symbolProvider.getTopLevelCallableSymbols(packageFqName, topName)
            }
            val symbol = findDeclarationByHash(candidates, signature.id) ?: return null
            val irSymbol = when {
                kind == CLASS_SYMBOL && symbol is FirRegularClassSymbol ->
                    externalDeclarationsGenerator.getOrCreateLazyClass(symbol, signature, packageFragment)
                kind == FUNCTION_SYMBOL && symbol is FirNamedFunctionSymbol ->
                    externalDeclarationsGenerator.getOrCreateLazySimpleFunction(symbol, signature, packageFragment)
                kind == PROPERTY_SYMBOL && symbol is FirPropertySymbol ->
                    externalDeclarationsGenerator.getOrCreateLazyProperty(symbol, signature, packageFragment)
                else -> error("Unsupported pair of kind and symbol for top-level declaration: $kind, $symbol")
            }
            irSymbol.owner
        } else {
            val classId = createParentClassId(packageFqName, nameSegments)
            // TODO: replace with getOrCreateLazyClass?
//            val topLevelClass = symbolProvider.getRegularClassSymbolByClassId(classId)?.fir ?: return null
            val irClass = externalDeclarationsGenerator.findDependencyClassByClassId(classId)?.owner ?: return null
            irClass.declarations.first { it.symbol.signature == signature }
        }
    }

    private fun createParentClassId(packageFqName: FqName, nameSegments: List<String>): ClassId {
        var classId = ClassId(packageFqName, Name.identifier(nameSegments[0]))
        for (name in nameSegments.subList(1, nameSegments.size - 1)) {
            classId = classId.createNestedClassId(Name.identifier(name))
        }
        return classId
    }

    private fun findDeclarationByHash(candidates: Collection<FirBasedSymbol<*>>, hash: Long?): FirBasedSymbol<*>? =
        candidates.firstOrNull { candidateSymbol ->
            val candidate = candidateSymbol.fir
            if (hash == null) {
                // We don't compute id for type aliases and classes.
                candidate is FirClass || candidate is FirEnumEntry || candidate is FirTypeAlias
            } else {
                // The next line should have singleOrNull, but in some cases we get multiple references to the same FIR declaration.
                with(components.signatureComposer.mangler) { candidate.signatureMangle(compatibleMode = false) == hash }
            }
        }
}
