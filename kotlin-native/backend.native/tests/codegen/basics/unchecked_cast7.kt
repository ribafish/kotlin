/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.basics.unchecked_cast7

import kotlin.test.*

@Test
fun runTest() {
    try {
        val x = Any().uncheckedCast<Int?>()
        println(x)
    } catch (e: ClassCastException) {
        println("Ok")
    }
}

fun <T> Any?.uncheckedCast(): T = this as T