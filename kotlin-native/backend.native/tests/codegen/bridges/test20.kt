/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.bridges.test20

import kotlin.test.*

open class A<T> {
    val s: T = "zzz" as T
}

interface C {
    val s: String
}

class B : C, A<String>()

class Data(val x: Int)

@Test fun runTest() {
    val b = B()
    assertFailsWith<ClassCastException> {
        println((b as A<Data>).s.x)
    }
}
