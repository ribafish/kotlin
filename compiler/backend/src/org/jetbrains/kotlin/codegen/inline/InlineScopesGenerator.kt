/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.inline

import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.tree.LabelNode
import org.jetbrains.org.objectweb.asm.tree.LineNumberNode
import org.jetbrains.org.objectweb.asm.tree.LocalVariableNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

class InlineScopesGenerator {
    var inlinedScopes = 0
    var currentCallSiteLineNumber = 0

    private data class ScopeInfo(val variable: LocalVariableNode, val scopeNumber: Int, val inlineNesting: Int)

    private data class MarkerVariableInfo(val callSiteLineNumber: Int, val surroundingScopeNumber: Int?, val inlineNesting: Int)

    private abstract inner class VariableRenamer {
        val inlineScopesStack = mutableListOf<ScopeInfo>()

        abstract fun visitMarkerVariable(
            variable: LocalVariableNode,
            scopeNumber: Int,
            inlineNesting: Int
        ): MarkerVariableInfo

        abstract fun shouldPostponeAddingAScopeNumber(variable: LocalVariableNode, inlineNesting: Int): Boolean

        open fun shouldSkipVariable(variable: LocalVariableNode): Boolean = false

        open fun inlineNesting(): Int = -1

        fun renameVariables(node: MethodNode): Int {
            val localVariables = node.localVariables ?: return 0
            val labelToIndex = node.getLabelToIndexMap()

            // Inline function and lambda parameters are introduced before the corresponding inline marker variable,
            // so we need to keep track of them to assign the correct scope number later.
            val variablesWithNotMatchingDepth = mutableListOf<LocalVariableNode>()
            var seenInlineScopesNumber = 0

            val sortedVariables = localVariables.sortedBy { labelToIndex[it.start.label] }
            var currentInlineScopeNumber: Int
            var currentInlineNesting: Int
            for (variable in sortedVariables) {
                dropToClosestSurroundingScope(variable, labelToIndex)

                val name = variable.name
                if (inlineScopesStack.isNotEmpty()) {
                    val info = inlineScopesStack.last()
                    currentInlineScopeNumber = info.scopeNumber
                    currentInlineNesting = info.inlineNesting
                } else if (shouldSkipVariable(variable)) {
                    continue
                } else {
                    // The number 0 belongs to the top frame
                    currentInlineScopeNumber = 0
                    currentInlineNesting = inlineNesting()
                }

                if (isFakeLocalVariableForInline(name)) {
                    seenInlineScopesNumber += 1
                    currentInlineScopeNumber = seenInlineScopesNumber

                    val (callSiteLineNumber, surroundingScopeNumber, inlineNesting) = visitMarkerVariable(
                        variable,
                        currentInlineScopeNumber,
                        currentInlineNesting
                    )

                    inlineScopesStack += ScopeInfo(variable, currentInlineScopeNumber, inlineNesting)

                    variable.name = computeNewVariableName(
                        name,
                        currentInlineScopeNumber + inlinedScopes,
                        callSiteLineNumber,
                        surroundingScopeNumber
                    )

                    for (variableWithNotMatchingDepth in variablesWithNotMatchingDepth) {
                        variableWithNotMatchingDepth.name =
                            computeNewVariableName(variableWithNotMatchingDepth.name, currentInlineScopeNumber + inlinedScopes, null, null)
                    }
                    variablesWithNotMatchingDepth.clear()
                } else {
                    if (shouldPostponeAddingAScopeNumber(variable, currentInlineNesting)) {
                        variablesWithNotMatchingDepth.add(variable)
                    } else {
                        variable.name = computeNewVariableName(name, currentInlineScopeNumber + inlinedScopes, null, null)
                    }
                }
            }

            return seenInlineScopesNumber
        }

        private fun dropToClosestSurroundingScope(variable: LocalVariableNode, labelToIndex: Map<Label, Int>) {
            fun LocalVariableNode.contains(other: LocalVariableNode): Boolean {
                val startIndex = labelToIndex[start.label] ?: return false
                val endIndex = labelToIndex[end.label] ?: return false
                val otherStartIndex = labelToIndex[other.start.label] ?: return false
                val otherEndIndex = labelToIndex[other.end.label] ?: return false
                return startIndex < otherStartIndex && endIndex >= otherEndIndex
            }

            while (inlineScopesStack.isNotEmpty() && !inlineScopesStack.last().variable.contains(variable)) {
                inlineScopesStack.removeLast()
            }
        }
    }

    fun addInlineScopesInfo(node: MethodNode, isRegeneratingAnonymousObject: Boolean) {
        val localVariables = node.localVariables
        if (localVariables?.isEmpty() == true) {
            return
        }

        val markerVariablesWithoutScopeInfoNum = localVariables.count {
            isFakeLocalVariableForInline(it.name) && !it.name.contains(INLINE_SCOPE_NUMBER_SEPARATOR)
        }

        when {
            isRegeneratingAnonymousObject -> {
                if (markerVariablesWithoutScopeInfoNum > 0) {
                    addInlineScopesInfoFromIVSuffixesWhenRegeneratingAnonymousObject(node)
                }
            }
            // When inlining a function its marker variable won't contain any scope numbers yet.
            // But if there are more than one marker variable like this, it means that we
            // are inlining the code produced by the old compiler versions, where inline scopes
            // have not been introduced.
            markerVariablesWithoutScopeInfoNum == 1 ->
                addInlineScopesInfoFromScopeNumbers(node)
            else ->
                addInlineScopesInfoFromIVSuffixes(node)
        }
    }

