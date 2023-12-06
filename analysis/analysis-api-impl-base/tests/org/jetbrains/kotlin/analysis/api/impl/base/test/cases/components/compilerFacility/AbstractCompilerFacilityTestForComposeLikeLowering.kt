/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.compilerFacility

import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.isInlineClassType
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

abstract class AbstractCompilerFacilityTestForComposeLikeLowering : AbstractCompilerFacilityTest() {
    override fun registerIrGenerationExtensions(irGenerationExtensionPoint: ExtensionPoint<IrGenerationExtension>, project: Project) {
        irGenerationExtensionPoint.registerExtension(ComposeLikeIrGenerationExtension(), LoadingOrder.LAST, project)
    }
}

private class ComposeLikeIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(ComposeStabilityAnnotationTransformer(pluginContext))
    }
}

private class ComposeStabilityAnnotationTransformer(private val context: IrPluginContext) : IrElementTransformerVoid() {
    private val stabilityInferredClass =
        getTopLevelClass(ClassId(FqName("androidx.compose.runtime.internal"), FqName("StabilityInferred"), false))

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitClass(declaration: IrClass): IrStatement {
        val result = super.visitClass(declaration)
        val cls = result as? IrClass ?: return result

        if (
            cls.visibility != DescriptorVisibilities.PUBLIC ||
            cls.isEnumClass ||
            cls.isEnumEntry ||
            cls.isInterface ||
            cls.isAnnotationClass ||
            cls.isAnonymousObject ||
            cls.isExpect ||
            cls.isInner ||
            cls.isFileClass ||
            cls.isCompanion ||
            cls.defaultType.isInlineClassType()
        ) return cls

        cls.annotations += IrConstructorCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            stabilityInferredClass.defaultType,
            stabilityInferredClass.constructors.first(),
            0,
            0,
            1,
            null
        ).also {
            it.putValueArgument(0, irConst(0))
        }
        return result
    }

    private fun irConst(value: Int): IrConst<Int> = IrConstImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        context.irBuiltIns.intType,
        IrConstKind.Int,
        value
    )

    private fun getTopLevelClass(classId: ClassId): IrClassSymbol {
        return context.referenceClass(classId)
            ?: error("Class not found in the classpath: ${classId.asSingleFqName()}")
    }
}