/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.serialization.signature.PublicIdSignatureComputer
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.UnsignedType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.ir.BuiltInOperatorNames
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.OperatorNameConventions
import kotlin.reflect.KProperty

class IrBuiltInsOverFir(
    private val components: Fir2IrComponents,
    override val languageVersionSettings: LanguageVersionSettings,
    private val moduleDescriptor: FirModuleDescriptor,
    irMangler: KotlinMangler.IrMangler
) : IrBuiltIns() {

    override val irFactory: IrFactory = components.symbolTable.table.irFactory

    private val kotlinPackage = StandardClassIds.BASE_KOTLIN_PACKAGE

    override val operatorsPackageFragment = createPackage(KOTLIN_INTERNAL_IR_FQN)

    private val irSignatureBuilder = PublicIdSignatureComputer(irMangler)

    override val booleanNotSymbol: IrSimpleFunctionSymbol by lazy {
        booleanClass.owner.functions.first { it.name == OperatorNameConventions.NOT && it.returnType == booleanType }.symbol
    }

    override val anyClass: IrClassSymbol by lazy {
        components.externalDeclarationsGenerator.findDependencyClassByClassId(StandardClassIds.Any)!!
    }

    override val anyType: IrType get() = anyClass.owner.defaultType
    override val anyNType by lazy { anyType.makeNullable() }

    override val numberClass: IrClassSymbol by loadClass(StandardClassIds.Number)
    override val numberType: IrType get() = numberClass.owner.defaultType

    override val nothingClass: IrClassSymbol by loadClass(StandardClassIds.Nothing)
    override val nothingType: IrType get() = nothingClass.owner.defaultType
    override val nothingNType: IrType by lazy { nothingType.makeNullable() }

    override val unitClass: IrClassSymbol by loadClass(StandardClassIds.Unit)
    override val unitType: IrType get() = unitClass.owner.defaultType

    override val booleanClass: IrClassSymbol by loadClass(StandardClassIds.Boolean)
    override val booleanType: IrType get() = booleanClass.owner.defaultType

    override val charClass: IrClassSymbol by loadClass(StandardClassIds.Char)
    override val charType: IrType get() = charClass.owner.defaultType

    override val byteClass: IrClassSymbol by loadClass(StandardClassIds.Byte)
    override val byteType: IrType get() = byteClass.owner.defaultType

    override val shortClass: IrClassSymbol by loadClass(StandardClassIds.Short)
    override val shortType: IrType get() = shortClass.owner.defaultType

    override val intClass: IrClassSymbol by loadClass(StandardClassIds.Int)
    override val intType: IrType get() = intClass.owner.defaultType

    override val longClass: IrClassSymbol by loadClass(StandardClassIds.Long)
    override val longType: IrType get() = longClass.owner.defaultType

    override val floatClass: IrClassSymbol by loadClass(StandardClassIds.Float)
    override val floatType: IrType get() = floatClass.owner.defaultType

    override val doubleClass: IrClassSymbol by loadClass(StandardClassIds.Double)
    override val doubleType: IrType get() = doubleClass.owner.defaultType

    override val charSequenceClass: IrClassSymbol by loadClass(StandardClassIds.CharSequence)

    override val stringClass: IrClassSymbol by loadClass(StandardClassIds.String)
    override val stringType: IrType get() = stringClass.owner.defaultType

    override val iteratorClass: IrClassSymbol by loadClass(StandardClassIds.Iterator)
    override val arrayClass: IrClassSymbol by loadClass(StandardClassIds.Array)

    override val annotationClass: IrClassSymbol by loadClass(StandardClassIds.Annotation)
    override val annotationType: IrType get() = annotationClass.owner.defaultType

    override val collectionClass: IrClassSymbol by loadClass(StandardClassIds.Collection)
    override val setClass: IrClassSymbol by loadClass(StandardClassIds.Set)
    override val listClass: IrClassSymbol by loadClass(StandardClassIds.List)
    override val mapClass: IrClassSymbol by loadClass(StandardClassIds.Map)
    private val mapEntry by BuiltInsClass({ true to referenceClassByClassId(StandardClassIds.MapEntry)!! })
    override val mapEntryClass: IrClassSymbol get() = mapEntry.klass

    override val iterableClass: IrClassSymbol by loadClass(StandardClassIds.Iterable)
    override val listIteratorClass: IrClassSymbol by loadClass(StandardClassIds.ListIterator)
    override val mutableCollectionClass: IrClassSymbol by loadClass(StandardClassIds.MutableCollection)
    override val mutableSetClass: IrClassSymbol by loadClass(StandardClassIds.MutableSet)
    override val mutableListClass: IrClassSymbol by loadClass(StandardClassIds.MutableList)
    override val mutableMapClass: IrClassSymbol by loadClass(StandardClassIds.MutableMap)
    private val mutableMapEntry by BuiltInsClass({ true to referenceClassByClassId(StandardClassIds.MutableMapEntry)!! })
    override val mutableMapEntryClass: IrClassSymbol get() = mutableMapEntry.klass

    override val mutableIterableClass: IrClassSymbol by loadClass(StandardClassIds.MutableIterable)
    override val mutableIteratorClass: IrClassSymbol by loadClass(StandardClassIds.MutableIterator)
    override val mutableListIteratorClass: IrClassSymbol by loadClass(StandardClassIds.MutableListIterator)
    override val comparableClass: IrClassSymbol by loadClass(StandardClassIds.Comparable)
    override val throwableType: IrType by lazy { throwableClass.defaultType }
    override val throwableClass: IrClassSymbol by loadClass(StandardClassIds.Throwable)

    override val kCallableClass: IrClassSymbol by loadClass(StandardClassIds.KCallable)
    override val kPropertyClass: IrClassSymbol by loadClass(StandardClassIds.KProperty)
    override val kClassClass: IrClassSymbol by loadClass(StandardClassIds.KClass)
    override val kTypeClass: IrClassSymbol by loadClass(StandardClassIds.KType)
    override val kProperty0Class: IrClassSymbol by loadClass(StandardClassIds.KProperty0)
    override val kProperty1Class: IrClassSymbol by loadClass(StandardClassIds.KProperty1)
    override val kProperty2Class: IrClassSymbol by loadClass(StandardClassIds.KProperty2)
    override val kMutableProperty0Class: IrClassSymbol by loadClass(StandardClassIds.KMutableProperty0)
    override val kMutableProperty1Class: IrClassSymbol by loadClass(StandardClassIds.KMutableProperty1)
    override val kMutableProperty2Class: IrClassSymbol by loadClass(StandardClassIds.KMutableProperty2)

    override val functionClass: IrClassSymbol by loadClass(StandardClassIds.Function)
    override val kFunctionClass: IrClassSymbol by loadClass(StandardClassIds.KFunction)

    override val primitiveTypeToIrType by lazy {
        mapOf(
            PrimitiveType.BOOLEAN to booleanType,
            PrimitiveType.CHAR to charType,
            PrimitiveType.BYTE to byteType,
            PrimitiveType.SHORT to shortType,
            PrimitiveType.INT to intType,
            PrimitiveType.LONG to longType,
            PrimitiveType.FLOAT to floatType,
            PrimitiveType.DOUBLE to doubleType
        )
    }

    private val primitiveIntegralIrTypes by lazy { listOf(byteType, shortType, intType, longType) }
    override val primitiveFloatingPointIrTypes by lazy { listOf(floatType, doubleType) }
    private val primitiveNumericIrTypes by lazy { primitiveIntegralIrTypes + primitiveFloatingPointIrTypes }
    override val primitiveIrTypesWithComparisons by lazy { listOf(charType) + primitiveNumericIrTypes }
    override val primitiveIrTypes by lazy { listOf(booleanType) + primitiveIrTypesWithComparisons }
    private val baseIrTypes by lazy { primitiveIrTypes + stringType }

    private fun loadPrimitiveArray(primitiveType: PrimitiveType): Lazy<IrClassSymbol> {
        return loadClass(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("${primitiveType.typeName}Array")))
    }

    override val booleanArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.BOOLEAN)
    override val charArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.CHAR)
    override val byteArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.BYTE)
    override val shortArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.SHORT)
    override val intArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.INT)
    override val longArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.LONG)
    override val floatArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.FLOAT)
    override val doubleArray: IrClassSymbol by loadPrimitiveArray(PrimitiveType.DOUBLE)

    override val primitiveArraysToPrimitiveTypes: Map<IrClassSymbol, PrimitiveType> by lazy {
        mapOf(
            booleanArray to PrimitiveType.BOOLEAN,
            charArray to PrimitiveType.CHAR,
            byteArray to PrimitiveType.BYTE,
            shortArray to PrimitiveType.SHORT,
            intArray to PrimitiveType.INT,
            longArray to PrimitiveType.LONG,
            floatArray to PrimitiveType.FLOAT,
            doubleArray to PrimitiveType.DOUBLE
        )
    }

    override val primitiveTypesToPrimitiveArrays get() = primitiveArraysToPrimitiveTypes.map { (k, v) -> v to k }.toMap()
    override val primitiveArrayElementTypes get() = primitiveArraysToPrimitiveTypes.mapValues { primitiveTypeToIrType[it.value] }
    override val primitiveArrayForType get() = primitiveArrayElementTypes.asSequence().associate { it.value to it.key }

    private val _ieee754equalsFunByOperandType = mutableMapOf<IrClassifierSymbol, IrSimpleFunctionSymbol>()
    override val ieee754equalsFunByOperandType: MutableMap<IrClassifierSymbol, IrSimpleFunctionSymbol>
        get() = _ieee754equalsFunByOperandType

    override var eqeqeqSymbol: IrSimpleFunctionSymbol private set
    override var eqeqSymbol: IrSimpleFunctionSymbol private set
    override var throwCceSymbol: IrSimpleFunctionSymbol private set
    override var throwIseSymbol: IrSimpleFunctionSymbol private set
    override var andandSymbol: IrSimpleFunctionSymbol private set
    override var ororSymbol: IrSimpleFunctionSymbol private set
    override var noWhenBranchMatchedExceptionSymbol: IrSimpleFunctionSymbol private set
    override var illegalArgumentExceptionSymbol: IrSimpleFunctionSymbol private set
    override var dataClassArrayMemberHashCodeSymbol: IrSimpleFunctionSymbol private set
    override var dataClassArrayMemberToStringSymbol: IrSimpleFunctionSymbol private set

    override var checkNotNullSymbol: IrSimpleFunctionSymbol private set
    override val arrayOfNulls: IrSimpleFunctionSymbol by lazy {
        findFunctions(kotlinPackage, Name.identifier("arrayOfNulls")).first {
            it.owner.dispatchReceiverParameter == null && it.owner.valueParameters.size == 1 &&
                    it.owner.valueParameters[0].type == intType
        }
    }

    override val linkageErrorSymbol: IrSimpleFunctionSymbol
        get() = TODO("Not yet implemented")

    override var lessFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override var lessOrEqualFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override var greaterOrEqualFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override var greaterFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set

    init {
        with(this.operatorsPackageFragment) {

            fun addBuiltinOperatorSymbol(
                name: String,
                returnType: IrType,
                vararg valueParameterTypes: Pair<String, IrType>,
                isIntrinsicConst: Boolean = false,
            ) =
                createFunction(name, returnType, valueParameterTypes, origin = BUILTIN_OPERATOR, isIntrinsicConst = isIntrinsicConst).also {
                    declarations.add(it)
                }.symbol

            primitiveFloatingPointIrTypes.forEach { fpType ->
                _ieee754equalsFunByOperandType[fpType.classifierOrFail] = addBuiltinOperatorSymbol(
                    BuiltInOperatorNames.IEEE754_EQUALS,
                    booleanType,
                    "arg0" to fpType.makeNullable(),
                    "arg1" to fpType.makeNullable(),
                    isIntrinsicConst = true
                )
            }
            eqeqeqSymbol =
                addBuiltinOperatorSymbol(BuiltInOperatorNames.EQEQEQ, booleanType, "" to anyNType, "" to anyNType)
            eqeqSymbol =
                addBuiltinOperatorSymbol(BuiltInOperatorNames.EQEQ, booleanType, "" to anyNType, "" to anyNType, isIntrinsicConst = true)
            throwCceSymbol = addBuiltinOperatorSymbol(BuiltInOperatorNames.THROW_CCE, nothingType)
            throwIseSymbol = addBuiltinOperatorSymbol(BuiltInOperatorNames.THROW_ISE, nothingType)
            andandSymbol =
                addBuiltinOperatorSymbol(
                    BuiltInOperatorNames.ANDAND,
                    booleanType,
                    "" to booleanType,
                    "" to booleanType,
                    isIntrinsicConst = true
                )
            ororSymbol =
                addBuiltinOperatorSymbol(
                    BuiltInOperatorNames.OROR,
                    booleanType,
                    "" to booleanType,
                    "" to booleanType,
                    isIntrinsicConst = true
                )
            noWhenBranchMatchedExceptionSymbol =
                addBuiltinOperatorSymbol(BuiltInOperatorNames.NO_WHEN_BRANCH_MATCHED_EXCEPTION, nothingType)
            illegalArgumentExceptionSymbol =
                addBuiltinOperatorSymbol(BuiltInOperatorNames.ILLEGAL_ARGUMENT_EXCEPTION, nothingType, "" to stringType)
            dataClassArrayMemberHashCodeSymbol = addBuiltinOperatorSymbol("dataClassArrayMemberHashCode", intType, "" to anyType)
            dataClassArrayMemberToStringSymbol = addBuiltinOperatorSymbol("dataClassArrayMemberToString", stringType, "" to anyNType)

            checkNotNullSymbol = run {
                val typeParameter: IrTypeParameter = irFactory.createTypeParameter(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    origin = BUILTIN_OPERATOR,
                    name = Name.identifier("T0"),
                    symbol = IrTypeParameterSymbolImpl(),
                    variance = Variance.INVARIANT,
                    index = 0,
                    isReified = true
                ).apply {
                    superTypes = listOf(anyType)
                }

                createFunction(
                    BuiltInOperatorNames.CHECK_NOT_NULL,
                    IrSimpleTypeImpl(typeParameter.symbol, SimpleTypeNullability.DEFINITELY_NOT_NULL, emptyList(), emptyList()),
                    arrayOf("" to IrSimpleTypeImpl(typeParameter.symbol, hasQuestionMark = true, emptyList(), emptyList())),
                    typeParameters = listOf(typeParameter),
                    origin = BUILTIN_OPERATOR
                ).also {
                    declarations.add(it)
                }.symbol
            }

            fun List<IrType>.defineComparisonOperatorForEachIrType(name: String) =
                associate {
                    it.classifierOrFail to addBuiltinOperatorSymbol(
                        name,
                        booleanType,
                        "" to it,
                        "" to it,
                        isIntrinsicConst = true
                    )
                }

            lessFunByOperandType = primitiveIrTypesWithComparisons.defineComparisonOperatorForEachIrType(BuiltInOperatorNames.LESS)
            lessOrEqualFunByOperandType =
                primitiveIrTypesWithComparisons.defineComparisonOperatorForEachIrType(BuiltInOperatorNames.LESS_OR_EQUAL)
            greaterOrEqualFunByOperandType =
                primitiveIrTypesWithComparisons.defineComparisonOperatorForEachIrType(BuiltInOperatorNames.GREATER_OR_EQUAL)
            greaterFunByOperandType = primitiveIrTypesWithComparisons.defineComparisonOperatorForEachIrType(BuiltInOperatorNames.GREATER)

        }
    }

    override val unsignedTypesToUnsignedArrays: Map<UnsignedType, IrClassSymbol> by lazy {
        UnsignedType.entries.mapNotNull { unsignedType ->
            val array = referenceClassByClassId(unsignedType.arrayClassId)
            if (array == null) null else unsignedType to array
        }.toMap()
    }

    override val unsignedArraysElementTypes: Map<IrClassSymbol, IrType?> by lazy {
        unsignedTypesToUnsignedArrays.map { (k, v) -> v to referenceClassByClassId(k.classId)?.owner?.defaultType }.toMap()
    }

    override fun getKPropertyClass(mutable: Boolean, n: Int): IrClassSymbol = when (n) {
        0 -> if (mutable) kMutableProperty0Class else kProperty0Class
        1 -> if (mutable) kMutableProperty1Class else kProperty1Class
        2 -> if (mutable) kMutableProperty2Class else kProperty2Class
        else -> error("No KProperty for n=$n mutable=$mutable")
    }

    override val enumClass: IrClassSymbol by loadClass(StandardClassIds.Enum)

    override val intPlusSymbol: IrSimpleFunctionSymbol
        get() = intClass.functions.single {
            it.owner.name == OperatorNameConventions.PLUS && it.owner.valueParameters[0].type == intType
        }

    override val intTimesSymbol: IrSimpleFunctionSymbol
        get() = intClass.functions.single {
            it.owner.name == OperatorNameConventions.TIMES && it.owner.valueParameters[0].type == intType
        }

    override val intXorSymbol: IrSimpleFunctionSymbol
        get() = intClass.functions.single {
            it.owner.name == OperatorNameConventions.XOR && it.owner.valueParameters[0].type == intType
        }

    override val extensionToString: IrSimpleFunctionSymbol by lazy {
        findFunctions(kotlinPackage, OperatorNameConventions.TO_STRING).single { function ->
            function.owner.extensionReceiverParameter?.let { receiver -> receiver.type == anyNType } ?: false
        }
    }

    override val memberToString: IrSimpleFunctionSymbol by lazy {
        findBuiltInClassMemberFunctions(anyClass, OperatorNameConventions.TO_STRING).single { function ->
            function.owner.valueParameters.isEmpty()
        }
    }

    override val extensionStringPlus: IrSimpleFunctionSymbol by lazy {
        findFunctions(kotlinPackage, OperatorNameConventions.PLUS).single { function ->
            val isStringExtension =
                function.owner.extensionReceiverParameter?.let { receiver -> receiver.type == stringType.makeNullable() }
                    ?: false
            isStringExtension && function.owner.valueParameters.size == 1 && function.owner.valueParameters[0].type == anyNType
        }
    }

    override val memberStringPlus: IrSimpleFunctionSymbol by lazy {
        findBuiltInClassMemberFunctions(stringClass, OperatorNameConventions.PLUS).single { function ->
            function.owner.valueParameters.size == 1 && function.owner.valueParameters[0].type == anyNType
        }
    }

    override val arrayOf: IrSimpleFunctionSymbol by lazy {
        // distinct() is needed because we can get two Fir symbols for arrayOf function (from builtins and from stdlib)
        //   with the same IR symbol for them
        findFunctions(kotlinPackage, Name.identifier("arrayOf")).distinct().single()
    }

    private fun <T : Any> getFunctionsByKey(
        name: Name,
        vararg packageNameSegments: String,
        makeKey: (IrSimpleFunctionSymbol) -> T?,
    ): Map<T, IrSimpleFunctionSymbol> {
        val result = mutableMapOf<T, IrSimpleFunctionSymbol>()
        for (fn in findFunctions(name, *packageNameSegments)) {
            makeKey(fn)?.let { key ->
                result[key] = fn
            }
        }
        return result
    }

    override fun getNonBuiltInFunctionsByExtensionReceiver(
        name: Name, vararg packageNameSegments: String,
    ): Map<IrClassifierSymbol, IrSimpleFunctionSymbol> =
        getFunctionsByKey(name, *packageNameSegments) { fn ->
            fn.owner.extensionReceiverParameter?.type?.classifierOrNull
        }

    override fun getNonBuiltinFunctionsByReturnType(
        name: Name, vararg packageNameSegments: String,
    ): Map<IrClassifierSymbol, IrSimpleFunctionSymbol> =
        getFunctionsByKey(name, *packageNameSegments) { fn ->
            fn.owner.returnType.classOrNull
        }

    private val functionNMap = mutableMapOf<Int, IrClass>()
    private val kFunctionNMap = mutableMapOf<Int, IrClass>()
    private val suspendFunctionNMap = mutableMapOf<Int, IrClass>()
    private val kSuspendFunctionNMap = mutableMapOf<Int, IrClass>()

    override fun functionN(arity: Int): IrClass = functionNMap.getOrPut(arity) {
        referenceClassByClassId(StandardClassIds.FunctionN(arity))!!.owner
    }

    override fun kFunctionN(arity: Int): IrClass = kFunctionNMap.getOrPut(arity) {
        referenceClassByClassId(StandardClassIds.KFunctionN(arity))!!.owner
    }

    override fun suspendFunctionN(arity: Int): IrClass = suspendFunctionNMap.getOrPut(arity) {
        referenceClassByClassId(StandardClassIds.SuspendFunctionN(arity))!!.owner
    }

    override fun kSuspendFunctionN(arity: Int): IrClass = kSuspendFunctionNMap.getOrPut(arity) {
        referenceClassByClassId(StandardClassIds.KSuspendFunctionN(arity))!!.owner
    }

    override fun findFunctions(name: Name, vararg packageNameSegments: String): Iterable<IrSimpleFunctionSymbol> =
        findFunctions(FqName.fromSegments(packageNameSegments.asList()), name)

    override fun findFunctions(name: Name, packageFqName: FqName): Iterable<IrSimpleFunctionSymbol> =
        findFunctions(packageFqName, name)

    override fun findProperties(name: Name, packageFqName: FqName): Iterable<IrPropertySymbol> =
        findProperties(packageFqName, name)

    override fun findClass(name: Name, vararg packageNameSegments: String): IrClassSymbol? =
        referenceClassByFqname(FqName.fromSegments(packageNameSegments.asList()), name)

    override fun findClass(name: Name, packageFqName: FqName): IrClassSymbol? =
        referenceClassByFqname(packageFqName, name)

    private fun referenceClassByFqname(packageName: FqName, identifier: Name) =
        referenceClassByClassId(ClassId(packageName, identifier))

    private val builtInClasses by lazy {
        setOf(anyClass)
    }

    override fun findBuiltInClassMemberFunctions(builtInClass: IrClassSymbol, name: Name): Iterable<IrSimpleFunctionSymbol> {
        require(builtInClass in builtInClasses)
        return builtInClass.functions.filter { it.owner.name == name }.asIterable()
    }

    override fun getBinaryOperator(name: Name, lhsType: IrType, rhsType: IrType): IrSimpleFunctionSymbol {
        val definingClass = lhsType.getMaybeBuiltinClass() ?: error("Defining class not found: $lhsType")
        return definingClass.functions.single { function ->
            function.name == name && function.valueParameters.size == 1 && function.valueParameters[0].type == rhsType
        }.symbol
    }

    override fun getUnaryOperator(name: Name, receiverType: IrType): IrSimpleFunctionSymbol {
        val definingClass = receiverType.getMaybeBuiltinClass() ?: error("Defining class not found: $receiverType")
        return definingClass.functions.single { function ->
            function.name == name && function.valueParameters.isEmpty()
        }.symbol
    }

