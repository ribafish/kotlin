/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.passes

import org.jetbrains.kotlin.sir.SirElement
import org.jetbrains.kotlin.sir.SirModule
import org.jetbrains.kotlin.sir.builder.buildModule
import org.jetbrains.sir.passes.SirPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SirPassTests {
    @Test
    fun smoke() {
        val moduleName = "Root"
        val module = buildModule { name = moduleName }

        val myPass = object : SirPass<SirElement, Unit, String> {
            override fun run(element: SirElement, data: Unit): String {
                assertIs<SirModule>(element)
                return element.name
            }
        }

        val result = myPass.run(module, Unit)
        assertEquals(moduleName, result)
    }
}