    private fun addInlineScopesInfoFromScopeNumbers(node: MethodNode) {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        val renamer = object : VariableRenamer() {
            override fun visitMarkerVariable(
                variable: LocalVariableNode,
                scopeNumber: Int,
                oldScopeNumberOfCurrentMarkerVariable: Int
            ): MarkerVariableInfo {
                val name = variable.name
                val info = name.getInlineScopeInfo()
                val callSiteLineNumber =
                    if (scopeNumber == 1) {
                        currentCallSiteLineNumber
                    } else {
                        info?.callSiteLineNumber ?: 0
                    }

                if (name.isInlineLambdaName) {
                    val surroundingScopeNumber = info?.surroundingScopeNumber
                    val newSurroundingScopeNumber =
                        when {
                            // The first encountered inline scope belongs to the lambda, which means
                            // that its surrounding scope is the function where the lambda is being inlined to.
                            scopeNumber == 1 -> 0
                            // Every lambda that is already inlined must have a surrounding scope number.
                            // If it doesn't, then it means that we are inlining the code compiled by
                            // the older versions of the Kotlin compiler, where surrounding scope numbers
                            // haven't been introduced yet.
                            surroundingScopeNumber != null -> surroundingScopeNumber + inlinedScopes + 1
                            // This situation shouldn't happen, so add invalid info here
                            else -> -1
                        }
                    return MarkerVariableInfo(callSiteLineNumber, newSurroundingScopeNumber, info?.scopeNumber ?: 0)
                }
                return MarkerVariableInfo(callSiteLineNumber, null, info?.scopeNumber ?: 0)
            }

            override fun shouldPostponeAddingAScopeNumber(
                variable: LocalVariableNode,
                oldScopeNumberOfCurrentMarkerVariable: Int
            ): Boolean {
                val scopeNumber = variable.name.getInlineScopeInfo()?.scopeNumber
                if (scopeNumber != null) {
                    return scopeNumber != oldScopeNumberOfCurrentMarkerVariable
                }
                return inlineScopesStack.isEmpty()
            }
        }

        inlinedScopes += renamer.renameVariables(node)
    }

    private fun addInlineScopesInfoFromIVSuffixes(node: MethodNode) {
        val labelToLineNumber = node.getLabelToLineNumberMap()

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        val renamer = object : VariableRenamer() {
            override fun visitMarkerVariable(variable: LocalVariableNode, scopeNumber: Int, ivDepth: Int): MarkerVariableInfo {
                val name = variable.name
                val currentIVDepth =
                    if (name.isInlineLambdaName) {
                        getInlineDepth(name)
                    } else {
                        ivDepth + 1
                    }

                val callSiteLineNumber =
                    if (scopeNumber == 1) {
                        currentCallSiteLineNumber
                    } else {
                        // When inlining from the code compiled by the old compiler versions,
                        // the marker variable will not contain the call site line number.
                        // In this case we will take the line number of the variable start offset
                        // as the call site line number.
                        labelToLineNumber[variable.start.label] ?: 0
                    }

                if (name.isInlineLambdaName) {
                    val newSurroundingScopeNumber = computeSurroundingScopeNumber(inlineScopesStack, scopeNumber, currentIVDepth)
                    return MarkerVariableInfo(callSiteLineNumber, newSurroundingScopeNumber, currentIVDepth)
                }
                return MarkerVariableInfo(callSiteLineNumber, null, currentIVDepth)
            }

            override fun shouldPostponeAddingAScopeNumber(variable: LocalVariableNode, ivDepth: Int): Boolean =
                inlineScopesStack.isEmpty() || getInlineDepth(variable.name) != ivDepth
        }

        inlinedScopes += renamer.renameVariables(node)
    }

