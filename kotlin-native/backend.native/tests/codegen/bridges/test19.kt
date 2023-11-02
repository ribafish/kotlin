/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.bridges.test19

import kotlin.test.*

open class Base<T> {
    open fun foo(x: T): String = "zzz"
}

open class Derived : Base<String>() {
    override fun foo(x: String) = x

    // override fun Base<Any?>.foo(x: Any?) = foo(x as String)
}

class Data(val x: Int)

@Test fun runTest() {
    val d = Derived()
    assertFailsWith<ClassCastException> {
        println((d as Base<Data>).foo(Data(42)).length)
    }
}
