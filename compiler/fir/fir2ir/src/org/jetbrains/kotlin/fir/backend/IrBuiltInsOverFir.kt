/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.backend.common.serialization.signature.PublicIdSignatureComputer
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.UnsignedType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.ir.BuiltInOperatorNames
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionPublicSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.generateUnboundSymbolsAsDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.OperatorNameConventions

class IrBuiltInsOverFir(
    private val components: Fir2IrComponents,
    override val languageVersionSettings: LanguageVersionSettings,
    private val moduleDescriptor: FirModuleDescriptor,
    irMangler: KotlinMangler.IrMangler
) : IrBuiltIns() {
    private val session: FirSession
        get() = components.session

    private val symbolProvider: FirSymbolProvider
        get() = session.symbolProvider

    override val irFactory: IrFactory = components.symbolTable.table.irFactory

    private val kotlinPackage = StandardClassIds.BASE_KOTLIN_PACKAGE

    override val operatorsPackageFragment = createPackage(KOTLIN_INTERNAL_IR_FQN)

    private val irSignatureBuilder = PublicIdSignatureComputer(irMangler)

    override val booleanNotSymbol: IrSimpleFunctionSymbol = run {
        val firFunction = findFirMemberFunctions(StandardClassIds.Boolean, OperatorNameConventions.NOT).first { it.resolvedReturnType.isBoolean }
        findFunction(firFunction)
    }

    private fun findFirMemberFunctions(classId: ClassId, name: Name): List<FirNamedFunctionSymbol> {
        val klass = symbolProvider.getClassLikeSymbolByClassId(classId) as FirRegularClassSymbol
        val scope = klass.unsubstitutedScope(
            session, components.scopeSession, withForcedTypeCalculator = true, memberRequiredPhase = null
        )
        return scope.getFunctions(name)
    }

    override val anyClass: IrClassSymbol = run {
        loadClass(StandardClassIds.Any)
    }

    override val anyType: IrType get() = anyClass.defaultType
    override val anyNType by lazy { anyType.makeNullable() }

    override val numberClass: IrClassSymbol = loadClass(StandardClassIds.Number)
    override val numberType: IrType get() = numberClass.defaultType

    override val nothingClass: IrClassSymbol = loadClass(StandardClassIds.Nothing)
    override val nothingType: IrType get() = nothingClass.defaultType
    override val nothingNType: IrType by lazy { nothingType.makeNullable() }

    override val unitClass: IrClassSymbol = loadClass(StandardClassIds.Unit)
    override val unitType: IrType get() = unitClass.defaultType

    override val booleanClass: IrClassSymbol = loadClass(StandardClassIds.Boolean)
    override val booleanType: IrType get() = booleanClass.defaultType

    override val charClass: IrClassSymbol = loadClass(StandardClassIds.Char)
    override val charType: IrType get() = charClass.defaultType

    override val byteClass: IrClassSymbol = loadClass(StandardClassIds.Byte)
    override val byteType: IrType get() = byteClass.defaultType

    override val shortClass: IrClassSymbol = loadClass(StandardClassIds.Short)
    override val shortType: IrType get() = shortClass.defaultType

    override val intClass: IrClassSymbol = loadClass(StandardClassIds.Int)
    override val intType: IrType get() = intClass.defaultType

    override val longClass: IrClassSymbol = loadClass(StandardClassIds.Long)
    override val longType: IrType get() = longClass.defaultType

    override val floatClass: IrClassSymbol = loadClass(StandardClassIds.Float)
    override val floatType: IrType get() = floatClass.defaultType

    override val doubleClass: IrClassSymbol = loadClass(StandardClassIds.Double)
    override val doubleType: IrType get() = doubleClass.defaultType

    override val charSequenceClass: IrClassSymbol = loadClass(StandardClassIds.CharSequence)

    override val stringClass: IrClassSymbol = loadClass(StandardClassIds.String)
    override val stringType: IrType get() = stringClass.defaultType

    override val iteratorClass: IrClassSymbol = loadClass(StandardClassIds.Iterator)
    override val arrayClass: IrClassSymbol = loadClass(StandardClassIds.Array)

    override val annotationClass: IrClassSymbol = loadClass(StandardClassIds.Annotation)
    override val annotationType: IrType get() = annotationClass.defaultType

    override val collectionClass: IrClassSymbol = loadClass(StandardClassIds.Collection)
    override val setClass: IrClassSymbol = loadClass(StandardClassIds.Set)
    override val listClass: IrClassSymbol = loadClass(StandardClassIds.List)
    override val mapClass: IrClassSymbol = loadClass(StandardClassIds.Map)
    override val mapEntryClass: IrClassSymbol = loadClass(StandardClassIds.MapEntry)

    override val iterableClass: IrClassSymbol = loadClass(StandardClassIds.Iterable)
    override val listIteratorClass: IrClassSymbol = loadClass(StandardClassIds.ListIterator)
    override val mutableCollectionClass: IrClassSymbol = loadClass(StandardClassIds.MutableCollection)
    override val mutableSetClass: IrClassSymbol = loadClass(StandardClassIds.MutableSet)
    override val mutableListClass: IrClassSymbol = loadClass(StandardClassIds.MutableList)
    override val mutableMapClass: IrClassSymbol = loadClass(StandardClassIds.MutableMap)
    override val mutableMapEntryClass: IrClassSymbol = loadClass(StandardClassIds.MutableMapEntry)

    override val mutableIterableClass: IrClassSymbol = loadClass(StandardClassIds.MutableIterable)
    override val mutableIteratorClass: IrClassSymbol = loadClass(StandardClassIds.MutableIterator)
    override val mutableListIteratorClass: IrClassSymbol = loadClass(StandardClassIds.MutableListIterator)
    override val comparableClass: IrClassSymbol = loadClass(StandardClassIds.Comparable)
    override val throwableType: IrType by lazy { throwableClass.defaultType }
    override val throwableClass: IrClassSymbol = loadClass(StandardClassIds.Throwable)

    override val kCallableClass: IrClassSymbol = loadClass(StandardClassIds.KCallable)
    override val kPropertyClass: IrClassSymbol = loadClass(StandardClassIds.KProperty)
    override val kClassClass: IrClassSymbol = loadClass(StandardClassIds.KClass)
    override val kTypeClass: IrClassSymbol = loadClass(StandardClassIds.KType)
    override val kProperty0Class: IrClassSymbol = loadClass(StandardClassIds.KProperty0)
    override val kProperty1Class: IrClassSymbol = loadClass(StandardClassIds.KProperty1)
    override val kProperty2Class: IrClassSymbol = loadClass(StandardClassIds.KProperty2)
    override val kMutableProperty0Class: IrClassSymbol = loadClass(StandardClassIds.KMutableProperty0)
    override val kMutableProperty1Class: IrClassSymbol = loadClass(StandardClassIds.KMutableProperty1)
    override val kMutableProperty2Class: IrClassSymbol = loadClass(StandardClassIds.KMutableProperty2)

    override val functionClass: IrClassSymbol = loadClass(StandardClassIds.Function)
    override val kFunctionClass: IrClassSymbol = loadClass(StandardClassIds.KFunction)

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

    private fun loadPrimitiveArray(primitiveType: PrimitiveType): IrClassSymbol {
        return loadClass(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("${primitiveType.typeName}Array")))
    }

    override val booleanArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.BOOLEAN)
    override val charArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.CHAR)
    override val byteArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.BYTE)
    override val shortArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.SHORT)
    override val intArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.INT)
    override val longArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.LONG)
    override val floatArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.FLOAT)
    override val doubleArray: IrClassSymbol = loadPrimitiveArray(PrimitiveType.DOUBLE)

    override val primitiveArraysToPrimitiveTypes: Map<IrClassSymbol, PrimitiveType> = run {
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

    override lateinit var eqeqeqSymbol: IrSimpleFunctionSymbol private set
    override lateinit var eqeqSymbol: IrSimpleFunctionSymbol private set
    override lateinit var throwCceSymbol: IrSimpleFunctionSymbol private set
    override lateinit var throwIseSymbol: IrSimpleFunctionSymbol private set
    override lateinit var andandSymbol: IrSimpleFunctionSymbol private set
    override lateinit var ororSymbol: IrSimpleFunctionSymbol private set
    override lateinit var noWhenBranchMatchedExceptionSymbol: IrSimpleFunctionSymbol private set
    override lateinit var illegalArgumentExceptionSymbol: IrSimpleFunctionSymbol private set
    override lateinit var dataClassArrayMemberHashCodeSymbol: IrSimpleFunctionSymbol private set
    override lateinit var dataClassArrayMemberToStringSymbol: IrSimpleFunctionSymbol private set

    override lateinit var checkNotNullSymbol: IrSimpleFunctionSymbol private set
    override val arrayOfNulls: IrSimpleFunctionSymbol = run {
        val firSymbol = symbolProvider
            .getTopLevelFunctionSymbols(kotlinPackage, Name.identifier("arrayOfNulls")).first {
                it.fir.valueParameters.singleOrNull()?.returnTypeRef?.coneType?.isInt == true
            }
        findFunction(firSymbol)
    }

    override val linkageErrorSymbol: IrSimpleFunctionSymbol
        get() = TODO("Not yet implemented")

    override lateinit var lessFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override lateinit var lessOrEqualFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override lateinit var greaterOrEqualFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set
    override lateinit var greaterFunByOperandType: Map<IrClassifierSymbol, IrSimpleFunctionSymbol> private set

    internal fun initialize() {
        generateUnboundSymbolsAsDependencies(
            components.irProviders,
            components.symbolTable,
            symbolExtractor = Fir2IrSymbolTableExtension::unboundClassifiersSymbols,
        )

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

    override val unsignedTypesToUnsignedArrays: Map<UnsignedType, IrClassSymbol> = run {
        UnsignedType.entries.mapNotNull { unsignedType ->
            val array = loadClassSafe(unsignedType.arrayClassId)
            if (array == null) null else unsignedType to array
        }.toMap()
    }

    override val unsignedArraysElementTypes: Map<IrClassSymbol, IrType?> by lazy {
        unsignedTypesToUnsignedArrays.map { (k, v) -> v to loadClass(k.classId).owner.defaultType }.toMap()
    }

    override fun getKPropertyClass(mutable: Boolean, n: Int): IrClassSymbol = when (n) {
        0 -> if (mutable) kMutableProperty0Class else kProperty0Class
        1 -> if (mutable) kMutableProperty1Class else kProperty1Class
        2 -> if (mutable) kMutableProperty2Class else kProperty2Class
        else -> error("No KProperty for n=$n mutable=$mutable")
    }

    override val enumClass: IrClassSymbol = loadClass(StandardClassIds.Enum)

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

    override val extensionToString: IrSimpleFunctionSymbol = run {
        val firFunctionSymbol = symbolProvider.getTopLevelFunctionSymbols(kotlinPackage, OperatorNameConventions.TO_STRING).single {
            it.receiverParameter?.typeRef?.coneType?.isNullableAny == true
        }
        findFunction(firFunctionSymbol)
    }

    override val memberToString: IrSimpleFunctionSymbol = run {
        val firFunction = findFirMemberFunctions(StandardClassIds.Any, OperatorNameConventions.TO_STRING).single {
            it.fir.valueParameters.isEmpty()
        }
        findFunction(firFunction)
    }

    override val extensionStringPlus: IrSimpleFunctionSymbol = run {
        val firFunction = symbolProvider.getTopLevelFunctionSymbols(kotlinPackage, OperatorNameConventions.PLUS).single { symbol ->
            val isStringExtension = symbol.fir.receiverParameter?.typeRef?.coneType?.isNullableString == true
            isStringExtension && symbol.fir.valueParameters.singleOrNull { it.returnTypeRef.coneType.isNullableAny } != null
        }
        findFunction(firFunction)
    }

    override val memberStringPlus: IrSimpleFunctionSymbol = run {
        val firFunction = findFirMemberFunctions(StandardClassIds.String, OperatorNameConventions.PLUS).single {
            it.fir.valueParameters.singleOrNull()?.returnTypeRef?.coneType?.isNullableAny == true
        }
        findFunction(firFunction)
    }

    override val arrayOf: IrSimpleFunctionSymbol = run {
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
        loadClass(StandardClassIds.FunctionN(arity)).owner
    }

    override fun kFunctionN(arity: Int): IrClass = kFunctionNMap.getOrPut(arity) {
        loadClass(StandardClassIds.KFunctionN(arity)).owner
    }

    override fun suspendFunctionN(arity: Int): IrClass = suspendFunctionNMap.getOrPut(arity) {
        loadClass(StandardClassIds.SuspendFunctionN(arity)).owner
    }

    override fun kSuspendFunctionN(arity: Int): IrClass = kSuspendFunctionNMap.getOrPut(arity) {
        loadClass(StandardClassIds.KSuspendFunctionN(arity)).owner
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

    private fun referenceClassByFqname(packageName: FqName, identifier: Name): IrClassSymbol? {
        return loadClassSafe(ClassId(packageName, identifier))
    }

    override fun findBuiltInClassMemberFunctions(builtInClass: IrClassSymbol, name: Name): Iterable<IrSimpleFunctionSymbol> {
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

    private fun referenceClassByFqname(topLevelFqName: FqName): IrClassSymbol? {
        return loadClassSafe(ClassId.topLevel(topLevelFqName))
    }

    private fun loadClass(classId: ClassId): IrClassSymbol {
        return loadClassSafe(classId) ?: error("Class not found: $classId")
    }

    private fun loadClassSafe(classId: ClassId): IrClassSymbol? {
        val firClassSymbol = symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: return null
        val signature = components.signatureComposer.composeSignature(firClassSymbol.fir)
        return components.symbolTable.referenceClass(firClassSymbol, signature)
    }

    private fun IrType.getMaybeBuiltinClass(): IrClass? {
        val lhsClassFqName = classFqName!!
        return baseIrTypes.find { it.classFqName == lhsClassFqName }?.getClass()
            ?: referenceClassByFqname(lhsClassFqName)?.owner
    }

    private fun createPackage(fqName: FqName): IrExternalPackageFragment =
        IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(moduleDescriptor, fqName)

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

    private fun findFunctions(packageName: FqName, name: Name): List<IrSimpleFunctionSymbol> {
        return symbolProvider.getTopLevelFunctionSymbols(packageName, name).map { findFunction(it) }
    }

    private fun findFunction(functionSymbol: FirNamedFunctionSymbol): IrSimpleFunctionSymbol {
        val signature = components.signatureComposer.composeSignature(functionSymbol.fir)
        return components.symbolTable.referenceFunction(functionSymbol, signature)
    }

    private fun findProperties(packageName: FqName, name: Name): List<IrPropertySymbol> {
        return symbolProvider.getTopLevelPropertySymbols(packageName, name).map { firOpSymbol ->
            val signature = components.signatureComposer.composeSignature(firOpSymbol.fir)
            components.symbolTable.referenceProperty(firOpSymbol, signature)
        }
    }

    private val IrClassSymbol.defaultType: IrSimpleType
        get() = IrSimpleTypeImpl(
            kotlinType = null,
            classifier = this,
            nullability = SimpleTypeNullability.DEFINITELY_NOT_NULL,
            arguments = emptyList(),
            annotations = emptyList()
        )
}
