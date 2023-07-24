/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.generators

import org.jetbrains.kotlin.builtins.StandardNames.HASHCODE_NAME
import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContextBase
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.DataClassMembersGenerator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.util.OperatorNameConventions.EQUALS
import org.jetbrains.kotlin.util.OperatorNameConventions.TO_STRING

/**
 * A generator that generates synthetic members of data class as well as part of inline class.
 *
 * This one uses [DataClassMembersGenerator] to generate function bodies, shared with the counterpart in psi. But, there are two main
 * differences. Unlike the counterpart in psi, which uses descriptor-based logic to determine which members to synthesize, this one uses
 * fir own logic that traverses class hierarchies in fir elements. Also, this one creates and passes IR elements, instead of providing how
 * to declare them, to [DataClassMembersGenerator].
 */
class DataClassMembersGenerator(val components: Fir2IrComponents) : Fir2IrComponents by components {

    fun generateSingleFieldValueClassMembers(klass: FirRegularClass, irClass: IrClass) {
        MyDataClassMethodsGenerator(irClass, IrDeclarationOrigin.GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER)
            .generate(klass)
    }

    fun generateMultiFieldValueClassMembers(klass: FirRegularClass, irClass: IrClass) {
        MyDataClassMethodsGenerator(irClass, IrDeclarationOrigin.GENERATED_MULTI_FIELD_VALUE_CLASS_MEMBER)
            .generate(klass)
    }

    fun generateDataClassMembers(klass: FirRegularClass, irClass: IrClass) {
        MyDataClassMethodsGenerator(irClass, IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER).generate(klass)
    }

    fun generateDataClassComponentBody(irFunction: IrFunction) {
        MyDataClassMethodsGenerator(irFunction.parentAsClass, IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER)
            .generateComponentBody(irFunction)
    }

    fun generateDataClassCopyBody(irFunction: IrFunction) {
        MyDataClassMethodsGenerator(irFunction.parentAsClass, IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER)
            .generateCopyBody(irFunction)
    }

