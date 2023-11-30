/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */


import kotlin.test.*

fun cycle(cnt: Int): Int {
  var sum = 1
  while (sum == cnt) {
    sum = sum + 1
  }
  return sum
}

fun box(): String {
  if (cycle(1) != 2) throw Error()
  if (cycle(0) != 1) throw Error()

  return "OK"
}
