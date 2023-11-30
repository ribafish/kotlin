/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.passes.translation

import org.jetbrains.kotlin.sir.*
import org.jetbrains.kotlin.sir.builder.buildFunction
import org.jetbrains.kotlin.sir.util.SirSwiftModule
import org.jetbrains.sir.passes.SirPass
import java.lang.IllegalStateException

class ForeignIntoSwiftFunctionTranslationPass : SirPass<SirFunction?, Unit> {
    override fun run(element: SirElement, data: Unit): SirFunction? = (element as? SirForeignFunction)?.toSir()
}

fun SirForeignFunction.toSir(): SirFunction? = buildFunction {
    val kotlinOrigin = this@toSir.origin as? SirOrigin.KotlinEntity.Function
        ?: return null

    origin = kotlinOrigin
    visibility = this@toSir.visibility
    name = kotlinOrigin.name().last()
    kotlinOrigin.parameters().mapTo(parameters) { it.toSir() }

    returnType = kotlinOrigin.returnType().toSir()
}

private fun SirOrigin.KotlinEntity.Parameter.toSir(): SirParameter = SirParameter(
    argumentName = name,
    type = type.toSir(),
)

fun SirOrigin.ExternallyDefined.toSir(): SirType = SirNominalType(
    type = when (this.name) {
        "kotlin/Byte" -> SirSwiftModule.int8
        "kotlin/Short" -> SirSwiftModule.int16
        "kotlin/Int" -> SirSwiftModule.int32
        "kotlin/Long" -> SirSwiftModule.int64
        "kotlin/Boolean" -> SirSwiftModule.bool
        "kotlin/Double" -> SirSwiftModule.double
        "kotlin/Float" -> SirSwiftModule.float
        else -> throw IllegalStateException("unknown externally defined type")
    }
)
