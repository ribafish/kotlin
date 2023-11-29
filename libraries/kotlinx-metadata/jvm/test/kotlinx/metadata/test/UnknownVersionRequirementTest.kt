/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.test

import kotlinx.metadata.internal.ClassWriter
import kotlinx.metadata.jvm.JvmMetadataVersion
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.Metadata
import kotlinx.metadata.jvm.internal.writeProtoBufData
import org.jetbrains.kotlin.metadata.jvm.serialization.JvmStringTable
import org.junit.Test
import kotlin.test.assertEquals

class UnknownVersionRequirementTest {

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.RequireKotlin("1.8.0", "foobar")
    class Sample

    private val requireKotlinValue = "KmVersionRequirement(kind=LANGUAGE_VERSION, level=ERROR, version=1.8.0, errorCode=null, message=foobar)"

    @Test
    fun incorrectVersionRequirementHandledAsUnknown() {
        val original = Sample::class.java.readMetadataAsKmClass()
        assertEquals(1, original.versionRequirements.size)
        assertEquals(
            requireKotlinValue,
            original.versionRequirements.single().toString()
        )

        val writer = ClassWriter(JvmStringTable())
        writer.writeClass(original)
        writer.t.addVersionRequirement(239) // invalid
        val (d1, d2) = writeProtoBufData(writer.t.build(), writer.c)
        val incorrect =
            Metadata(KotlinClassMetadata.CLASS_KIND, JvmMetadataVersion.LATEST_STABLE_SUPPORTED.toIntArray(), d1, d2, extraInt = 0)

        val withInvalidRequirement = incorrect.readMetadataAsClass()
        assertEquals(2, withInvalidRequirement.kmClass.versionRequirements.size)
        assertEquals(
            "[$requireKotlinValue, KmVersionRequirement(kind=UNKNOWN, level=HIDDEN, version=256.256.256, errorCode=null, message=null)]",
            withInvalidRequirement.kmClass.versionRequirements.toString()
        )

        val rewritten = withInvalidRequirement.write().readAsKmClass()
        assertEquals(1, rewritten.versionRequirements.size)
        assertEquals(
            requireKotlinValue,
            rewritten.versionRequirements.single().toString()
        )
    }
}
