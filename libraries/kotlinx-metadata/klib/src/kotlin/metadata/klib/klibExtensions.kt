/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.metadata.klib

import kotlin.metadata.*
import kotlin.metadata.internal.common.KmModuleFragment
import kotlin.metadata.klib.impl.klibExtensions

val KmFunction.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

var KmFunction.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

var KmFunction.file: KlibSourceFile?
    get() = klibExtensions.file
    set(value) {
        klibExtensions.file = value
    }

val KmClass.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

var KmClass.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

var KmClass.file: KlibSourceFile?
    get() = klibExtensions.file
    set(value) {
        klibExtensions.file = value
    }

val KmClass.klibEnumEntries: MutableList<KlibEnumEntry>
    get() = klibExtensions.enumEntries

val KmProperty.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

val KmProperty.setterAnnotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.setterAnnotations

val KmProperty.getterAnnotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.getterAnnotations

var KmProperty.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

var KmProperty.file: Int?
    get() = klibExtensions.file
    set(value) {
        klibExtensions.file = value
    }

var KmProperty.compileTimeValue: kotlin.metadata.KmAnnotationArgument?
    get() = klibExtensions.compileTimeValue
    set(value) {
        klibExtensions.compileTimeValue = value
    }

val KmType.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

val KmConstructor.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

var KmConstructor.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

var KmPackage.fqName: String?
    get() = klibExtensions.fqName
    set(value) {
        klibExtensions.fqName = value
    }

var KmModuleFragment.fqName: String?
    get() = klibExtensions.fqName
    set(value) {
        klibExtensions.fqName = value
    }

val KmModuleFragment.className: MutableList<ClassName>
    get() = klibExtensions.className

val KmModuleFragment.moduleFragmentFiles: MutableList<KlibSourceFile>
    get() = klibExtensions.moduleFragmentFiles

val KmTypeParameter.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations

var KmTypeParameter.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

var KmTypeAlias.uniqId: UniqId?
    get() = klibExtensions.uniqId
    set(value) {
        klibExtensions.uniqId = value
    }

val KmValueParameter.annotations: MutableList<kotlin.metadata.KmAnnotation>
    get() = klibExtensions.annotations
