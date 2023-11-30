// OUTPUT_DATA_FILE: inline20.out
/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

@Suppress("NOTHING_TO_INLINE")
inline fun bar(block: () -> String) : String {
    return block()
}

fun bar2() : String {
    return bar { return "def" }
}

fun box(): String {
    println(bar2())

    return "OK"
}
