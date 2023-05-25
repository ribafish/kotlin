@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kt57640.*
import kotlin.test.*

class GrandDerived: InterfaceDerivedWithoutPropertyOverride() {}

/**
 * class KotlinInterfaceDerived would cause errors
 * error: class 'KotlinInterfaceDerived' must override public final var delegate: InterfaceBase? defined in library.InterfaceBase because it inherits many implementations of it
 * error: 'public open external fun delegate(): InterfaceBase? defined in KotlinInterfaceDerived' clashes with 'public abstract fun delegate(): NSInteger /* = Long */ defined in library.IntegerPropertyProtocol': return types are incompatible
 * error: 'public final var delegate: InterfaceBase? defined in KotlinInterfaceDerived' clashes with 'public final val delegate: NSInteger /* = Long */ defined in library.IntegerPropertyProtocol': property types do not match
 */
// class KotlinInterfaceDerived: InterfaceBase(), IntegerPropertyProtocol, UIntegerPropertyProtocol

fun main() {
    testBase()
    testDerivedWithoutPropertyOverride()
    testDerivedWithPropertyReadonlyOverride()
    testDerivedWithPropertyReadWriteOverride()
    testGrandDerived()
}

private fun testBase() {
    val base = InterfaceBase()
    // values of `delegate` and `delegate()` are zero initially
    val delegate00_InterfaceBase: InterfaceBase? = base.delegate
    assertEquals(null, delegate00_InterfaceBase)
    val delegate01_InterfaceBase: InterfaceBase? = base.delegate()
    assertEquals(null, delegate01_InterfaceBase)

    base.delegate = base
    val delegate02_InterfaceBase: InterfaceBase? = base.delegate
    assertEquals(base, delegate02_InterfaceBase)
    // `delegate()` is just a getter for `delegate`
    val delegate03_InterfaceBase: InterfaceBase? = base.delegate()
    assertEquals(base, delegate03_InterfaceBase)
}

private fun testDerivedWithoutPropertyOverride() {
    // Note: `clang -Werror` would raise error for InterfaceDerivedWithoutPropertyOverride:
    // error: 'retain (or strong)' attribute on property 'delegate' does not match the property inherited from 'IntegerProperty' [-Wproperty-attribute-mismatch]

    val derivedWithoutOverride = InterfaceDerivedWithoutPropertyOverride()
    // values of `delegate` and `delegate()` are zero initially
    val delegate10_InterfaceBase: InterfaceBase? = derivedWithoutOverride.delegate
    assertEquals(null, delegate10_InterfaceBase)
    val delegate11_ULong: ULong = derivedWithoutOverride.delegate()
    assertEquals(0UL, delegate11_ULong)

    derivedWithoutOverride.delegate = derivedWithoutOverride
    val delegate12_InterfaceBase: InterfaceBase? = derivedWithoutOverride.delegate
    assertEquals(derivedWithoutOverride, delegate12_InterfaceBase)
    val delegate13_InterfaceBase: InterfaceBase? = (derivedWithoutOverride as InterfaceBase).delegate
    assertEquals(derivedWithoutOverride, delegate13_InterfaceBase)
    // getters in protocols get bitcasted value from backing field in base class.
    val delegate14_Long: Long = (derivedWithoutOverride as IntegerPropertyProtocol).delegate
    assertNotEquals(0L, delegate14_Long)
    val delegate15_ULong: ULong = (derivedWithoutOverride as UIntegerPropertyProtocol).delegate
    assertNotEquals(0UL, delegate15_ULong)

    // value of all `delegate()` should be changed when backing field is changed,
    // since they share the same bitmask.
    val delegate16_ULong: ULong = derivedWithoutOverride.delegate()
    assertNotEquals(0UL, delegate16_ULong)
    val delegate17_InterfaceBase: InterfaceBase? = (derivedWithoutOverride as InterfaceBase).delegate()
    assertEquals(derivedWithoutOverride, delegate17_InterfaceBase)
    val delegate18_Long: Long = (derivedWithoutOverride as IntegerPropertyProtocol).delegate()
    assertNotEquals(0L, delegate18_Long)
    val delegate19_ULong: ULong = (derivedWithoutOverride as UIntegerPropertyProtocol).delegate()
    assertNotEquals(0UL, delegate19_ULong)
}

private fun testDerivedWithPropertyReadonlyOverride() {
    val derivedWithOverride = InterfaceDerivedWithPropertyReadonlyOverride()
    // values of `delegate` and `delegate()` are zero initially
    // WARNING: inferred type of backing field is `InterfaceBase?`, not expected `InterfaceDerivedWithPropertyReadonlyOverride?`
    val delegate10_InterfaceBase: InterfaceBase? = derivedWithOverride.delegate
    assertEquals(null, delegate10_InterfaceBase)
    val delegate11_InterfaceDerivedWithPropertyReadonlyOverride: InterfaceDerivedWithPropertyReadonlyOverride? = derivedWithOverride.delegate()
    assertEquals(null, delegate11_InterfaceDerivedWithPropertyReadonlyOverride)

    derivedWithOverride.delegate = derivedWithOverride
    val delegate12_InterfaceBase: InterfaceBase? = derivedWithOverride.delegate
    assertEquals(derivedWithOverride, delegate12_InterfaceBase)
    val delegate13_InterfaceBase: InterfaceBase? = (derivedWithOverride as InterfaceBase).delegate
    assertEquals(derivedWithOverride, delegate13_InterfaceBase)
    // getters in protocols get bitcasted value from backing field in base class.
    val delegate14_Long: Long = (derivedWithOverride as IntegerPropertyProtocol).delegate
    assertNotEquals(0L, delegate14_Long)
    val delegate15_ULong: ULong = (derivedWithOverride as UIntegerPropertyProtocol).delegate
    assertNotEquals(0UL, delegate15_ULong)

    // value of all `delegate()` should be changed when backing field is changed,
    // since they share the same bitmask.
    val delegate16_InterfaceDerivedWithPropertyReadonlyOverride: InterfaceDerivedWithPropertyReadonlyOverride? = derivedWithOverride.delegate()
    assertEquals(derivedWithOverride, delegate16_InterfaceDerivedWithPropertyReadonlyOverride)
    val delegate17_InterfaceBase: InterfaceBase? = (derivedWithOverride as InterfaceBase).delegate()
    assertEquals(derivedWithOverride, delegate17_InterfaceBase)
    val delegate18_Long: Long = (derivedWithOverride as IntegerPropertyProtocol).delegate()
    assertNotEquals(0L, delegate18_Long)
    val delegate19_ULong: ULong = (derivedWithOverride as UIntegerPropertyProtocol).delegate()
    assertNotEquals(0UL, delegate19_ULong)
}

