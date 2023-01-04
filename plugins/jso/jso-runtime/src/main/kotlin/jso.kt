/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.jso

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
internal annotation class JsObjectWithBuilder

public inline fun <T: Any> jso(builder: T.() -> Unit): T {
    val jso = js("{}").unsafeCast<T>()
    return jso.apply { builder() }
}