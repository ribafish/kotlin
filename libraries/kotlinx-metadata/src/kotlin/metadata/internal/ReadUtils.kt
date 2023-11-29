/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.metadata.internal

import kotlin.metadata.ClassName
import kotlin.metadata.KmAnnotation
import kotlin.metadata.KmAnnotationArgument
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Annotation.Argument.Value.Type.*
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.metadata.deserialization.NameResolver

public fun ProtoBuf.Annotation.readAnnotation(strings: NameResolver): kotlin.metadata.KmAnnotation =
    kotlin.metadata.KmAnnotation(
        strings.getClassName(id),
        argumentList.mapNotNull { argument ->
            argument.value.readAnnotationArgument(strings)?.let { value ->
                strings.getString(argument.nameId) to value
            }
        }.toMap()
    )

public fun ProtoBuf.Annotation.Argument.Value.readAnnotationArgument(strings: NameResolver): kotlin.metadata.KmAnnotationArgument? {
    if (Flags.IS_UNSIGNED[flags]) {
        return when (type) {
            BYTE -> kotlin.metadata.KmAnnotationArgument.UByteValue(intValue.toByte().toUByte())
            SHORT -> kotlin.metadata.KmAnnotationArgument.UShortValue(intValue.toShort().toUShort())
            INT -> kotlin.metadata.KmAnnotationArgument.UIntValue(intValue.toInt().toUInt())
            LONG -> kotlin.metadata.KmAnnotationArgument.ULongValue(intValue.toULong())
            else -> error("Cannot read value of unsigned type: $type")
        }
    }

    return when (type) {
        BYTE -> kotlin.metadata.KmAnnotationArgument.ByteValue(intValue.toByte())
        CHAR -> kotlin.metadata.KmAnnotationArgument.CharValue(intValue.toInt().toChar())
        SHORT -> kotlin.metadata.KmAnnotationArgument.ShortValue(intValue.toShort())
        INT -> kotlin.metadata.KmAnnotationArgument.IntValue(intValue.toInt())
        LONG -> kotlin.metadata.KmAnnotationArgument.LongValue(intValue)
        FLOAT -> kotlin.metadata.KmAnnotationArgument.FloatValue(floatValue)
        DOUBLE -> kotlin.metadata.KmAnnotationArgument.DoubleValue(doubleValue)
        BOOLEAN -> kotlin.metadata.KmAnnotationArgument.BooleanValue(intValue != 0L)
        STRING -> kotlin.metadata.KmAnnotationArgument.StringValue(strings.getString(stringValue))
        CLASS -> strings.getClassName(classId).let { className ->
            if (arrayDimensionCount == 0)
                kotlin.metadata.KmAnnotationArgument.KClassValue(className)
            else
                kotlin.metadata.KmAnnotationArgument.ArrayKClassValue(className, arrayDimensionCount)
        }
        ENUM -> kotlin.metadata.KmAnnotationArgument.EnumValue(strings.getClassName(classId), strings.getString(enumValueId))
        ANNOTATION -> kotlin.metadata.KmAnnotationArgument.AnnotationValue(annotation.readAnnotation(strings))
        ARRAY -> kotlin.metadata.KmAnnotationArgument.ArrayValue(arrayElementList.mapNotNull { it.readAnnotationArgument(strings) })
        null -> null
    }
}

internal fun NameResolver.getClassName(index: Int): ClassName {
    val name = getQualifiedClassName(index)
    return if (isLocalClassName(index)) ".$name" else name
}
