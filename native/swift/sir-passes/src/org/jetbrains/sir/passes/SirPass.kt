/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.passes

import org.jetbrains.kotlin.sir.SirElement

/**
 * Swift IR is supposed to be transformed by a series of passes.
 * This is a base interface that all such passes should implement.
 */
interface SirPass<in E : SirElement, in T, out R> {

    /**
     * Executes the pass over the given [SirElement].
     *
     * @param element SIR element to be processed.
     * @param data Additional data that is required to run the pass.
     * @return The result of the pass.
     */
    fun run(element: E, data: T): R
}

fun <E : SirElement, R> SirPass<E, Unit, R>.run(element: E) = this.run(element, Unit)