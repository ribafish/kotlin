/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jso.compiler.fir

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlinx.jso.compiler.fir.services.jsObjectPropertiesProvider
import org.jetbrains.kotlinx.jso.compiler.resolve.StandardIds

class JsObjectFactoryFunctionGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private val predicateBasedProvider = session.predicateBasedProvider

    private val jsFunction by lazy {
        session.symbolProvider
            .getTopLevelFunctionSymbols(StandardIds.JS_FUNCTION_ID.packageName, StandardIds.JS_FUNCTION_ID.callableName)
            .single()
    }

    private val matchedInterfaces by lazy {
        predicateBasedProvider.getSymbolsByPredicate(JsObjectPredicates.AnnotatedWithJsSimpleObject.LOOKUP)
            .filterIsInstance<FirRegularClassSymbol>()
    }

    private val factoryFqNamesToJsObjectInterface by lazy {
        matchedInterfaces.associateBy { it.classId.asSingleFqName() }
    }

    override fun getTopLevelCallableIds(): Set<CallableId> {
        return matchedInterfaces.mapNotNullTo(mutableSetOf()) {
            if (it.moduleData != session.moduleData) {
                null
            } else {
                val classId = it.classId
                CallableId(classId.packageFqName, Name.identifier(classId.shortClassName.identifier))
            }
        }
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        if (context != null) return emptyList()
        val jsSimpleObjectInterface = factoryFqNamesToJsObjectInterface[callableId.asSingleFqName()] ?: return emptyList()
        return listOf(createJsObjectFactoryFunction(callableId, jsSimpleObjectInterface).symbol)
    }

    /**
     * The method generate a synthetic factory for an `external interface` annotated with @JsSimpleObject
     * Imagine the next interfaces:
     * ```
     * external interface User {
     *   val name: String
     * }
     * @JsSimpleObject
     * external interface Admin {
     *   val chat: Chat
     * }
     * ```
     *
     * For the interface `Admin` this function should generate the next top-level function:
     * ```
     * fun Admin(chat: Chat, name: String): Admin {
     *   return js("{ chat: chat, name: name }")
     * }
     * ```
     */
    @OptIn(SymbolInternals::class)
    private fun createJsObjectFactoryFunction(callableId: CallableId, jsSimpleObjectInterface: FirRegularClassSymbol): FirSimpleFunction {
        val jsSimpleObjectProperties = session.jsObjectPropertiesProvider.getJsObjectPropertiesForClass(jsSimpleObjectInterface)
        val functionTarget = FirFunctionTarget(null, isLambda = false)
        return buildSimpleFunction {
            moduleData = jsSimpleObjectInterface.moduleData
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            origin = JsSimpleObjectPluginKey.origin
            symbol = FirNamedFunctionSymbol(callableId)
            name = callableId.callableName
            returnTypeRef = jsSimpleObjectInterface.defaultType().toFirResolvedTypeRef()

            status = FirResolvedDeclarationStatusImpl(
                jsSimpleObjectInterface.visibility,
                Modality.FINAL,
                jsSimpleObjectInterface.visibility.toEffectiveVisibility(ownerSymbol = null)
            )

            jsSimpleObjectInterface.typeParameterSymbols.mapTo(typeParameters) { it.fir }
            jsSimpleObjectProperties.mapTo(valueParameters) {
                buildValueParameter {
                    moduleData = session.moduleData
                    origin = JsSimpleObjectPluginKey.origin
                    returnTypeRef = it.resolvedReturnTypeRef
                    name = it.name
                    symbol = FirValueParameterSymbol(it.name)
                    isCrossinline = false
                    isNoinline = false
                    isVararg = false
                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                    containingFunctionSymbol = this@buildSimpleFunction.symbol
                }
            }

            body = buildBlock {
                statements += buildReturnExpression {
                    target = functionTarget
                    result = buildFunctionCall {
                        val propertiesObject = buildString {
                            append('{')
                            jsSimpleObjectProperties.forEachIndexed { i, it ->
                                append(it.name.identifier)
                                append(':')
                                append(it.name.identifier)
                                if (i != jsSimpleObjectProperties.lastIndex) append(',')
                            }
                            append('}')
                        }
                        calleeReference = buildResolvedNamedReference {
                            name = jsFunction.name
                            resolvedSymbol = jsFunction
                        }
                        argumentList = buildResolvedArgumentList(
                            linkedMapOf(buildConstExpression(null, ConstantValueKind.String, propertiesObject) to jsFunction.valueParameterSymbols.first().fir)
                        )
                    }
                }
            }
        }.also(functionTarget::bind)
    }
}