    private fun addInlineScopesInfoFromIVSuffixesWhenRegeneratingAnonymousObject(node: MethodNode) {
        val labelToLineNumber = node.getLabelToLineNumberMap()

        // This renamer is slightly different from the one we used when computing inline scopes from the
        // $iv suffixes. Here no function is being inlined, so the base depth in $iv suffixes is equal to 0.
        // When we meet the first marker variable, it should have its depth equal to 1. Apart from that,
        // when calculating call site line numbers, we always pick the line number of the marker variable
        // start offset and not rely on the `currentCallSiteLineNumber` field. Also, when computing surrounding
        // scope numbers we assign surrounding scope 0 (that represents the top frame) to inline lambda
        // marker variables that don't have a surrounding scope.
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        val renamer = object : VariableRenamer() {
            // Here inline nesting means depth in $iv suffixes.
            // On contrary with the situation when we are inlining a function,
            // here we won't meet a marker variable that represents the method node.
            // When we meet the first marker variable, it should have depth equal to 1.
            override fun inlineNesting(): Int = 0

            override fun shouldSkipVariable(variable: LocalVariableNode): Boolean =
                !isFakeLocalVariableForInline(variable.name) && !variable.name.contains(INLINE_FUN_VAR_SUFFIX)

            override fun visitMarkerVariable(variable: LocalVariableNode, scopeNumber: Int, ivDepth: Int): MarkerVariableInfo {
                val name = variable.name
                val currentIVDepth =
                    if (name.isInlineLambdaName) {
                        getInlineDepth(name)
                    } else {
                        ivDepth + 1
                    }

                val callSiteLineNumber = labelToLineNumber[variable.start.label] ?: 0
                if (name.isInlineLambdaName) {
                    val newSurroundingScopeNumber = computeSurroundingScopeNumber(inlineScopesStack, scopeNumber, currentIVDepth)
                    return MarkerVariableInfo(callSiteLineNumber, newSurroundingScopeNumber, currentIVDepth)
                }
                return MarkerVariableInfo(callSiteLineNumber, null, currentIVDepth)
            }

            override fun shouldPostponeAddingAScopeNumber(variable: LocalVariableNode, ivDepth: Int): Boolean =
                inlineScopesStack.isEmpty() || getInlineDepth(variable.name) != ivDepth
        }

        renamer.renameVariables(node)
    }

    private fun computeSurroundingScopeNumber(inlineScopesStack: List<ScopeInfo>, scopeNumber: Int, currentIVDepth: Int): Int =
        if (scopeNumber == 1) {
            0
        } else {
            val surroundingScopeNumber = inlineScopesStack.lastOrNull { it.inlineNesting == currentIVDepth }?.scopeNumber
            surroundingScopeNumber?.plus(inlinedScopes) ?: 0
        }

    private fun computeNewVariableName(
        name: String,
        scopeNumber: Int,
        callSiteLineNumber: Int?,
        surroundingScopeNumber: Int?
    ): String {
        val prefix = name.replace(INLINE_FUN_VAR_SUFFIX, "").dropInlineScopeInfo()
        return buildString {
            append(prefix)
            append(INLINE_SCOPE_NUMBER_SEPARATOR)
            append(scopeNumber)

            if (callSiteLineNumber != null) {
                append(INLINE_SCOPE_NUMBER_SEPARATOR)
                append(callSiteLineNumber)
            }

            if (surroundingScopeNumber != null) {
                append(INLINE_SCOPE_NUMBER_SEPARATOR)
                append(surroundingScopeNumber)
            }
        }
    }
}

fun updateCallSiteLineNumber(name: String, lineNumberMapping: Map<Int, Int>): String =
    updateCallSiteLineNumber(name) { lineNumberMapping[it] ?: it }

fun updateCallSiteLineNumber(name: String, newLineNumber: Int): String =
    updateCallSiteLineNumber(name) { newLineNumber }

private fun updateCallSiteLineNumber(name: String, calculate: (Int) -> Int): String {
    val (scopeNumber, callSiteLineNumber, surroundingScopeNumber) = name.getInlineScopeInfo() ?: return name
    if (callSiteLineNumber == null) {
        return name
    }

    val newLineNumber = calculate(callSiteLineNumber)
    if (newLineNumber == callSiteLineNumber) {
        return name
    }

    val newName = name
        .dropInlineScopeInfo()
        .addScopeInfo(scopeNumber)
        .addScopeInfo(newLineNumber)

    if (surroundingScopeNumber == null) {
        return newName
    }
    return newName.addScopeInfo(surroundingScopeNumber)
}

internal fun MethodNode.getLabelToIndexMap(): Map<Label, Int> =
    instructions.filterIsInstance<LabelNode>()
        .withIndex()
        .associate { (index, labelNode) ->
            labelNode.label to index
        }

private fun MethodNode.getLabelToLineNumberMap(): Map<Label, Int> {
    val result = mutableMapOf<Label, Int>()
    var currentLineNumber = 0
    for (insn in instructions) {
        if (insn is LineNumberNode) {
            currentLineNumber = insn.line
        } else if (insn is LabelNode) {
            result[insn.label] = currentLineNumber
        }
    }

    return result
}

fun String.addScopeInfo(number: Int): String =
    "$this$INLINE_SCOPE_NUMBER_SEPARATOR$number"

private fun getInlineDepth(variableName: String): Int {
    var endIndex = variableName.length
    var depth = 0

    val suffixLen = INLINE_FUN_VAR_SUFFIX.length
    while (endIndex >= suffixLen) {
        if (variableName.substring(endIndex - suffixLen, endIndex) != INLINE_FUN_VAR_SUFFIX) {
            break
        }

        depth++
        endIndex -= suffixLen
    }

    return depth
}

private val String.isInlineLambdaName: Boolean
    get() = startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)