// ---------------

    class BuiltInClassValue(
        private val generatedClass: IrClassSymbol,
        private var lazyContents: (IrClass.() -> Unit)?,
    ) {
        fun ensureLazyContentsCreated() {
            if (lazyContents != null) synchronized(this) {
                lazyContents?.invoke(generatedClass.owner)
                lazyContents = null
            }
        }

        val klass: IrClassSymbol
            get() {
                ensureLazyContentsCreated()
                return generatedClass
            }

        val type: IrType get() = generatedClass.defaultType
    }

    private inner class BuiltInsClass(
        private var generator: (() -> Pair<Boolean, IrClassSymbol>)?,
        private var lazyContents: (IrClass.() -> Unit)? = null,
    ) {

        private var value: BuiltInClassValue? = null

        operator fun getValue(thisRef: Any?, property: KProperty<*>): BuiltInClassValue = value ?: run {
            synchronized(this) {
                if (value == null) {
                    val (isLoaded, symbol) = generator!!()
                    value = BuiltInClassValue(symbol, if (isLoaded) null else lazyContents)
                    generator = null
                    lazyContents = null
                }
            }
            value!!
        }
    }

    private fun loadClass(classId: ClassId): Lazy<IrClassSymbol> {
        return lazy { components.externalDeclarationsGenerator.findDependencyClassByClassId(classId)!! }
    }

    private fun referenceClassByFqname(topLevelFqName: FqName) =
        referenceClassByClassId(ClassId.topLevel(topLevelFqName))

    private fun referenceClassByClassId(classId: ClassId): IrClassSymbol? {
        return components.externalDeclarationsGenerator.findDependencyClassByClassId(classId)
    }

    private fun IrType.getMaybeBuiltinClass(): IrClass? {
        val lhsClassFqName = classFqName!!
        return baseIrTypes.find { it.classFqName == lhsClassFqName }?.getClass()
            ?: referenceClassByFqname(lhsClassFqName)?.owner
    }

    private fun createPackage(fqName: FqName): IrExternalPackageFragment =
        IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(moduleDescriptor, fqName)

    private fun IrClass.forEachSuperClass(body: IrClass.() -> Unit) {
        for (st in superTypes) {
            st.getClass()?.let {
                it.body()
                it.forEachSuperClass(body)
            }
        }
    }

    private fun IrClass.createMemberFunction(
        name: String, returnType: IrType, vararg valueParameterTypes: Pair<String, IrType>,
        origin: IrDeclarationOrigin = object : IrDeclarationOriginImpl("BUILTIN_CLASS_METHOD") {},
        modality: Modality = Modality.FINAL,
        isOperator: Boolean = false,
        isInfix: Boolean = false,
        isIntrinsicConst: Boolean = true,
        build: IrFunctionBuilder.() -> Unit = {},
    ) = createFunction(
        name, returnType, valueParameterTypes,
        origin = origin, modality = modality, isOperator = isOperator, isInfix = isInfix, isIntrinsicConst = isIntrinsicConst,
        postBuild = {
            addDispatchReceiver { type = this@createMemberFunction.defaultType }
        },
        build = build
    ).also { fn ->
        // very simple and fragile logic, but works for all current usages
        // TODO: replace with correct logic or explicit specification if cases become more complex
        forEachSuperClass {
            functions.find {
                it.name == fn.name && it.typeParameters.count() == fn.typeParameters.count() &&
                        it.valueParameters.count() == fn.valueParameters.count() &&
                        it.valueParameters.zip(fn.valueParameters).all { (l, r) -> l.type == r.type }
            }?.let {
                assert(it.symbol != fn) { "Cannot add function $fn to its own overriddenSymbols" }
                fn.overriddenSymbols += it.symbol
            }
        }

        declarations.add(fn)
    }

    private fun IrDeclarationParent.createFunction(
        name: String,
        returnType: IrType,
        valueParameterTypes: Array<out Pair<String, IrType>>,
        typeParameters: List<IrTypeParameter> = emptyList(),
        origin: IrDeclarationOrigin = IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB,
        modality: Modality = Modality.FINAL,
        isOperator: Boolean = false,
        isInfix: Boolean = false,
        isIntrinsicConst: Boolean = false,
        postBuild: IrSimpleFunction.() -> Unit = {},
        build: IrFunctionBuilder.() -> Unit = {},
    ): IrSimpleFunction {

        fun makeWithSymbol(symbol: IrSimpleFunctionSymbol) = IrFunctionBuilder().run {
            this.name = Name.identifier(name)
            this.returnType = returnType
            this.origin = origin
            this.modality = modality
            this.isOperator = isOperator
            this.isInfix = isInfix
            build()
            irFactory.createSimpleFunction(
                startOffset = startOffset,
                endOffset = endOffset,
                origin = this.origin,
                name = this.name,
                visibility = visibility,
                isInline = isInline,
                isExpect = isExpect,
                returnType = this.returnType,
                modality = this.modality,
                symbol = symbol,
                isTailrec = isTailrec,
                isSuspend = isSuspend,
                isOperator = this.isOperator,
                isInfix = this.isInfix,
                isExternal = isExternal,
                containerSource = containerSource,
                isFakeOverride = isFakeOverride,
            )
        }.also { fn ->
            valueParameterTypes.forEachIndexed { index, (pName, irType) ->
                fn.addValueParameter(Name.identifier(pName.ifBlank { "arg$index" }), irType, origin)
            }
            fn.typeParameters = typeParameters
            typeParameters.forEach { it.parent = fn }
            if (isIntrinsicConst) {
//                fn.annotations += intrinsicConstAnnotation
            }
            fn.parent = this@createFunction
            fn.postBuild()
        }

        val irFun4SignatureCalculation = makeWithSymbol(IrSimpleFunctionSymbolImpl())
        val signature = irSignatureBuilder.computeSignature(irFun4SignatureCalculation)
        return components.symbolTable.table.declareSimpleFunction(signature, { IrSimpleFunctionPublicSymbolImpl(signature, null) }, ::makeWithSymbol)
    }

    private fun findFunctions(packageName: FqName, name: Name): List<IrSimpleFunctionSymbol> =
        components.session.symbolProvider.getTopLevelFunctionSymbols(packageName, name).mapNotNull { firOpSymbol ->
            components.declarationStorage.getIrFunctionSymbol(firOpSymbol) as? IrSimpleFunctionSymbol
        }

    private fun findProperties(packageName: FqName, name: Name): List<IrPropertySymbol> =
        components.session.symbolProvider.getTopLevelPropertySymbols(packageName, name).mapNotNull { firOpSymbol ->
            components.declarationStorage.getIrPropertySymbol(firOpSymbol) as? IrPropertySymbol
        }
}
