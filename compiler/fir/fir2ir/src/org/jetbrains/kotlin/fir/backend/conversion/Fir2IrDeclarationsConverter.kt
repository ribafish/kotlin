/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.conversion

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtPsiSourceFileLinesMapping
import org.jetbrains.kotlin.KtSourceFileLinesMappingFromLineStartOffsets
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.comparators.FirMemberDeclarationComparator
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedBaseSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.withScopeCleanup
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.scopes.processAllClassifiers
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrEnumConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.NameUtils
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
            is FirAnonymousInitializer -> generateIrAnonymousInitializer(declaration, conversionScope)
            is FirEnumEntry -> generateIrEnumEntry(declaration, conversionScope)
            else -> error("Unsupported declaration type: ${declaration::class}")
        }
    }

    fun generateIrClass(klass: FirClass, conversionScope: Fir2IrConversionScope): IrClass {
        val parent = conversionScope.parentFromStack()
        val irClass = when (klass) {
            is FirRegularClass -> classifierGenerator.createIrClass(klass, parent)
            is FirAnonymousObject -> classifierGenerator.createIrAnonymousObject(klass, irParent = parent)
        }
        symbolTable.withScope(irClass) {
            classifierGenerator.processTypeParameters(klass, irClass)
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
        val irTypeAlias = classifierGenerator.createTypeAlias(typeAlias, parent)
        symbolTable.withScope(irTypeAlias) {
            classifierGenerator.processTypeParameters(typeAlias, irTypeAlias)
        }
        return irTypeAlias
    }

    fun generateIrFunction(function: FirFunction, conversionScope: Fir2IrConversionScope): IrSimpleFunction {
        val irFunction = callablesGenerator.createIrFunction(function, conversionScope.parent())
        symbolTable.withScope(irFunction) {
            classifierGenerator.processTypeParameters(function, irFunction)
            callablesGenerator.processValueParameters(function, irFunction, conversionScope.lastClass())
            // TODO: process default values of value parameters
            // TODO: process body
        }
        return irFunction
    }

    fun generateIrConstructor(constructor: FirConstructor, conversionScope: Fir2IrConversionScope): IrConstructor {
        val containingIrClass = conversionScope.lastClass()!!
        val irConstructor = callablesGenerator.createIrConstructor(constructor, containingIrClass)
        symbolTable.withScope(irConstructor) {
            classifierGenerator.processTypeParameters(constructor, irConstructor)
            callablesGenerator.processValueParameters(constructor, irConstructor, containingIrClass)
            // TODO: process default values of value parameters
            // TODO: process body
        }
        return irConstructor
    }

    fun generateIrProperty(property: FirProperty, conversionScope: Fir2IrConversionScope): IrProperty {
        val parent = conversionScope.parentFromStack()
        val irProperty = callablesGenerator.createIrProperty(property, parent)

        // fields and getters are part of the scope of the container, not property itself,
        // so there is no need to call symbolTable.withScope(irProperty)
        processBackingField(property, irProperty)
        processPropertyAccessors(property, irProperty, conversionScope)

        return irProperty
    }

    private fun processBackingField(property: FirProperty, irProperty: IrProperty) {
        val delegate = property.delegate
        when {
            delegate != null -> {
                callablesGenerator.createBackingField(
                    property,
                    irProperty,
                    property.delegateFieldSymbol!!,
                    IrDeclarationOrigin.PROPERTY_DELEGATE,
                    NameUtils.propertyDelegateName(property.name),
                    isFinal = true
                )
                // TODO: process delegate expression
            }

            property.hasBackingField -> {
                callablesGenerator.createBackingField(
                    property,
                    irProperty,
                    property.backingField!!.symbol,
                    IrDeclarationOrigin.PROPERTY_BACKING_FIELD,
                    property.name,
                    isFinal = property.isVal
                )
                // TODO: process initializer
            }
        }
    }

    private fun processPropertyAccessors(property: FirProperty, irProperty: IrProperty, conversionScope: Fir2IrConversionScope) {
        val getter = property.getter ?: FirDefaultPropertyAccessor.createGetterOrSetter(
            property.source?.fakeElement(KtFakeSourceElementKind.DefaultAccessor),
            property.moduleData, property.origin, property.returnTypeRef,
            property.visibility, property.symbol, isGetter = true
        )
        processPropertyAccessor(property, irProperty, getter, isSetter = false, conversionScope)
        if (property.isVar) {
            val setter = property.getter ?: FirDefaultPropertyAccessor.createGetterOrSetter(
                property.source?.fakeElement(KtFakeSourceElementKind.DefaultAccessor),
                property.moduleData, property.origin, property.returnTypeRef,
                property.visibility, property.symbol, isGetter = false
            )
            processPropertyAccessor(property, irProperty, setter, isSetter = true, conversionScope)
        }
    }

    private fun processPropertyAccessor(
        property: FirProperty,
        irProperty: IrProperty,
        accessor: FirPropertyAccessor,
        isSetter: Boolean,
        conversionScope: Fir2IrConversionScope
    ) {
        val irAccessor = accessor.convertWithOffsets { startOffset, endOffset ->
            callablesGenerator.createIrPropertyAccessor(
                accessor,
                property,
                irProperty,
                isSetter,
                accessor.computeIrOrigin(irProperty.origin, property.delegate != null)
            )
        }
        // TODO: pass IR type origin
        symbolTable.withScope(irAccessor) {
            classifierGenerator.processTypeParameters(accessor, irAccessor)
            callablesGenerator.processValueParameters(accessor, irAccessor, conversionScope.lastClass())
            // TODO: process body
        }
    }

    private fun FirPropertyAccessor.computeIrOrigin(
        propertyOrigin: IrDeclarationOrigin,
        propertyIsDelegated: Boolean,
    ): IrDeclarationOrigin {
        if (propertyIsDelegated) return IrDeclarationOrigin.DELEGATED_PROPERTY_ACCESSOR
        when (propertyOrigin) {
            IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB,
            IrDeclarationOrigin.ENUM_CLASS_SPECIAL_MEMBER,
            IrDeclarationOrigin.FAKE_OVERRIDE,
            IrDeclarationOrigin.DELEGATED_MEMBER -> return propertyOrigin
        }
        if (this is FirDefaultPropertyAccessor) return IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        return propertyOrigin
    }

    fun generateIrEnumEntry(enumEntry: FirEnumEntry, conversionScope: Fir2IrConversionScope): IrEnumEntry {
        val irEnumEntry = classifierGenerator.createIrEnumEntry(enumEntry, conversionScope.lastClass()!!)

        symbolTable.withScope(irEnumEntry) {
            if (isEnumEntryWhichRequiresSubclass(enumEntry)) {
                val initializingObjectExpression = enumEntry.initializer as FirAnonymousObjectExpression
                val irClassForEntry = generateIrClass(initializingObjectExpression.anonymousObject, conversionScope)
                irEnumEntry.correspondingClass = irClassForEntry
                val irConstructor = irClassForEntry.constructors.first()
                irEnumEntry.initializerExpression = irFactory.createExpressionBody(
                    IrEnumConstructorCallImpl(
                        startOffset, endOffset, irType,
                        constructor.symbol,
                        typeArgumentsCount = constructor.typeParameters.size,
                        valueArgumentsCount = constructor.valueParameters.size
                    )
                )
            }
        }

        return irEnumEntry
    }

    fun generateIrAnonymousInitializer(
        anonymousInitializer: FirAnonymousInitializer,
        conversionScope: Fir2IrConversionScope,
    ): IrAnonymousInitializer {
        val irAnonymousInitializer = classifierGenerator.createIrAnonymousInitializer(anonymousInitializer, conversionScope.lastClass()!!)
        // TODO: generate body
        return irAnonymousInitializer
    }

    // -------------------------------------------- Utilities --------------------------------------------

    private fun isEnumEntryWhichRequiresSubclass(enumEntry: FirEnumEntry): Boolean {
        val initializer = enumEntry.initializer
        // TODO: rewrite with scopes
        return initializer is FirAnonymousObjectExpression && initializer.anonymousObject.declarations.any { it !is FirConstructor }
    }

    private inline fun <E, R> Fir2IrConversionScope.withScopeAndParent(
        owner: E,
        block: () -> R,
    ): R where E : IrSymbolOwner, E : IrDeclarationParent {
        symbolTable.withScope(owner) {
            withParent(owner, block) // TODO
        }
    }
}
