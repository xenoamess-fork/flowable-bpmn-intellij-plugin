package com.valb3r.bpmn.intellij.plugin.core.render

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithBpmnId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.activities.BpmnCallActivity
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.begin.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.boundary.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateConditionalCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateMessageCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateSignalCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateTimerCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.end.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.throwing.BpmnIntermediateEscalationThrowingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.throwing.BpmnIntermediateNoneThrowingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.throwing.BpmnIntermediateSignalThrowingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnEventGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnExclusiveGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnInclusiveGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnParallelGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.subprocess.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.BoundsElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.ShapeElement
import com.valb3r.bpmn.intellij.plugin.core.Colors
import com.valb3r.bpmn.intellij.plugin.core.debugger.currentDebugger
import com.valb3r.bpmn.intellij.plugin.core.events.BpmnElementRemovedEvent
import com.valb3r.bpmn.intellij.plugin.core.events.DiagramElementRemovedEvent
import com.valb3r.bpmn.intellij.plugin.core.events.ProcessModelUpdateEvents
import com.valb3r.bpmn.intellij.plugin.core.properties.uionly.UiOnlyPropertyType
import com.valb3r.bpmn.intellij.plugin.core.render.elements.BaseBpmnRenderElement
import com.valb3r.bpmn.intellij.plugin.core.render.elements.BaseDiagramRenderElement
import com.valb3r.bpmn.intellij.plugin.core.render.elements.RenderState
import com.valb3r.bpmn.intellij.plugin.core.render.elements.edges.EdgeRenderElement
import com.valb3r.bpmn.intellij.plugin.core.render.elements.elemIdToRemove
import com.valb3r.bpmn.intellij.plugin.core.render.elements.planes.PlaneRenderElement
import com.valb3r.bpmn.intellij.plugin.core.render.elements.shapes.*
import java.awt.BasicStroke
import java.awt.geom.Point2D
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

interface BpmnProcessRenderer {
    fun render(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex>
}

private val lastState = AtomicReference<RenderedState>()
data class RenderedState(val state: RenderState, val elementsById: Map<BpmnElementId, BaseDiagramRenderElement>) {

    fun canCopyOrCut(): Boolean {
        return state.ctx.selectedIds
                .mapNotNull { state.currentState.elementByDiagramId[it] }
                .mapNotNull { elementsById[it] }
                .isNotEmpty()
    }

    fun allChildrenOf(elem: Set<DiagramElementId>): Set<DiagramElementId> {
        val result = mutableSetOf<DiagramElementId>()

        result += elem.map { state.currentState.elementByDiagramId[it] }
                .mapNotNull { elementsById[it] }
                .flatMap { allChildrenOf(it) }

        return result
    }

    private fun allChildrenOf(elem: BaseDiagramRenderElement): Set<DiagramElementId> {
        val result = mutableSetOf<DiagramElementId>()
        result += elem.children.map { it.elementId }
        result += elem.children.flatMap { allChildrenOf(it) }
        return result
    }
}

fun lastRenderedState(): RenderedState? {
    return lastState.get()
}

class DefaultBpmnProcessRenderer(val icons: IconProvider) : BpmnProcessRenderer {
    private val undoRedoStartMargin = 20.0f
    private val closeAnchorRadius = 2f
    private val anchorRadius = 5f
    private val actionsIcoSize = 15f

    private val undoId = DiagramElementId("UNDO")
    private val redoId = DiagramElementId("REDO")

