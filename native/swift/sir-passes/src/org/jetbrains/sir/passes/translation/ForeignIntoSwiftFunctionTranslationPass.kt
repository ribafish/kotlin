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


/**
 * Translates `SirForeignFunction` into `SirFunction`.
 * Works only with Nominal Types currently.
 * If received `element` of different type than `SirForeignFunction`,
 * or `element` does not contain origin of type `SirOrigin.KotlinEntity.Function`,
 * returns `null`.
 */
class ForeignIntoSwiftFunctionTranslationPass : SirPass<SirFunction?, Unit> {
    override fun run(element: SirElement, data: Unit): SirFunction? = buildFunction {
        val foreignFunction = element as? SirForeignFunction
            ?: return null
        val kotlinOrigin = foreignFunction.origin as? SirOrigin.KotlinEntity.Function
            ?: return null

        origin = kotlinOrigin
        visibility = foreignFunction.visibility
        name = kotlinOrigin.fqName().last()
        kotlinOrigin.parameters().mapTo(parameters) { it.toSir() }

        returnType = kotlinOrigin.returnType().toSir()
    }
}

private fun SirOrigin.KotlinEntity.Parameter.toSir(): SirParameter = SirParameter(
    argumentName = name,
    type = type.toSir(),
)

private fun SirOrigin.KotlinEntity.KotlinType.toSir(): SirType = SirNominalType(
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
