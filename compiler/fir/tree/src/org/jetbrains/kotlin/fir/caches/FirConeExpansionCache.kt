/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.caches

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.util.PrivateForInline
import org.jetbrains.kotlin.utils.addToStdlib.applyIf
import java.util.*

class FirConeExpansionCache(synchronized: Boolean) : FirSessionComponent {
    @PrivateForInline
    val cache: MutableMap<ConeClassLikeTypeImpl, ConeClassLikeType> = WeakHashMap<ConeClassLikeTypeImpl, ConeClassLikeType>()
        .applyIf(synchronized, Collections::synchronizedMap)

    @OptIn(PrivateForInline::class)
    inline fun compute(key: ConeClassLikeTypeImpl, crossinline map: (ConeClassLikeTypeImpl) -> ConeClassLikeType): ConeClassLikeType {
        return cache.computeIfAbsent(key) { map(it) }
    }
}

val FirSession.coneExpansionCache: FirConeExpansionCache by FirSession.sessionComponentAccessor()
