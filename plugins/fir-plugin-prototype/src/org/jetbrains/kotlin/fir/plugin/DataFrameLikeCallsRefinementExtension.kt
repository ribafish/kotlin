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
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.superConeTypes
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLambdaArgumentExpression
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.FirImplicitAnyTypeRef
import org.jetbrains.kotlin.fir.types.toClassSymbol
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
        val refinedTypeId = ClassId(FqName.ROOT, Name.identifier("DataFrameType"))

        val schemaId = ClassId(FqName.ROOT, Name.identifier("Schema1"))
        val schemaSymbol = FirRegularClassSymbol(schemaId)
        buildRegularClass {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
            deprecationsProvider = EmptyDeprecationsProvider
            classKind = ClassKind.CLASS
            scopeProvider = FirKotlinScopeProvider()
            superTypeRefs += FirImplicitAnyTypeRef(null)

            name = schemaId.shortClassName
            this.symbol = schemaSymbol
        }


        val refinedTypeSymbol = FirRegularClassSymbol(refinedTypeId)
        buildRegularClass {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
            deprecationsProvider = EmptyDeprecationsProvider
            classKind = ClassKind.CLASS
            scopeProvider = FirKotlinScopeProvider()

            name = refinedTypeId.shortClassName
            this.symbol = refinedTypeSymbol
            superTypeRefs += buildResolvedTypeRef {
                type = ConeClassLikeTypeImpl(
                    ConeClassLookupTagWithFixedSymbol(schemaId, schemaSymbol),
                    emptyArray(),
                    isNullable = false
                )
            }
        }

        val typeRef = buildResolvedTypeRef {
            type = ConeClassLikeTypeImpl(
                lookupTag,
                arrayOf(
                    ConeClassLikeTypeImpl(
                        ConeClassLookupTagWithFixedSymbol(refinedTypeId, refinedTypeSymbol),
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
        val resolvedLet = findLet()
        val parameter = resolvedLet.valueParameterSymbols[0]

        val explicitReceiver = call.explicitReceiver ?: return call
        val receiverType = explicitReceiver.coneTypeOrNull ?: return call

        val returnType = call.coneTypeOrNull ?: return call
        val refinedType = (returnType.typeArguments.getOrNull(0) as? ConeClassLikeTypeImpl)?.toClassSymbol(session)?.fir ?: return call

        val schema = refinedType.superConeTypes.firstOrNull() ?: return call
        val schemaSymbol = schema.toClassSymbol(session) ?: return call
        val schemaClass = schemaSymbol.fir

        val scope = ClassId(FqName.ROOT, Name.identifier("Scope1"))
        val scopeSymbol = FirRegularClassSymbol(scope)
        val columns: List<Column> = listOf(Column(Name.identifier("column"), session.builtinTypes.intType))

        schemaClass.callShapeData = CallShapeData.Schema(columns)

        val scopeClass = buildRegularClass {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT, EffectiveVisibility.Local)
            deprecationsProvider = EmptyDeprecationsProvider
            classKind = ClassKind.CLASS
            scopeProvider = FirKotlinScopeProvider()
            superTypeRefs += FirImplicitAnyTypeRef(null)
            name = scope.shortClassName
            this.symbol = scopeSymbol
        }

        scopeClass.callShapeData = CallShapeData.Scope(schemaClass.symbol, columns)

        refinedType.callShapeData = CallShapeData.RefinedType(listOf(scopeSymbol))

        val argument = buildLambdaArgumentExpression {
            expression = buildAnonymousFunctionExpression {
                val fSymbol = FirAnonymousFunctionSymbol()
                anonymousFunction = buildAnonymousFunction {
                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                    moduleData = session.moduleData
                    origin = FirDeclarationOrigin.Plugin(KEY)
                    status = FirResolvedDeclarationStatusImpl(Visibilities.Local, Modality.FINAL, EffectiveVisibility.Local)
                    deprecationsProvider = EmptyDeprecationsProvider
                    returnTypeRef = buildResolvedTypeRef {
                        type = returnType
                    }
                    val itName = Name.identifier("it")
                    val parameterSymbol = FirValueParameterSymbol(itName)
                    valueParameters += buildValueParameter {
                        moduleData = session.moduleData
                        origin = FirDeclarationOrigin.Plugin(KEY)
                        returnTypeRef = buildResolvedTypeRef {
                            type = receiverType
                        }
                        name = itName
                        this.symbol = parameterSymbol
                        containingFunctionSymbol = fSymbol
                        isCrossinline = false
                        isNoinline = false
                        isVararg = false
                    }.also { parameterSymbol.bind(it) }
                    body = buildBlock {
                        this.coneTypeOrNull = returnType

                        // Schema is required for static extensions resolve and holds information for subsequent call modifications
                        statements += schemaClass

                        // Scope (provides extensions API)
                        statements += scopeClass

                        // Return type - dataframe schema
                        statements += refinedType
                    }
                    this.symbol = fSymbol
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
                arrayOf(
                    ConeClassLikeTypeImpl(
                        ConeClassLookupTagWithFixedSymbol(refinedType.symbol.classId, refinedType.symbol),
                        emptyArray(),
                        isNullable = false
                    )
                ),
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

    private fun findLet(): FirFunctionSymbol<*> {
        return session.symbolProvider.getTopLevelFunctionSymbols(FqName("kotlin"), Name.identifier("let")).single()
    }
}