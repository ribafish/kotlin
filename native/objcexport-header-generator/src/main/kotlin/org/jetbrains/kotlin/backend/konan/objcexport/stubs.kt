/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

@file:JvmName("ObjCExportStubKt")

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ParameterDescriptor

@Deprecated("Use 'ObjCExportStub' instead", replaceWith = ReplaceWith("ObjCExportStub"))
typealias Stub<T> = ObjCExportStub

sealed interface ObjCExportStub {
    /**
     * The ObjC name of this entity;
     * Note: The original 'Kotlin Name' can be found in [origin]
     */
    val name: String

    val comment: ObjCComment?


    /**
     * Leaves breadcrumbs, minimal information, about the origin of this stub.
     * A [origin] can either be
     * - [ObjCExportStubOrigin.Source] indicating that the stub was produced from Source Code (happens inside the IDE)
     * - [ObjCExportStubOrigin.Binary] indicating that the stub was produced by deserializing a klib (Note: CLI only works in this mode)
     * - null: Indicating that we not provide information about the origin of this stub. This can happen e.g.
     * if the stub is just synthetically created by this tool.
     */
    val origin: ObjCExportStubOrigin?
}

val ObjCExportStub.psiOrNull
    get() = when (val origin = origin) {
        is ObjCExportStubOrigin.Source -> origin.psi
        else -> null
    }


abstract class ObjCTopLevel : ObjCExportStub

sealed class ObjCClass : ObjCTopLevel() {
    abstract val attributes: List<String>
    abstract val superProtocols: List<String>
    abstract val members: List<ObjCExportStub>
}

abstract class ObjCProtocol : ObjCClass()

abstract class ObjCInterface : ObjCClass() {
    abstract val categoryName: String?
    abstract val generics: List<ObjCGenericTypeDeclaration>
    abstract val superClass: String?
    abstract val superClassGenerics: List<ObjCNonNullReferenceType>
}

class ObjCComment(val contentLines: List<String>) {
    constructor(vararg contentLines: String) : this(contentLines.toList())
}

data class ObjCClassForwardDeclaration(
    val className: String,
    val typeDeclarations: List<ObjCGenericTypeDeclaration> = emptyList(),
)

class ObjCProtocolImpl(
    override val name: String,
    override val comment: ObjCComment?,
    override val origin: ObjCExportStubOrigin?,
    override val attributes: List<String>,
    override val superProtocols: List<String>,
    override val members: List<ObjCExportStub>,
) : ObjCProtocol() {
    constructor(
        name: String,
        descriptor: ClassDescriptor,
        superProtocols: List<String>,
        members: List<ObjCExportStub>,
        attributes: List<String> = emptyList(),
        comment: ObjCComment? = null,
    ) : this(
        name = name,
        comment = comment,
        origin = ObjCExportStubOrigin(descriptor),
        attributes = attributes,
        superProtocols = superProtocols,
        members = members
    )
}

class ObjCInterfaceImpl(
    override val name: String,
    override val comment: ObjCComment?,
    override val origin: ObjCExportStubOrigin?,
    override val attributes: List<String>,
    override val superProtocols: List<String>,
    override val members: List<ObjCExportStub>,
    override val categoryName: String?,
    override val generics: List<ObjCGenericTypeDeclaration>,
    override val superClass: String?,
    override val superClassGenerics: List<ObjCNonNullReferenceType>,
) : ObjCInterface() {
    constructor(
        name: String,
        generics: List<ObjCGenericTypeDeclaration> = emptyList(),
        descriptor: ClassDescriptor? = null,
        superClass: String? = null,
        superClassGenerics: List<ObjCNonNullReferenceType> = emptyList(),
        superProtocols: List<String> = emptyList(),
        categoryName: String? = null,
        members: List<ObjCExportStub> = emptyList(),
        attributes: List<String> = emptyList(),
        comment: ObjCComment? = null,
    ) : this(
        name = name,
        comment = comment,
        origin = ObjCExportStubOrigin(descriptor),
        attributes = attributes,
        superProtocols = superProtocols,
        members = members,
        categoryName = categoryName,
        generics = generics,
        superClass = superClass,
        superClassGenerics = superClassGenerics
    )
}

class ObjCMethod(
    override val comment: ObjCComment?,
    override val origin: ObjCExportStubOrigin?,
    val isInstanceMethod: Boolean,
    val returnType: ObjCType,
    val selectors: List<String>,
    val parameters: List<ObjCParameter>,
    val attributes: List<String>,
) : ObjCExportStub {
    constructor(
        descriptor: DeclarationDescriptor?,
        isInstanceMethod: Boolean,
        returnType: ObjCType,
        selectors: List<String>,
        parameters: List<ObjCParameter>,
        attributes: List<String>,
        comment: ObjCComment? = null,
    ) : this(
        comment = comment,
        origin = ObjCExportStubOrigin(descriptor),
        isInstanceMethod = isInstanceMethod,
        returnType = returnType,
        selectors = selectors,
        parameters = parameters,
        attributes = attributes
    )

    override val name: String = buildMethodName(selectors, parameters)
}

class ObjCParameter private constructor(
    override val name: String,
    override val origin: ObjCExportStubOrigin?,
    val type: ObjCType,
) : ObjCExportStub {

    constructor(
        name: String,
        descriptor: ParameterDescriptor?,
        type: ObjCType,
    ) : this(
        name = name,
        origin = ObjCExportStubOrigin(descriptor),
        type = type
    )

    override val comment: Nothing? = null
}

class ObjCProperty(
    override val name: String,
    override val comment: ObjCComment?,
    override val origin: ObjCExportStubOrigin?,
    val type: ObjCType,
    val propertyAttributes: List<String>,
    val setterName: String? = null,
    val getterName: String? = null,
    val declarationAttributes: List<String> = emptyList(),
) : ObjCExportStub {
    constructor(
        name: String,
        descriptor: DeclarationDescriptorWithSource?,
        type: ObjCType,
        propertyAttributes: List<String>,
        setterName: String? = null,
        getterName: String? = null,
        declarationAttributes: List<String> = emptyList(),
        comment: ObjCComment? = null,
    ) : this(
        name = name,
        comment = comment,
        origin = ObjCExportStubOrigin(descriptor),
        type = type,
        propertyAttributes = propertyAttributes,
        setterName = setterName,
        getterName = getterName,
        declarationAttributes = declarationAttributes
    )
}

private fun buildMethodName(selectors: List<String>, parameters: List<ObjCParameter>): String =
    if (selectors.size == 1 && parameters.size == 0) {
        selectors[0]
    } else {
        assert(selectors.size == parameters.size)
        selectors.joinToString(separator = "")
    }
