package org.jetbrains.kotlin.abicmp

inline fun <reified T : Any> List<Any?>?.listOfNotNull() = orEmpty().filterIsInstance<T>()

const val PROPERTY_VAL_STUB = "---"