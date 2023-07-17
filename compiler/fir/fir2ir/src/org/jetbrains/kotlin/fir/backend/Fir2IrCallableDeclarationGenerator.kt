/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.isSubstitutionOrIntersectionOverride
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazySimpleFunction
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.isKFunctionInvoke
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.arrayElementType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSuspendOrKSuspendFunctionType
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.UNDEFINED_PARAMETER_INDEX
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBodyKind
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.runUnless

@Suppress("DuplicatedCode")
@OptIn(ObsoleteDescriptorBasedAPI::class)
class Fir2IrCallableDeclarationGenerator(
    private val components: Fir2IrComponents,
    private val moduleDescriptor: FirModuleDescriptor,
) : Fir2IrComponents by components {
    companion object {
        internal val ENUM_SYNTHETIC_NAMES = mapOf(
            Name.identifier("values") to IrSyntheticBodyKind.ENUM_VALUES,
            Name.identifier("valueOf") to IrSyntheticBodyKind.ENUM_VALUEOF,
            Name.identifier("entries") to IrSyntheticBodyKind.ENUM_ENTRIES
        )
    }

    private val firProvider = session.firProvider

    fun createIrFunction(
        function: FirFunction,
        irParent: IrDeclarationParent?,
        predefinedOrigin: IrDeclarationOrigin? = null,
    ): IrSimpleFunction = convertCatching(function) {
        val simpleFunction = function as? FirSimpleFunction
        val isLambda = function is FirAnonymousFunction && function.isLambda
        val updatedOrigin = when {
            isLambda -> IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            function.symbol.callableId.isKFunctionInvoke() -> IrDeclarationOrigin.FAKE_OVERRIDE
            simpleFunction?.isStatic == true && simpleFunction.name in ENUM_SYNTHETIC_NAMES -> IrDeclarationOrigin.ENUM_CLASS_SPECIAL_MEMBER

            // Kotlin built-in class and Java originated method (Collection.forEach, etc.)
            // It's necessary to understand that such methods do not belong to DefaultImpls but actually generated as default
            // See org.jetbrains.kotlin.backend.jvm.lower.InheritedDefaultMethodsOnClassesLoweringKt.isDefinitelyNotDefaultImplsMethod
            (irParent as? IrClass)?.origin == IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB &&
                    function.isJavaOrEnhancement -> IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            else -> function.computeIrOrigin(predefinedOrigin)
        }

        val signature = signatureComposer.composeSignature(function)

        if (irParent is Fir2IrLazyClass && signature != null) {
            // For private functions, signature is null, fallback to non-lazy function
            return createIrLazyFunction(function as FirSimpleFunction, signature, irParent, updatedOrigin)
        }

        val name = simpleFunction?.name ?: when {
            isLambda -> SpecialNames.ANONYMOUS
            else -> SpecialNames.NO_NAME_PROVIDED
        }

        val visibility = simpleFunction?.visibility ?: Visibilities.Local
        val isSuspend = when {
            isLambda -> (function as FirAnonymousFunction).typeRef.coneType.isSuspendOrKSuspendFunctionType(session)
            else -> function.isSuspend
        }

        val irFunction = function.convertWithOffsets { startOffset, endOffset ->
            val result = symbolTable.declareFunction(function.symbol, signature) { symbol ->
                irFactory.createSimpleFunction(
                    startOffset = if (updatedOrigin == IrDeclarationOrigin.DELEGATED_MEMBER) SYNTHETIC_OFFSET else startOffset,
                    endOffset = if (updatedOrigin == IrDeclarationOrigin.DELEGATED_MEMBER) SYNTHETIC_OFFSET else endOffset,
                    origin = updatedOrigin,
                    name = name,
                    visibility = visibility.toDescriptorVisibility(),
                    isInline = simpleFunction?.isInline == true,
                    isExpect = simpleFunction?.isExpect == true,
                    returnType = function.returnTypeRef.toIrType(),
                    modality = simpleFunction?.modality ?: Modality.FINAL,
                    symbol = symbol,
                    isTailrec = simpleFunction?.isTailRec == true,
                    isSuspend = isSuspend,
                    isOperator = simpleFunction?.isOperator == true,
                    isInfix = simpleFunction?.isInfix == true,
                    isExternal = simpleFunction?.isExternal == true,
                    containerSource = simpleFunction?.containerSource,
                ).apply {
                    metadata = FirMetadataSource.Function(function)
                    irParent?.let { parent = it }
                }
            }
            result
        }
        return irFunction
    }

    /**
     * Initializes IR value parameters for given [irFunction]
     */
    internal fun processValueParameters(function: FirFunction, irFunction: IrFunction, containingClass: IrClass?) {
        val forSetter = function is FirPropertyAccessor && function.isSetter
        val typeOrigin = if (forSetter) ConversionTypeOrigin.SETTER else ConversionTypeOrigin.DEFAULT
        when (function) {
            is FirDefaultPropertySetter -> {
                val valueParameter = function.valueParameters.first()
                val type = valueParameter.returnTypeRef.toIrType(typeConverter, ConversionTypeOrigin.SETTER)
                irFunction.declareDefaultSetterParameter(type, valueParameter)
            }

            else -> {
                val contextReceivers = function.contextReceiversForFunctionOrContainingProperty()

                irFunction.contextReceiverParametersCount = contextReceivers.size
                irFunction.valueParameters = buildList {
                    addContextReceiverParametersTo(contextReceivers, irFunction, this)

                    function.valueParameters.mapIndexedTo(this) { index, valueParameter ->
                        createIrValueParameter(
                            valueParameter,
                            index = index + irFunction.contextReceiverParametersCount,
                            typeOrigin
                        ).apply {
                            this.parent = irFunction
                        }
                    }
                }
            }
        }

        val functionOrigin = IrDeclarationOrigin.DEFINED
        when (function) {
            is FirConstructor -> {
                // Set dispatch receiver parameter for inner class's constructor.
                val outerClass = containingClass?.parentClassOrNull
                if (containingClass?.isInner == true && outerClass != null) {
                    irFunction.dispatchReceiverParameter = irFunction.declareThisReceiverParameter(
                        thisType = outerClass.thisReceiver!!.type,
                        thisOrigin = functionOrigin
                    )
                }
            }
            else -> {
                val receiverParameter = when (function) {
                    is FirPropertyAccessor -> function.propertySymbol.receiverParameter
                    else -> function.receiverParameter
                }
                if (receiverParameter != null) {
                    irFunction.extensionReceiverParameter = receiverParameter.convertWithOffsets { startOffset, endOffset ->
                        val name = (function as? FirAnonymousFunction)?.label?.name?.let {
                            val suffix = it.takeIf(Name::isValidIdentifier) ?: "\$receiver"
                            Name.identifier("\$this\$$suffix")
                        } ?: SpecialNames.THIS
                        irFunction.declareThisReceiverParameter(
                            thisType = receiverParameter.typeRef.toIrType(this.typeConverter, typeOrigin),
                            thisOrigin = functionOrigin,
                            startOffset = startOffset,
                            endOffset = endOffset,
                            name = name,
                            explicitReceiver = receiverParameter,
                        )
                    }
                }
                // See [LocalDeclarationsLowering]: "local function must not have dispatch receiver."
                val isLocal = function is FirSimpleFunction && function.isLocal
                if (function !is FirAnonymousFunction && containingClass != null && !function.isStatic && !isLocal) {
                    irFunction.dispatchReceiverParameter = irFunction.declareThisReceiverParameter(
                        thisType = containingClass.thisReceiver?.type ?: error("No this receiver"),
                        thisOrigin = functionOrigin
                    )
                }
            }
        }
    }

    private fun createIrLazyFunction(
        fir: FirSimpleFunction,
        signature: IdSignature,
        lazyParent: IrDeclarationParent,
        declarationOrigin: IrDeclarationOrigin,
    ): IrSimpleFunction {
        val symbol = symbolTable.table.referenceSimpleFunction(signature)
        val irFunction = fir.convertWithOffsets { startOffset, endOffset ->
            symbolTable.table.declareSimpleFunction(signature, { symbol }) {
                val isFakeOverride = fir.isSubstitutionOrIntersectionOverride
                Fir2IrLazySimpleFunction(
                    components, startOffset, endOffset, declarationOrigin,
                    fir, (lazyParent as? Fir2IrLazyClass)?.fir, symbol, isFakeOverride
                ).apply {
                    this.parent = lazyParent
                }
            }
        }
        // NB: this is needed to prevent recursions in case of self bounds
        // TODO: extract method to here, use scoped/global type paremeters for members/classes
        (irFunction as Fir2IrLazySimpleFunction).prepareTypeParameters()
        return irFunction
    }

    private fun <T : IrFunction> T.declareDefaultSetterParameter(type: IrType, firValueParameter: FirValueParameter?): T {
        valueParameters = listOf(
            createDefaultSetterParameter(startOffset, endOffset, type, parent = this, firValueParameter)
        )
        return this
    }

    internal fun createIrValueParameter(
        valueParameter: FirValueParameter,
        index: Int = UNDEFINED_PARAMETER_INDEX,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT,
    ): IrValueParameter = convertCatching(valueParameter) {
        val origin = valueParameter.computeIrOrigin()
        val type = valueParameter.returnTypeRef.toIrType(typeOrigin)
        val irParameter = valueParameter.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareValueParameter(valueParameter.symbol) {
                irFactory.createValueParameter(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = valueParameter.name,
                    type = type,
                    isAssignable = false,
                    symbol = IrValueParameterSymbolImpl(),
                    index = index,
                    varargElementType =
                    if (!valueParameter.isVararg) null
                    else valueParameter.returnTypeRef.coneType.arrayElementType()?.toIrType(typeOrigin),
                    isCrossinline = valueParameter.isCrossinline,
                    isNoinline = valueParameter.isNoinline,
                    isHidden = false,
                )
            }
        }
        return irParameter
    }

    internal fun createDefaultSetterParameter(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        parent: IrFunction,
        firValueParameter: FirValueParameter?,
        name: Name? = null,
        isCrossinline: Boolean = false,
        isNoinline: Boolean = false,
    ): IrValueParameter {
        return irFactory.createValueParameter(
            startOffset = startOffset,
            endOffset = endOffset,
            origin = IrDeclarationOrigin.DEFINED,
            name = name ?: SpecialNames.IMPLICIT_SET_PARAMETER,
            type = type,
            isAssignable = false,
            symbol = IrValueParameterSymbolImpl(),
            index = parent.contextReceiverParametersCount,
            varargElementType = null,
            isCrossinline = isCrossinline,
            isNoinline = isNoinline,
            isHidden = false,
        ).apply {
            this.parent = parent
            if (firValueParameter != null) {
                // TODO: where annotations should be generated?
                annotationGenerator.generate(this, firValueParameter)
            }
        }
    }

    fun addContextReceiverParametersTo(
        contextReceivers: List<FirContextReceiver>,
        parent: IrFunction,
        result: MutableList<IrValueParameter>,
    ) {
        contextReceivers.mapIndexedTo(result) { index, contextReceiver ->
            createIrParameterFromContextReceiver(contextReceiver, index).apply {
                this.parent = parent
            }
        }
    }

    private fun createIrParameterFromContextReceiver(
        contextReceiver: FirContextReceiver,
        index: Int,
    ): IrValueParameter = convertCatching(contextReceiver) {
        val type = contextReceiver.typeRef.toIrType()
        return contextReceiver.convertWithOffsets { startOffset, endOffset ->
            irFactory.createValueParameter(
                startOffset = startOffset,
                endOffset = endOffset,
                origin = IrDeclarationOrigin.DEFINED,
                name = NameUtils.contextReceiverName(index),
                type = type,
                isAssignable = false,
                symbol = IrValueParameterSymbolImpl(),
                index = index,
                varargElementType = null,
                isCrossinline = false,
                isNoinline = false,
                isHidden = false,
            )
        }
    }

    fun createIrConstructor(
        constructor: FirConstructor,
        irParent: IrClass,
        predefinedOrigin: IrDeclarationOrigin? = null,
        isLocal: Boolean = false,
    ): IrConstructor = convertCatching(constructor) {
        val origin = constructor.computeIrOrigin(predefinedOrigin)
        val isPrimary = constructor.isPrimary
        val signature =
            runUnless(isLocal || !configuration.linkViaSignatures) {
                signatureComposer.composeSignature(constructor)
            }
        val visibility = if (irParent.isAnonymousObject) Visibilities.Public else constructor.visibility
        return constructor.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareConstructor(constructor.symbol, signature) { symbol ->
                classifierStorage.preCacheTypeParameters(constructor, symbol)
                irFactory.createConstructor(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = SpecialNames.INIT,
                    visibility = visibility.toDescriptorVisibility(),
                    isInline = false,
                    isExpect = constructor.isExpect,
                    returnType = constructor.returnTypeRef.toIrType(),
                    symbol = symbol,
                    isPrimary = isPrimary,
                    isExternal = false,
                ).apply {
                    metadata = FirMetadataSource.Function(constructor)
                }
            }
        }
    }

    fun createIrProperty(
        property: FirProperty,
        irParent: IrDeclarationParent,
        predefinedOrigin: IrDeclarationOrigin? = null
    ): IrProperty = convertCatching(property) {
        val origin =
            if (property.isStatic && property.name in Fir2IrDeclarationStorage.ENUM_SYNTHETIC_NAMES) IrDeclarationOrigin.ENUM_CLASS_SPECIAL_MEMBER
            else property.computeIrOrigin(predefinedOrigin)
        val signature = signatureComposer.composeSignature(property)
        return property.convertWithOffsets { startOffset, endOffset ->
            val result = symbolTable.declareProperty(property.symbol, signature) { symbol ->
                irFactory.createProperty(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = property.name,
                    visibility = property.visibility.toDescriptorVisibility(),
                    modality = property.modality!!,
                    symbol = symbol,
                    isVar = property.isVar,
                    isConst = property.isConst,
                    isLateinit = property.isLateInit,
                    isDelegated = property.delegate != null,
                    isExternal = property.isExternal,
                    containerSource = property.containerSource,
                    isExpect = property.isExpect,
                ).apply {
                    metadata = FirMetadataSource.Property(property)
                    parent = irParent
                }
            }
            result
        }
    }

    internal fun createBackingField(
        property: FirProperty,
        irProperty: IrProperty,
        fieldSymbol: FirVariableSymbol<*>,
        origin: IrDeclarationOrigin,
        name: Name,
        isFinal: Boolean,
        // TODO: maybe can remove?
        // firInitializerExpression: FirExpression?,
        // type: IrType? = null,
    ): IrField = convertCatching(property) {
        val inferredType = fieldSymbol.resolvedReturnType.toIrType() //type ?: firInitializerExpression!!.typeRef.toIrType()
        val signature = signatureComposer.composeSignature(fieldSymbol.fir)
        return symbolTable.declareField(fieldSymbol, signature) { symbol ->
            val visibility = property.fieldVisibility.toDescriptorVisibility()
            irFactory.createField(
                startOffset = irProperty.startOffset,
                endOffset = irProperty.endOffset,
                origin = origin,
                name = name,
                visibility = visibility,
                symbol = symbol,
                type = inferredType,
                isFinal = isFinal,
                isStatic = property.isStatic || !(irProperty.parent is IrClass || irProperty.parent is IrScript),
                isExternal = property.isExternal,
            ).apply {
                metadata = FirMetadataSource.Property(property)
                correspondingPropertySymbol = irProperty.symbol
                parent = irProperty.parent
                // TODO: annotations
                // convertAnnotationsForNonDeclaredMembers(property, origin)
            }
        }
    }

    internal fun createIrPropertyAccessor(
        propertyAccessor: FirPropertyAccessor,
        property: FirProperty,
        irProperty: IrProperty,
        isSetter: Boolean,
        origin: IrDeclarationOrigin,
    ): IrSimpleFunction = convertCatching(propertyAccessor) {
        val irParent = irProperty.parent
        val prefix = if (isSetter) "set" else "get"
        val signature = signatureComposer.composeAccessorSignature(property, isSetter)
        val containerSource = (irProperty as? IrProperty)?.containerSource
        return symbolTable.declareFunction(propertyAccessor.symbol, signature) { symbol ->
            val accessorReturnType = if (isSetter) irBuiltIns.unitType else property.returnTypeRef.toIrType()
            val visibility = property.visibility.toDescriptorVisibility()
            propertyAccessor.convertWithOffsets { startOffset, endOffset ->
                irFactory.createSimpleFunction(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    origin = origin,
                    name = Name.special("<$prefix-${irProperty.name}>"),
                    visibility = visibility,
                    isInline = propertyAccessor.isInline == true,
                    isExpect = false,
                    returnType = accessorReturnType,
                    modality = (irProperty as? IrOverridableMember)?.modality ?: Modality.FINAL,
                    symbol = symbol,
                    isTailrec = false,
                    isSuspend = false,
                    isOperator = false,
                    isInfix = false,
                    isExternal = propertyAccessor.isExternal == true,
                    containerSource = containerSource,
                ).apply {
                    parent = irParent
                    correspondingPropertySymbol = (irProperty as? IrProperty)?.symbol
                    metadata = FirMetadataSource.Function(propertyAccessor)
                    // Note that deserialized annotations are stored in the accessor, not the property.
                    // TODO: annotations
//                convertAnnotationsForNonDeclaredMembers(propertyAccessor, origin)

                    // TODO
//                if (propertyAccessorForAnnotations != null) {
//                    convertAnnotationsForNonDeclaredMembers(propertyAccessorForAnnotations, origin)
//                }
                }

            }
        }
    }

    // -------------------------------------------- Utilities --------------------------------------------

    private val FirProperty.fieldVisibility: Visibility
        get() = when {
            hasExplicitBackingField -> backingField?.visibility ?: status.visibility
            isLateInit -> setter?.visibility ?: status.visibility
            isConst -> status.visibility
            hasJvmFieldAnnotation(session) -> status.visibility
            else -> Visibilities.Private
        }
}

inline fun <R> convertCatching(element: FirElement, block: () -> R): R {
    try {
        return block()
    } catch (e: Throwable) {
        throw KotlinExceptionWithAttachments("Exception was thrown during transformation of ${element.render()}", e)
    }
}
