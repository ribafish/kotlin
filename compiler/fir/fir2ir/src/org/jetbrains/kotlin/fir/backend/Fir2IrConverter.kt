/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.linkage.partial.PartialLinkageSupportForLinker
import org.jetbrains.kotlin.backend.common.overrides.FakeOverrideBuilder
import org.jetbrains.kotlin.backend.common.overrides.FileLocalAwareLinker
import org.jetbrains.kotlin.backend.common.serialization.CompatibilityMode
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.conversion.Fir2IrDeclarationsConverter
import org.jetbrains.kotlin.fir.backend.generators.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.isSynthetic
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.extensions.declarationGenerators
import org.jetbrains.kotlin.fir.extensions.extensionService
import org.jetbrains.kotlin.fir.extensions.generatedMembers
import org.jetbrains.kotlin.fir.extensions.generatedNestedClassifiers
import org.jetbrains.kotlin.fir.isEnumEntries
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.symbols.lazyDeclarationResolver
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterConfiguration
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterEnvironment
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.transformer.transformConst
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.generateUnboundSymbolsAsDependencies

class Fir2IrConverter(
    private val moduleDescriptor: FirModuleDescriptor,
    private val components: Fir2IrComponents
) : Fir2IrComponents by components {

    private val generatorExtensions = session.extensionService.declarationGenerators

    private var wereSourcesFakeOverridesBound = false
    private val postponedDeclarationsForFakeOverridesBinding = mutableListOf<IrDeclaration>()

    private fun runSourcesConversion(
        allFirFiles: List<FirFile>,
        irModuleFragment: IrModuleFragmentImpl
    ) {
        session.lazyDeclarationResolver.disableLazyResolveContractChecks()
        for (firFile in allFirFiles) {
            irModuleFragment.files += declarationsConverter.generateFile(firFile, irModuleFragment)
        }
        generateUnboundSymbolsAsDependencies(
            irProviders,
            symbolTable,
            symbolExtractor = Fir2IrSymbolTableExtension::unboundClassifiersSymbols,
            onSymbol = {
                if (it is IrClassSymbol) {
                    val irClass = it.owner
//                    if (irClass is Fir2IrLazyClass) {
//                        // Trigger computation of declarations to reference other external classes
//                        irClass.declarations
//                    }
//                    fakeOverrideBuilder.enqueueClass(irClass, it.signature!!, CompatibilityMode.CURRENT)
                }
            }
        )
        fakeOverrideBuilder.provideFakeOverrides()
        generateUnboundSymbolsAsDependencies(irProviders, symbolTable)
        evaluateConstants(irModuleFragment, configuration)
    }

    fun bindFakeOverridesOrPostpone(declarations: List<IrDeclaration>) {
        // Do not run binding for lazy classes until all sources declarations are processed
        if (wereSourcesFakeOverridesBound) {
            fakeOverrideGenerator.bindOverriddenSymbols(declarations)
        } else {
            postponedDeclarationsForFakeOverridesBinding += declarations
        }
    }

    fun processLocalClassAndNestedClassesOnTheFly(klass: FirClass, parent: IrDeclarationParent): IrClass {
        val irClass = registerClassAndNestedClasses(klass, parent)
        processClassAndNestedClassHeaders(klass)
        return irClass
    }

    internal fun processClassMembers(klass: FirClass, irClass: IrClass): IrClass {
        val allDeclarations = mutableListOf<FirDeclaration>().apply {
            addAll(klass.declarations)
            if (klass is FirRegularClass && generatorExtensions.isNotEmpty()) {
                addAll(klass.generatedMembers(session))
                addAll(klass.generatedNestedClassifiers(session))
            }
        }
        val irConstructor = klass.primaryConstructorIfAny(session)?.let {
            declarationStorage.getOrCreateIrConstructor(
                it.fir, irClass, isLocal = klass.isLocal
            )
        }
        if (irConstructor != null) {
            irClass.declarations += irConstructor
        }
        // At least on enum entry creation we may need a default constructor, so ctors should be converted first
        for (declaration in syntheticPropertiesLast(allDeclarations)) {
            val irDeclaration = processMemberDeclaration(declaration, klass, irClass) ?: continue
            irClass.declarations += irDeclaration
        }
        // Add delegated members *before* fake override generations.
        // Otherwise, fake overrides for delegated members, which are redundant, will be added.
        allDeclarations += delegatedMembers(irClass)
        // Add synthetic members *before* fake override generations.
        // Otherwise, redundant members, e.g., synthetic toString _and_ fake override toString, will be added.
        if (klass is FirRegularClass && irConstructor != null && (irClass.isValue || irClass.isData)) {
            declarationStorage.enterScope(irConstructor)
            val dataClassMembersGenerator = DataClassMembersGenerator(components)
            if (irClass.isSingleFieldValueClass) {
                allDeclarations += dataClassMembersGenerator.generateSingleFieldValueClassMembers(klass, irClass)
            }
            if (irClass.isMultiFieldValueClass) {
                allDeclarations += dataClassMembersGenerator.generateMultiFieldValueClassMembers(klass, irClass)
            }
            if (irClass.isData) {
                allDeclarations += dataClassMembersGenerator.generateDataClassMembers(klass, irClass)
            }
            declarationStorage.leaveScope(irConstructor)
        }
        with(fakeOverrideGenerator) {
            irClass.addFakeOverrides(klass, allDeclarations)
        }

        return irClass
    }

    fun bindFakeOverridesInClass(klass: IrClass) {
        fakeOverrideGenerator.bindOverriddenSymbols(klass.declarations)
        delegatedMemberGenerator.bindDelegatedMembersOverriddenSymbols(klass)
        for (irDeclaration in klass.declarations) {
            if (irDeclaration is IrClass) {
                bindFakeOverridesInClass(irDeclaration)
            }
        }
    }

    private fun delegatedMembers(irClass: IrClass): List<FirDeclaration> {
        return irClass.declarations.filter {
            it.origin == IrDeclarationOrigin.DELEGATED_MEMBER
        }.mapNotNull {
            components.declarationStorage.originalDeclarationForDelegated(it)
        }
    }

    // Sort declarations so that all non-synthetic declarations and `synthetic class delegation fields` are before other synthetic ones.
    // This is needed because converting synthetic fields for implementation delegation needs to know
    // existing declarations in the class to avoid adding redundant delegated members.
    private fun syntheticPropertiesLast(declarations: Iterable<FirDeclaration>): Iterable<FirDeclaration> {
        return declarations.sortedBy {
            when {
                !it.isSynthetic -> false
                it.source?.kind is KtFakeSourceElementKind.ClassDelegationField -> false
                else -> true
            }
        }
    }

    private fun registerClassAndNestedClasses(klass: FirClass, parent: IrDeclarationParent): IrClass {
        // Local classes might be referenced before they declared (see usages of Fir2IrClassifierStorage.createLocalIrClassOnTheFly)
        // So, we only need to set its parent properly
        val irClass =
            classifierStorage.getCachedIrClass(klass)?.apply {
                this.parent = parent
            } ?: when (klass) {
                is FirRegularClass -> classifierStorage.registerIrClass(klass, parent)
                is FirAnonymousObject -> classifierStorage.registerIrAnonymousObject(klass, irParent = parent)
            }
        registerNestedClasses(klass, irClass)
        return irClass
    }

    private fun registerNestedClasses(klass: FirClass, irClass: IrClass) {
        klass.declarations.forEach {
            if (it is FirRegularClass) {
                registerClassAndNestedClasses(it, irClass)
            }
        }
        if (klass is FirRegularClass && generatorExtensions.isNotEmpty()) {
            klass.generatedNestedClassifiers(session).forEach {
                if (it is FirRegularClass) {
                    registerClassAndNestedClasses(it, irClass)
                }
            }
        }
    }

    private fun processClassAndNestedClassHeaders(klass: FirClass) {
        classifierStorage.processClassHeader(klass)
        processNestedClassHeaders(klass)
    }

    private fun processNestedClassHeaders(klass: FirClass) {
        klass.declarations.forEach {
            if (it is FirRegularClass) {
                processClassAndNestedClassHeaders(it)
            }
        }
        if (klass is FirRegularClass && generatorExtensions.isNotEmpty()) {
            klass.generatedNestedClassifiers(session).forEach {
                if (it is FirRegularClass) {
                    processClassAndNestedClassHeaders(it)
                }
            }
        }
    }

    private fun processMemberDeclaration(
        declaration: FirDeclaration,
        containingClass: FirClass?,
        parent: IrDeclarationParent
    ): IrDeclaration? {
        val isLocal = containingClass != null &&
                (containingClass !is FirRegularClass || containingClass.isLocal)
        return when (declaration) {
            is FirRegularClass -> {
                processClassMembers(declaration, classifierStorage.getCachedIrClass(declaration)!!)
            }
            is FirScript -> {
                parent as IrFile
                declarationStorage.getOrCreateIrScript(declaration).also { irScript ->
                    declarationStorage.enterScope(irScript)
                    irScript.parent = parent
                    for (scriptStatement in declaration.statements) {
                        when (scriptStatement) {
                            is FirRegularClass -> {
                                registerClassAndNestedClasses(scriptStatement, irScript)
                                processClassAndNestedClassHeaders(scriptStatement)
                            }
                            is FirTypeAlias -> classifierStorage.registerTypeAlias(scriptStatement, irScript)
                            else -> {}
                        }
                    }
                    for (scriptStatement in declaration.statements) {
                        if (scriptStatement is FirDeclaration) {
                            processMemberDeclaration(scriptStatement, null, irScript)
                        }
                    }
                    declarationStorage.leaveScope(irScript)
                }
            }
            is FirSimpleFunction -> {
                declarationStorage.getOrCreateIrFunction(
                    declaration, parent, isLocal = isLocal
                )
            }
            is FirProperty -> {
                if (containingClass != null &&
                    declaration.isEnumEntries(containingClass) &&
                    !session.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)
                ) {
                    // Note: we have to do it, because backend without the feature
                    // cannot process Enum.entries properly
                    null
                } else {
                    declarationStorage.getOrCreateIrProperty(
                        declaration, parent, isLocal = isLocal
                    )
                }
            }
            is FirField -> {
                if (declaration.isSynthetic) {
                    declarationStorage.createIrFieldAndDelegatedMembers(declaration, containingClass!!, parent as IrClass)
                } else {
                    throw AssertionError("Unexpected non-synthetic field: ${declaration::class}")
                }
            }
            is FirConstructor -> if (!declaration.isPrimary) {
                declarationStorage.getOrCreateIrConstructor(
                    declaration, parent as IrClass, isLocal = isLocal
                )
            } else {
                null
            }
            is FirEnumEntry -> {
                classifierStorage.getIrEnumEntry(declaration, parent as IrClass)
            }
            is FirAnonymousInitializer -> {
                declarationStorage.createIrAnonymousInitializer(declaration, parent as IrClass)
            }
            is FirTypeAlias -> {
                // DO NOTHING
                null
            }
            else -> {
                error("Unexpected member: ${declaration::class}")
            }
        }
    }

    companion object {
        private fun evaluateConstants(irModuleFragment: IrModuleFragment, fir2IrConfiguration: Fir2IrConfiguration) {
//            val firModuleDescriptor = irModuleFragment.descriptor as? FirModuleDescriptor
//            val targetPlatform = firModuleDescriptor?.platform
//            val languageVersionSettings = firModuleDescriptor?.session?.languageVersionSettings
//            val intrinsicConstEvaluation = languageVersionSettings?.supportsFeature(LanguageFeature.IntrinsicConstEvaluation) == true
//
//            val configuration = IrInterpreterConfiguration(
//                platform = targetPlatform,
//                printOnlyExceptionMessage = true,
//            )
//            val interpreter = IrInterpreter(IrInterpreterEnvironment(irModuleFragment.irBuiltins, configuration))
//            val mode = if (intrinsicConstEvaluation) EvaluationMode.ONLY_INTRINSIC_CONST else EvaluationMode.ONLY_BUILTINS
//            irModuleFragment.files.forEach {
//                it.transformConst(interpreter, mode, fir2IrConfiguration.evaluatedConstTracker, fir2IrConfiguration.inlineConstTracker)
//            }
        }

        fun createModuleFragmentWithSignaturesIfNeeded(
            session: FirSession,
            scopeSession: ScopeSession,
            firFiles: List<FirFile>,
            fir2IrExtensions: Fir2IrExtensions,
            fir2IrConfiguration: Fir2IrConfiguration,
            irMangler: KotlinMangler.IrMangler,
            irFactory: IrFactory,
            visibilityConverter: Fir2IrVisibilityConverter,
            specialSymbolProvider: Fir2IrSpecialSymbolProvider,
            kotlinBuiltIns: KotlinBuiltIns,
            commonMemberStorage: Fir2IrCommonMemberStorage,
            initializedIrBuiltIns: IrBuiltInsOverFir?
        ): Fir2IrResult {
            return createModuleFragmentWithSignaturesIfNeeded(
                session,
                scopeSession,
                firFiles,
                fir2IrExtensions,
                fir2IrConfiguration,
                { irMangler },
                irFactory,
                visibilityConverter,
                specialSymbolProvider,
                kotlinBuiltIns,
                commonMemberStorage,
                initializedIrBuiltIns
            )
        }

        fun createModuleFragmentWithSignaturesIfNeeded(
            session: FirSession,
            scopeSession: ScopeSession,
            firFiles: List<FirFile>,
            fir2IrExtensions: Fir2IrExtensions,
            fir2IrConfiguration: Fir2IrConfiguration,
            irManglerProvider: (List<IrProvider>) -> KotlinMangler.IrMangler,
            irFactory: IrFactory,
            visibilityConverter: Fir2IrVisibilityConverter,
            specialSymbolProvider: Fir2IrSpecialSymbolProvider,
            kotlinBuiltIns: KotlinBuiltIns,
            commonMemberStorage: Fir2IrCommonMemberStorage,
            initializedIrBuiltIns: IrBuiltInsOverFir?
        ): Fir2IrResult {
            val moduleDescriptor = FirModuleDescriptor(session, kotlinBuiltIns)
            val fir2IrSymbolTableExtension = Fir2IrSymbolTableExtension(commonMemberStorage.symbolTable, commonMemberStorage.firSignatureComposer)
            val components = Fir2IrComponentsStorage(
                session,
                scopeSession,
                fir2IrSymbolTableExtension,
                irFactory,
                fir2IrExtensions,
                fir2IrConfiguration,
                visibilityConverter,
                moduleDescriptor,
                commonMemberStorage,
                initializedIrBuiltIns,
                irManglerProvider,
                specialSymbolProvider
            )
            fir2IrExtensions.registerDeclarations(commonMemberStorage.symbolTable)

            val irModuleFragment = IrModuleFragmentImpl(moduleDescriptor, components.irBuiltIns)

            val allFirFiles = buildList {
                addAll(firFiles)
                addAll(session.createFilesWithGeneratedDeclarations())
            }

            components.converter.runSourcesConversion(
                allFirFiles, irModuleFragment
            )

            return Fir2IrResult(irModuleFragment, components, moduleDescriptor)
        }
    }
}
