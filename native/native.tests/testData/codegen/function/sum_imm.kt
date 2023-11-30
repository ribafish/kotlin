/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

fun sum(a:Int): Int = a + 33

fun box(): String {
    if (sum(2) != 35) throw Error()

    return "OK"
}
