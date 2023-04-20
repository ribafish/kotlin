// IGNORE_BACKEND: WASM
// FILE: test.kt

fun box() {
    foo({
            val a = 1
        })

    foo() {
        val a = 1
    }
}

fun foo(f: () -> Unit) {
    f()
}

// EXPECTATIONS JVM JVM_IR
// test.kt:5 box
// test.kt:15 foo
// EXPECTATIONS JVM
// test.kt:6 invoke
// test.kt:7 invoke
// EXPECTATIONS JVM_IR
// test.kt:6 box$lambda$0
// test.kt:7 box$lambda$0
// EXPECTATIONS JVM JVM_IR
// test.kt:15 foo
// test.kt:16 foo
// EXPECTATIONS JVM_IR
// test.kt:5 box
// EXPECTATIONS JVM JVM_IR
// test.kt:9 box
// test.kt:15 foo
// EXPECTATIONS JVM
// test.kt:10 invoke
// test.kt:11 invoke
// EXPECTATIONS JVM_IR
// test.kt:10 box$lambda$1
// test.kt:11 box$lambda$1
// EXPECTATIONS JVM JVM_IR
// test.kt:15 foo
// test.kt:16 foo
// test.kt:12 box

// EXPECTATIONS JS_IR
// test.kt:5 box
// test.kt:15 foo
// test.kt:6 box$lambda
// test.kt:7 box$lambda
// test.kt:16 foo
// test.kt:9 box
// test.kt:15 foo
// test.kt:10 box$lambda
// test.kt:11 box$lambda
// test.kt:16 foo
// test.kt:12 box
