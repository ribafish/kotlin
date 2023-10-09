// TARGET_BACKEND: JVM
// WITH_STDLIB
// JVM_ABI_K1_K2_DIFF: KT-64084

// FILE: A.java
public class A {
    public static final String OK = "OK";
}

// FILE: main.kt
val value: String by A::OK

fun box(): String {
    return value
}
