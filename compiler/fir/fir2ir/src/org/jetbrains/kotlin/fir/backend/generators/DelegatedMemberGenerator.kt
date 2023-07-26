/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.generators

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmNames.JVM_DEFAULT_CLASS_ID
import org.jetbrains.kotlin.name.Name

/**
 * A generator for delegated members from implementation by delegation.
 *
 * It assumes a synthetic field with the super-interface type has been created for the delegate expression. It looks for delegatable
 * methods and properties in the super-interface, and creates corresponding members in the subclass.
 * TODO: generic super interface types and generic delegated members.
 */
class DelegatedMemberGenerator(private val components: Fir2IrComponents) : Fir2IrComponents by components {
    private data class DeclarationBodyInfo(
        val declaration: IrDeclaration,
        val field: IrField,
        val delegateToSymbol: FirCallableSymbol<*>,
        val delegateToLookupTag: ConeClassLikeLookupTag?
    )

    private fun generateBodies(bodiesInfo: List<DeclarationBodyInfo>) {
        for ((declaration, irField, delegateToSymbol, delegateToLookupTag) in bodiesInfo) {
            val callTypeCanBeNullable = Fir2IrImplicitCastInserter.typeCanBeEnhancedOrFlexibleNullable(delegateToSymbol.fir.returnTypeRef.coneType.fullyExpandedType(session))
            val signature = signatureComposer.composeSignature(delegateToSymbol.fir, delegateToLookupTag)
            when (declaration) {
                is IrSimpleFunction -> {
                    val member = symbolTable.referenceFunction(delegateToSymbol as FirNamedFunctionSymbol, signature)
                    // TODO: get rid of owner here
                    val body = createDelegateBody(irField, declaration, member.owner, callTypeCanBeNullable)
                    declaration.body = body
                }
                is IrProperty -> {
                    // TODO: get rid of owner here
                    val member = symbolTable.referenceProperty(delegateToSymbol as FirPropertySymbol, signature).owner
                    val getter = declaration.getter!!
                    getter.body = createDelegateBody(irField, getter, member.getter!!, callTypeCanBeNullable)
                    if (declaration.isVar) {
                        val setter = declaration.setter!!
                        setter.body = createDelegateBody(irField, setter, member.setter!!, false)
                    }
                }
            }
        }
    }

    // Generate delegated members for [klass]. The synthetic field [irField] has the super interface type.
    fun generateDelegatedMethodsForSpecificDelegateField(firField: FirField, irField: IrField, klass: FirClass, irClass: IrClass) {
        require(irClass !is Fir2IrLazyClass)
        val subClassScope = klass.unsubstitutedScope(
            session,
            scopeSession,
            withForcedTypeCalculator = false,
            memberRequiredPhase = null,
        )

        val delegateToScope = firField.initializer!!.typeRef.coneType
            .fullyExpandedType(session)
            .lowerBoundIfFlexible()
            .scope(session, scopeSession, FakeOverrideTypeCalculator.DoNothing, null) ?: return

        val subClassLookupTag = klass.symbol.toLookupTag()
        val bodiesInfo = mutableListOf<DeclarationBodyInfo>()

        subClassScope.processAllFunctions { functionSymbol ->
            val unwrapped =
                functionSymbol.unwrapDelegateTarget(subClassLookupTag, firField)
                    ?: return@processAllFunctions

            val delegateToSymbol = findDelegateToSymbol(
                unwrapped.unwrapSubstitutionOverrides().symbol,
                delegateToScope::processFunctionsByName,
                delegateToScope::processOverriddenFunctions
            ) ?: return@processAllFunctions

            val delegateToLookupTag = delegateToSymbol.dispatchReceiverClassLookupTagOrNull()
                ?: return@processAllFunctions

            val irSubFunction = declarationsConverter.generateIrFunction(functionSymbol.fir, IrDeclarationOrigin.DELEGATED_MEMBER)
            bodiesInfo += DeclarationBodyInfo(irSubFunction, irField, delegateToSymbol, delegateToLookupTag)
            irClass.addMember(irSubFunction)
        }

        subClassScope.processAllProperties { propertySymbol ->
            if (propertySymbol !is FirPropertySymbol) return@processAllProperties

            val unwrapped =
                propertySymbol.unwrapDelegateTarget(subClassLookupTag, firField)
                    ?: return@processAllProperties

            val delegateToSymbol = findDelegateToSymbol(
                unwrapped.unwrapSubstitutionOverrides().symbol,
                { name, processor ->
                    delegateToScope.processPropertiesByName(name) {
                        if (it !is FirPropertySymbol) return@processPropertiesByName
                        processor(it)
                    }
                },
                delegateToScope::processOverriddenProperties
            ) ?: return@processAllProperties

            val delegateToLookupTag = delegateToSymbol.dispatchReceiverClassLookupTagOrNull()
                ?: return@processAllProperties

            val irSubProperty = declarationsConverter.generateIrProperty(propertySymbol.fir, IrDeclarationOrigin.DELEGATED_MEMBER)
            bodiesInfo += DeclarationBodyInfo(irSubProperty, irField, delegateToSymbol, delegateToLookupTag)
            irClass.addMember(irSubProperty)
        }
        generateBodies(bodiesInfo)
    }

