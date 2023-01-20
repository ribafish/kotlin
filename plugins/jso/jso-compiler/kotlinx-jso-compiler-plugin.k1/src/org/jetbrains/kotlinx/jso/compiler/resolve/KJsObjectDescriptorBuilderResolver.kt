/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.resolve

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeAttributes
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.Variance

object KJsObjectDescriptorBuilderResolver {
    fun getJsObjectBuilderPropertyNames(classDescriptor: ClassDescriptor): List<Name> {
        val typeToBuild = classDescriptor.containingDeclaration as ClassDescriptor
        return typeToBuild.unsubstitutedMemberScope.getVariableNames().toList()
    }

    fun generateJsObjectBuilderProperties(
        thisDescriptor: ClassDescriptor,
        name: Name,
        result: MutableSet<PropertyDescriptor>
    ) {
        val typeToBuild = thisDescriptor.containingDeclaration as ClassDescriptor
        val typeToBuildMemberScope =
            typeToBuild.getMemberScope(thisDescriptor.declaredTypeParameters.map { TypeProjectionImpl(it.variance, it.defaultType) })
        val originalProperty = typeToBuildMemberScope.getContributedVariables(name, NoLookupLocation.FROM_BACKEND).single()

        if (originalProperty.getter?.isDefault != false) {
            result.add(originalProperty.createJsObjectBuilderPropertyDescriptor(thisDescriptor))
        }
    }

    fun addJsObjectBuilderImplClass(
        thisDescriptor: ClassDescriptor,
        declarationProvider: ClassMemberDeclarationProvider,
        ctx: LazyClassContext
    ): ClassDescriptor {
        val thisDeclaration = declarationProvider.correspondingClassOrObject!!
        val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(declarationProvider.ownerInfo!!.scopeAnchor)
        val jsoBuilderDescriptor = SyntheticClassOrObjectDescriptor(
            ctx,
            thisDeclaration,
            thisDescriptor,
            JsObjectDeclarationNames.BUILDER_INTERFACE_NAME,
            thisDescriptor.source,
            scope,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC,
            Annotations.EMPTY,
            DescriptorVisibilities.PRIVATE,
            ClassKind.INTERFACE,
            false
        )
        val typeParameters: List<TypeParameterDescriptor> =
            thisDescriptor.declaredTypeParameters.mapIndexed { index, param ->
                TypeParameterDescriptorImpl.createWithDefaultBound(
                    jsoBuilderDescriptor,
                    Annotations.EMPTY,
                    false,
                    Variance.INVARIANT,
                    param.name, index, LockBasedStorageManager.NO_LOCKS
                )
            }
        jsoBuilderDescriptor.initialize(typeParameters)
        return jsoBuilderDescriptor
    }

    private fun PropertyDescriptor.createJsObjectBuilderPropertyDescriptor(jsObjectBuilder: ClassDescriptor): PropertyDescriptor {
        val propertyDescriptor = PropertyDescriptorImpl.create(
            jsObjectBuilder,
            Annotations.EMPTY,
            modality,
            visibility,
            true,
            name,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            jsObjectBuilder.source,
            false,
            false,
            false,
            false,
            false,
            false
        )

        propertyDescriptor.setType(
            type,
            typeParameters,
            jsObjectBuilder.thisAsReceiverParameter,
            null,
            emptyList()
        )

        propertyDescriptor.overriddenDescriptors = listOf(this)

        propertyDescriptor.initialize(
            PropertyGetterDescriptorImpl(
                propertyDescriptor,
                Annotations.EMPTY,
                propertyDescriptor.modality,
                propertyDescriptor.visibility,
                getter?.isDefault ?: true,
                true,
                getter?.isInline ?: false,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                null,
                propertyDescriptor.source
            ).apply { initialize(propertyDescriptor.type) },
            PropertySetterDescriptorImpl(
                propertyDescriptor,
                Annotations.EMPTY,
                propertyDescriptor.modality,
                propertyDescriptor.visibility,
                setter?.isDefault ?: true,
                true,
                setter?.isInline ?: false,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                null,
                propertyDescriptor.source
            ).apply {
                initialize(
                    ValueParameterDescriptorImpl(
                        this,
                        null,
                        0,
                        Annotations.EMPTY,
                        Name.identifier("value"),
                        propertyDescriptor.type,
                        false,
                        false,
                        false,
                        null,
                        propertyDescriptor.source
                    )
                )
            },
        )

        return propertyDescriptor
    }
}
