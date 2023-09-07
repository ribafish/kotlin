/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.tree.generator.printer

import org.jetbrains.kotlin.fir.tree.generator.context.AbstractFirTreeBuilder
import org.jetbrains.kotlin.fir.tree.generator.model.Element
import org.jetbrains.kotlin.fir.tree.generator.model.Field
import org.jetbrains.kotlin.fir.tree.generator.pureAbstractElementType
import org.jetbrains.kotlin.fir.tree.generator.util.get
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.utils.SmartPrinter
import org.jetbrains.kotlin.utils.withIndent
import java.io.File

private class ElementPrinter(printer: SmartPrinter) : AbstractElementPrinter<Element, Field>(printer) {

    override val fieldPrinter = FieldPrinter(printer)

    private val Element.isInterface: Boolean
        get() = kind?.isInterface ?: false

    override fun pureAbstractElementType(element: Element): String? {
        val needPureAbstractElement =
            !element.isInterface && !element.allParents.any { it.kind == ImplementationKind.AbstractClass || it.kind == ImplementationKind.SealedClass }
        return if (needPureAbstractElement) pureAbstractElementType.type else null
    }

    override fun multipleUpperBoundsList(element: Element): String = element.multipleUpperBoundsList()

    override fun SmartPrinter.printAdditionalMethods(element: Element) {
        with(element) {
            fun abstract() {
                if (!element.isInterface) {
                    print("abstract ")
                }
            }

            fun override() {
                if (this != AbstractFirTreeBuilder.baseFirElement) {
                    print("override ")
                }
            }

            override()
            println("fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visit$name(this, data)")

            println()
            println("@Suppress(\"UNCHECKED_CAST\")")
            override()
            println("fun <E : FirElement, D> transform(transformer: FirTransformer<D>, data: D): E =")
            withIndent {
                println("transformer.transform$name(this, data) as E")
            }

            fun Field.replaceDeclaration(override: Boolean, overridenType: Importable? = null, forceNullable: Boolean = false) {
                println()
                if (name == "source") {
                    println("@FirImplementationDetail")
                }
                abstract()
                if (override) print("override ")
                println(replaceFunctionDeclaration(overridenType, forceNullable))
            }

            allFields.filter { it.withReplace }.forEach {
                val override = overridenFields[it, it] &&
                        !(it.name == "source" && fullQualifiedName.endsWith("FirQualifiedAccessExpression"))
                it.replaceDeclaration(override, forceNullable = it.useNullableForReplace)
                for (overridenType in it.overridenTypes) {
                    it.replaceDeclaration(true, overridenType)
                }
            }

            for (field in allFields) {
                if (!field.needsSeparateTransform) continue
                println()
                abstract()
                if (field.fromParent && field.parentHasSeparateTransform) {
                    print("override ")
                }
                println(field.transformFunctionDeclaration(typeWithArguments))
            }
            if (needTransformOtherChildren) {
                println()
                abstract()
                if (element.parents.any { it.needTransformOtherChildren }) {
                    print("override ")
                }
                println(transformFunctionDeclaration("OtherChildren", typeWithArguments))
            }

            if (element == AbstractFirTreeBuilder.baseFirElement) {
                require(isInterface)
                println()
                println("fun accept(visitor: FirVisitorVoid) = accept(visitor, null)")
                println()
                println("fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D)")
                println()
                println("fun acceptChildren(visitor: FirVisitorVoid) = acceptChildren(visitor, null)")
                println()
                println("fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement")
            }
        }
    }
}

fun Element.generateCode(generationPath: File): GeneratedFile {
    val dir = generationPath.resolve(packageName.replace(".", "/"))
    val file = File(dir, "$type.kt")
    val stringBuilder = StringBuilder()
    SmartPrinter(stringBuilder).apply {
        printCopyright()
        println("package $packageName")
        println()
        val imports = collectImports()
        imports.forEach { println("import $it") }
        if (imports.isNotEmpty()) {
            println()
        }
        printGeneratedMessage()
        printElement(this@generateCode)
    }
    return GeneratedFile(file, stringBuilder.toString())
}

fun SmartPrinter.printElement(element: Element) {
    ElementPrinter(this).printElement(element)
}
