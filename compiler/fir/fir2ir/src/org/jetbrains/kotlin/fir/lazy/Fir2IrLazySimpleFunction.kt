/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lazy

import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.fir.backend.contextReceiversForFunctionOrContainingProperty
import org.jetbrains.kotlin.fir.backend.generateOverriddenFunctionSymbols
import org.jetbrains.kotlin.fir.backend.toIrType
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.initialSignatureAttr
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.lazy.lazyVar
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.withScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

class Fir2IrLazySimpleFunction(
    components: Fir2IrComponents,
    startOffset: Int,
    endOffset: Int,
    origin: IrDeclarationOrigin,
    override val fir: FirSimpleFunction,
    firParent: FirRegularClass?,
    symbol: IrSimpleFunctionSymbol,
    isFakeOverride: Boolean,
    parent: IrDeclarationParent
) : AbstractFir2IrLazyFunction<FirSimpleFunction>(components, startOffset, endOffset, origin, symbol, isFakeOverride) {
    init {
        symbol.bind(this)
        this.parent = parent
    }

    override var annotations: List<IrConstructorCall> by createLazyAnnotations()

    override var name: Name
        get() = fir.name
        set(_) = mutationNotSupported()

    override var returnType: IrType = run {
        fir.returnTypeRef.toIrType(typeConverter)
    }

    override var dispatchReceiverParameter: IrValueParameter? = run {
        val containingClass = parent as? IrClass
        if (containingClass != null && shouldHaveDispatchReceiver(containingClass)) {
            createThisReceiverParameter(thisType = containingClass.thisReceiver?.type ?: error("No this receiver for containing class"))
        } else null
    }

    override var extensionReceiverParameter: IrValueParameter? = run {
        fir.receiverParameter?.let {
            createThisReceiverParameter(it.typeRef.toIrType(typeConverter), it)
        }
    }

    override var contextReceiverParametersCount: Int = fir.contextReceiversForFunctionOrContainingProperty().size

    override var valueParameters: List<IrValueParameter> = run {
        symbolTable.withScope(this) {
            buildList {
                callablesGenerator.addContextReceiverParametersTo(
                    fir.contextReceiversForFunctionOrContainingProperty(),
                    this@Fir2IrLazySimpleFunction,
                    this@buildList,
                )

                fir.valueParameters.mapIndexedTo(this) { index, valueParameter ->
                    callablesGenerator.createIrValueParameter(
                        valueParameter,
                        index + contextReceiverParametersCount,
                        // skipDefaultParameter = isFakeOverride // TODO check if this really needed
                    ).apply {
                        this.parent = this@Fir2IrLazySimpleFunction
                    }
                }
            }
        }
    }

    override var overriddenSymbols: List<IrSimpleFunctionSymbol> = run lazyVar@{
        // TODO: seems like it's not needed, because overridden symbols are set
        //    during creating of declarations list of the parent class
    //        if (firParent == null) return@lazyVar emptyList()
//        val parent = parent
//        if (isFakeOverride && parent is Fir2IrLazyClass) {
//            fakeOverrideGenerator.calcBaseSymbolsForFakeOverrideFunction(
//                firParent, this, fir.symbol
//            )
//            fakeOverrideGenerator.getOverriddenSymbolsForFakeOverride(this)?.let {
//                assert(!it.contains(symbol)) { "Cannot add function $symbol to its own overriddenSymbols" }
//                return@lazyVar it
//            }
//        }
//        fir.generateOverriddenFunctionSymbols(firParent)
        emptyList()
    }

    override val initialSignatureFunction: IrFunction? by lazy {
        val initialFirFunction = fir.initialSignatureAttr as? FirFunction ?: return@lazy null
        val signature = signatureComposer.composeSignature(initialFirFunction)
        val initialIrSymbol = symbolTable.declareFunctionIfNotExists(initialFirFunction.symbol, signature) {
            conversionScope.withParent(this.parent) {
                declarationsConverter.generateIrFunction(initialFirFunction)
            }
        }
        initialIrSymbol
    }

    override val containerSource: DeserializedContainerSource?
        get() = fir.containerSource
}
