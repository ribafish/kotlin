// OUTPUT_DATA_FILE: inline10.out
/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

@Suppress("NOTHING_TO_INLINE")
inline fun foo(i2: Int, body: () -> Int): Int {
    return i2 + body()
}

fun bar(i1: Int): Int {
    return foo(i1) { 1 }
}

fun box(): String {
    println(bar(1).toString())

    return "OK"
}
