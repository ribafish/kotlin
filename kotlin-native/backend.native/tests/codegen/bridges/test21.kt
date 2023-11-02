/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.bridges.test21

import kotlin.test.*

open class Base<T> {
    open var x: T? = null
}

open class Derived : Base<String>() {
    // override fun <get-x>: String? = super.<get-x> as String?
    // override fun <set-x>(value: String?) = super.<set-x>(value) // no bridge is needed
}

class Data(val x: Int)

fun garble(d: Derived) {
    (d as Base<Data>).x = Data(42)
}

@Test fun runTest() {
    val d = Derived()
    garble(d)
    assertFailsWith<ClassCastException> {
        println(d.x?.length)
    }
}
