/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
// OUTPUT_DATA_FILE: initializers3.out

import kotlin.test.*

class Foo(val bar: Int)

var x = Foo(42)

fun box(): String {
    println(x.bar)

    return "OK"
}