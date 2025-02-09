/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
// !LANGUAGE: +EnumEntries
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import enums.*
import kotlinx.cinterop.*
import kotlin.test.*

@OptIn(kotlin.ExperimentalStdlibApi::class)
fun main() {
    memScoped {
        val e = alloc<E.Var>()
        e.value = E.C
        assertEquals(E.C, e.value)

        assertFailsWith<NotImplementedError> {
            e.value = TODO()
        }
    }
    val values = E.values()
    assertEquals(values[0], E.A)
    assertEquals(values[1], E.B)
    assertEquals(values[2], E.C)
// TODO: temporariry commented. Task for investigation is KT-56107
//    val entries = E.entries
//    assertEquals(entries[0], E.A)
//    assertEquals(entries[1], E.B)
//    assertEquals(entries[2], E.C)
}
