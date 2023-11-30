/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

fun when2(i: Int): Int {
  when (i) {
    0 -> return 42
    else -> return 24
  }
}

fun box(): String {
  if (when2(0) != 42) return "FAIL: 0"

  return "OK"
}