    private val DASHED_STROKE = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0f, floatArrayOf(5.0f), 0.0f)
    private val ACTION_AREA_STROKE = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0f, floatArrayOf(2.0f), 0.0f)

    override fun render(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex> {
        val elementsByDiagramId = mutableMapOf<DiagramElementId, BaseDiagramRenderElement>()
        val currentState = ctx.stateProvider.currentState()
        val history = currentDebugger()?.executionSequence(currentState.processId.id)?.history ?: emptyList()
        val state = RenderState(
                elementsByDiagramId,
                currentState,
                history,
                ctx,
                icons
        )
        
        val elements = mutableListOf<BaseBpmnRenderElement>()
        val elementsById = mutableMapOf<BpmnElementId, BaseDiagramRenderElement>()
        val root = createRootProcessElem(state, elements, elementsById)
        createShapes(state, elements, elementsById)
        createEdges(state, elements, elementsById)
        linkChildrenToParent(state, elementsById)
        // Not all elements have BpmnElementId, but they have DiagramElementId
        linkDiagramElementId(root, elementsByDiagramId)

        root.applyContextChangesAndPrecomputeExpandViewTransform()
        val rendered = root.render()

        // Overlay system elements on top of rendered BPMN diagram
        ctx.interactionContext.anchorsHit?.apply { drawAnchorsHit(ctx.canvas, this) }
        drawUndoRedo(state, rendered)
        drawSelectionRect(ctx)
        drawMultiremovalRect(state, rendered)

        lastState.set(RenderedState(state, elementsById))
        return rendered
    }

    private fun createRootProcessElem(state: RenderState, elements: MutableList<BaseBpmnRenderElement>, elementsById: MutableMap<BpmnElementId, BaseDiagramRenderElement>): BaseBpmnRenderElement {
        val processElem = PlaneRenderElement(state.currentState.processDiagramId(), state.currentState.processId, state, mutableListOf())
        elements += processElem
        elementsById[state.currentState.processId] = processElem
        return processElem
    }

    private fun createShapes(state: RenderState, elements: MutableList<BaseBpmnRenderElement>, elementsById: MutableMap<BpmnElementId, BaseDiagramRenderElement>) {
        state.currentState.shapes.forEach {
            val elem = state.currentState.elementByBpmnId[it.bpmnElement]
            elem?.let { bpmn ->
                mapFromShape(state, it.id, it, bpmn.element)?.let { shape ->
                    elements += shape
                    elementsById[bpmn.id] = shape
                }
            }
        }
    }

    private fun createEdges(state: RenderState, elements: MutableList<BaseBpmnRenderElement>, elementsById: MutableMap<BpmnElementId, BaseDiagramRenderElement>) {
        state.currentState.edges.forEach {
            val edge = EdgeRenderElement(it.id, it.bpmnElement!!, it, state)
            elements += edge
            elementsById[it.bpmnElement!!] = edge
        }
    }

    private fun linkChildrenToParent(state: RenderState, elementsById: MutableMap<BpmnElementId, BaseDiagramRenderElement>) {
        elementsById.forEach { (id, renderElem) ->
            val elem = state.currentState.elementByBpmnId[id]
            elem?.parent?.let {elementsById[it]}?.let { if (it is BaseBpmnRenderElement) it else null }?.let { parent ->
                parent.children.add(renderElem)
                parent.let { renderElem.parents.add(it) }
            }
        }
    }

    private fun linkDiagramElementId(root: BaseDiagramRenderElement, elementsByDiagramId: MutableMap<DiagramElementId, BaseDiagramRenderElement>) {
        elementsByDiagramId[root.elementId] = root
        root.children.forEach { linkDiagramElementId(it, elementsByDiagramId)}
    }

    private fun mapFromShape(state: RenderState, id: DiagramElementId, shape: ShapeElement, bpmn: WithBpmnId): BaseBpmnRenderElement? {
        val icons = state.icons
        return when (bpmn) {
            is BpmnStartEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.startEvent, shape, state, Colors.START_EVENT)
            is BpmnStartEscalationEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.escalationStartEvent, shape, state, Colors.START_EVENT)
            is BpmnStartConditionalEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.conditionalStartEvent, shape, state, Colors.START_EVENT)
            is BpmnStartErrorEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.errorStartEvent, shape, state, Colors.START_EVENT)
            is BpmnStartMessageEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.messageStartEvent, shape, state, Colors.START_EVENT)
            is BpmnStartSignalEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.signalStartEvent, shape, state, Colors.START_EVENT)
            is BpmnStartTimerEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.timerStartEvent, shape, state, Colors.START_EVENT)
            is BpmnBoundaryCancelEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryCancelEvent, shape, state)
            is BpmnBoundaryCompensationEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryCompensationEvent, shape, state)
            is BpmnBoundaryConditionalEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryConditionalEvent, shape, state)
            is BpmnBoundaryErrorEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryErrorEvent, shape, state)
            is BpmnBoundaryEscalationEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryEscalationEvent, shape, state)
            is BpmnBoundaryMessageEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryMessageEvent, shape, state)
            is BpmnBoundarySignalEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundarySignalEvent, shape, state)
            is BpmnBoundaryTimerEvent -> AnyShapeNestableIconShape(id, bpmn.id, icons.boundaryTimerEvent, shape, state)
            is BpmnUserTask -> TopLeftIconShape(id, bpmn.id, icons.user, shape, state)
            is BpmnScriptTask -> TopLeftIconShape(id, bpmn.id, icons.script, shape, state)
            is BpmnServiceTask -> TopLeftIconShape(id, bpmn.id, icons.gear, shape, state)
            is BpmnBusinessRuleTask -> TopLeftIconShape(id, bpmn.id, icons.businessRule, shape, state)
            is BpmnReceiveTask -> TopLeftIconShape(id, bpmn.id, icons.receive, shape, state)
            is BpmnManualTask -> TopLeftIconShape(id, bpmn.id, icons.manual, shape, state)
            is BpmnCamelTask -> TopLeftIconShape(id, bpmn.id, icons.camel, shape, state)
            is BpmnHttpTask -> TopLeftIconShape(id, bpmn.id, icons.http, shape, state)
            is BpmnMailTask -> TopLeftIconShape(id, bpmn.id, icons.mail, shape, state)
            is BpmnMuleTask -> TopLeftIconShape(id, bpmn.id, icons.mule, shape, state)
            is BpmnDecisionTask -> TopLeftIconShape(id, bpmn.id, icons.decision, shape, state)
            is BpmnShellTask -> TopLeftIconShape(id, bpmn.id, icons.shell, shape, state)
            is BpmnSubProcess -> NoIconShape(id, bpmn.id, shape, state, Colors.PROCESS_COLOR, Colors.ELEMENT_BORDER_COLOR, Colors.SUBPROCESS_TEXT_COLOR, areaType = AreaType.SHAPE_THAT_NESTS)
            is BpmnEventSubprocess -> NoIconShape(id, bpmn.id, shape, state, Colors.PROCESS_COLOR, Colors.ELEMENT_BORDER_COLOR, Colors.SUBPROCESS_TEXT_COLOR, areaType = AreaType.SHAPE_THAT_NESTS, borderStroke = DASHED_STROKE)
            is BpmnTransactionalSubProcess -> NoIconDoubleBorderShape(id, bpmn.id, shape, state, areaType = AreaType.SHAPE_THAT_NESTS)
            is BpmnCollapsedSubprocess -> ExpandableShapeNoIcon(id, bpmn.id, isCollapsed(bpmn.id, state), icons.plus, icons.minus, shape, state, areaType = AreaType.SHAPE_THAT_NESTS)
            is BpmnTransactionCollapsedSubprocess -> ExpandableShapeNoIcon(id, bpmn.id, isCollapsed(bpmn.id, state), icons.plus, icons.minus, shape, state, areaType = AreaType.SHAPE_THAT_NESTS)
            is BpmnCallActivity -> NoIconShape(id, bpmn.id, shape, state)
            is BpmnAdHocSubProcess -> BottomMiddleIconShape(id, bpmn.id, icons.tilde, shape, state, areaType = AreaType.SHAPE_THAT_NESTS)
            is BpmnExclusiveGateway -> IconShape(id, bpmn.id, icons.exclusiveGateway, shape, state)
            is BpmnParallelGateway -> IconShape(id, bpmn.id, icons.parallelGateway, shape, state)
            is BpmnInclusiveGateway -> IconShape(id, bpmn.id, icons.inclusiveGateway, shape, state)
            is BpmnEventGateway -> IconShape(id, bpmn.id, icons.eventGateway, shape, state)
            is BpmnEndEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.endEvent, shape, state, Colors.END_EVENT)
            is BpmnEndCancelEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.cancelEndEvent, shape, state, Colors.END_EVENT)
            is BpmnEndErrorEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.errorEndEvent, shape, state, Colors.END_EVENT)
            is BpmnEndEscalationEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.escalationEndEvent, shape, state, Colors.END_EVENT)
            is BpmnEndTerminateEvent -> EllipticIconOnLayerShape(id, bpmn.id, icons.terminateEndEvent, shape, state, Colors.END_EVENT)
            is BpmnIntermediateTimerCatchingEvent -> IconShape(id, bpmn.id, icons.timerCatchEvent, shape, state)
            is BpmnIntermediateMessageCatchingEvent -> IconShape(id, bpmn.id, icons.messageCatchEvent, shape, state)
            is BpmnIntermediateSignalCatchingEvent -> IconShape(id, bpmn.id, icons.signalCatchEvent, shape, state)
            is BpmnIntermediateConditionalCatchingEvent -> IconShape(id, bpmn.id, icons.conditionalCatchEvent, shape, state)
            is BpmnIntermediateNoneThrowingEvent -> IconShape(id, bpmn.id, icons.noneThrowEvent, shape, state)
            is BpmnIntermediateSignalThrowingEvent -> IconShape(id, bpmn.id, icons.signalThrowEvent, shape, state)
            is BpmnIntermediateEscalationThrowingEvent -> IconShape(id, bpmn.id, icons.escalationThrowEvent, shape, state)
            else -> throw IllegalArgumentException("Unknown shape: ${bpmn.javaClass}")
        }
    }

    private fun isCollapsed(id: BpmnElementId, state: RenderState): Boolean {
        return state.currentState.elemUiOnlyPropertiesByStaticElementId[id]?.get(UiOnlyPropertyType.EXPANDED)?.value as Boolean? ?: false
    }

    private fun drawSelectionRect(state: RenderContext) {
        state.interactionContext.dragSelectionRect?.let {
            val rect = it.toRect()
            state.canvas.drawRectNoCameraTransform(Point2D.Float(rect.x, rect.y), rect.width, rect.height, ACTION_AREA_STROKE, Colors.ACTIONS_BORDER_COLOR.color)
        }
    }

    private fun drawMultiremovalRect(state: RenderState, renderedArea: MutableMap<DiagramElementId, AreaWithZindex>) {
        if (null != state.ctx.interactionContext.dragSelectionRect || state.ctx.selectedIds.size <= 1) {
            return
        }

        val areas = state.ctx.selectedIds.mapNotNull { renderedArea[it] }

        val minX = areas.map { it.area.bounds2D.minX }.min()?.toFloat()
        val minY = areas.map { it.area.bounds2D.minY }.min()?.toFloat()
        val maxX = areas.map { it.area.bounds2D.maxX }.max()?.toFloat()
        val maxY = areas.map { it.area.bounds2D.maxY }.max()?.toFloat()

        // TODO: This currently does not support event cascading, so only plain elements can be removed
        if (null != minX && null != minY && null != maxX && null != maxY) {
            val ownerId = state.ctx.selectedIds.joinToString { it.id }
            state.ctx.canvas.drawRectNoCameraTransform(Point2D.Float(minX, minY), maxX - minX, maxY - minY, ACTION_AREA_STROKE, Colors.ACTIONS_BORDER_COLOR.color)
            val delId = DiagramElementId(ownerId).elemIdToRemove()
            val deleteIconArea = state.ctx.canvas.drawIconNoCameraTransform(BoundsElement(maxX, minY, actionsIcoSize, actionsIcoSize), icons.recycleBin)
            state.ctx.interactionContext.clickCallbacks[delId] = { dest ->
                val targetIds = state.ctx.selectedIds.filter {
                    renderedArea[it]?.areaType == AreaType.SHAPE_THAT_NESTS
                            || renderedArea[it]?.areaType == AreaType.SHAPE
                            || renderedArea[it]?.areaType == AreaType.EDGE
                }

                dest.addElementRemovedEvent(
                        targetIds.map { DiagramElementRemovedEvent(it) },
                        targetIds.mapNotNull { state.currentState.elementByDiagramId[it] }.map { BpmnElementRemovedEvent(it) }
                )
            }
            renderedArea[delId] = AreaWithZindex(deleteIconArea, AreaType.POINT, mutableSetOf(), mutableSetOf(), ANCHOR_Z_INDEX, null)
        }
    }

    private fun drawUndoRedo(
            state: RenderState,
            renderedArea: MutableMap<DiagramElementId, AreaWithZindex>
    ) {
        var locationX = undoRedoStartMargin
        val locationY = undoRedoStartMargin

        if (state.currentState.undoRedo.contains(ProcessModelUpdateEvents.UndoRedo.UNDO)) {
            locationX += drawIconWithAction(state, undoId, locationX, locationY, renderedArea, { dest -> dest.undo() }, icons.undo)
        }

        if (state.currentState.undoRedo.contains(ProcessModelUpdateEvents.UndoRedo.REDO)) {
            locationX += drawIconWithAction(state, redoId, locationX, locationY, renderedArea, { dest -> dest.redo() }, icons.redo)
        }
    }

    private fun drawIconWithAction(
            state: RenderState,
            actionElementId: DiagramElementId,
            locationX: Float,
            locationY: Float,
            renderedArea:
            MutableMap<DiagramElementId, AreaWithZindex>,
            onClick: (ProcessModelUpdateEvents) -> Unit,
            icon: Icon
    ): Float {
        val color = if (isActive(actionElementId, state)) Colors.SELECTED_COLOR else null
        val areaRedo = color?.let { state.ctx.canvas.drawIconAtScreen(Point2D.Float(locationX, locationY), icon, it.color) }
                ?: state.ctx.canvas.drawIconAtScreen(Point2D.Float(locationX, locationY), icon)
        renderedArea[actionElementId] = AreaWithZindex(areaRedo, AreaType.SHAPE, index = ICON_Z_INDEX)
        state.ctx.interactionContext.clickCallbacks[actionElementId] = onClick
        return icon.iconWidth + undoRedoStartMargin
    }

    private fun drawAnchorsHit(canvas: CanvasPainter, anchors: AnchorHit) {
        anchors.anchors.forEach {
            when (it.key) {
                AnchorType.VERTICAL, AnchorType.HORIZONTAL -> canvas.drawZeroAreaLine(it.value, anchors.objectAnchor, DASHED_STROKE, Colors.ANCHOR_COLOR.color)
                AnchorType.POINT -> canvas.drawCircle(it.value, anchorRadius, Colors.ANCHOR_COLOR.color)
            }
        }

        anchors.closeAnchors.forEach { canvas.drawCircle(it, closeAnchorRadius, Colors.CLOSE_ANCHOR_COLOR.color) }
    }

    private fun isActive(elemId: DiagramElementId, state: RenderState): Boolean {
        return elemId.let { state.ctx.selectedIds.contains(it) }
    }
}