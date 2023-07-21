/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.overrides.FakeOverrideBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.conversion.Fir2IrDeclarationsConverter
import org.jetbrains.kotlin.fir.backend.generators.AnnotationGenerator
import org.jetbrains.kotlin.fir.backend.generators.CallAndReferenceGenerator
import org.jetbrains.kotlin.fir.backend.generators.DelegatedMemberGenerator
import org.jetbrains.kotlin.fir.backend.generators.FakeOverrideGenerator
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.signaturer.FirBasedSignatureComposer
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.ir.IrLock
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.SymbolTable

interface Fir2IrComponents {
    val session: FirSession
    val scopeSession: ScopeSession

    val irMangler: KotlinMangler.IrMangler
    val fakeOverrideBuilder: FakeOverrideBuilder

    val converter: Fir2IrConverter // TODO: to remove
    val declarationsConverter: Fir2IrDeclarationsConverter

    val symbolTable: Fir2IrSymbolTableExtension
    val irBuiltIns: IrBuiltInsOverFir
    val builtIns: Fir2IrBuiltIns
    val irFactory: IrFactory
    val irProviders: List<IrProvider>
    val lock: IrLock

    val classifierStorage: Fir2IrClassifierStorage // TODO: to remove
    val declarationStorage: Fir2IrDeclarationStorage // TODO: to remove

    val conversionScope: Fir2IrConversionScope

    val callablesGenerator: Fir2IrCallableDeclarationGenerator
    val classifierGenerator: Fir2IrClassifierGenerator
    val externalDeclarationsGenerator: Fir2IrExternalDeclarationsGenerator

    val typeConverter: Fir2IrTypeConverter
    val signatureComposer: FirBasedSignatureComposer
    val visibilityConverter: Fir2IrVisibilityConverter

    val annotationGenerator: AnnotationGenerator
    val callGenerator: CallAndReferenceGenerator
    val fakeOverrideGenerator: FakeOverrideGenerator // TODO: to remove
    val delegatedMemberGenerator: DelegatedMemberGenerator

    val extensions: Fir2IrExtensions
    val configuration: Fir2IrConfiguration

    val annotationsFromPluginRegistrar: Fir2IrAnnotationsFromPluginRegistrar
}

context(Fir2IrComponents)
fun FirTypeRef.toIrType(typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT): IrType {
    return with(typeConverter) { toIrType(typeOrigin) }
}

context(Fir2IrComponents)
fun ConeKotlinType.toIrType(typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT): IrType {
    return with(typeConverter) { toIrType(typeOrigin) }
}

context(Fir2IrComponents)
fun Visibility.toDescriptorVisibility(): DescriptorVisibility {
    return visibilityConverter.convertToDescriptorVisibility(this)
}
