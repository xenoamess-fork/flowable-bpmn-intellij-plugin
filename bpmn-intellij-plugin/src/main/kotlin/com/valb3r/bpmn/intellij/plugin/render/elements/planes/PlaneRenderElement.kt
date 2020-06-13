package com.valb3r.bpmn.intellij.plugin.render.elements.planes

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.render.AreaType
import com.valb3r.bpmn.intellij.plugin.render.AreaWithZindex
import com.valb3r.bpmn.intellij.plugin.render.Camera
import com.valb3r.bpmn.intellij.plugin.render.RenderContext
import com.valb3r.bpmn.intellij.plugin.render.elements.BaseBpmnRenderElement
import com.valb3r.bpmn.intellij.plugin.render.elements.BaseDiagramRenderElement
import com.valb3r.bpmn.intellij.plugin.render.elements.RenderState
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.util.*

class PlaneRenderElement(
        override val elementId: DiagramElementId,
        override val bpmnElementId: BpmnElementId,
        state: RenderState,
        override val children: MutableList<BaseDiagramRenderElement> = mutableListOf()
): BaseBpmnRenderElement(elementId, bpmnElementId, state) {

    override fun drawActionsElement(): Map<DiagramElementId, AreaWithZindex> {
        return mutableMapOf()
    }

    override fun doDragToWithoutChildren(dx: Float, dy: Float) {
        // NOP
    }

    override fun doOnDragEndWithoutChildren(dx: Float, dy: Float, droppedOn: BpmnElementId?, allDroppedOn: SortedMap<AreaType, BpmnElementId>): MutableList<Event> {
        return mutableListOf()
    }

    override fun doResizeWithoutChildren(dw: Float, dh: Float) {
        // NOP
    }

    override fun doResizeEndWithoutChildren(dw: Float, dh: Float): MutableList<Event> {
        return mutableListOf()
    }

    override fun currentRect(camera: Camera): Rectangle2D.Float {
        return Rectangle2D.Float()
    }

    override fun waypointAnchors(camera: Camera): MutableSet<Point2D.Float> {
        return mutableSetOf()
    }

    override fun shapeAnchors(camera: Camera): MutableSet<Point2D.Float> {
        return mutableSetOf()
    }

    override fun doRenderWithoutChildren(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex> {
        val area = InfiniteShape()
        return mutableMapOf(elementId to AreaWithZindex(area, AreaType.PARENT_PROCESS_SHAPE, mutableSetOf(), mutableSetOf(), bpmnElementId = bpmnElementId, index = zIndex()))
    }

    override fun drawActions(x: Float, y: Float): Map<DiagramElementId, AreaWithZindex> {
        return mutableMapOf()
    }
}

private class InfiniteShape: Area() {

    override fun contains(x: Double, y: Double): Boolean {
        return true
    }

    override fun contains(p: Point2D?): Boolean {
        return true
    }

    override fun contains(x: Double, y: Double, w: Double, h: Double): Boolean {
        return true
    }

    override fun contains(r: Rectangle2D?): Boolean {
        return true
    }

    override fun getBounds2D(): Rectangle2D {
        return Rectangle()
    }

    override fun intersects(x: Double, y: Double, w: Double, h: Double): Boolean {
        return true
    }

    override fun intersects(r: Rectangle2D?): Boolean {
        return true
    }

    override fun getBounds(): Rectangle {
        return Rectangle()
    }
}