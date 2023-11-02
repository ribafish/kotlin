/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.bridges.test22

import kotlin.test.*

open class Base<T> {
    open fun foo(x: T): Int = 42
}

open class Derived : Base<Int>() {
    override fun foo(x: Int) = x

    // override fun Base<Any?>.foo(x: Any?) = foo(<Int-unbox>(x as Int))
}

class Data(val s: String)

@Test fun runTest() {
    val d = Derived()
    assertFailsWith<ClassCastException> {
        println((d as Base<Data>).foo(Data("zzz")))
    }
}
