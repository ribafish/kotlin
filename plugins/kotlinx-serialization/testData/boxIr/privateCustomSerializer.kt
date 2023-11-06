// TARGET_BACKEND: JVM_IR

// WITH_STDLIB

// FILE: serializer.kt

package a

import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.test.*

@Serializable(DataSerializer::class)
data class Data(
    val i: Int
)



@Serializer(forClass = Data::class)
private object DataSerializer

// FILE: holder.kt

package b

import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.test.*
import a.Data

@Serializable
data class Holder(
    val data: Data
)


fun box(): String {
    val json = Json.encodeToString(Holder(Data(1)))
    return if (json == "{\"data\":{\"i\":1}}") "OK" else json
}
