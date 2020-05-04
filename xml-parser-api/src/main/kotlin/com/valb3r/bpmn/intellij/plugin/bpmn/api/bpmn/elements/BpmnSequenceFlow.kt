package com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements

import com.github.pozo.KotlinBuilder

@KotlinBuilder
data class BpmnSequenceFlow(
        override val id: String,
        val name: String?,
        val documentation: String?,
        val sourceRef: String,
        val targetRef: String,
        val conditionExpression: ConditionExpression?
): WithId

@KotlinBuilder
data class ConditionExpression(
        val type: String,
        val text: String
)