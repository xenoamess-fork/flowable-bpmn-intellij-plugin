package com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId

data class WithParentId(
        val parent: BpmnElementId,
        val element: WithBpmnId,
        val parentIdForXml: BpmnElementId = parent
): WithBpmnId by element