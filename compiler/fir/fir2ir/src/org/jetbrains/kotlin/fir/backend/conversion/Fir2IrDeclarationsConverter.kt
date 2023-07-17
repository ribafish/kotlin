/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.conversion

import org.jetbrains.kotlin.KtPsiSourceFileLinesMapping
import org.jetbrains.kotlin.KtSourceFileLinesMappingFromLineStartOffsets
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.comparators.FirMemberDeclarationComparator
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.scopes.processAllClassifiers
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtFile

class Fir2IrDeclarationsConverter(val components: Fir2IrComponents) : Fir2IrComponents by components {
    fun generateFile(file: FirFile): IrFile {
        val fileEntry = when (file.origin) {
            FirDeclarationOrigin.Source ->
                file.psi?.let { PsiIrFileEntry(it as KtFile) }
                    ?: when (val linesMapping = file.sourceFileLinesMapping) {
                        is KtSourceFileLinesMappingFromLineStartOffsets ->
                            NaiveSourceBasedFileEntryImpl(
                                file.sourceFile?.path ?: file.sourceFile?.name ?: file.name,
                                linesMapping.lineStartOffsets,
                                linesMapping.lastOffset
                            )
                        is KtPsiSourceFileLinesMapping -> PsiIrFileEntry(linesMapping.psiFile)
                        else ->
                            NaiveSourceBasedFileEntryImpl(file.sourceFile?.path ?: file.sourceFile?.name ?: file.name)
                    }
            FirDeclarationOrigin.Synthetic -> NaiveSourceBasedFileEntryImpl(file.name)
            else -> error("Unsupported file origin: ${file.origin}")
        }
        val irFile = IrFileImpl(
            fileEntry,
            IrFileSymbolImpl(),
            file.packageFqName
        )

        processDeclarationsInFile(file, irFile)

        return irFile
    }

    private fun processDeclarationsInFile(file: FirFile, irFile: IrFile) {
        val conversionScope = Fir2IrConversionScope()
        conversionScope.withParent(irFile) {
            for (declaration in file.declarations) {
                generateIrDeclaration(declaration, conversionScope)
            }
        }
    }

    fun generateIrDeclaration(declaration: FirDeclaration, conversionScope: Fir2IrConversionScope): IrDeclaration {
        return when (declaration) {
            is FirProperty -> generateIrProperty(declaration, conversionScope)
            is FirConstructor -> generateIrConstructor(declaration, conversionScope)
            is FirFunction -> generateIrFunction(declaration, conversionScope)
            is FirClass -> generateIrClass(declaration, conversionScope)
            is FirTypeAlias -> generateIrTypeAlias(declaration, conversionScope)
            is FirField -> generateIrField(declaration, conversionScope)
            is FirAnonymousInitializer -> generateIrAnonymousInitializer(declaration, conversionScope)
            is FirEnumEntry -> generateIrEnumEntry(declaration, conversionScope)
            else -> error("Unsupported declaration type: ${declaration::class}")
        }
    }

    fun generateIrClass(klass: FirClass, conversionScope: Fir2IrConversionScope): IrClass {
        val parent = conversionScope.parentFromStack()
        val irClass = when (klass) {
            is FirRegularClass -> classifierStorage.createIrClass(klass, parent)
            is FirAnonymousObject -> classifierStorage.createIrAnonymousObject(klass, irParent = parent)
        }
        symbolTable.withScope(irClass) {
            processTypeParameters(klass, irClass)
            val classMembers = collectAllClassMembers(klass)
            conversionScope.withParent(irClass) {
                for (declaration in classMembers) {
                    irClass.declarations += generateIrDeclaration(declaration, conversionScope)
                }
            }
        }
        return irClass
    }

    private fun collectAllClassMembers(klass: FirClass): List<FirDeclaration> {
        val classSymbol = klass.symbol

        val declarationsFromSources = LinkedHashSet(klass.declarations)
        val scope = klass.unsubstitutedScope(session, scopeSession, withForcedTypeCalculator = true, memberRequiredPhase = null)

        val syntheticDeclarations = mutableListOf<FirMemberDeclaration>()

        fun addSyntheticDeclarationIfNeeded(declaration: FirDeclaration) {
            if (declaration in declarationsFromSources) return
            when (declaration) {
                is FirCallableDeclaration -> {
                    // fake overrides will be added later
                    if (declaration.isSubstitutionOrIntersectionOverride) return
                    // we want to collect declarations which are actually declared in this class
                    //   not inherited ones
                    if (declaration.containingClassLookupTag()?.toSymbol(session) != classSymbol) return
                }
                is FirClassLikeDeclaration -> {
                    // skip nested classes which came from parent classes
                    if (declaration.classId.parentClassId != classSymbol.classId) return
                }
                else -> {}
            }
            require(declaration is FirMemberDeclaration) { "Non member declaration came from scope: ${declaration.render()}" }
            syntheticDeclarations += declaration
        }

        scope.processAllCallables { addSyntheticDeclarationIfNeeded(it.fir) }
        scope.processAllClassifiers { addSyntheticDeclarationIfNeeded(it.fir) }

        // Order of synthetic declarations may be unstable (e.g., because of order of compiler plugins)
        // So to have stable IR we need to sort them manually
        // Order of declarations declared in sources remains the smae
        syntheticDeclarations.sortWith(FirMemberDeclarationComparator)

        return buildList {
            addAll(declarationsFromSources)
            addAll(syntheticDeclarations)
        }
    }

    fun generateIrTypeAlias(typeAlias: FirTypeAlias, conversionScope: Fir2IrConversionScope): IrTypeAlias {
        val parent = conversionScope.parentFromStack()
        val irTypeAlias = classifierStorage.createTypeAlias(typeAlias, parent)
        symbolTable.withScope(irTypeAlias) {
            processTypeParameters(typeAlias, irTypeAlias)
        }
        return irTypeAlias
    }

    private fun processTypeParameters(firOwner: FirTypeParameterRefsOwner, irOwner: IrTypeParametersContainer) {
        irOwner.typeParameters = firOwner.typeParameters.mapIndexedNotNull { index, typeParameter ->
            if (typeParameter !is FirTypeParameter) return@mapIndexedNotNull null
            classifierStorage.createIrTypeParameter(typeParameter, index, irOwner.symbol)
        }
    }

    fun generateIrFunction(function: FirFunction, conversionScope: Fir2IrConversionScope): IrSimpleFunction {
        val irFunction = declarationStorage.createIrFunctionNew(function, conversionScope.parent())
        symbolTable.withScope(irFunction) {
            processTypeParameters(function, irFunction)
            declarationStorage.processValueParameters(function, irFunction, conversionScope.lastClass())
            // TODO: process default values of value parameters
            // TODO: process body
        }
        return irFunction
    }

    fun generateIrProperty(property: FirProperty, conversionScope: Fir2IrConversionScope): IrProperty {
        TODO()
    }

    fun generateIrConstructor(property: FirConstructor, conversionScope: Fir2IrConversionScope): IrConstructor {
        TODO()
    }

    fun generateIrField(field: FirField, conversionScope: Fir2IrConversionScope): IrField {
        TODO()
    }

    fun generateIrEnumEntry(field: FirEnumEntry, conversionScope: Fir2IrConversionScope): IrEnumEntry {
        TODO()
    }

    fun generateIrAnonymousInitializer(
        anonymousInitializer: FirAnonymousInitializer,
        conversionScope: Fir2IrConversionScope,
    ): IrAnonymousInitializer {
        TODO()
    }
}
