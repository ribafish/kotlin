// OUTPUT_DATA_FILE: localInitialized.out
/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

fun box(): String {
    lateinit var s: String
    s = "zzz"
    println(s)

    return "OK"
}
