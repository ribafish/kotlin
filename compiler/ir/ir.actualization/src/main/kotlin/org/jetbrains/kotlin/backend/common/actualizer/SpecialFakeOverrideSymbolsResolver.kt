/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.actualizer

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFakeOverrideSymbolBase
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.utils.addToStdlib.shouldNotBeCalled


class SpecialFakeOverrideSymbolsResolver(private val expectActualMap: Map<IrSymbol, IrSymbol>) : IrElementVisitorVoid {
    private val cachedFakeOverrides = mutableMapOf<Pair<IrClassSymbol, IrSymbol>, IrSymbol>()
    private val _remappedSymbols = mutableMapOf<IrSymbol, IrSymbol>()
    val remappedSymbols: Map<IrSymbol, IrSymbol>
        get() = _remappedSymbols

    private val processedClasses = mutableSetOf<IrClass>()

    private fun IrOverridableDeclaration<*>.collectOverrides(visited: MutableSet<IrSymbol>): Sequence<IrSymbol> = sequence {
        if (visited.add(symbol)) {
            if (!isFakeOverride) {
                yield(symbol)
            }
            for (overridden in overriddenSymbols) {
                yieldAll((overridden.owner as IrOverridableDeclaration<*>).collectOverrides(visited))
            }
        }
    }

    private fun processDeclaration(classSymbol: IrClassSymbol, declaration: IrOverridableDeclaration<*>) {
        for (overridden in declaration.collectOverrides(mutableSetOf())) {
            cachedFakeOverrides[classSymbol to overridden] = declaration.symbol
        }
    }

    private fun processClass(irClass: IrClass) {
        require(!irClass.isExpect) { "There should be no references to expect classes at this point" }
        if (!processedClasses.add(irClass)) return
        for (declaration in irClass.declarations) {
            if (declaration !is IrOverridableDeclaration<*>) continue
            processDeclaration(irClass.symbol, declaration)
            if (declaration is IrProperty) {
                declaration.getter?.let { processDeclaration(irClass.symbol, it) }
                declaration.setter?.let { processDeclaration(irClass.symbol, it) }
            }
        }
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        declaration.overriddenSymbols = declaration.overriddenSymbols.map { it.remap() }
        declaration.acceptChildrenVoid(this)
    }

    override fun visitProperty(declaration: IrProperty) {
        declaration.overriddenSymbols = declaration.overriddenSymbols.map { it.remap() }
        declaration.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        expression.symbol = expression.symbol.remap()
        expression.acceptChildrenVoid(this)
    }

    override fun visitFunctionReference(expression: IrFunctionReference) {
        expression.symbol = expression.symbol.remap()
        expression.reflectionTarget = expression.reflectionTarget?.remap()
        expression.acceptChildrenVoid(this)
    }

    override fun visitPropertyReference(expression: IrPropertyReference) {
        expression.symbol = expression.symbol.remap()
        expression.getter = expression.getter?.remap()
        expression.setter = expression.setter?.remap()
        expression.acceptChildrenVoid(this)
    }

    override fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference) {
        expression.getter = expression.getter.remap()
        expression.setter = expression.setter?.remap()
        expression.acceptChildrenVoid(this)
    }

    private inline fun <reified S : IrSymbol> S.remap(): S = if (this is IrFakeOverrideSymbolBase<*,*,*>) {
        val actualizedClassSymbol = containingClassSymbol.actualize()
        val actualizedOriginalSymbol = originalSymbol.actualize()
        processClass(actualizedClassSymbol.owner)
        val result = cachedFakeOverrides[actualizedClassSymbol to actualizedOriginalSymbol]
        if (result !is S) {
            shouldNotBeCalled("No override for ${actualizedOriginalSymbol} in ${actualizedClassSymbol}")
        }
        _remappedSymbols[this] = result
        result
    } else {
        this
    }

    private fun IrClassSymbol.actualize() = (this as IrSymbol).actualize() as IrClassSymbol
    private fun IrSymbol.actualize() = expectActualMap[this] ?: this
}