private fun testDerivedWithPropertyReadWriteOverride() {
    val derivedWithOverride = InterfaceDerivedWithPropertyReadWriteOverride()
    // values of `delegate` and `delegate()` are zero initially
    // WARNING: inferred type of backing field is `InterfaceBase?`, not expected `InterfaceDerivedWithPropertyReadWriteOverride?`
    val delegate10_InterfaceBase: InterfaceBase? = derivedWithOverride.delegate
    assertEquals(null, delegate10_InterfaceBase)

    val delegate11_InterfaceDerivedWithPropertyReadWriteOverride: InterfaceDerivedWithPropertyReadWriteOverride? = derivedWithOverride.delegate()
    assertEquals(null, delegate11_InterfaceDerivedWithPropertyReadWriteOverride)

    derivedWithOverride.delegate = derivedWithOverride
    val delegate12_InterfaceBase: InterfaceBase? = derivedWithOverride.delegate
    assertEquals(derivedWithOverride, delegate12_InterfaceBase)
    val delegate13_InterfaceBase: InterfaceBase? = (derivedWithOverride as InterfaceBase).delegate
    assertEquals(derivedWithOverride, delegate13_InterfaceBase)
    // getters in protocols get bitcasted value from backing field in base class.
    val delegate14_Long: Long = (derivedWithOverride as IntegerPropertyReadWriteProtocol).delegate
    assertNotEquals(0L, delegate14_Long)
    val delegate15_ULong: ULong = (derivedWithOverride as UIntegerPropertyReadWriteProtocol).delegate
    assertNotEquals(0UL, delegate15_ULong)

    // value of all `delegate()` should be changed when backing field is changed,
    // since they share the same bitmask.
    val delegate16_InterfaceDerivedWithPropertyReadWriteOverride: InterfaceDerivedWithPropertyReadWriteOverride? = derivedWithOverride.delegate()
    assertEquals(derivedWithOverride, delegate16_InterfaceDerivedWithPropertyReadWriteOverride)
    val delegate17_InterfaceBase: InterfaceBase? = (derivedWithOverride as InterfaceBase).delegate()
    assertEquals(derivedWithOverride, delegate17_InterfaceBase)
    val delegate18_Long: Long = (derivedWithOverride as IntegerPropertyReadWriteProtocol).delegate()
    assertNotEquals(0L, delegate18_Long)
    val delegate19_ULong: ULong = (derivedWithOverride as UIntegerPropertyReadWriteProtocol).delegate()
    assertNotEquals(0UL, delegate19_ULong)
}

private fun testGrandDerived() {
    val grandDerived = GrandDerived()
    // values of `delegate` and `delegate()` are zero initially
    val delegate10_InterfaceBase: InterfaceBase? = grandDerived.delegate
    assertEquals(null, delegate10_InterfaceBase)
    val delegate11_ULong: ULong = grandDerived.delegate()
    assertEquals(0UL, delegate11_ULong)

    grandDerived.delegate = grandDerived
    val delegate12_InterfaceBase: InterfaceBase? = grandDerived.delegate
    assertEquals(grandDerived, delegate12_InterfaceBase)
    val delegate13_InterfaceBase: InterfaceBase? = (grandDerived as InterfaceBase).delegate
    assertEquals(grandDerived, delegate13_InterfaceBase)
    // getters in protocols get bitcasted value from backing field in base class.
    val delegate14_Long: Long = (grandDerived as IntegerPropertyProtocol).delegate
    assertNotEquals(0L, delegate14_Long)
    val delegate15_ULong: ULong = (grandDerived as UIntegerPropertyProtocol).delegate
    assertNotEquals(0UL, delegate15_ULong)

    // value of all `delegate()` should be changed when backing field is changed,
    // since they share the same bitmask.
    val delegate16_ULong: ULong = grandDerived.delegate()
    assertNotEquals(0UL, delegate16_ULong)
    val delegate17_InterfaceBase: InterfaceBase? = (grandDerived as InterfaceBase).delegate()
    assertEquals(grandDerived, delegate17_InterfaceBase)
    val delegate18_Long: Long = (grandDerived as IntegerPropertyProtocol).delegate()
    assertNotEquals(0L, delegate18_Long)
    val delegate19_ULong: ULong = (grandDerived as UIntegerPropertyProtocol).delegate()
    assertNotEquals(0UL, delegate19_ULong)
}
