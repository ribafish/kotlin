/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.contracts.description.LogicOperationKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.diagnostics.findChildByType
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.backend.generators.OperatorExpressionGenerator
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.deserialization.toQualifiedPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirContractCallBlock
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.extensions.extensionService
import org.jetbrains.kotlin.fir.references.*
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.isIteratorNext
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.UNDEFINED_PARAMETER_INDEX
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrErrorClassImpl
import org.jetbrains.kotlin.ir.types.impl.IrErrorTypeImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.addToStdlib.runUnless

class Fir2IrVisitor(
    private val components: Fir2IrComponents,
    override val conversionScope: Fir2IrConversionScope = components.conversionScope
) : Fir2IrComponents by components, FirDefaultVisitor<IrElement, Any?>(), IrGeneratorContextInterface {

    internal val implicitCastInserter = Fir2IrImplicitCastInserter(components)

    private val operatorGenerator = OperatorExpressionGenerator(components, this, conversionScope)

    private var _annotationMode: Boolean = false
    val annotationMode: Boolean
        get() = _annotationMode

    private fun FirTypeRef.toIrType(): IrType = with(typeConverter) { toIrType() }

    private fun <T : IrDeclaration> applyParentFromStackTo(declaration: T): T = conversionScope.applyParentFromStackTo(declaration)

    internal inline fun <T> withAnnotationMode(enableAnnotationMode: Boolean = true, block: () -> T): T {
        val oldAnnotationMode = _annotationMode
        _annotationMode = enableAnnotationMode
        try {
            return block()
        } finally {
            _annotationMode = oldAnnotationMode
        }
    }

    override fun visitElement(element: FirElement, data: Any?): IrElement {
        error("Should not be here: ${element::class} ${element.render()}")
    }

    // ==================================================================================

    override fun visitRegularClass(regularClass: FirRegularClass, data: Any?): IrElement = whileAnalysing(session, regularClass) {
        requireLocal(regularClass.visibility)
        declarationsConverter.generateIrClass(regularClass)
    }

    // TODO: do something with scripts
    @OptIn(UnexpandedTypeCheck::class)
    override fun visitScript(script: FirScript, data: Any?): IrElement {
        return declarationStorage.getCachedIrScript(script)!!.also { irScript ->
            irScript.parent = conversionScope.parentFromStack()
            declarationStorage.enterScope(irScript)

            irScript.explicitCallParameters = script.parameters.map { parameter ->
                declarationStorage.createIrVariable(parameter, irScript, givenOrigin = IrDeclarationOrigin.SCRIPT_CALL_PARAMETER)
            }

            // NOTE: index should correspond to one generated in the collectTowerDataElementsForScript
            irScript.implicitReceiversParameters = script.contextReceivers.mapIndexedNotNull { index, receiver ->
                val isSelf = receiver.customLabelName?.asString() == SCRIPT_SPECIAL_NAME_STRING
                val name =
                    if (isSelf) SpecialNames.THIS
                    else Name.identifier("${receiver.labelName?.asStringStripSpecialMarkers() ?: SCRIPT_RECEIVER_NAME_PREFIX}_$index")
                val origin = if (isSelf) IrDeclarationOrigin.INSTANCE_RECEIVER else IrDeclarationOrigin.SCRIPT_IMPLICIT_RECEIVER
                val irReceiver =
                    receiver.convertWithOffsets { startOffset, endOffset ->
                        irFactory.createValueParameter(
                            startOffset, endOffset, origin, name, receiver.typeRef.toIrType(), isAssignable = false,
                            IrValueParameterSymbolImpl(),
                            if (isSelf) UNDEFINED_PARAMETER_INDEX else index,
                            varargElementType = null, isCrossinline = false, isNoinline = false, isHidden = false
                        ).also {
                            it.parent = irScript
                        }
                    }
                if (isSelf) {
                    irScript.thisReceiver = irReceiver
                    irScript.baseClass = irReceiver.type
                    null
                } else irReceiver
            }

            conversionScope.withParent(irScript) {
                val destructComposites = mutableMapOf<FirVariableSymbol<*>, IrComposite>()
                for (statement in script.statements) {
                    val irStatement = if (statement is FirDeclaration) {
                        when {
                            statement is FirProperty && statement.origin == FirDeclarationOrigin.ScriptCustomization.ResultProperty -> {
                                // Generating the result property only for expressions with a meaningful result type
                                // otherwise skip the property and convert the expression into the statement
                                if (statement.returnTypeRef.let { (it.isUnit || it.isNothing || it.isNullableNothing) } == true) {
                                    statement.initializer!!.toIrStatement()
                                } else {
                                    (statement.accept(this@Fir2IrVisitor, null) as? IrDeclaration)?.also {
                                        irScript.resultProperty = (it as? IrProperty)?.symbol
                                    }
                                }
                            }
                            statement is FirVariable && statement.isDestructuringDeclarationContainerVariable == true -> {
                                statement.convertWithOffsets { startOffset, endOffset ->
                                    IrCompositeImpl(
                                        startOffset, endOffset,
                                        irBuiltIns.unitType, IrStatementOrigin.DESTRUCTURING_DECLARATION
                                    ).also {
                                        it.statements.add(
                                            declarationStorage.createIrVariable(statement, conversionScope.parentFromStack()).also {
                                                it.initializer = statement.initializer?.toIrStatement() as? IrExpression
                                            }
                                        )
                                        destructComposites[(statement).symbol] = it
                                    }
                                }
                            }
                            statement is FirProperty && statement.destructuringDeclarationContainerVariable != null -> {
                                (statement.accept(this@Fir2IrVisitor, null) as IrProperty).also {
                                    val irComponentInitializer = IrSetFieldImpl(
                                        it.startOffset, it.endOffset,
                                        it.backingField!!.symbol,
                                        irBuiltIns.unitType,
                                        origin = null, superQualifierSymbol = null
                                    ).apply {
                                        value = it.backingField!!.initializer!!.expression
                                        receiver = null
                                    }
                                    val correspondingComposite = destructComposites[statement.destructuringDeclarationContainerVariable!!]!!
                                    correspondingComposite.statements.add(irComponentInitializer)
                                    it.backingField!!.initializer = null
                                }
                            }
                            statement is FirClass -> {
                                (statement.accept(this@Fir2IrVisitor, null) as IrClass).also {
                                    converter.bindFakeOverridesInClass(it)
                                }
                            }
                            else -> {
                                statement.accept(this@Fir2IrVisitor, null) as? IrDeclaration
                            }
                        }
                    } else {
                        statement.toIrStatement()
                    }
                    irScript.statements.add(irStatement!!)
                }
            }
            for (configurator in session.extensionService.fir2IrScriptConfigurators) {
                with(configurator) {
                    irScript.configure(script) { declarationStorage.getCachedIrScript(it.fir)?.symbol }
                }
            }
            declarationStorage.leaveScope(irScript)
        }
    }

    override fun visitAnonymousObjectExpression(anonymousObjectExpression: FirAnonymousObjectExpression, data: Any?): IrElement {
        return whileAnalysing(session, anonymousObjectExpression) {
            visitAnonymousObject(anonymousObjectExpression.anonymousObject, data)
        }
    }

    override fun visitAnonymousObject(anonymousObject: FirAnonymousObject, data: Any?): IrElement {
        val irAnonymousObject = declarationsConverter.generateIrClass(anonymousObject)
        val anonymousClassType = irAnonymousObject.thisReceiver!!.type
        return anonymousObject.convertWithOffsets { startOffset, endOffset ->
            IrBlockImpl(
                startOffset, endOffset, anonymousClassType, IrStatementOrigin.OBJECT_LITERAL,
                listOf(
                    irAnonymousObject,
                    IrConstructorCallImpl.fromSymbolOwner(
                        startOffset,
                        endOffset,
                        anonymousClassType,
                        irAnonymousObject.constructors.first().symbol,
                        irAnonymousObject.typeParameters.size,
                        origin = IrStatementOrigin.OBJECT_LITERAL
                    )
                )
            )
        }
    }

    private fun requireLocal(visibility: Visibility) {
        require(visibility == Visibilities.Local) { "Only local declarations should be traversed with Fir2IrVisitor" }
    }

    // ==================================================================================

    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, data: Any?): IrElement = whileAnalysing(session, simpleFunction) {
        requireLocal(simpleFunction.visibility)
        return declarationsConverter.generateIrFunction(simpleFunction)
    }

    override fun visitAnonymousFunctionExpression(anonymousFunctionExpression: FirAnonymousFunctionExpression, data: Any?): IrElement {
        return visitAnonymousFunction(anonymousFunctionExpression.anonymousFunction, data)
    }

    override fun visitAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        data: Any?
    ): IrElement = whileAnalysing(session, anonymousFunction) {
        return anonymousFunction.convertWithOffsets { startOffset, endOffset ->
            val irFunction = declarationsConverter.generateIrFunction(anonymousFunction)
            val type = anonymousFunction.typeRef.toIrType()

            IrFunctionExpressionImpl(
                startOffset, endOffset, type, irFunction,
                if (irFunction.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) IrStatementOrigin.LAMBDA
                else IrStatementOrigin.ANONYMOUS_FUNCTION
            )
        }
    }

    private fun IrExpression.insertImplicitCast(
        baseExpression: FirExpression,
        valueType: FirTypeRef,
        expectedType: FirTypeRef
    ): IrExpression {
        return with(implicitCastInserter) {
            this@insertImplicitCast.cast(baseExpression, valueType, expectedType)
        }
    }

    override fun visitProperty(property: FirProperty, data: Any?): IrElement = whileAnalysing(session, property) {
        require(property.isLocal) { "Only local properties should be traversed with Fir2IrVisitor" }
        val delegate = property.delegate
        if (delegate != null) {
            TODO("Handle local delegated properties")
//            val irProperty = declarationStorage.createIrLocalDelegatedProperty(property, conversionScope.parentFromStack())
//            irProperty.delegate.initializer = convertToIrExpression(delegate, isDelegate = true)
//            conversionScope.withFunction(irProperty.getter) {
//                memberGenerator.convertFunctionContent(irProperty.getter, property.getter, null)
//            }
//            irProperty.setter?.let {
//                conversionScope.withFunction(it) {
//                    memberGenerator.convertFunctionContent(it, property.setter, null)
//                }
//            }
//            return irProperty
        }
        val initializer = property.initializer
        val isNextVariable = initializer is FirFunctionCall &&
                initializer.calleeReference.toResolvedFunctionSymbol()?.callableId?.isIteratorNext() == true &&
                property.source?.isChildOfForLoop == true
        val irVariable = callablesGenerator.createLocalVariable(
            property, conversionScope.parentFromStack(),
            givenOrigin = runIf(isNextVariable) {
                if (property.name.isSpecial && property.name == SpecialNames.DESTRUCT) {
                    IrDeclarationOrigin.IR_TEMPORARY_VARIABLE
                } else {
                    IrDeclarationOrigin.FOR_LOOP_VARIABLE
                }
            }
        )
        if (initializer != null) {
            irVariable.initializer = convertToIrExpression(initializer)
                .insertImplicitCast(initializer, initializer.typeRef, property.returnTypeRef)
        }
        annotationGenerator.generate(irVariable, property)
        return irVariable
    }

    // ==================================================================================

    override fun visitReturnExpression(returnExpression: FirReturnExpression, data: Any?): IrElement {
        val irTarget = conversionScope.returnTarget(returnExpression)
        return returnExpression.convertWithOffsets { startOffset, endOffset ->
            val result = returnExpression.result
            // For implicit returns, use the expression endOffset to generate the expected line number for debugging.
            val returnStartOffset = if (returnExpression.source?.kind is KtFakeSourceElementKind.ImplicitReturn) endOffset else startOffset
            IrReturnImpl(
                returnStartOffset, endOffset, irBuiltIns.nothingType,
                when (irTarget) {
                    is IrConstructor -> irTarget.symbol
                    is IrSimpleFunction -> irTarget.symbol
                    else -> error("Unknown return target: $irTarget")
                },
                convertToIrExpression(result)
            )
        }.let {
            returnExpression.accept(implicitCastInserter, it)
        }
    }

    override fun visitWrappedArgumentExpression(wrappedArgumentExpression: FirWrappedArgumentExpression, data: Any?): IrElement {
        // Note: we deal with specific arguments in CallAndReferenceGenerator
        return convertToIrExpression(wrappedArgumentExpression.expression)
    }

    override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression, data: Any?): IrElement {
        return varargArgumentsExpression.convertWithOffsets { startOffset, endOffset ->
            IrVarargImpl(
                startOffset,
                endOffset,
                varargArgumentsExpression.typeRef.toIrType(),
                varargArgumentsExpression.varargElementType.toIrType(),
                varargArgumentsExpression.arguments.map { it.convertToIrVarargElement() }
            )
        }
    }

    private fun FirExpression.convertToIrVarargElement(): IrVarargElement =
        if (this is FirSpreadArgumentExpression || this is FirNamedArgumentExpression && this.isSpread) {
            IrSpreadElementImpl(
                source?.startOffset ?: UNDEFINED_OFFSET,
                source?.endOffset ?: UNDEFINED_OFFSET,
                convertToIrExpression(this)
            )
        } else convertToIrExpression(this)

    private fun convertToIrCall(functionCall: FirFunctionCall): IrExpression {
        if (functionCall.isCalleeDynamic &&
            functionCall.calleeReference.name == OperatorNameConventions.SET &&
            functionCall.calleeReference.source?.kind == KtFakeSourceElementKind.ArrayAccessNameReference
        ) {
            return convertToIrArrayAccessDynamicCall(functionCall)
        }
        return convertToIrCall(functionCall, dynamicOperator = null)
    }

    private fun convertToIrCall(
        functionCall: FirFunctionCall,
        dynamicOperator: IrDynamicOperator?
    ): IrExpression {
        val explicitReceiverExpression = convertToIrReceiverExpression(functionCall.explicitReceiver, functionCall.calleeReference)
        return callGenerator.convertToIrCall(
            functionCall,
            functionCall.typeRef,
            explicitReceiverExpression,
            dynamicOperator
        )
    }

    private fun convertToIrArrayAccessDynamicCall(functionCall: FirFunctionCall): IrExpression {
        val explicitReceiverExpression = convertToIrCall(
            functionCall, dynamicOperator = IrDynamicOperator.ARRAY_ACCESS
        )
        if (explicitReceiverExpression is IrDynamicOperatorExpression) {
            explicitReceiverExpression.arguments.removeLast()
        }
        val result = callGenerator.convertToIrCall(
            functionCall, functionCall.typeRef, explicitReceiverExpression,
            dynamicOperator = IrDynamicOperator.EQ
        )
        if (result is IrDynamicOperatorExpression) {
            val arguments = result.arguments
            arguments[0] = arguments[arguments.lastIndex]
            while (arguments.size > 1) {
                arguments.removeLast()
            }
        }
        return result
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Any?): IrExpression = whileAnalysing(session, functionCall) {
        return convertToIrCall(functionCall = functionCall)
    }

    override fun visitSafeCallExpression(
        safeCallExpression: FirSafeCallExpression,
        data: Any?
    ): IrElement = whileAnalysing(session, safeCallExpression) {
        val explicitReceiverExpression = convertToIrExpression(safeCallExpression.receiver)

        val (receiverVariable, variableSymbol) = components.createTemporaryVariableForSafeCallConstruction(
            explicitReceiverExpression,
            conversionScope
        )

        return conversionScope.withSafeCallSubject(receiverVariable) {
            val afterNotNullCheck =
                (safeCallExpression.selector as? FirExpression)?.let(::convertToIrExpression)
                    ?: safeCallExpression.selector.accept(this, data) as IrExpression
            components.createSafeCallConstruction(receiverVariable, variableSymbol, afterNotNullCheck)
        }
    }

    override fun visitCheckedSafeCallSubject(checkedSafeCallSubject: FirCheckedSafeCallSubject, data: Any?): IrElement {
        val lastSubjectVariable = conversionScope.lastSafeCallSubject()
        return checkedSafeCallSubject.convertWithOffsets { startOffset, endOffset ->
            IrGetValueImpl(startOffset, endOffset, lastSubjectVariable.type, lastSubjectVariable.symbol)
        }
    }

    override fun visitAnnotation(annotation: FirAnnotation, data: Any?): IrElement {
        return callGenerator.convertToIrConstructorCall(annotation)
    }

    override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: Any?): IrElement = whileAnalysing(session, annotationCall) {
        return callGenerator.convertToIrConstructorCall(annotationCall)
    }

    override fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression, data: Any?): IrElement {
        return convertQualifiedAccessExpression(qualifiedAccessExpression)
    }

    override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Any?): IrElement {
        return convertQualifiedAccessExpression(propertyAccessExpression)
    }

    private fun convertQualifiedAccessExpression(
        qualifiedAccessExpression: FirQualifiedAccessExpression,
    ): IrExpression = whileAnalysing(session, qualifiedAccessExpression) {
        val explicitReceiverExpression = convertToIrReceiverExpression(
            qualifiedAccessExpression.explicitReceiver, qualifiedAccessExpression.calleeReference
        )
        return callGenerator.convertToIrCall(
            qualifiedAccessExpression, qualifiedAccessExpression.typeRef, explicitReceiverExpression
        )
    }

    // Note that this mimics psi2ir [StatementGenerator#shouldGenerateReceiverAsSingletonReference].
    /*
    TODO: function before rebase
    private fun isThisForClassPhysicallyAvailable(irClassSymbol: IrClassSymbol): Boolean {
        var lastClass = conversionScope.lastClass()
        while (lastClass != null) {
            if (irClassSymbol == lastClass.symbol) return true
            if (!lastClass.isInner) return false
            lastClass = lastClass.parentClassOrNull
        }
        return false
    }
     */
    private fun shouldGenerateReceiverAsSingletonReference(irClass: IrClass): Boolean {
        val scopeOwner = conversionScope.parent()
        return irClass.isObject &&
                scopeOwner != irClass && // For anonymous initializers
                !((scopeOwner is IrFunction || scopeOwner is IrProperty || scopeOwner is IrField) && (scopeOwner as IrDeclaration).parent == irClass) // Members of object
    }

    override fun visitThisReceiverExpression(
        thisReceiverExpression: FirThisReceiverExpression,
        data: Any?
    ): IrElement = whileAnalysing(session, thisReceiverExpression) {
        val calleeReference = thisReceiverExpression.calleeReference
        when (val boundSymbol = calleeReference.boundSymbol) {
            is FirClassSymbol<*> -> {
                // Object case
                val firClass = boundSymbol.fir
                val irClassSymbol = symbolTable.referenceClass(boundSymbol)

                if (shouldGenerateReceiverAsSingletonReference(irClassSymbol)) {
                    return thisReceiverExpression.convertWithOffsets { startOffset, endOffset ->
                        IrGetObjectValueImpl(startOffset, endOffset, firClass.defaultType().toIrType(), irClassSymbol)
                    }
                }

                val dispatchReceiver = conversionScope.dispatchReceiverParameter(irClassSymbol)
                if (dispatchReceiver != null) {
                    return convertThisExpressionWithSpecificDispatchReceiver(
                        thisReceiverExpression,
                        irClassSymbol.owner,
                        dispatchReceiver,
                        calleeReference
                    )
                }
            }
            is FirScriptSymbol -> {
                val firScript = boundSymbol.fir
                // TODO: what if script was not processed yet?
                val irScript = symbolTable.referenceScript(firScript.symbol).owner ?: error("IrScript for ${firScript.name} not found")
                val receiverParameter =
                    irScript.implicitReceiversParameters.find { it.index == calleeReference.contextReceiverNumber } ?: irScript.thisReceiver
                if (receiverParameter != null) {
                    return thisReceiverExpression.convertWithOffsets { startOffset, endOffset ->
                        IrGetValueImpl(startOffset, endOffset, receiverParameter.type, receiverParameter.symbol)
                    }
                } else {
                    error("No script receiver found") // TODO: check if any valid situations possible here
                }
            }
            is FirCallableSymbol<*> -> {
                val signature = signatureComposer.composeSignature(boundSymbol.fir)
                val irFunction = when (boundSymbol) {
                    is FirFunctionSymbol -> symbolTable.referenceFunction(boundSymbol, signature).owner
                    is FirPropertySymbol -> {
                        val property = symbolTable.referenceProperty(boundSymbol, signature).owner
                        conversionScope.parentAccessorOfPropertyFromStack(property)
                    }
                    else -> null
                }

                val receiver = irFunction?.let { function ->
                    if (calleeReference.contextReceiverNumber != -1)
                        function.valueParameters[calleeReference.contextReceiverNumber]
                    else
                        function.extensionReceiverParameter
                }

                if (receiver != null) {
                    return thisReceiverExpression.convertWithOffsets { startOffset, endOffset ->
                        IrGetValueImpl(startOffset, endOffset, receiver.type, receiver.symbol)
                    }
                }
            }
        }
        return visitQualifiedAccessExpression(thisReceiverExpression, data)
    }

    private fun convertThisExpressionWithSpecificDispatchReceiver(
        thisReceiverExpression: FirThisReceiverExpression,
        irClass: IrClass,
        dispatchReceiver: IrValueParameter,
        calleeReference: FirThisReference,
    ): IrDeclarationReference {
        return thisReceiverExpression.convertWithOffsets { startOffset, endOffset ->
            val thisRef = IrGetValueImpl(startOffset, endOffset, dispatchReceiver.type, dispatchReceiver.symbol)
            if (calleeReference.contextReceiverNumber == -1) {
                return thisRef
            }
            val constructorForCurrentlyGeneratedDelegatedConstructor =
                conversionScope.getConstructorForCurrentlyGeneratedDelegatedConstructor(irClass)

            if (constructorForCurrentlyGeneratedDelegatedConstructor != null) {
                val constructorParameter =
                    constructorForCurrentlyGeneratedDelegatedConstructor.valueParameters[calleeReference.contextReceiverNumber]
                IrGetValueImpl(startOffset, endOffset, constructorParameter.type, constructorParameter.symbol)
            } else {
                val contextReceivers =
                    components.classifierStorage.getFieldsWithContextReceiversForClass(irClass)
                        ?: error("Not defined context receivers for $irClass")

                IrGetFieldImpl(
                    startOffset, endOffset, contextReceivers[calleeReference.contextReceiverNumber].symbol,
                    thisReceiverExpression.typeRef.toIrType(),
                    thisRef,
                )
            }
        }
    }

    override fun visitInaccessibleReceiverExpression(
        inaccessibleReceiverExpression: FirInaccessibleReceiverExpression,
        data: Any?,
    ): IrElement {
        return inaccessibleReceiverExpression.convertWithOffsets { startOffset, endOffset ->
            IrErrorExpressionImpl(
                startOffset, endOffset,
                inaccessibleReceiverExpression.typeRef.toIrType(),
                "Receiver is inaccessible"
            )
        }
    }

    override fun visitSmartCastExpression(smartCastExpression: FirSmartCastExpression, data: Any?): IrElement {
        // Generate the expression with the original type and then cast it to the smart cast type.
        val value = convertToIrExpression(smartCastExpression.originalExpression)
        return implicitCastInserter.visitSmartCastExpression(smartCastExpression, value)
    }

    override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess, data: Any?): IrElement {
        return whileAnalysing(session, callableReferenceAccess) {
            convertCallableReferenceAccess(callableReferenceAccess, false)
        }
    }

    private fun convertCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess, isDelegate: Boolean): IrElement {
        val explicitReceiverExpression = convertToIrReceiverExpression(
            callableReferenceAccess.explicitReceiver, callableReferenceAccess.calleeReference, callableReferenceAccess
        )
        return callGenerator.convertToIrCallableReference(
            callableReferenceAccess,
            explicitReceiverExpression,
            isDelegate = isDelegate
        )
    }

    override fun visitVariableAssignment(
        variableAssignment: FirVariableAssignment,
        data: Any?
    ): IrElement = whileAnalysing(session, variableAssignment) {
        val explicitReceiverExpression = convertToIrReceiverExpression(
            variableAssignment.explicitReceiver, variableAssignment.calleeReference
        )
        return callGenerator.convertToIrSetCall(variableAssignment, explicitReceiverExpression)
    }

    override fun visitDesugaredAssignmentValueReferenceExpression(
        desugaredAssignmentValueReferenceExpression: FirDesugaredAssignmentValueReferenceExpression,
        data: Any?
    ): IrElement {
        return desugaredAssignmentValueReferenceExpression.expressionRef.value.accept(this, null)
    }

    override fun <T> visitConstExpression(constExpression: FirConstExpression<T>, data: Any?): IrElement {
        return constExpression.toIrConst(constExpression.typeRef.toIrType())
    }

    // ==================================================================================

    private fun FirStatement.toIrStatement(): IrStatement? {
        if (this is FirTypeAlias) return null
        if (this is FirUnitExpression) return runUnless(source?.kind is KtFakeSourceElementKind.ImplicitUnit.IndexedAssignmentCoercion) {
            convertToIrExpression(this)
        }
        if (this is FirContractCallBlock) return null
        if (this is FirBlock) return convertToIrExpression(this)
        return accept(this@Fir2IrVisitor, null) as IrStatement
    }

    internal fun convertToIrExpression(
        expression: FirExpression,
        isDelegate: Boolean = false
    ): IrExpression {
        return when (expression) {
            is FirBlock -> expression.convertToIrExpressionOrBlock(
                if (expression.source?.kind == KtFakeSourceElementKind.DesugaredForLoop) IrStatementOrigin.FOR_LOOP else null
            )
            is FirUnitExpression -> expression.convertWithOffsets { _, endOffset ->
                IrGetObjectValueImpl(
                    endOffset, endOffset, irBuiltIns.unitType, this.irBuiltIns.unitClass
                )
            }
            else -> {
                when (val unwrappedExpression = expression.unwrapArgument()) {
                    is FirCallableReferenceAccess -> convertCallableReferenceAccess(unwrappedExpression, isDelegate)
                    else -> expression.accept(this, null) as IrExpression
                }
            }
        }.let {
            expression.accept(implicitCastInserter, it) as IrExpression
        }
    }

    internal fun convertToIrReceiverExpression(
        expression: FirExpression?,
        calleeReference: FirReference?,
        callableReferenceAccess: FirCallableReferenceAccess? = null
    ): IrExpression? {
        return when (expression) {
            null -> return null
            is FirResolvedQualifier -> callGenerator.convertToGetObject(expression, callableReferenceAccess)
            is FirFunctionCall, is FirThisReceiverExpression, is FirCallableReferenceAccess, is FirSmartCastExpression ->
                convertToIrExpression(expression)
            else -> if (expression is FirQualifiedAccessExpression && expression.explicitReceiver == null) {
                val variableAsFunctionMode = calleeReference is FirResolvedNamedReference &&
                        calleeReference.name != OperatorNameConventions.INVOKE &&
                        (calleeReference.resolvedSymbol as? FirCallableSymbol)?.callableId?.callableName == OperatorNameConventions.INVOKE
                callGenerator.convertToIrCall(
                    expression, expression.typeRef, explicitReceiverExpression = null,
                    variableAsFunctionMode = variableAsFunctionMode
                )
            } else {
                convertToIrExpression(expression)
            }
        }?.run {
            if (expression is FirQualifiedAccessExpression && expression.calleeReference is FirSuperReference) return@run this

            implicitCastInserter.implicitCastFromDispatchReceiver(
                this, expression.typeRef, calleeReference,
                conversionScope.defaultConversionTypeOrigin()
            )
        }
    }

    private fun List<FirStatement>.mapToIrStatements(recognizePostfixIncDec: Boolean = true): List<IrStatement?> {
        var index = 0
        val result = ArrayList<IrStatement?>(size)
        while (index < size) {
            var irStatement: IrStatement? = null
            if (recognizePostfixIncDec) {
                val statementsOrigin = getStatementsOrigin(index)
                if (statementsOrigin != null) {
                    val subStatements = this.subList(index, index + 3).mapNotNull { it.toIrStatement() }
                    irStatement = IrBlockImpl(
                        subStatements[0].startOffset,
                        subStatements[2].endOffset,
                        (subStatements[0] as IrVariable).type,
                        statementsOrigin,
                        subStatements
                    )
                    index += 3
                }
            }

            if (irStatement == null) {
                irStatement = this[index].toIrStatement()
                index++
            }
            result.add(irStatement)
        }

        return result
    }

    private fun List<FirStatement>.getStatementsOrigin(index: Int): IrStatementOrigin? {
        val incrementStatement = getOrNull(index + 1)
        if (incrementStatement !is FirVariableAssignment) return null

        return incrementStatement.getIrPrefixPostfixOriginIfAny()
    }

    internal fun convertToIrBlockBody(block: FirBlock): IrBlockBody {
        return block.convertWithOffsets { startOffset, endOffset ->
            val irStatements = block.statements.mapToIrStatements()
            irFactory.createBlockBody(
                startOffset, endOffset,
                if (irStatements.isNotEmpty()) {
                    irStatements.filterNotNull().takeIf { it.isNotEmpty() }
                        ?: listOf(IrBlockImpl(startOffset, endOffset, irBuiltIns.unitType, null, emptyList()))
                } else {
                    emptyList()
                }
            ).also {
                with(implicitCastInserter) {
                    it.insertImplicitCasts()
                }
            }
        }
    }

    private val IrStatementOrigin.isLoop: Boolean
        get() {
            return this == IrStatementOrigin.DO_WHILE_LOOP || this == IrStatementOrigin.WHILE_LOOP || this == IrStatementOrigin.FOR_LOOP
        }

    private inline fun <reified K> List<*>.findFirst() = firstOrNull { it is K } as? K

    private inline fun <reified K> List<*>.findLast() = lastOrNull { it is K } as? K

    private fun extractOperationFromDynamicSetCall(functionCall: FirFunctionCall) =
        functionCall.dynamicVarargArguments?.lastOrNull() as? FirFunctionCall

    private val FirExpression.isIncrementOrDecrementCall: Boolean
        get() {
            val name = (this as? FirFunctionCall)?.calleeReference?.resolved?.name
            return name == OperatorNameConventions.INC || name == OperatorNameConventions.DEC
        }

    private fun FirBlock.tryConvertDynamicIncrementOrDecrementToIr(): IrExpression? {
        val receiver = statements.findFirst<FirProperty>() ?: return null
        val receiverValue = receiver.initializer ?: return null

        if (receiverValue.typeRef.coneType !is ConeDynamicType) {
            return null
        }

        val savedValue = statements.findLast<FirProperty>()?.initializer ?: return null
        val isPrefix = savedValue.isIncrementOrDecrementCall

        val (operationReceiver, operationCall) = if (isPrefix) {
            val operation = savedValue as? FirFunctionCall ?: return null
            val operationReceiver = operation.explicitReceiver ?: return null
            operationReceiver to operation
        } else {
            val operation = statements.findLast<FirVariableAssignment>()?.rValue as? FirFunctionCall
                ?: statements.findLast<FirFunctionCall>()?.let { extractOperationFromDynamicSetCall(it) }
                ?: return null
            savedValue to operation
        }

        val isArrayAccess = receiver.name == SpecialNames.ARRAY

        val explicitReceiverExpression = if (isArrayAccess) {
            val arrayAccess = operationReceiver as? FirFunctionCall ?: return null
            val originalVararg = arrayAccess.resolvedArgumentMapping?.keys?.filterIsInstance<FirVarargArgumentsExpression>()?.firstOrNull()
            (callGenerator.convertToIrCall(
                arrayAccess, arrayAccess.typeRef,
                convertToIrReceiverExpression(receiverValue, arrayAccess.calleeReference),
                noArguments = true
            ) as IrDynamicOperatorExpression).apply {
                originalVararg?.arguments?.forEach {
                    val that = (it as? FirPropertyAccessExpression)?.calleeReference?.toResolvedPropertySymbol()?.fir
                    val initializer = that?.initializer ?: return@forEach
                    arguments.add(convertToIrExpression(initializer))
                }
            }
        } else {
            val qualifiedAccess = operationReceiver as? FirQualifiedAccessExpression ?: return null
            val receiverExpression = if (receiverValue != qualifiedAccess) {
                receiverValue
            } else {
                null
            }
            callGenerator.convertToIrCall(
                qualifiedAccess,
                qualifiedAccess.typeRef,
                convertToIrReceiverExpression(receiverExpression, qualifiedAccess.calleeReference),
            )
        }
        return callGenerator.convertToIrCall(
            operationCall, operationCall.typeRef, explicitReceiverExpression
        )
    }

    private fun FirBlock.convertToIrExpressionOrBlock(origin: IrStatementOrigin? = null): IrExpression {
        if (this.source?.kind == KtFakeSourceElementKind.DesugaredIncrementOrDecrement) {
            tryConvertDynamicIncrementOrDecrementToIr()?.let {
                return it
            }
        }
        return statements.convertToIrExpressionOrBlock(source, origin)
    }

    private fun FirBlock.convertToIrBlock(forceUnitType: Boolean): IrExpression {
        return statements.convertToIrBlock(source, null, forceUnitType)
    }

    private fun List<FirStatement>.convertToIrExpressionOrBlock(
        source: KtSourceElement?,
        origin: IrStatementOrigin?
    ): IrExpression {
        if (size == 1) {
            val firStatement = single()
            if (firStatement is FirExpression &&
                (firStatement !is FirBlock || firStatement.source?.kind != KtFakeSourceElementKind.DesugaredForLoop)
            ) {
                return convertToIrExpression(firStatement)
            }
        }
        return convertToIrBlock(source, origin, forceUnitType = origin?.isLoop == true)
    }

    private fun List<FirStatement>.convertToIrBlock(
        source: KtSourceElement?,
        origin: IrStatementOrigin?,
        forceUnitType: Boolean,
    ): IrExpression {
        val type = if (forceUnitType)
            irBuiltIns.unitType
        else
            (lastOrNull() as? FirExpression)?.typeRef?.toIrType() ?: irBuiltIns.unitType
        return source.convertWithOffsets { startOffset, endOffset ->
            if (origin == IrStatementOrigin.DO_WHILE_LOOP) {
                IrCompositeImpl(
                    startOffset, endOffset, type, origin,
                    mapToIrStatements(recognizePostfixIncDec = false).filterNotNull()
                )
            } else {
                val irStatements = mapToIrStatements()
                val singleStatement = irStatements.singleOrNull()
                if (singleStatement is IrBlock &&
                    (singleStatement.origin == IrStatementOrigin.POSTFIX_INCR || singleStatement.origin == IrStatementOrigin.POSTFIX_DECR)
                ) {
                    singleStatement
                } else {
                    IrBlockImpl(startOffset, endOffset, type, origin, irStatements.filterNotNull())
                }
            }
        }
    }

    override fun visitErrorExpression(errorExpression: FirErrorExpression, data: Any?): IrElement {
        return errorExpression.convertWithOffsets { startOffset, endOffset ->
            IrErrorExpressionImpl(
                startOffset, endOffset,
                errorExpression.typeRef.toIrType(),
                errorExpression.diagnostic.reason
            )
        }
    }

    override fun visitEnumEntryDeserializedAccessExpression(
        enumEntryDeserializedAccessExpression: FirEnumEntryDeserializedAccessExpression,
        data: Any?
    ): IrElement {
        return visitPropertyAccessExpression(enumEntryDeserializedAccessExpression.toQualifiedPropertyAccessExpression(session), data)
    }

    override fun visitElvisExpression(elvisExpression: FirElvisExpression, data: Any?): IrElement {
        val firLhsVariable = buildProperty {
            source = elvisExpression.source
            moduleData = session.moduleData
            origin = FirDeclarationOrigin.Source
            returnTypeRef = elvisExpression.lhs.typeRef
            name = Name.special("<elvis>")
            initializer = elvisExpression.lhs
            symbol = FirPropertySymbol(name)
            isVar = false
            isLocal = true
            status = FirDeclarationStatusImpl(Visibilities.Local, Modality.FINAL)
        }
        val irLhsVariable = firLhsVariable.accept(this, null) as IrVariable
        return elvisExpression.convertWithOffsets { startOffset, endOffset ->
            fun irGetLhsValue(): IrGetValue =
                IrGetValueImpl(startOffset, endOffset, irLhsVariable.type, irLhsVariable.symbol)

            val originalType = firLhsVariable.returnTypeRef.coneType
            val notNullType = originalType.withNullability(ConeNullability.NOT_NULL, session.typeContext)
            val irBranches = listOf(
                IrBranchImpl(
                    startOffset, endOffset,
                    primitiveOp2(
                        startOffset, endOffset, irBuiltIns.eqeqSymbol,
                        irBuiltIns.booleanType, IrStatementOrigin.EQEQ,
                        irGetLhsValue(),
                        IrConstImpl.constNull(startOffset, endOffset, irBuiltIns.nothingNType)
                    ),
                    convertToIrExpression(elvisExpression.rhs)
                        .insertImplicitCast(elvisExpression, elvisExpression.rhs.typeRef, elvisExpression.typeRef)
                ),
                IrElseBranchImpl(
                    IrConstImpl.boolean(startOffset, endOffset, irBuiltIns.booleanType, true),
                    if (notNullType == originalType) {
                        irGetLhsValue()
                    } else {
                        Fir2IrImplicitCastInserter.implicitCastOrExpression(
                            irGetLhsValue(),
                            firLhsVariable.returnTypeRef.resolvedTypeFromPrototype(notNullType).toIrType()
                        )
                    }
                )
            )

            generateWhen(
                startOffset, endOffset, IrStatementOrigin.ELVIS,
                irLhsVariable, irBranches,
                elvisExpression.typeRef.toIrType()
            )
        }
    }

    override fun visitWhenExpression(whenExpression: FirWhenExpression, data: Any?): IrElement {
        val subjectVariable = generateWhenSubjectVariable(whenExpression)
        val origin = when (whenExpression.source?.elementType) {
            KtNodeTypes.WHEN -> IrStatementOrigin.WHEN
            KtNodeTypes.IF -> IrStatementOrigin.IF
            KtNodeTypes.BINARY_EXPRESSION -> when (whenExpression.source?.operationToken) {
                KtTokens.OROR -> IrStatementOrigin.OROR
                KtTokens.ANDAND -> IrStatementOrigin.ANDAND
                else -> null
            }
            KtNodeTypes.POSTFIX_EXPRESSION -> IrStatementOrigin.EXCLEXCL
            else -> null
        }
        return conversionScope.withWhenSubject(subjectVariable) {
            whenExpression.convertWithOffsets { startOffset, endOffset ->
                if (whenExpression.branches.isEmpty()) {
                    return@convertWithOffsets IrBlockImpl(startOffset, endOffset, irBuiltIns.unitType, origin)
                }
                val whenExpressionType =
                    if (whenExpression.isProperlyExhaustive && whenExpression.branches.none {
                            it.condition is FirElseIfTrueCondition && it.result.statements.isEmpty()
                        }) whenExpression.typeRef else session.builtinTypes.unitType
                val irBranches = whenExpression.branches.mapTo(mutableListOf()) { branch ->
                    branch.toIrWhenBranch(whenExpressionType)
                }
                if (whenExpression.isProperlyExhaustive && whenExpression.branches.none { it.condition is FirElseIfTrueCondition }) {
                    val irResult = IrCallImpl(
                        startOffset, endOffset, irBuiltIns.nothingType,
                        irBuiltIns.noWhenBranchMatchedExceptionSymbol,
                        typeArgumentsCount = 0,
                        valueArgumentsCount = 0
                    )
                    irBranches += IrElseBranchImpl(
                        IrConstImpl.boolean(startOffset, endOffset, irBuiltIns.booleanType, true), irResult
                    )
                }
                generateWhen(startOffset, endOffset, origin, subjectVariable, irBranches, whenExpressionType.toIrType())
            }
        }.also {
            whenExpression.accept(implicitCastInserter, it)
        }
    }

    private fun generateWhen(
        startOffset: Int,
        endOffset: Int,
        origin: IrStatementOrigin?,
        subjectVariable: IrVariable?,
        branches: List<IrBranch>,
        resultType: IrType
    ): IrExpression {
        val irWhen = IrWhenImpl(startOffset, endOffset, resultType, origin, branches)
        return if (subjectVariable == null) {
            irWhen
        } else {
            IrBlockImpl(startOffset, endOffset, irWhen.type, origin, listOf(subjectVariable, irWhen))
        }
    }

    private fun generateWhenSubjectVariable(whenExpression: FirWhenExpression): IrVariable? {
        val subjectVariable = whenExpression.subjectVariable
        val subjectExpression = whenExpression.subject
        return when {
            subjectVariable != null -> subjectVariable.accept(this, null) as IrVariable
            subjectExpression != null -> {
                applyParentFromStackTo(declarationStorage.declareTemporaryVariable(convertToIrExpression(subjectExpression), "subject"))
            }
            else -> null
        }
    }

    private fun FirWhenBranch.toIrWhenBranch(whenExpressionType: FirTypeRef): IrBranch {
        return convertWithOffsets { startOffset, endOffset ->
            val condition = condition
            val irResult = convertToIrExpression(result).insertImplicitCast(result, result.typeRef, whenExpressionType)
            if (condition is FirElseIfTrueCondition) {
                IrElseBranchImpl(IrConstImpl.boolean(irResult.startOffset, irResult.endOffset, irBuiltIns.booleanType, true), irResult)
            } else {
                IrBranchImpl(startOffset, endOffset, convertToIrExpression(condition), irResult)
            }
        }
    }

    override fun visitWhenSubjectExpression(whenSubjectExpression: FirWhenSubjectExpression, data: Any?): IrElement {
        val lastSubjectVariable = conversionScope.lastWhenSubject()
        return whenSubjectExpression.convertWithOffsets { startOffset, endOffset ->
            IrGetValueImpl(startOffset, endOffset, lastSubjectVariable.type, lastSubjectVariable.symbol)
        }
    }

    private val loopMap = mutableMapOf<FirLoop, IrLoop>()

    override fun visitDoWhileLoop(doWhileLoop: FirDoWhileLoop, data: Any?): IrElement {
        val irLoop = doWhileLoop.convertWithOffsets { startOffset, endOffset ->
            IrDoWhileLoopImpl(
                startOffset, endOffset, irBuiltIns.unitType,
                IrStatementOrigin.DO_WHILE_LOOP
            ).apply {
                loopMap[doWhileLoop] = this
                label = doWhileLoop.label?.name
                body = doWhileLoop.block.convertToIrExpressionOrBlock(origin)
                condition = convertToIrExpression(doWhileLoop.condition)
                loopMap.remove(doWhileLoop)
            }
        }.also {
            doWhileLoop.accept(implicitCastInserter, it)
        }
        return IrBlockImpl(irLoop.startOffset, irLoop.endOffset, irBuiltIns.unitType).apply {
            statements.add(irLoop)
        }
    }

    override fun visitWhileLoop(whileLoop: FirWhileLoop, data: Any?): IrElement {
        return whileLoop.convertWithOffsets { startOffset, endOffset ->
            val isForLoop = whileLoop.source?.elementType == KtNodeTypes.FOR
            val origin = if (isForLoop) IrStatementOrigin.FOR_LOOP_INNER_WHILE else IrStatementOrigin.WHILE_LOOP
            val firLoopBody = whileLoop.block
            IrWhileLoopImpl(startOffset, endOffset, irBuiltIns.unitType, origin).apply {
                loopMap[whileLoop] = this
                label = whileLoop.label?.name
                condition = convertToIrExpression(whileLoop.condition)
                body = if (isForLoop) {
                    /*
                     * for loops in IR should have specific for of their body, because some of lowerings (e.g. `ForLoopLowering`) expects
                     *   exactly that shape:
                     *
                     * for (x in list) { ...body...}
                     *
                     * IR (loop body):
                     *   IrBlock:
                     *     x = <iterator>.next()
                     *     IrBlock:
                     *         ...body...
                     */
                    firLoopBody.convertWithOffsets { innerStartOffset, innerEndOffset ->
                        val loopBodyStatements = firLoopBody.statements
                        if (loopBodyStatements.isEmpty()) {
                            error("Unexpected shape of body of for loop")
                        }
                        val loopVariables = mutableListOf<IrStatement>()
                        var loopVariableIndex = 0
                        for (loopBodyStatement in loopBodyStatements) {
                            if (loopVariableIndex > 0) {
                                if (loopBodyStatement !is FirProperty || loopBodyStatement.initializer !is FirComponentCall) {
                                    break
                                }
                            }
                            loopBodyStatement.toIrStatement()?.let { loopVariables.add(it) }
                            loopVariableIndex++
                        }

                        IrBlockImpl(
                            innerStartOffset,
                            innerEndOffset,
                            irBuiltIns.unitType,
                            origin,
                            loopVariables +
                                    loopBodyStatements.drop(loopVariableIndex).convertToIrExpressionOrBlock(firLoopBody.source, null)
                        )
                    }
                } else {
                    firLoopBody.convertToIrExpressionOrBlock()
                }
                loopMap.remove(whileLoop)
            }
        }.also {
            whileLoop.accept(implicitCastInserter, it)
        }
    }

    private fun FirJump<FirLoop>.convertJumpWithOffsets(
        f: (startOffset: Int, endOffset: Int, irLoop: IrLoop, label: String?) -> IrBreakContinue
    ): IrExpression {
        return convertWithOffsets { startOffset, endOffset ->
            val firLoop = target.labeledElement
            val irLoop = loopMap[firLoop]
            if (irLoop == null) {
                IrErrorExpressionImpl(startOffset, endOffset, irBuiltIns.nothingType, "Unbound loop: ${render()}")
            } else {
                f(startOffset, endOffset, irLoop, irLoop.label.takeIf { target.labelName != null })
            }
        }
    }

    override fun visitBreakExpression(breakExpression: FirBreakExpression, data: Any?): IrElement {
        return breakExpression.convertJumpWithOffsets { startOffset, endOffset, irLoop, label ->
            IrBreakImpl(startOffset, endOffset, irBuiltIns.nothingType, irLoop).apply {
                this.label = label
            }
        }
    }

    override fun visitContinueExpression(continueExpression: FirContinueExpression, data: Any?): IrElement {
        return continueExpression.convertJumpWithOffsets { startOffset, endOffset, irLoop, label ->
            IrContinueImpl(startOffset, endOffset, irBuiltIns.nothingType, irLoop).apply {
                this.label = label
            }
        }
    }

    override fun visitThrowExpression(throwExpression: FirThrowExpression, data: Any?): IrElement {
        return throwExpression.convertWithOffsets { startOffset, endOffset ->
            IrThrowImpl(startOffset, endOffset, irBuiltIns.nothingType, convertToIrExpression(throwExpression.exception))
        }
    }

    override fun visitTryExpression(tryExpression: FirTryExpression, data: Any?): IrElement {
        // Always generate a block for try, catch and finally blocks. When leaving the finally block in the debugger
        // for both Java and Kotlin there is a step on the end brace. For that to happen we need the block with
        // that line number for the finally block.
        return tryExpression.convertWithOffsets { startOffset, endOffset ->
            IrTryImpl(
                startOffset, endOffset, tryExpression.typeRef.toIrType(),
                tryExpression.tryBlock.convertToIrBlock(forceUnitType = false),
                tryExpression.catches.map { it.accept(this, data) as IrCatch },
                tryExpression.finallyBlock?.convertToIrBlock(forceUnitType = true)
            )
        }.also {
            tryExpression.accept(implicitCastInserter, it)
        }
    }

    override fun visitCatch(catch: FirCatch, data: Any?): IrElement {
        return catch.convertWithOffsets { startOffset, endOffset ->
            val catchParameter = callablesGenerator.createLocalVariable(
                catch.parameter, conversionScope.parentFromStack(), IrDeclarationOrigin.CATCH_PARAMETER
            )
            IrCatchImpl(startOffset, endOffset, catchParameter, catch.block.convertToIrBlock(forceUnitType = false))
        }
    }

    override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression, data: Any?): IrElement =
        operatorGenerator.convertComparisonExpression(comparisonExpression)

    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall,
        data: Any?
    ): IrElement = whileAnalysing(session, stringConcatenationCall) {
        return stringConcatenationCall.convertWithOffsets { startOffset, endOffset ->
            val arguments = mutableListOf<IrExpression>()
            val sb = StringBuilder()
            var startArgumentOffset = -1
            var endArgumentOffset = -1
            for (firArgument in stringConcatenationCall.arguments) {
                val argument = convertToIrExpression(firArgument)
                if (argument is IrConst<*>) {
                    if (sb.isEmpty()) {
                        startArgumentOffset = argument.startOffset
                    }
                    sb.append(argument.value)
                    endArgumentOffset = argument.endOffset
                } else {
                    if (sb.isNotEmpty()) {
                        arguments += IrConstImpl.string(startArgumentOffset, endArgumentOffset, irBuiltIns.stringType, sb.toString())
                        sb.clear()
                    }
                    arguments += argument
                }
            }
            if (sb.isNotEmpty()) {
                arguments += IrConstImpl.string(startArgumentOffset, endArgumentOffset, irBuiltIns.stringType, sb.toString())
            }
            IrStringConcatenationImpl(startOffset, endOffset, irBuiltIns.stringType, arguments)
        }
    }

    override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall, data: Any?): IrElement {
        return typeOperatorCall.convertWithOffsets { startOffset, endOffset ->
            val irTypeOperand = typeOperatorCall.conversionTypeRef.toIrType()
            val (irType, irTypeOperator) = when (typeOperatorCall.operation) {
                FirOperation.IS -> irBuiltIns.booleanType to IrTypeOperator.INSTANCEOF
                FirOperation.NOT_IS -> irBuiltIns.booleanType to IrTypeOperator.NOT_INSTANCEOF
                FirOperation.AS -> irTypeOperand to IrTypeOperator.CAST
                FirOperation.SAFE_AS -> irTypeOperand.makeNullable() to IrTypeOperator.SAFE_CAST
                else -> TODO("Should not be here: ${typeOperatorCall.operation} in type operator call")
            }

            IrTypeOperatorCallImpl(
                startOffset, endOffset, irType, irTypeOperator, irTypeOperand,
                convertToIrExpression(typeOperatorCall.argument)
            )
        }
    }

    override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall, data: Any?): IrElement {
        return whileAnalysing(session, equalityOperatorCall) {
            operatorGenerator.convertEqualityOperatorCall(equalityOperatorCall)
        }
    }

    override fun visitCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall, data: Any?): IrElement = whileAnalysing(
        session, checkNotNullCall
    ) {
        return checkNotNullCall.convertWithOffsets { startOffset, endOffset ->
            IrCallImpl(
                startOffset, endOffset,
                checkNotNullCall.typeRef.toIrType(),
                irBuiltIns.checkNotNullSymbol,
                typeArgumentsCount = 1,
                valueArgumentsCount = 1,
                origin = IrStatementOrigin.EXCLEXCL
            ).apply {
                putTypeArgument(0, checkNotNullCall.argument.typeRef.toIrType().makeNotNull())
                putValueArgument(0, convertToIrExpression(checkNotNullCall.argument))
            }
        }
    }

    override fun visitGetClassCall(getClassCall: FirGetClassCall, data: Any?): IrElement = whileAnalysing(session, getClassCall) {
        val argument = getClassCall.argument
        val irType = getClassCall.typeRef.toIrType()
        val irClassType =
            if (argument is FirClassReferenceExpression) {
                argument.classTypeRef.toIrType()
            } else {
                argument.typeRef.toIrType()
            }
        val irClassReferenceSymbol = when (argument) {
            is FirResolvedReifiedParameterReference -> {
                symbolTable.referenceTypeParameter(argument.symbol)
            }
            is FirResolvedQualifier -> {
                when (val symbol = argument.symbol) {
                    is FirClassSymbol -> {
                        symbolTable.referenceClass(symbol)
                    }
                    is FirTypeAliasSymbol -> {
                        symbol.fir.expandedConeType.toIrClassSymbol()
                    }
                    else ->
                        return getClassCall.convertWithOffsets { startOffset, endOffset ->
                            IrErrorCallExpressionImpl(
                                startOffset, endOffset, irType, "Resolved qualifier ${argument.render()} does not have correct symbol"
                            )
                        }
                }
            }
            is FirClassReferenceExpression -> {
                (argument.classTypeRef.coneType.lowerBoundIfFlexible() as? ConeClassLikeType)?.toIrClassSymbol()
                // A null value means we have some unresolved code, possibly in a binary dependency that's missing a transitive dependency,
                // see KT-60181.
                // Returning null will lead to convertToIrExpression(argument) being called below which leads to a crash.
                // Instead, we return an error symbol.
                    ?: IrErrorClassImpl.symbol
            }
            else -> null
        }
        return getClassCall.convertWithOffsets { startOffset, endOffset ->
            if (irClassReferenceSymbol != null) {
                IrClassReferenceImpl(startOffset, endOffset, irType, irClassReferenceSymbol, irClassType)
            } else {
                IrGetClassImpl(startOffset, endOffset, irType, convertToIrExpression(argument))
            }
        }
    }

    private fun ConeClassLikeType?.toIrClassSymbol(): IrClassSymbol? {
        val classSymbol = this?.lookupTag?.toSymbol(session) as? FirClassSymbol<*> ?: return null
        return symbolTable.referenceClass(classSymbol)
    }

    private fun convertToArrayOfCall(arrayOfCall: FirArrayOfCall): IrVararg {
        return arrayOfCall.convertWithOffsets { startOffset, endOffset ->
            val arrayType = arrayOfCall.typeRef.toIrType()
            val elementType = if (arrayOfCall.typeRef is FirResolvedTypeRef) {
                arrayType.getArrayElementType(irBuiltIns)
            } else {
                createErrorType()
            }
            IrVarargImpl(
                startOffset, endOffset,
                type = arrayType,
                varargElementType = elementType,
                elements = arrayOfCall.arguments.map { it.convertToIrVarargElement() }
            )
        }
    }

    override fun visitArrayOfCall(arrayOfCall: FirArrayOfCall, data: Any?): IrElement = whileAnalysing(session, arrayOfCall) {
        return convertToArrayOfCall(arrayOfCall)
    }

    override fun visitAugmentedArraySetCall(
        augmentedArraySetCall: FirAugmentedArraySetCall,
        data: Any?
    ): IrElement = whileAnalysing(session, augmentedArraySetCall) {
        return augmentedArraySetCall.convertWithOffsets { startOffset, endOffset ->
            IrErrorCallExpressionImpl(
                startOffset, endOffset, irBuiltIns.unitType,
                "FirArraySetCall (resolve isn't supported yet)"
            )
        }
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: Any?): IrElement {
        return callGenerator.convertToGetObject(resolvedQualifier)
    }

    private fun LogicOperationKind.toIrDynamicOperator() = when (this) {
        LogicOperationKind.AND -> IrDynamicOperator.ANDAND
        LogicOperationKind.OR -> IrDynamicOperator.OROR
    }

    override fun visitBinaryLogicExpression(binaryLogicExpression: FirBinaryLogicExpression, data: Any?): IrElement {
        return binaryLogicExpression.convertWithOffsets<IrElement> { startOffset, endOffset ->
            val leftOperand = binaryLogicExpression.leftOperand.accept(this, data) as IrExpression
            val rightOperand = binaryLogicExpression.rightOperand.accept(this, data) as IrExpression
            if (leftOperand.type is IrDynamicType) {
                IrDynamicOperatorExpressionImpl(
                    startOffset,
                    endOffset,
                    irBuiltIns.booleanType,
                    binaryLogicExpression.kind.toIrDynamicOperator(),
                ).apply {
                    receiver = leftOperand
                    arguments.add(rightOperand)
                }
            } else when (binaryLogicExpression.kind) {
                LogicOperationKind.AND -> {
                    IrIfThenElseImpl(startOffset, endOffset, irBuiltIns.booleanType, IrStatementOrigin.ANDAND).apply {
                        branches.add(IrBranchImpl(leftOperand, rightOperand))
                        branches.add(elseBranch(constFalse(rightOperand.startOffset, rightOperand.endOffset)))
                    }
                }
                LogicOperationKind.OR -> {
                    IrIfThenElseImpl(startOffset, endOffset, irBuiltIns.booleanType, IrStatementOrigin.OROR).apply {
                        branches.add(IrBranchImpl(leftOperand, constTrue(leftOperand.startOffset, leftOperand.endOffset)))
                        branches.add(elseBranch(rightOperand))
                    }
                }
            }
        }
    }
}

val KtSourceElement.isChildOfForLoop: Boolean
    get() =
        if (this is KtPsiSourceElement) psi.parent is KtForExpression
        else treeStructure.getParent(lighterASTNode)?.tokenType == KtNodeTypes.FOR

val KtSourceElement.operationToken: IElementType?
    get() {
        assert(elementType == KtNodeTypes.BINARY_EXPRESSION)
        return if (this is KtPsiSourceElement) (psi as? KtBinaryExpression)?.operationToken
        else treeStructure.findChildByType(lighterASTNode, KtNodeTypes.OPERATION_REFERENCE)?.tokenType
            ?: error("No operation reference for binary expression: $lighterASTNode")
    }
