/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlinx.jso.compiler.k1.extensions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlinx.jso.compiler.k1.utils.isJsObjectBuilderInterface
import org.jetbrains.kotlinx.jso.compiler.k1.utils.shouldHaveGeneratedJsObjectBuilder
import org.jetbrains.kotlinx.jso.compiler.resolve.JsObjectDeclarationNames
import org.jetbrains.kotlinx.jso.compiler.resolve.KJsObjectDescriptorBuilderResolver

open class JsObjectResolveExtension : SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = when {
        (thisDescriptor.shouldHaveGeneratedJsObjectBuilder) -> listOf(JsObjectDeclarationNames.BUILDER_INTERFACE_NAME)
        else -> emptyList()
    }

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? {
        return listOf(JsObjectDeclarationNames.BUILDER_INTERFACE_NAME)
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        if (thisDescriptor.shouldHaveGeneratedJsObjectBuilder && result.none { it.name == JsObjectDeclarationNames.BUILDER_INTERFACE_NAME })
            result.add(KJsObjectDescriptorBuilderResolver.addJsObjectBuilderImplClass(thisDescriptor, declarationProvider, ctx))
        return
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        if (thisDescriptor.isJsObjectBuilderInterface)
            KJsObjectDescriptorBuilderResolver.addJsObjectBuilderSupertypes(thisDescriptor, supertypes)
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return if (thisDescriptor.isJsObjectBuilderInterface) {
            KJsObjectDescriptorBuilderResolver.getJsObjectBuilderPropertyNames(thisDescriptor)
        } else {
            emptyList()
        }
    }

    override fun generateSyntheticProperties(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: ArrayList<PropertyDescriptor>,
        result: MutableSet<PropertyDescriptor>
    ) {
        if (thisDescriptor.isJsObjectBuilderInterface)
            KJsObjectDescriptorBuilderResolver.generateJsObjectBuilderProperties(thisDescriptor, name, result)
    }
}
