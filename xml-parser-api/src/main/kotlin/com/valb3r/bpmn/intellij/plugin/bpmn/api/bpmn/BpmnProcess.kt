package com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn

import com.github.pozo.KotlinBuilder
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.*

@KotlinBuilder
data class BpmnProcess(
        val id: String,
        val name: String,
        val documentation: String?,
        val isExecutable: Boolean?,
        val startEvent: BpmnStartEvent,
        val callActivity: List<BpmnCallActivity>?,
        val serviceTask: List<BpmnServiceTask>?,
        val sequenceFlow: List<BpmnSequenceFlow>?,
        val exclusiveGateway: List<BpmnExclusiveGateway>?,
        val endEvent: BpmnEndEvent
)