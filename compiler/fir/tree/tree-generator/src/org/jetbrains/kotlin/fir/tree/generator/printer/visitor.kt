/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.tree.generator.printer

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.tree.generator.*
import org.jetbrains.kotlin.fir.tree.generator.context.AbstractFirTreeBuilder
import org.jetbrains.kotlin.fir.tree.generator.model.Element
import org.jetbrains.kotlin.fir.tree.generator.model.Field
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.printer.GeneratedFile
import org.jetbrains.kotlin.generators.tree.printer.printGeneratedType
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.SmartPrinter
import org.jetbrains.kotlin.utils.withIndent
import java.io.File

private open class VisitorPrinter(
    printer: SmartPrinter,
    override val visitorType: ClassRef<*>,
    visitSuperTypeByDefault: Boolean,
    private val uselessElementsForDefaultVisiting: Set<Element>
) : AbstractVisitorPrinter<Element, Field>(printer, visitSuperTypeByDefault) {
    override val visitorTypeParameters: List<TypeVariable>
        get() = listOf(resultTypeVariable, dataTypeVariable)

    override val visitorSuperType: ClassRef<PositionTypeParameterRef>? =
        firVisitorType.takeIf { visitSuperTypeByDefault }?.withArgs(resultTypeVariable, dataTypeVariable)

    override val visitorDataType: TypeRef
        get() = dataTypeVariable

    override fun visitMethodReturnType(element: Element): TypeRef = resultTypeVariable

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = true

    context(ImportCollector)
    override fun printMethodsForElement(element: Element) {
        val isInterface = element.kind?.isInterface == true
        val parentInVisitor = parentInVisitor(element)

        if (isInterface && !visitSuperTypeByDefault) return
        if (!isInterface && visitSuperTypeByDefault && parentInVisitor == AbstractFirTreeBuilder.baseFirAbstractElement) return
        if (visitSuperTypeByDefault && element in uselessElementsForDefaultVisiting) return

        val (modality, override) = when {
            element == AbstractFirTreeBuilder.baseFirAbstractElement -> Modality.ABSTRACT to false
            visitSuperTypeByDefault && !isInterface -> null to true
            else -> Modality.OPEN to false
        }

        printer.printVisitMethod(element, modality, override)
    }

    context(ImportCollector)
    protected open fun SmartPrinter.printVisitMethod(element: Element, modality: Modality?, override: Boolean) {
        printMethodDeclarationForElement(element, modality, override)
        val parentInVisitor = parentInVisitor(element)
        if (parentInVisitor != null) {
            println(" =")
            withIndent {
                print(
                    parentInVisitor.visitFunctionName,
                    "(",
                    element.visitorParameterName,
                    element.castToFirElementIfNeeded(),
                    ", data)"
                )
            }
        }
        println()
    }

    override fun parentInVisitor(element: Element): Element? = when {
        element == AbstractFirTreeBuilder.baseFirAbstractElement -> null
        visitSuperTypeByDefault -> element.parentInVisitor ?: AbstractFirTreeBuilder.baseFirAbstractElement
        else -> AbstractFirTreeBuilder.baseFirAbstractElement
    }

    context (ImportCollector)
    protected fun Element.castToFirElementIfNeeded(): String {
        val isInterface = kind?.isInterface == true
        return if (isInterface && parentInVisitor(element) == AbstractFirTreeBuilder.baseFirAbstractElement) {
            " as ${baseAbstractElementType.render()}"
        } else {
            ""
        }
    }
}

fun printVisitor(elements: List<Element>, generationPath: File, visitSuperTypeByDefault: Boolean) =
    printVisitorCommon(
        elements,
        generationPath,
        if (visitSuperTypeByDefault) firDefaultVisitorType else firVisitorType,
    ) { printer, visitorType ->
        VisitorPrinter(printer, visitorType, visitSuperTypeByDefault, getUselessElementsForDefaultVisiting(elements))
    }

private class VisitorVoidPrinter(
    printer: SmartPrinter,
    override val visitorType: ClassRef<*>,
) : AbstractVisitorVoidPrinter<Element, Field>(printer, visitSuperTypeByDefault = false) {

    override val visitorSuperClass: ClassRef<PositionTypeParameterRef>
        get() = firVisitorType

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = true

    override val useAbstractMethodForRootElement: Boolean
        get() = true

    override val overriddenVisitMethodsAreFinal: Boolean
        get() = true

    override val allowVisitInterfaces: Boolean
        get() = false

    override fun parentInVisitor(element: Element): Element = AbstractFirTreeBuilder.baseFirAbstractElement
}

fun printVisitorVoid(elements: List<Element>, generationPath: File) =
    printVisitorCommon(elements, generationPath, firVisitorVoidType, ::VisitorVoidPrinter)

private class DefaultVisitorVoidPrinter(
    printer: SmartPrinter,
    override val visitorType: ClassRef<*>,
    val uselessElementsForDefaultVisiting: Set<Element>
) : VisitorPrinter(printer, visitorType, true, uselessElementsForDefaultVisiting) {
    override val visitorTypeParameters: List<TypeVariable>
        get() = emptyList()

    override val visitorDataType: TypeRef
        get() = StandardTypes.nothing.copy(nullable = true)

    override fun visitMethodReturnType(element: Element) = StandardTypes.unit

    override val visitorSuperType: ClassRef<PositionTypeParameterRef>
        get() = firVisitorVoidType

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = true


    context(ImportCollector) override fun SmartPrinter.printVisitMethod(element: Element, modality: Modality?, override: Boolean) {
        printVisitMethodDeclaration(
            element,
            hasDataParameter = false,
            modality = modality,
            override = override,
        )
        val parentInVisitor = parentInVisitor(element)
        if (parentInVisitor != null) {
            println(" = ", parentInVisitor.visitFunctionName, "(", element.visitorParameterName, element.castToFirElementIfNeeded(), ")")
        }
        println()
    }
}

fun printDefaultVisitorVoid(elements: List<Element>, generationPath: File) =
    printVisitorCommon(elements, generationPath, firDefaultVisitorVoidType) { printer, visitorType ->
        DefaultVisitorVoidPrinter(printer, visitorType, getUselessElementsForDefaultVisiting(elements))
    }

private fun printVisitorCommon(
    elements: List<Element>,
    generationPath: File,
    visitorType: ClassRef<*>,
    makePrinter: (SmartPrinter, ClassRef<*>) -> AbstractVisitorPrinter<Element, Field>,
): GeneratedFile =
    printGeneratedType(generationPath, TREE_GENERATOR_README, visitorType.packageName, visitorType.simpleName) {
        makePrinter(this, visitorType).printVisitor(elements)
    }

private fun getUselessElementsForDefaultVisiting(elements: List<Element>): Set<Element> {
    val neighbors = DFS.Neighbors<Element> { element ->
        element.parentInVisitor?.let { listOf(it) } ?: emptyList()
    }
    val visited = object: DFS.Visited<Element> {
        val visitedElements = mutableSetOf<Element>()

        override fun checkAndMarkVisited(current: Element): Boolean {
            return visitedElements.add(current)
        }
    }
    val dummyHandler = object : DFS.AbstractNodeHandler<Element, Unit>() {
        override fun result() {}
    }

    val notInterfaceElements = elements.filter { it.kind?.isInterface != true }
    val interfaceElements = elements.filter { it.kind?.isInterface == true }
    DFS.dfs(notInterfaceElements, neighbors, visited, dummyHandler)

    return interfaceElements.toSet() - visited.visitedElements
}