/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.basics.unchecked_cast5

import kotlin.test.*

class Data(val x: Int)

@Test
fun runTest() {
    val arr = arrayOf("zzz")
    try {
        println((arr as Array<Data>)[0].x)
    } catch (e: ClassCastException) {
        println("Ok")
    }
}
