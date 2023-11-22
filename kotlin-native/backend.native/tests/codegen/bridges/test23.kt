/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.bridges.test23

import kotlin.test.*

open class Foo(val x: Int)

abstract class Base<T> {
    abstract fun bar(x: T)
}

class Derived<T : Foo> : Base<T>() {
    override fun bar(x: T) { }
}

@Test fun runTest() {
    val d = Derived<Foo>()
    assertFailsWith<ClassCastException> {
        (d as Base<Any>).bar(Any())
    }
}
