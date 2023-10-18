/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.providers

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.StandardTypes
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase.Companion.ANALYZED_DEPENDENCIES
import org.jetbrains.kotlin.fir.declarations.builder.buildOuterClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.impl.FirImplicitAnyTypeRef
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name.identifier
import org.jetbrains.kotlin.types.Variance

class FirNotFoundClassesStorage(
    private val session: FirSession
) : FirSessionComponent {
    class Symbol(classId: ClassId) : FirRegularClassSymbol(classId)

    private fun buildNotFoundClass(
        classId: ClassId,
        moduleData: FirModuleData,
        typeParametersCountList: List<Int>,
        outerClass: FirRegularClass?,
    ): FirRegularClass {
        return buildRegularClass {
            resolvePhase = ANALYZED_DEPENDENCIES
            this.moduleData = moduleData
            origin = FirDeclarationOrigin.Library
            status = FirResolvedDeclarationStatusImpl(
                Visibilities.Public,
                Modality.FINAL,
                EffectiveVisibility.Public
            ).also {
                it.isInner = outerClass != null
            }
            classKind = ClassKind.CLASS
            symbol = Symbol(classId)
            name = classId.shortClassName
            scopeProvider = moduleData.session.kotlinScopeProvider
            superTypeRefs += moduleData.session.builtinTypes.anyType
            val typeParametersCount = typeParametersCountList.firstOrNull()
            if (typeParametersCount != null) {
                repeat(typeParametersCount) { index ->
                    typeParameters += buildTypeParameter {
                        resolvePhase = ANALYZED_DEPENDENCIES
                        this.moduleData = moduleData
                        origin = FirDeclarationOrigin.Library
                        name = identifier("T$index")
                        symbol = FirTypeParameterSymbol()
                        containingDeclarationSymbol = this@buildRegularClass.symbol
                        variance = Variance.INVARIANT
                        isReified = false
                    }
                }
            }
            if (outerClass != null) {
                for (typeParameter in outerClass.typeParameters) {
                    typeParameters += buildOuterClassTypeParameterRef { this.symbol = typeParameter.symbol }
                }
            }
        }
    }

    private val notFoundClassCache = session.firCachesFactory.createCache(::getNotFoundClass)

    private fun getNotFoundClass(classId: ClassId, context: Context): FirRegularClass {
        val outerClassId = classId.outerClassId
        val outerClass = if (outerClassId != null) {
            notFoundClassCache.getValue(outerClassId, context.outer())
        } else null
        return buildNotFoundClass(classId, context.moduleData, context.typeParametersCountList, outerClass)
    }

    private class Context(val moduleData: FirModuleData, val typeParametersCountList: List<Int>) {
        fun outer() = Context(moduleData, typeParametersCountList.drop(1))
    }

    fun getOrCreateNotFoundClass(classId: ClassId, moduleData: FirModuleData, typeParametersCountList: List<Int>): FirRegularClass {
        return notFoundClassCache.getValue(classId, Context(moduleData, typeParametersCountList))
    }

    fun getExistingNotFoundClassSymbol(classId: ClassId): FirRegularClassSymbol? {
        return notFoundClassCache.getValueIfComputed(classId)?.symbol
    }
}

val FirSession.notFoundClassesStorage: FirNotFoundClassesStorage by FirSession.sessionComponentAccessor()
