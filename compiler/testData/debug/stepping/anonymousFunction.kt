// IGNORE_BACKEND: WASM
// FILE: test.kt

fun <T> eval(f: () -> T) = f()

fun box() {
    eval {
        "OK"
    }
}

// EXPECTATIONS JVM JVM_IR
// test.kt:7 box
// test.kt:4 eval
// EXPECTATIONS JVM
// test.kt:8 invoke
// EXPECTATIONS JVM_IR
// test.kt:8 box$lambda$0
// EXPECTATIONS JVM JVM_IR
// test.kt:4 eval
// test.kt:7 box
// test.kt:10 box

// EXPECTATIONS JS_IR
// test.kt:6 box
// test.kt:3 eval
// test.kt:7 box$lambda
// test.kt:9 box
