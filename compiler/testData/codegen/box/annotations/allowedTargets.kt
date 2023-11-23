// WITH_REFLECT
// TARGET_BACKEND: JVM_IR
// DUMP_IR
// DUMP_SIGNATURES

import kotlin.reflect.KProperty
import java.lang.reflect.Type

interface IDelegate<T> {
    operator fun getValue(t: T, p: KProperty<*>)
}

val <T> T.property by object : IDelegate<T> {
    override fun getValue(t: T, p: KProperty<*>) {}
}

fun box(): String {
    val clazz = Class.forName("AllowedTargetsKt\$property\$2")
    val superInterfaces = clazz.getGenericInterfaces()
    return if (superInterfaces[0].toString() == "IDelegate<java.lang.Object>") "OK" else "FAIL"
}