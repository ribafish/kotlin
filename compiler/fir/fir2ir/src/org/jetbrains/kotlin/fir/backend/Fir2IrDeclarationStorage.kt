/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.expressions.FirComponentCall
import org.jetbrains.kotlin.fir.expressions.impl.FirExpressionStub
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.arrayElementType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.UNDEFINED_PARAMETER_INDEX
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBodyKind
import org.jetbrains.kotlin.ir.expressions.impl.IrErrorExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.threadLocal
import java.util.concurrent.ConcurrentHashMap

// TODO: to remove
class Fir2IrDeclarationStorage(
    private val components: Fir2IrComponents,
    commonMemberStorage: Fir2IrCommonMemberStorage,
) : Fir2IrComponents by components {


    private val scriptCache: ConcurrentHashMap<FirScript, IrScript> = ConcurrentHashMap()

    private val functionCache: ConcurrentHashMap<FirFunction, IrSimpleFunction> = commonMemberStorage.functionCache


    private val propertyCache: ConcurrentHashMap<FirProperty, IrProperty> = commonMemberStorage.propertyCache


    sealed class FakeOverrideKey {
        data class Signature(val signature: IdSignature) : FakeOverrideKey()

        /*
         * Used for declarations which don't have id signature (e.g. members of local classes)
         */
        data class Declaration(val declaration: FirCallableDeclaration) : FakeOverrideKey()
    }

    // For pure fields (from Java) only
    private val fieldToPropertyCache: ConcurrentHashMap<Pair<FirField, IrDeclarationParent>, IrProperty> = ConcurrentHashMap()

    private val delegatedReverseCache: ConcurrentHashMap<IrDeclaration, FirDeclaration> = ConcurrentHashMap()


    private val localStorage: Fir2IrLocalCallableStorage by threadLocal { Fir2IrLocalCallableStorage() }


    fun enterScope(declaration: IrDeclaration) {
        symbolTable.enterScope(declaration)
        if (declaration is IrSimpleFunction ||
            declaration is IrConstructor ||
            declaration is IrAnonymousInitializer ||
            declaration is IrProperty ||
            declaration is IrEnumEntry ||
            declaration is IrScript
        ) {
            localStorage.enterCallable()
        }
    }

    fun leaveScope(declaration: IrDeclaration) {
        if (declaration is IrSimpleFunction ||
            declaration is IrConstructor ||
            declaration is IrAnonymousInitializer ||
            declaration is IrProperty ||
            declaration is IrEnumEntry ||
            declaration is IrScript
        ) {
            localStorage.leaveCallable()
        }
        symbolTable.leaveScope(declaration)
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

    internal fun cacheDelegationFunction(function: FirSimpleFunction, irFunction: IrSimpleFunction) {
        functionCache[function] = irFunction
        delegatedReverseCache[irFunction] = function
    }

    fun createIrFunction(
        function: FirFunction,
        irParent: IrDeclarationParent?,
        thisReceiverOwner: IrClass? = irParent as? IrClass,
        predefinedOrigin: IrDeclarationOrigin? = null,
        isLocal: Boolean = false,
        fakeOverrideOwnerLookupTag: ConeClassLikeLookupTag? = null,
    ): IrSimpleFunction = convertCatching(function) {
        TODO()
    }

    fun getOrCreateIrPropertyByPureField(
        field: FirField,
        irParent: IrDeclarationParent,
    ): IrProperty {
        return fieldToPropertyCache.getOrPut(field to irParent) {
            val containingClassId = (irParent as? IrClass)?.classId
            createIrProperty(
                field.toStubProperty(),
                irParent,
                fakeOverrideOwnerLookupTag = containingClassId?.toLookupTag()
            )
        }
    }

    private fun FirField.toStubProperty(): FirProperty {
        val field = this
        return buildProperty {
            source = field.source
            moduleData = field.moduleData
            origin = field.origin
            returnTypeRef = field.returnTypeRef
            name = field.name
            isVar = field.isVar
            getter = field.getter
            setter = field.setter
            symbol = FirPropertySymbol(field.symbol.callableId)
            isLocal = false
            status = field.status
        }.apply {
            isStubPropertyForPureField = true
        }
    }

    private object IsStubPropertyForPureFieldKey : FirDeclarationDataKey()

    private var FirProperty.isStubPropertyForPureField: Boolean? by FirDeclarationDataRegistry.data(IsStubPropertyForPureFieldKey)

    fun createIrProperty(
        property: FirProperty,
        irParent: IrDeclarationParent?,
        thisReceiverOwner: IrClass? = irParent as? IrClass,
        predefinedOrigin: IrDeclarationOrigin? = null,
        isLocal: Boolean = false,
        fakeOverrideOwnerLookupTag: ConeClassLikeLookupTag? = (irParent as? IrClass)?.classId?.toLookupTag(),
    ): IrProperty = convertCatching(property) {
        TODO()
    }


    internal fun cacheDelegatedProperty(property: FirProperty, irProperty: IrProperty) {
        propertyCache[property] = irProperty
        delegatedReverseCache[irProperty] = property
    }


    internal fun createIrParameter(
        valueParameter: FirValueParameter,
        index: Int = UNDEFINED_PARAMETER_INDEX,
        useStubForDefaultValueStub: Boolean = true,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT,
        skipDefaultParameter: Boolean = false,
    ): IrValueParameter = convertCatching(valueParameter) {
        val origin = valueParameter.computeIrOrigin()
        val type = valueParameter.returnTypeRef.toIrType(typeOrigin)
        val irParameter = valueParameter.convertWithOffsets { startOffset, endOffset ->
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
            ).apply {
                if (!skipDefaultParameter && valueParameter.defaultValue.let {
                        it != null && (useStubForDefaultValueStub || it !is FirExpressionStub)
                    }
                ) {
                    this.defaultValue = factory.createExpressionBody(
                        IrErrorExpressionImpl(
                            UNDEFINED_OFFSET, UNDEFINED_OFFSET, type,
                            "Stub expression for default value of ${valueParameter.name}"
                        )
                    )
                }
                annotationGenerator.generate(this, valueParameter)
            }
        }
        localStorage.putParameter(valueParameter, irParameter)
        return irParameter
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

    // TODO: KT-58686
    private var lastTemporaryIndex: Int = 0
    private fun nextTemporaryIndex(): Int = lastTemporaryIndex++

    private fun getNameForTemporary(nameHint: String?): String {
        val index = nextTemporaryIndex()
        return if (nameHint != null) "tmp${index}_$nameHint" else "tmp$index"
    }

    private fun declareIrVariable(
        startOffset: Int, endOffset: Int,
        origin: IrDeclarationOrigin, name: Name, type: IrType,
        isVar: Boolean, isConst: Boolean, isLateinit: Boolean,
    ): IrVariable =
        IrVariableImpl(
            startOffset, endOffset, origin, IrVariableSymbolImpl(), name, type,
            isVar, isConst, isLateinit
        )

    fun createIrVariable(
        variable: FirVariable,
        irParent: IrDeclarationParent,
        givenOrigin: IrDeclarationOrigin? = null,
    ): IrVariable = convertCatching(variable) {
        // Note: for components call, we have to change type here (to original component type) to keep compatibility with PSI2IR
        // Some backend optimizations related to withIndex() probably depend on this type: index should always be Int
        // See e.g. forInStringWithIndexWithExplicitlyTypedIndexVariable.kt from codegen box tests
        val type = ((variable.initializer as? FirComponentCall)?.typeRef ?: variable.returnTypeRef).toIrType()
        // Some temporary variables are produced in RawFirBuilder, but we consistently use special names for them.
        val origin = when {
            givenOrigin != null -> givenOrigin
            variable.name == SpecialNames.ITERATOR -> IrDeclarationOrigin.FOR_LOOP_ITERATOR
            variable.name.isSpecial -> IrDeclarationOrigin.IR_TEMPORARY_VARIABLE
            else -> IrDeclarationOrigin.DEFINED
        }
        val isLateInit = if (variable is FirProperty) variable.isLateInit else false
        val irVariable = variable.convertWithOffsets { startOffset, endOffset ->
            symbolTable.declareVariable(
                startOffset, endOffset, origin, variable.symbol,
                variable.name, type, variable.isVar, isConst = false, isLateinit = isLateInit
            )
        }
        irVariable.parent = irParent
        return irVariable
    }

    fun declareTemporaryVariable(base: IrExpression, nameHint: String? = null): IrVariable {
        return declareIrVariable(
            base.startOffset, base.endOffset, IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            Name.identifier(getNameForTemporary(nameHint)), base.type,
            isVar = false, isConst = false, isLateinit = false
        ).apply {
            initializer = base
        }
    }

    fun getCachedIrScript(script: FirScript): IrScript? = scriptCache[script]

    companion object {
        internal val ENUM_SYNTHETIC_NAMES = mapOf(
            Name.identifier("values") to IrSyntheticBodyKind.ENUM_VALUES,
            Name.identifier("valueOf") to IrSyntheticBodyKind.ENUM_VALUEOF,
            Name.identifier("entries") to IrSyntheticBodyKind.ENUM_ENTRIES
        )
    }

    private inline fun <R> convertCatching(element: FirElement, block: () -> R): R {
        try {
            return block()
        } catch (e: Exception) {
            errorWithAttachment("Exception was thrown during transformation of ${element::class.java}", cause = e) {
                withFirEntry("element", element)
            }
        }
    }
}