    private inner class MyDataClassMethodsGenerator(val irClass: IrClass, val origin: IrDeclarationOrigin) {
        private val irDataClassMembersGenerator = object : IrBasedDataClassMembersGenerator(
            IrGeneratorContextBase(components.irBuiltIns),
            components.symbolTable.table,
            irClass,
            irClass.kotlinFqName,
            origin,
            forbidDirectFieldAccess = false
        ) {

            override fun generateSyntheticFunctionParameterDeclarations(irFunction: IrFunction) {
                // TODO
            }

            override fun getProperty(irValueParameter: IrValueParameter?): IrProperty =
                irValueParameter?.let {
                    irClass.properties.single { irProperty ->
                        irProperty.name == irValueParameter.name && irProperty.backingField?.type == irValueParameter.type
                    }
                } ?: error("Property for parameter $irValueParameter")

            inner class Fir2IrHashCodeFunctionInfo(override val symbol: IrSimpleFunctionSymbol) : HashCodeFunctionInfo {
                override fun commitSubstituted(irMemberAccessExpression: IrMemberAccessExpression<*>) {
                    // TODO
                }
            }

            private fun getHashCodeFunction(klass: IrClass): IrSimpleFunctionSymbol =
                klass.functions.singleOrNull {
                    it.name.asString() == "hashCode" && it.valueParameters.isEmpty() && it.extensionReceiverParameter == null
                }?.symbol
                    ?: context.irBuiltIns.anyClass.functions.single { it.owner.name.asString() == "hashCode" }


            val IrTypeParameter.erasedUpperBound: IrClass
                get() {
                    // Pick the (necessarily unique) non-interface upper bound if it exists
                    for (type in superTypes) {
                        val irClass = type.classOrNull?.owner ?: continue
                        if (!irClass.isInterface && !irClass.isAnnotationClass) return irClass
                    }

                    // Otherwise, choose either the first IrClass supertype or recurse.
                    // In the first case, all supertypes are interface types and the choice was arbitrary.
                    // In the second case, there is only a single supertype.
                    return when (val firstSuper = superTypes.first().classifierOrNull?.owner) {
                        is IrClass -> firstSuper
                        is IrTypeParameter -> firstSuper.erasedUpperBound
                        else -> error("unknown supertype kind $firstSuper")
                    }
                }


            override fun getHashCodeFunctionInfo(type: IrType): HashCodeFunctionInfo {
                val classifier = type.classifierOrNull
                val symbol = when {
                    classifier.isArrayOrPrimitiveArray -> context.irBuiltIns.dataClassArrayMemberHashCodeSymbol
                    classifier is IrClassSymbol -> getHashCodeFunction(classifier.owner)
                    classifier is IrTypeParameterSymbol -> getHashCodeFunction(classifier.owner.erasedUpperBound)
                    else -> error("Unknown classifier kind $classifier")
                }
                return Fir2IrHashCodeFunctionInfo(symbol)
            }
        }

        fun generate(klass: FirRegularClass) {
            val propertyParametersCount = irClass.primaryConstructor?.explicitParameters?.size ?: 0
            val properties = irClass.properties.filter { it.backingField != null }.take(propertyParametersCount).toList()

            val scope = klass.unsubstitutedScope(
                components.session,
                components.scopeSession,
                withForcedTypeCalculator = true,
                memberRequiredPhase = null,
            )
            val contributedSyntheticFunctions =
                buildMap<Name, FirSimpleFunction> {
                    for (name in listOf(EQUALS, HASHCODE_NAME, TO_STRING)) {
                        scope.processFunctionsByName(name) {
                            // We won't synthesize a function if there is a user-contributed (non-synthetic) one.
                            if (it.origin != FirDeclarationOrigin.Synthetic) return@processFunctionsByName
                            if (it.containingClassLookupTag() != klass.symbol.toLookupTag()) return@processFunctionsByName
                            require(!contains(name)) {
                                "Two synthetic functions $name were found in data/value class ${klass.name}:\n" +
                                        "${this[name]?.render()}\n${it.fir.render()}"
                            }
                            this[name] = it.fir
                        }
                    }
                }

            val toStringContributedFunction = contributedSyntheticFunctions[TO_STRING]
            if (toStringContributedFunction != null) {
                val toStringFunction = findSyntheticIrFunction(toStringContributedFunction)
                irDataClassMembersGenerator.generateToStringMethod(toStringFunction, properties)
            }

            val hashcodeNameContributedFunction = contributedSyntheticFunctions[HASHCODE_NAME]
            if (hashcodeNameContributedFunction != null) {
                val hashCodeFunction = findSyntheticIrFunction(hashcodeNameContributedFunction)
                irDataClassMembersGenerator.generateHashCodeMethod(hashCodeFunction, properties)
            }

            val equalsContributedFunction = contributedSyntheticFunctions[EQUALS]
            if (equalsContributedFunction != null) {
                val equalsFunction = findSyntheticIrFunction(equalsContributedFunction)
                irDataClassMembersGenerator.generateEqualsMethod(equalsFunction, properties)
            }
        }

        fun generateComponentBody(irFunction: IrFunction) {
            irFunction.origin = origin
            val index = DataClassResolver.getComponentIndex(irFunction.name.asString())
            val valueParameter = irClass.primaryConstructor!!.valueParameters[index - 1]
            val irProperty = irDataClassMembersGenerator.getProperty(valueParameter)
            irDataClassMembersGenerator.generateComponentFunction(irFunction, irProperty)
        }

        fun generateCopyBody(irFunction: IrFunction) {
            irFunction.origin = origin
            irDataClassMembersGenerator.generateCopyFunction(irFunction, irClass.primaryConstructor!!.symbol)
        }

        private fun findSyntheticIrFunction(syntheticCounterpart: FirSimpleFunction): IrFunction {
            val signature = components.signatureComposer.composeSignature(syntheticCounterpart)
            val irFunctionSymbol = components.symbolTable.referenceFunction(syntheticCounterpart.symbol, signature)
            // Function should be created at this moment, so it's safe to take the owner from the symbol
            return irFunctionSymbol.owner
        }
    }
}
