package org.jetbrains.kotlinx.jso.compiler.fir.services

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.scopes.processAllProperties
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jso.compiler.fir.JsObjectPredicates

class JsSimpleObjectPropertiesProvider(session: FirSession) : FirExtensionSessionComponent(session) {
    private val cache: FirCache<FirClassSymbol<*>, List<FirPropertySymbol>, Nothing?> =
        session.firCachesFactory.createCache(this::createJsSimpleObjectProperties)

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(JsObjectPredicates.AnnotatedWithJsSimpleObject.DECLARATION)
    }

    fun getJsObjectPropertiesForClass(classSymbol: FirClassSymbol<*>): List<FirPropertySymbol> {
        return cache.getValue(classSymbol)
    }

    private fun createJsSimpleObjectProperties(classSymbol: FirClassSymbol<*>): List<FirPropertySymbol> {
        return buildList {
            classSymbol.resolvedSuperTypes.forEach {
                val superInterface = it.fullyExpandedType(session)
                    .toRegularClassSymbol(session)
                    ?.takeIf { it.classKind == ClassKind.INTERFACE } ?: return@forEach

                val superInterfaceSimpleObjectProperties = createJsSimpleObjectProperties(superInterface)
                superInterfaceSimpleObjectProperties.forEach(::addIfNotNull)
            }

            classSymbol
                .declaredMemberScope(session, null)
                .processAllProperties {
                    addIfNotNull(it as? FirPropertySymbol)
                }
        }
    }
}

val FirSession.jsObjectPropertiesProvider: JsSimpleObjectPropertiesProvider by FirSession.sessionComponentAccessor()