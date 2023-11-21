import kotlin.annotation.AnnotationTarget.*

@Target(
    CLASS,
    TYPE_PARAMETER,
    PROPERTY,
    FIELD,
    VALUE_PARAMETER,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    TYPE,
    TYPEALIAS,
)
@Repeatable
annotation class A(val s: String)

@A("class-1")
@A("class-2")
class C<@A("class-type-param") T> @A("primary-ctor") constructor(
    @property:A("ctor-property") @param:A("ctor-param") val p: Int
) {
    @A("secondary-ctor") constructor() : this(0)

    @A("property")
    @field:A("field")
    @get:A("getter")
    @set:A("setter")
    @setparam:A("setparam-2")
    var q: Int = 1
        set(@A("setparam-1") value) {}

    @A("fun")
    fun <@A("fun-type-param") T> f(@A("fun-param-1") @A("fun-param-2") r: Any): @A("return-type") Unit {}

    @A("nested-class") class Nested
}

@A("typealias")
typealias Z = String
