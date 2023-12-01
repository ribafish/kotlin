/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.passes

import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.builder.buildForeignFunction
import org.jetbrains.kotlin.sir.passes.asserts.assertSirFunctionsEquals
import org.jetbrains.kotlin.sir.passes.mocks.MockSirFunction
import org.jetbrains.kotlin.sir.util.SirSwiftModule
import org.jetbrains.sir.passes.translation.ForeignIntoSwiftFunctionTranslationPass
import kotlin.test.Test
import kotlin.test.assertNotNull

class SirPassTests {
    @Test
    fun `foreign toplevel function without params should be translated`() {
        val mySirElement = buildForeignFunction {
            origin = SirOrigin.KotlinEntity.Function(
                fqName = { listOf("foo") },
                parameters = { emptyList() },
                returnType = { SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Boolean") },
            )
            visibility = SirVisibility.PUBLIC
        }
        val myPass = ForeignIntoSwiftFunctionTranslationPass()
        val result = myPass.run(mySirElement, Unit)
        assertNotNull(result, "SirFunction should be produced")
        val exp = MockSirFunction(
            name = "foo",
            parameters = emptyList(),
            returnType = SirNominalType(SirSwiftModule.bool),
        )
        assertSirFunctionsEquals(actual = result, expected = exp)
    }

    @Test
    fun `foreign toplevel function with all params should be translated`() {
        val mySirElement = buildForeignFunction {
            origin = SirOrigin.KotlinEntity.Function(
                fqName = { listOf("foo") },
                parameters = {
                    listOf(
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg1",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Byte")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg2",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Short")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg3",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Int")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg4",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Long")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg5",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Double")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg6",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Float")
                        ),
                        SirOrigin.KotlinEntity.Parameter(
                            name = "arg7",
                            type = SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Boolean")
                        )
                    )
                },
                returnType = { SirOrigin.KotlinEntity.KotlinType(name = "kotlin/Byte") },
            )
            visibility = SirVisibility.PUBLIC
        }
        val myPass = ForeignIntoSwiftFunctionTranslationPass()
        val result = myPass.run(mySirElement, Unit)
        assertNotNull(result, "SirFunction should be produced")
        val exp = MockSirFunction(
            name = "foo",
            parameters = listOf(
                SirParameter(argumentName = "arg1", type = SirNominalType(SirSwiftModule.int8)),
                SirParameter(argumentName = "arg2", type = SirNominalType(SirSwiftModule.int16)),
                SirParameter(argumentName = "arg3", type = SirNominalType(SirSwiftModule.int32)),
                SirParameter(argumentName = "arg4", type = SirNominalType(SirSwiftModule.int64)),

                SirParameter(argumentName = "arg5", type = SirNominalType(SirSwiftModule.double)),
                SirParameter(argumentName = "arg6", type = SirNominalType(SirSwiftModule.float)),

                SirParameter(argumentName = "arg7", type = SirNominalType(SirSwiftModule.bool)),
            ),
            returnType = SirNominalType(SirSwiftModule.int8),
        )
        assertSirFunctionsEquals(actual = result, expected = exp)
    }

}
