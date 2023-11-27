/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js

@JsName("ReadonlyArray")
public external interface JsImmutableArray<out E>

@JsName("Array")
public external open class JsMutableArray<E> : JsImmutableArray<E>

@JsName("ReadonlySet")
public external interface JsImmutableSet<out E>

@JsName("Set")
public external open class JsMutableSet<E> : JsImmutableSet<E>

@JsName("ReadonlyMap")
public external interface JsImmutableMap<K, out V>

@JsName("Map")
public external open class JsMutableMap<K, V> : JsImmutableMap<K, V>