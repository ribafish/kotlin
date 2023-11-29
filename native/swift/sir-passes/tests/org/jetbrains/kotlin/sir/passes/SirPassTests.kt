/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.passes

import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.builder.buildForeignFunction
import org.jetbrains.kotlin.sir.util.SirSwiftModule
import org.jetbrains.kotlin.sir.visitors.SirTransformer
import org.jetbrains.kotlin.sir.visitors.SirVisitor
import org.jetbrains.sir.passes.translation.ForeignIntoSwiftFunctionTranslationPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SirPassTests {
    @Test
    fun `foreign toplevel function without params should be translated`() {
        val mySirElement = buildForeignFunction {
            origin = SirOrigin.KotlinEntity.Function(
                name = { listOf("foo") },
                parameters = {
                    mutableListOf(
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg1",
                            type = SirOrigin.ExternallyDefined("kotlin/Byte")
                        )
                    )},
                returnType = { SirOrigin.ExternallyDefined("kotlin/Byte") },
            )
            visibility = SirVisibility.PUBLIC
        }
        val myPass = ForeignIntoSwiftFunctionTranslationPass()
        val result = myPass.run(mySirElement, Unit)
        assertNotNull(result, "SirFunction should be produced")
        val exp = MockSirFunction()
        assertEquals(
            actual = result.name,
            expected = exp.name
        )
        assertEquals(
            actual = result.parameters,
            expected = exp.parameters
        )
        assertEquals(
            actual = result.returnType,
            expected = exp.returnType
        )
    }
}

class MockSirFunction(
    override val origin: SirOrigin = SirOrigin.Unknown,
    override val visibility: SirVisibility = SirVisibility.PUBLIC,
    override var parent: SirDeclarationParent = SirSwiftModule,
    override val name: String = "foo",
    override val parameters: List<SirParameter> = listOf(
        SirParameter(argumentName = "arg1", type = SirNominalType(SirSwiftModule.int8))
    ),
    override val returnType: SirType = SirNominalType(SirSwiftModule.int8)
) : SirFunction() {
    override fun <R, D> acceptChildren(visitor: SirVisitor<R, D>, data: D) {
        TODO("Not yet implemented")
    }

    override fun <D> transformChildren(transformer: SirTransformer<D>, data: D) {
        TODO("Not yet implemented")
    }
}
