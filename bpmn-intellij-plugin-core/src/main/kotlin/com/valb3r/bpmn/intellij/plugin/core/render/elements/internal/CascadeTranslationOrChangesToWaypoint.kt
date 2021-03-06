package com.valb3r.bpmn.intellij.plugin.core.render.elements.internal

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import java.awt.geom.Point2D

data class CascadeTranslationOrChangesToWaypoint(
        val cascadeSource: BpmnElementId,
        val waypointId: DiagramElementId,
        val location: Point2D.Float,
        val parentEdgeId: DiagramElementId,
        val internalId: Int
)