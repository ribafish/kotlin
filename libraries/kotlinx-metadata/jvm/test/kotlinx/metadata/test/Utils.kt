/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.test

import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassMetadata

internal fun Class<*>.getMetadata(): Metadata {
    return getAnnotation(Metadata::class.java)
}

internal fun Metadata.readAsKmClass(): KmClass {
    val clazz = KotlinClassMetadata.readStrict(this) as? KotlinClassMetadata.Class
    return clazz?.kmClass ?: error("Not a KotlinClassMetadata.Class: $clazz")
}

internal fun Class<*>.readMetadataAsKmClass(): KmClass = getMetadata().readAsKmClass()

internal fun Class<*>.readMetadataAsClass(): KotlinClassMetadata.Class = getMetadata().readMetadataAsClass()

internal fun Metadata.readMetadataAsClass(): KotlinClassMetadata.Class = KotlinClassMetadata.readStrict(this) as KotlinClassMetadata.Class
