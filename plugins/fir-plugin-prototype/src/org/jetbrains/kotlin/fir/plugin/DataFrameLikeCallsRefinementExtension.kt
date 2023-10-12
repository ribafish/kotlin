/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLambdaArgumentExpression
import org.jetbrains.kotlin.fir.expressions.calleeReference
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.FirImplicitAnyTypeRef
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

class DataFrameLikeCallsRefinementExtension(session: FirSession) : FirFunctionCallRefinementExtension(session) {
    companion object {
        val REFINE = ClassId(FqName.ROOT, Name.identifier("Refine"))
        val DATAFRAME = ClassId(FqName.ROOT, Name.identifier("DataFrame"))
        object KEY : GeneratedDeclarationKey()
    }

    @OptIn(SymbolInternals::class)
    override fun intercept(callInfo: CallInfo, symbol: FirBasedSymbol<*>): FirBasedSymbol<*>? {
        if (!symbol.hasAnnotation(REFINE, session)) return null
        if (symbol !is FirNamedFunctionSymbol) return null
        val generatedName = CallableId(FqName.ROOT, callableName = Name.identifier("add_123"))
        val newSymbol = FirNamedFunctionSymbol(generatedName)
        val lookupTag = ConeClassLikeLookupTagImpl(DATAFRAME)
        val typeRef = buildResolvedTypeRef {
            type = ConeClassLikeTypeImpl(
                lookupTag,
                arrayOf(
                    ConeClassLikeTypeImpl(
                        ConeClassLikeLookupTagImpl(ClassId(FqName.ROOT, Name.identifier("Schema"))),
                        emptyArray(),
                        isNullable = false
                    )
                ),
                isNullable = false
            )
        }
        val function = buildSimpleFunctionCopy(symbol.fir) {
            name = generatedName.callableName
            body = null
            this.symbol = newSymbol
            returnTypeRef = typeRef
        }
        newSymbol.bind(function)
        return newSymbol
    }

