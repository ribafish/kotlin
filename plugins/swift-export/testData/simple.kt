// WITH_STDLIB

// FILE: foo.kt
fun foo(): String = "123"

// FILE: bar.kt
fun bar(): String = "123"

// FILE: main.kt
fun foobar() = "${foo()}-${bar()}"
