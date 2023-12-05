/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.renderer.FirRenderer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import kotlin.reflect.KClass

/**
 * @param containerSymbols a list of symbols that should be resolved to make [annotations] are fully resolved.
 * Required only for "lazy" resolve mode in AA FIR to make a type annotation lazily resolved.
 * See KtFirAnnotationListForType for reference.
 * Example:
 * ```kotlin
 * fun foo(): @Anno Type
 * ```
 * This `Anno` annotation will have `foo` function as [containerSymbols].
 * More than one [containerSymbols] possible in case of type aliases:
 * ```kotlin
 * interface BaseInterface
 * typealias FirstTypeAlias = @Anno1 BaseInterface
 * typealias SecondTypeAlias = @Anno2 FirstTypeAlias
 *
 * fun foo(): @Anno3 SecondTypeAlias = TODO()
 * ```
 * here `@Anno3 SecondTypeAlias` will be expanded to ` @Anno1 @Anno2 @Anno3 BaseInterface`
 * and will have all intermediate type-aliases as [containerSymbols].
 */
class CustomAnnotationTypeAttribute(
    val annotations: List<FirAnnotation>,
    val containerSymbols: List<FirBasedSymbol<*>> = emptyList(),
) : ConeAttribute<CustomAnnotationTypeAttribute>() {
    constructor(annotations: List<FirAnnotation>, containerSymbol: FirBasedSymbol<*>?) : this(
        annotations,
        listOfNotNull(containerSymbol),
    )

    override fun union(other: CustomAnnotationTypeAttribute?): CustomAnnotationTypeAttribute? = null

    override fun intersect(other: CustomAnnotationTypeAttribute?): CustomAnnotationTypeAttribute? = null

    override fun add(other: CustomAnnotationTypeAttribute?): CustomAnnotationTypeAttribute {
        if (other == null || other === this) return this
        return CustomAnnotationTypeAttribute(annotations + other.annotations, containerSymbols + other.containerSymbols)
    }

    override fun isSubtypeOf(other: CustomAnnotationTypeAttribute?): Boolean = true

    override fun toString(): String = annotations.joinToString(separator = " ") { it.render() }

    override fun renderForReadability(): String =
        annotations.joinToString(separator = " ") { FirRenderer.forReadability().renderElementAsString(it, trim = true) }

    override val key: KClass<out CustomAnnotationTypeAttribute>
        get() = CustomAnnotationTypeAttribute::class
    override val keepInInferredDeclarationType: Boolean
        get() = true
}

val ConeAttributes.custom: CustomAnnotationTypeAttribute? by ConeAttributes.attributeAccessor<CustomAnnotationTypeAttribute>()

val ConeAttributes.customAnnotations: List<FirAnnotation> get() = custom?.annotations.orEmpty()

val ConeKotlinType.customAnnotations: List<FirAnnotation> get() = attributes.customAnnotations
