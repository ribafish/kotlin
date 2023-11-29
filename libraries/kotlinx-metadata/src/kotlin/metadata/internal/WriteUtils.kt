/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.metadata.internal

import kotlin.metadata.*
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.metadata.serialization.StringTable

public fun kotlin.metadata.KmAnnotation.writeAnnotation(strings: StringTable): ProtoBuf.Annotation.Builder =
    ProtoBuf.Annotation.newBuilder().apply {
        id = strings.getClassNameIndex(className)
        for ((name, argument) in arguments) {
            addArgument(ProtoBuf.Annotation.Argument.newBuilder().apply {
                nameId = strings.getStringIndex(name)
                value = argument.writeAnnotationArgument(strings).build()
            })
        }
    }

public fun kotlin.metadata.KmAnnotationArgument.writeAnnotationArgument(strings: StringTable): ProtoBuf.Annotation.Argument.Value.Builder =
    ProtoBuf.Annotation.Argument.Value.newBuilder().apply {
        when (this@writeAnnotationArgument) {
            is kotlin.metadata.KmAnnotationArgument.ByteValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.BYTE
                this.intValue = value.toLong()
            }
            is kotlin.metadata.KmAnnotationArgument.CharValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.CHAR
                this.intValue = value.code.toLong()
            }
            is kotlin.metadata.KmAnnotationArgument.ShortValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.SHORT
                this.intValue = value.toLong()
            }
            is kotlin.metadata.KmAnnotationArgument.IntValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.INT
                this.intValue = value.toLong()
            }
            is kotlin.metadata.KmAnnotationArgument.LongValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.LONG
                this.intValue = value
            }
            is kotlin.metadata.KmAnnotationArgument.FloatValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.FLOAT
                this.floatValue = value
            }
            is kotlin.metadata.KmAnnotationArgument.DoubleValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.DOUBLE
                this.doubleValue = value
            }
            is kotlin.metadata.KmAnnotationArgument.BooleanValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.BOOLEAN
                this.intValue = if (value) 1 else 0
            }
            is kotlin.metadata.KmAnnotationArgument.UByteValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.BYTE
                this.intValue = value.toLong()
                this.flags = Flags.IS_UNSIGNED.toFlags(true)
            }
            is kotlin.metadata.KmAnnotationArgument.UShortValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.SHORT
                this.intValue = value.toLong()
                this.flags = Flags.IS_UNSIGNED.toFlags(true)
            }
            is kotlin.metadata.KmAnnotationArgument.UIntValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.INT
                this.intValue = value.toLong()
                this.flags = Flags.IS_UNSIGNED.toFlags(true)
            }
            is kotlin.metadata.KmAnnotationArgument.ULongValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.LONG
                this.intValue = value.toLong()
                this.flags = Flags.IS_UNSIGNED.toFlags(true)
            }
            is kotlin.metadata.KmAnnotationArgument.StringValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.STRING
                this.stringValue = strings.getStringIndex(value)
            }
            is kotlin.metadata.KmAnnotationArgument.KClassValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.CLASS
                this.classId = strings.getClassNameIndex(className)
            }
            is kotlin.metadata.KmAnnotationArgument.ArrayKClassValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.CLASS
                this.classId = strings.getClassNameIndex(className)
                this.arrayDimensionCount = this@writeAnnotationArgument.arrayDimensionCount
            }
            is kotlin.metadata.KmAnnotationArgument.EnumValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.ENUM
                this.classId = strings.getClassNameIndex(enumClassName)
                this.enumValueId = strings.getStringIndex(enumEntryName)
            }
            is kotlin.metadata.KmAnnotationArgument.AnnotationValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.ANNOTATION
                this.annotation = this@writeAnnotationArgument.annotation.writeAnnotation(strings).build()
            }
            is kotlin.metadata.KmAnnotationArgument.ArrayValue -> {
                this.type = ProtoBuf.Annotation.Argument.Value.Type.ARRAY
                for (element in elements) {
                    this.addArrayElement(element.writeAnnotationArgument(strings))
                }
            }
        }
    }

internal fun StringTable.getClassNameIndex(name: ClassName): Int =
    if (name.isLocalClassName())
        getQualifiedClassNameIndex(name.substring(1), true)
    else
        getQualifiedClassNameIndex(name, false)