    @OptIn(SymbolInternals::class)
    override fun transform(call: FirFunctionCall): FirFunctionCall {
        val resolvedLet = findLet(call)
        val parameter = resolvedLet.valueParameterSymbols[0]

        val explicitReceiver = call.explicitReceiver ?: return call
        val receiverType = explicitReceiver.coneTypeOrNull ?: return call

        val returnType = call.coneTypeOrNull ?: return call
        val schemaId = returnType.typeArguments.getOrNull(0)?.type?.classId ?: return call
        val schemaLookupTag = ConeClassLikeLookupTagImpl(schemaId)
        val schemaSymbol = FirRegularClassSymbol(schemaId)
        val argument = buildLambdaArgumentExpression {
            expression = buildAnonymousFunctionExpression {
                val fSymbol = FirAnonymousFunctionSymbol()
                anonymousFunction = buildAnonymousFunction {
                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                    moduleData = session.moduleData
                    origin = FirDeclarationOrigin.Source
                    status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.FINAL, EffectiveVisibility.Local)
                    deprecationsProvider = EmptyDeprecationsProvider
                    returnTypeRef = buildResolvedTypeRef {
                        type = returnType
                    }
                    val itName = Name.identifier("it")
                    val parameterSymbol = FirValueParameterSymbol(itName)
                    valueParameters += buildValueParameter {
                        moduleData = session.moduleData
                        origin = FirDeclarationOrigin.Source
                        returnTypeRef = buildResolvedTypeRef {
                            type = receiverType
                        }
                        name = itName
                        symbol = parameterSymbol
                        containingFunctionSymbol = fSymbol
                        isCrossinline = false
                        isNoinline = false
                        isVararg = false
                    }.also { parameterSymbol.bind(it) }
                    val tokenId = ClassId(FqName.ROOT, Name.identifier("Token1"))
                    val tokenSymbol = FirRegularClassSymbol(tokenId)
                    body = buildBlock {
                        this.coneTypeOrNull = returnType

                        // Type token required for static extensions resolve
                        statements += buildRegularClass {
                            resolvePhase = FirResolvePhase.BODY_RESOLVE
                            moduleData = session.moduleData
                            origin = FirDeclarationOrigin.Source
                            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
                            deprecationsProvider = EmptyDeprecationsProvider
                            classKind = ClassKind.CLASS
                            scopeProvider = FirKotlinScopeProvider()
                            superTypeRefs += FirImplicitAnyTypeRef(null)

                            name = tokenId.shortClassName
                            symbol = tokenSymbol
                            declarations += buildPrimaryConstructor(ConeClassLikeLookupTagImpl(tokenId), tokenId)
                        }

                        // Scope (provides extensions API)
                        val scope = ClassId(FqName.ROOT, Name.identifier("Scope1"))
                        val scopeSymbol = FirRegularClassSymbol(scope)
                        statements += buildRegularClass {
                            resolvePhase = FirResolvePhase.BODY_RESOLVE
                            moduleData = session.moduleData
                            origin = FirDeclarationOrigin.Source
                            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
                            deprecationsProvider = EmptyDeprecationsProvider
                            classKind = ClassKind.CLASS
                            scopeProvider = FirKotlinScopeProvider()
                            superTypeRefs += FirImplicitAnyTypeRef(null)


                            name = scope.shortClassName
                            symbol = scopeSymbol
                            val lookupTag = ConeClassLikeLookupTagImpl(scope)
                            declarations += buildPrimaryConstructor(lookupTag, scope)
                            val firPropertySymbol = FirPropertySymbol(CallableId(scope, Name.identifier("column")))
                            declarations += buildProperty {
                                moduleData = session.moduleData
                                resolvePhase = FirResolvePhase.BODY_RESOLVE
                                origin = FirDeclarationOrigin.Source
                                status = FirResolvedDeclarationStatusImpl(
                                    Visibilities.Local,
                                    Modality.FINAL,
                                    EffectiveVisibility.Local
                                )
                                receiverParameter = buildReceiverParameter {
                                    typeRef = buildResolvedTypeRef {
                                        type = ConeClassLikeTypeImpl(
                                            ConeClassLikeLookupTagImpl(DATAFRAME),
                                            arrayOf(
                                                ConeClassLikeTypeImpl(
                                                    ConeClassLookupTagWithFixedSymbol(tokenId, tokenSymbol),
                                                    emptyArray(),
                                                    isNullable = false
                                                )
                                            ),
                                            isNullable = false
                                        )
                                    }
                                }
                                dispatchReceiverType = ConeClassLikeTypeImpl(
                                    ConeClassLookupTagWithFixedSymbol(scope, scopeSymbol),
                                    emptyArray(),
                                    isNullable = false
                                )
                                this.returnTypeRef = session.builtinTypes.intType
                                val firPropertyAccessorSymbol = FirPropertyAccessorSymbol()
                                getter = buildPropertyAccessor {
                                    moduleData = session.moduleData
                                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                                    origin = FirDeclarationOrigin.Source
                                    this.returnTypeRef = session.builtinTypes.intType
                                    symbol = firPropertyAccessorSymbol
                                    propertySymbol = firPropertySymbol
                                    isGetter = true
                                    status = FirResolvedDeclarationStatusImpl(
                                        Visibilities.Local,
                                        Modality.FINAL,
                                        EffectiveVisibility.Local
                                    )
                                }.also { firPropertyAccessorSymbol.bind(it) }
                                name = Name.identifier("column")
                                symbol = firPropertySymbol
                                isVar = false
                                isLocal = false
                            }
                        }

                        // Return type - dataframe schema
                        statements += buildRegularClass {
                            resolvePhase = FirResolvePhase.BODY_RESOLVE
                            moduleData = session.moduleData
                            origin = FirDeclarationOrigin.Source
                            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
                            deprecationsProvider = EmptyDeprecationsProvider
                            classKind = ClassKind.CLASS
                            scopeProvider = FirKotlinScopeProvider()
                            declarations += buildPrimaryConstructor(schemaLookupTag, schemaId)

                            name = schemaId.shortClassName
                            symbol = schemaSymbol
                            superTypeRefs += buildResolvedTypeRef {
                                type = ConeClassLikeTypeImpl(
                                    ConeClassLookupTagWithFixedSymbol(tokenId, tokenSymbol),
                                    emptyArray(),
                                    isNullable = false
                                )
                            }
                            val firPropertySymbol = FirPropertySymbol(CallableId(scope, Name.identifier("scope1")))
                            declarations += buildProperty {
                                moduleData = session.moduleData
                                resolvePhase = FirResolvePhase.BODY_RESOLVE
                                origin = FirDeclarationOrigin.Source
                                status = FirResolvedDeclarationStatusImpl(
                                    Visibilities.Local,
                                    Modality.FINAL,
                                    EffectiveVisibility.Local
                                )
                                this.returnTypeRef = buildResolvedTypeRef {
                                    type = ConeClassLikeTypeImpl(
                                        ConeClassLookupTagWithFixedSymbol(scope, scopeSymbol),
                                        emptyArray(),
                                        isNullable = false
                                    )
                                }
                                val firPropertyAccessorSymbol = FirPropertyAccessorSymbol()
                                getter = buildPropertyAccessor {
                                    moduleData = session.moduleData
                                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                                    origin = FirDeclarationOrigin.Source
                                    this.returnTypeRef = buildResolvedTypeRef {
                                        type = ConeClassLikeTypeImpl(
                                            ConeClassLookupTagWithFixedSymbol(scope, scopeSymbol),
                                            emptyArray(),
                                            isNullable = false
                                        )
                                    }
                                    symbol = firPropertyAccessorSymbol
                                    propertySymbol = firPropertySymbol
                                    isGetter = true
                                    status = FirResolvedDeclarationStatusImpl(
                                        Visibilities.Local,
                                        Modality.FINAL,
                                        EffectiveVisibility.Local
                                    )
                                }.also { firPropertyAccessorSymbol.bind(it) }
                                name = Name.identifier("scope1")
                                symbol = firPropertySymbol
                                isVar = false
                                isLocal = false
                            }
                        }
                    }
                    symbol = fSymbol
                    isLambda = true
                    hasExplicitParameterList = false
                    typeRef = buildResolvedTypeRef {
                        type = ConeClassLikeTypeImpl(
                            ConeClassLikeLookupTagImpl(ClassId(FqName("kotlin"), Name.identifier("Function1"))),
                            typeArguments = arrayOf(receiverType, returnType),
                            isNullable = false
                        )
                    }
                    invocationKind = EventOccurrencesRange.EXACTLY_ONCE
                    inlineStatus = InlineStatus.Inline
                }.also { fSymbol.bind(it) }
            }
        }

