/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.passes

import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.builder.buildEnum
import org.jetbrains.kotlin.sir.builder.buildModule
import org.jetbrains.kotlin.sir.visitors.SirTransformer

class SirInflatePackagesPass : SirPass<SirModule, Unit, SirModule>, SirTransformer<SirInflatePackagesPass.Context>() {
    class Context(val root: Namespace = Namespace(name = ""))

    data class Namespace(
        val name: String,
        val elements: MutableList<SirDeclaration> = mutableListOf(),
        val children: MutableMap<String, Namespace> = mutableMapOf(),
    ) {
        fun <R> reduce(transform: (List<String>, List<SirDeclaration>, List<R>) -> R): R {
            fun reduceFrom(
                node: Namespace,
                rootPath: List<String> = emptyList(),
                transform: (List<String>, List<SirDeclaration>, List<R>) -> R,
            ): R =
                transform(rootPath + node.name, node.elements, node.children.map { reduceFrom(it.value, rootPath + node.name, transform) })
            return reduceFrom(this, emptyList(), transform)
        }

        fun makePath(path: List<String>): Namespace {
            if (path.isEmpty()) {
                return this
            }

            val key = path.first()
            val next = children.getOrPut(key) { Namespace(key) }
            return next.makePath(path.drop(1))
        }
    }

    override fun run(element: SirModule, data: Unit): SirModule = element.transform(this, Context())

    override fun <E : SirElement> transformElement(element: E, data: Context): E = element

    override fun transformModule(module: SirModule, data: Context): SirModule = buildModule {
        name = module.name

        for (declaration in module.declarations) {
            if (declaration is SirForeignDeclaration) {
                val origin = declaration.origin
                if (origin is SirOrigin.KotlinEntity) {
                    // FIXME: for now we assume everything before the last dot is a package name.
                    //  This should change as we add type declarations into the mix
                    val path = origin.path.dropLast(1)
                    data.root.makePath(path).elements.add(declaration)
                    continue
                }
            }

            declarations += declaration
        }

        val additionals = data.root.reduce { path, declarations, children ->
            buildEnum {
                name = path.last()
                this.declarations += children
                this.declarations += declarations
            }
        }

        declarations += additionals.declarations
    }
}