    private inline fun <reified S : FirCallableSymbol<*>> findDelegateToSymbol(
        unwrappedSymbol: S,
        processCallables: (name: Name, processor: (S) -> Unit) -> Unit,
        crossinline processOverridden: (base: S, processor: (S) -> ProcessorAction) -> ProcessorAction
    ): S? {
        var result: S? = null
        // The purpose of this code is to find member in delegate-to scope
        // which matches or overrides unwrappedSymbol (which is in turn taken from subclass scope).
        processCallables(unwrappedSymbol.name) { candidateSymbol ->
            if (result != null) return@processCallables
            if (candidateSymbol === unwrappedSymbol) {
                result = candidateSymbol
                return@processCallables
            }
            processOverridden(candidateSymbol) {
                if (it === unwrappedSymbol) {
                    result = candidateSymbol
                    ProcessorAction.STOP
                } else {
                    ProcessorAction.NEXT
                }
            }
        }
        return result?.unwrapSubstitutionOverrides()
    }

    private fun createDelegateBody(
        irField: IrField,
        delegateFunction: IrSimpleFunction,
        superFunction: IrSimpleFunction,
        callTypeCanBeNullable: Boolean
    ): IrBlockBody {
        val startOffset = SYNTHETIC_OFFSET
        val endOffset = SYNTHETIC_OFFSET
        val body = irFactory.createBlockBody(startOffset, endOffset)
        val irCall = IrCallImpl(
            startOffset,
            endOffset,
            superFunction.returnType,
            superFunction.symbol,
            superFunction.typeParameters.size,
            superFunction.valueParameters.size
        ).apply {
            val getField = IrGetFieldImpl(
                startOffset, endOffset,
                irField.symbol,
                irField.type,
                IrGetValueImpl(
                    startOffset, endOffset,
                    delegateFunction.dispatchReceiverParameter?.type!!,
                    delegateFunction.dispatchReceiverParameter?.symbol!!
                )
            )

            // When the delegation expression has an intersection type, it is not guaranteed that the field will have the same type as the
            // dispatch receiver of the target method. Therefore, we need to check if a cast must be inserted.
            val superFunctionParent = superFunction.parent as? IrClass
            dispatchReceiver = if (superFunctionParent == null || irField.type.isSubtypeOfClass(superFunctionParent.symbol)) {
                getField
            } else {
                Fir2IrImplicitCastInserter.implicitCastOrExpression(getField, superFunction.dispatchReceiverParameter!!.type)
            }

            extensionReceiver =
                delegateFunction.extensionReceiverParameter?.let { extensionReceiver ->
                    IrGetValueImpl(startOffset, endOffset, extensionReceiver.type, extensionReceiver.symbol)
                }
            delegateFunction.valueParameters.forEach {
                putValueArgument(it.index, IrGetValueImpl(startOffset, endOffset, it.type, it.symbol))
            }
            superFunction.typeParameters.forEach {
                putTypeArgument(
                    it.index, IrSimpleTypeImpl(
                        delegateFunction.typeParameters[it.index].symbol,
                        hasQuestionMark = false,
                        arguments = emptyList(),
                        annotations = emptyList()
                    )
                )
            }
        }
        val resultType = delegateFunction.returnType

        val irCastOrCall =
            if (callTypeCanBeNullable && !resultType.isNullable()) Fir2IrImplicitCastInserter.implicitNotNullCast(irCall)
            else irCall
        if (superFunction.returnType.isUnit() || superFunction.returnType.isNothing()) {
            body.statements.add(irCastOrCall)
        } else {
            val irReturn = IrReturnImpl(startOffset, endOffset, irBuiltIns.nothingType, delegateFunction.symbol, irCastOrCall)
            body.statements.add(irReturn)
        }
        return body
    }

    companion object {
        private val PLATFORM_DEPENDENT_CLASS_ID = ClassId.topLevel(FqName("kotlin.internal.PlatformDependent"))

        context(Fir2IrComponents)
        private fun <S : FirCallableSymbol<D>, D : FirCallableDeclaration> S.unwrapDelegateTarget(
            subClassLookupTag: ConeClassLikeLookupTag,
            firField: FirField,
        ): D? {
            val callable = this.fir as? D ?: return null

            val delegatedWrapperData = callable.delegatedWrapperData ?: return null
            if (delegatedWrapperData.containingClass != subClassLookupTag) return null
            if (delegatedWrapperData.delegateField != firField) return null

            val wrapped = delegatedWrapperData.wrapped as? D ?: return null

            @Suppress("UNCHECKED_CAST")
            val wrappedSymbol = wrapped.symbol as? S ?: return null

            @Suppress("UNCHECKED_CAST")
            return (wrappedSymbol.unwrapCallRepresentative().fir as D).takeIf { !shouldSkipDelegationFor(it, session) }
        }

        private fun shouldSkipDelegationFor(unwrapped: FirCallableDeclaration, session: FirSession): Boolean {
            // See org.jetbrains.kotlin.resolve.jvm.JvmDelegationFilter
            return (unwrapped is FirSimpleFunction && unwrapped.isDefaultJavaMethod()) ||
                    unwrapped.hasAnnotation(JVM_DEFAULT_CLASS_ID, session) ||
                    unwrapped.hasAnnotation(PLATFORM_DEPENDENT_CLASS_ID, session)
        }

        private fun FirSimpleFunction.isDefaultJavaMethod(): Boolean =
            when {
                isIntersectionOverride ->
                    baseForIntersectionOverride!!.isDefaultJavaMethod()
                isSubstitutionOverride ->
                    originalForSubstitutionOverride!!.isDefaultJavaMethod()
                else -> {
                    // Check that we have a non-abstract method from Java interface
                    isJavaOrEnhancement && modality == Modality.OPEN
                }
            }
    }
}
