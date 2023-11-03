/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.psi

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isNullableType
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.filterIsInstanceAnd
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectAnnotations
import org.jetbrains.kotlinx.jso.compiler.resolve.SpecialNames

open class JsObjectFactoryFunctionGenerator : SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> =
        if (thisDescriptor.isJsSimpleObject()) listOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) else emptyList()

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? =
        listOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> =
        thisDescriptor.containingDeclaration
            .takeIf { thisDescriptor.isCompanionObject && it.isJsSimpleObject() }
            ?.let { listOf(it.name) } ?: emptyList()

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? =
        SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.takeIf { thisDescriptor.isJsSimpleObject() }

    /**
     * The method generate a synthetic factory for an `external interface` annotated with @JsSimpleObject
     * Imagine the next interfaces:
     * ```
     * external interface User {
     *   val name: String
     * }
     * @JsSimpleObject
     * external interface Admin {
     *   val chat: Chat
     * }
     * ```
     *
     * For the interface `Admin` this function should generate the companion inline function:
     * ```
     * external interface Admin {
     *   val chat: Chat
     *   companion object {
     *      inline operator fun invoke(chat: Chat, name: String): Admin =
     *          js("{ chat: chat, name: name }")
     *   }
     * }
     * ```
     */
    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        if (name != SpecialNames.INVOKE_OPERATOR_NAME) return
        val classDescriptor = thisDescriptor
            .takeIf { it.isCompanionObject }
            ?.let { it.containingDeclaration as? ClassDescriptor }
            ?.takeIf { it.isJsSimpleObject() } ?: return

        val factoryFunction = SimpleFunctionDescriptorImpl.create(
            thisDescriptor,
            Annotations.EMPTY,
            name,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            thisDescriptor.source
        ).apply {
            isInline = true
            isOperator = true
        }

        val parameters = getAllJsSimpleObjectProperties(classDescriptor).mapIndexed { index, property ->
            val propertyType = property.returnTypeOrNothing
            ValueParameterDescriptorImpl(
                containingDeclaration = factoryFunction,
                original = null,
                index = index,
                annotations = Annotations.EMPTY,
                name = property.name,
                outType = propertyType,
                declaresDefaultValue = propertyType.isNullableType(),
                isCrossinline = false,
                isNoinline = true,
                source = factoryFunction.source,
                varargElementType = null
            )
        }

        factoryFunction.initialize(
            null,
            thisDescriptor.thisAsReceiverParameter,
            emptyList(),
            classDescriptor.declaredTypeParameters,
            parameters,
            classDescriptor.defaultType,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC
        )

        result.add(factoryFunction)
    }

    private fun getAllJsSimpleObjectProperties(thisDescriptor: ClassDescriptor): List<PropertyDescriptor> {
        if (!thisDescriptor.isJsSimpleObject()) return emptyList()
        return buildList {
            thisDescriptor.getSuperInterfaces().forEach {
                val superInterfaceSimpleObjectProperties = getAllJsSimpleObjectProperties(it)
                superInterfaceSimpleObjectProperties.forEach(::addIfNotNull)
            }

            thisDescriptor
                .unsubstitutedMemberScope
                .getDescriptorsFiltered(DescriptorKindFilter.VARIABLES)
                .filterIsInstanceAnd<PropertyDescriptor> { it.visibility == DescriptorVisibilities.PUBLIC }
                .forEach(::add)
        }
    }

    private fun DeclarationDescriptor.isJsSimpleObject() =
        annotations.hasAnnotation(JsObjectAnnotations.jsSimpleObjectAnnotationFqName)
}