        val newCall = buildFunctionCall {
            this.coneTypeOrNull = ConeClassLikeTypeImpl(
                ConeClassLikeLookupTagImpl(call.coneTypeOrNull?.classId!!),
                arrayOf(ConeClassLikeTypeImpl(ConeClassLookupTagWithFixedSymbol(schemaId, schemaSymbol), emptyArray(), isNullable = false)),
                isNullable = false
            )
            typeArguments += buildTypeProjectionWithVariance {
                typeRef = buildResolvedTypeRef {
                    type = receiverType
                }
                variance = Variance.INVARIANT
            }

            typeArguments += buildTypeProjectionWithVariance {
                typeRef = buildResolvedTypeRef {
                    type = returnType
                }
                variance = Variance.INVARIANT
            }
            dispatchReceiver = call.dispatchReceiver
            this.explicitReceiver = call.explicitReceiver
            extensionReceiver = call.extensionReceiver
            argumentList = buildResolvedArgumentList(linkedMapOf(argument to parameter.fir))
            calleeReference = buildResolvedNamedReference {
                name = Name.identifier("let")
                resolvedSymbol = resolvedLet
            }
        }
        return newCall
    }

    @OptIn(SymbolInternals::class)
    private fun findLet(call: FirFunctionCall): FirFunctionSymbol<*> {
        val refineSymbol =
            ((call.calleeReference as FirResolvedNamedReference).resolvedSymbol.annotations[0].calleeReference as FirResolvedNamedReference).resolvedSymbol
        val findArgumentByName =
            (refineSymbol.fir as FirPrimaryConstructor).returnTypeRef.toRegularClassSymbol(session)!!.annotations[0].findArgumentByName(
                Name.identifier("klass")
            ) as FirGetClassCall
        val coneTypeOrNull = findArgumentByName.argument.coneTypeOrNull
        val functions = coneTypeOrNull
            ?.toRegularClassSymbol(session)
            ?.declaredMemberScope(session, FirResolvePhase.BODY_RESOLVE)
            ?.getFunctions(Name.identifier("resolvedLet"))

        return functions!![0].fir.body!!.statements[0].calleeReference!!.toResolvedFunctionSymbol()!!
    }

    private fun buildPrimaryConstructor(
        tokenLookupTag: ConeClassLikeLookupTagImpl,
        token: ClassId,
    ): FirConstructor {
        return buildPrimaryConstructor {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            returnTypeRef = buildResolvedTypeRef {
                type = ConeClassLikeTypeImpl(
                    tokenLookupTag,
                    emptyArray(),
                    isNullable = false
                )
            }
            status = FirResolvedDeclarationStatusImpl(
                Visibilities.Local,
                Modality.FINAL,
                EffectiveVisibility.Local
            )
            symbol = FirConstructorSymbol(token)
        }.also {
            it.containingClassForStaticMemberAttr = tokenLookupTag
        }
    }
}