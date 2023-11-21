/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.basics.unchecked_cast8

import kotlin.test.*

interface I1
interface I2 {
    val z: Int
}

open class Base<T : I1> {
    open fun foo(x: T): I1 = x
}

open class Derived<T> : Base<T>() where T : I1, T: I2 {
    override fun foo(x: T): I1 {
        println(x.z)
        return x
    }
}

class C : I1

class D : I1, I2 {
    override val z = 42
}

@Test
fun runTest() {
    try {
        val b = Derived<D>()
        (b as Base<C>).foo(C())
    } catch (e: ClassCastException) {
        println("Ok")
    }
}
