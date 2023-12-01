// OUTPUT_DATA_FILE: cleaner_leak_without_checker.out
/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

import kotlin.test.*

import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

// This cleaner won't be run, because it's deinitialized with globals after
// cleaners are disabled.
val globalCleaner = createCleaner(42) {
    println(it)
}

fun box(): String {
    // Make sure cleaner is initialized.
    assertNotNull(globalCleaner)

    return "OK"
}
