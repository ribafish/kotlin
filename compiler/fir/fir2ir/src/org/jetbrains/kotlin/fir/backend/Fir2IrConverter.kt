/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.symbols.lazyDeclarationResolver
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.generateUnboundSymbolsAsDependencies

class Fir2IrConverter(private val components: Fir2IrComponents) {
    private fun runSourcesConversion(
        allFirFiles: List<FirFile>,
        irModuleFragment: IrModuleFragmentImpl
    ) {
        with(components) {
            session.lazyDeclarationResolver.disableLazyResolveContractChecks()
            for (firFile in allFirFiles) {
                irModuleFragment.files += declarationsConverter.generateFile(firFile, irModuleFragment)
            }
            generateUnboundSymbolsAsDependencies(
                irProviders,
                symbolTable,
                symbolExtractor = Fir2IrSymbolTableExtension::unboundClassifiersSymbols,
            )
            fakeOverrideBuilder.provideFakeOverrides()
            generateUnboundSymbolsAsDependencies(irProviders, symbolTable)
            evaluateConstants(irModuleFragment, configuration)
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
