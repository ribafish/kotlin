/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
// OUTPUT_DATA_FILE: initializers1.out

import kotlin.test.*

class TestClass {
    companion object {
        init {
            println("Init Test")
        }
    }
}

fun box(): String {
    val t1 = TestClass()
    val t2 = TestClass()
    println("Done")

    return "OK"
}