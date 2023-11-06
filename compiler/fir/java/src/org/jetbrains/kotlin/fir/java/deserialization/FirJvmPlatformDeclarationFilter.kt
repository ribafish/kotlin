/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java.deserialization

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.scopes.platformClassMapper
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.jvm.FirJavaTypeRef
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.JavaClassifierType
import org.jetbrains.kotlin.load.java.structure.JavaPrimitiveType
import org.jetbrains.kotlin.load.java.structure.JavaTypeParameter
import org.jetbrains.kotlin.name.FqName

internal object FirJvmPlatformDeclarationFilter {
    fun isFunctionAvailable(function: FirSimpleFunction, session: FirSession): Boolean {
        val javaAnalogueClassId =
            session.platformClassMapper.getCorrespondingPlatformClass(function.containingClassLookupTag()?.classId) ?: return true

        if (!function.hasAnnotation(StandardNames.FqNames.platformDependentClassId, session)) return true

        val matcher = when (function.name.asString()) {
            "getOrDefault" -> GET_OR_DEFAULT_MATCHER
            "remove" -> REMOVE_MATCHER
            else -> error("Unsupported @PlatformDependent function: ${function.render()}")
        }

        val javaAnalogue = session.symbolProvider.getClassLikeSymbolByClassId(javaAnalogueClassId) as? FirClassSymbol<*> ?: return true
        val scope = session.declaredMemberScope(javaAnalogue, null)
        var isFunctionPresentInJavaAnalogue = false
        scope.processFunctionsByName(function.name) {
            if (matcher(it.fir)) {
                isFunctionPresentInJavaAnalogue = true
            }
        }
        return isFunctionPresentInJavaAnalogue
    }

    // V getOrDefault(Object, V)
    private val GET_OR_DEFAULT_MATCHER: (FirSimpleFunction) -> Boolean = { function ->
        function.typeParameters.isEmpty() &&
                function.valueParameters.size == 2 &&
                function.valueParameters[0].returnTypeRef.isJavaObjectType &&
                function.valueParameters[1].returnTypeRef.isJavaTypeParameterType &&
                function.returnTypeRef.isJavaTypeParameterType
    }

    // boolean remove(Object, Object)
    private val REMOVE_MATCHER: (FirSimpleFunction) -> Boolean = { function ->
        function.typeParameters.isEmpty() &&
                function.valueParameters.size == 2 &&
                function.valueParameters.all { it.returnTypeRef.isJavaObjectType } &&
                function.returnTypeRef.isJavaBooleanType
    }

    private val FirTypeRef.isJavaObjectType: Boolean
        get() = this is FirJavaTypeRef &&
                ((type as? JavaClassifierType)?.classifier as? JavaClass)?.fqName == JAVA_LANG_OBJECT

    private val FirTypeRef.isJavaTypeParameterType: Boolean
        get() = this is FirJavaTypeRef && (type as? JavaClassifierType)?.classifier is JavaTypeParameter

    private val FirTypeRef.isJavaBooleanType: Boolean
        get() = this is FirJavaTypeRef && (type as? JavaPrimitiveType)?.type == PrimitiveType.BOOLEAN

    private val JAVA_LANG_OBJECT: FqName = FqName("java.lang.Object")
}
