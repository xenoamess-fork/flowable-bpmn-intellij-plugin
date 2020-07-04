package com.valb3r.bpmn.intellij.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.nhaarman.mockitokotlin2.*
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnParser
import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnProcessObject
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnProcess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnProcessBodyBuilder
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.BpmnSequenceFlow
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithParentId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.boundary.BpmnBoundaryErrorEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.subprocess.BpmnSubProcess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.BpmnServiceTask
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.BoundsElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.PlaneElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.ShapeElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.Property
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.PropertyType
import com.valb3r.bpmn.intellij.plugin.events.*
import com.valb3r.bpmn.intellij.plugin.properties.SelectedValueAccessor
import com.valb3r.bpmn.intellij.plugin.properties.TextValueAccessor
import com.valb3r.bpmn.intellij.plugin.properties.propertiesVisualizer
import com.valb3r.bpmn.intellij.plugin.render.*
import com.valb3r.bpmn.intellij.plugin.state.currentStateProvider
import org.amshove.kluent.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

// These are global singletons as i.e. `initializeNewElementsFactory` is
private val textFieldsConstructed: MutableMap<Pair<BpmnElementId, PropertyType>, TextValueAccessor> = mutableMapOf()
private val boolFieldsConstructed: MutableMap<Pair<BpmnElementId, PropertyType>, SelectedValueAccessor> = mutableMapOf()
private val comboboxFactory = { id: BpmnElementId, type: PropertyType, value: String, allowedValues: Set<String> -> textFieldsConstructed.computeIfAbsent(Pair(id, type)) {
    val res = mock<TextValueAccessor>()
    whenever(res.text).thenReturn(value)
    return@computeIfAbsent res
} }
private val editorFactory = { id: BpmnElementId, type: PropertyType, value: String -> textFieldsConstructed.computeIfAbsent(Pair(id, type)) {
    val res = mock<TextValueAccessor>()
    whenever(res.text).thenReturn(value)
    return@computeIfAbsent res
} }
private val checkboxFieldFactory = { id: BpmnElementId, type: PropertyType, value: Boolean -> boolFieldsConstructed.computeIfAbsent(Pair(id, type)) {
    val res = mock<SelectedValueAccessor>()
    whenever(res.isSelected).thenReturn(value)
    return@computeIfAbsent res
} }

internal class UiEditorLightE2ETest {

    private val newLink = "NEW-SEQUENCE"
    private val doDel = "DEL"

    private val icon = "dummy-icon.svg".asResource()

    private val startElemX = 0.0f
    private val startElemY = 0.0f
    private val serviceTaskSize = 60.0f
    private val boundaryEventSize = 15.0f
    private val subProcessElemX = 0.0f
    private val subProcessElemY = 0.0f
    private val subProcessSize = 200.0f

    private val endElemX = 10 * serviceTaskSize
    private val endElemY = 0.0f
    private val endElemMidY = serviceTaskSize / 2.0f

    private val diagramMainElementId = DiagramElementId("diagramMainElement")
    private val diagramMainPlaneElementId = DiagramElementId("diagramMainPlaneElement")

    private val optionalBoundaryErrorEventBpmnId = BpmnElementId("boundaryErrorEvent")
    private val subprocessBpmnId = BpmnElementId("subProcess")
    private val subprocessInSubProcessBpmnId = BpmnElementId("nestedSubProcess")
    private val serviceTaskStartBpmnId = BpmnElementId("startServiceTask")
    private val serviceTaskEndBpmnId = BpmnElementId("endServiceTask")

    private val optionalBoundaryErrorEventDiagramId = DiagramElementId("DIAGRAM-boundaryErrorEvent")
    private val subprocessInSubProcessDiagramId = DiagramElementId("DIAGRAM-nestedSubProcess")
    private val subprocessDiagramId = DiagramElementId("DIAGRAM-subProcess")
    private val serviceTaskStartDiagramId = DiagramElementId("DIAGRAM-startServiceTask")
    private val serviceTaskEndDiagramId = DiagramElementId("DIAGRAM-endServiceTask")

    private val bpmnServiceTaskStart = BpmnServiceTask(serviceTaskStartBpmnId, null, null, null, null, null, null, null, null, null, null, null, null)
    private val bpmnSubProcess = BpmnSubProcess(subprocessBpmnId, null, null, null, null, false, false)
    private val bpmnNestedSubProcess = BpmnSubProcess(subprocessInSubProcessBpmnId, null, null, null, null, false, false)
    private val bpmnServiceTaskEnd = BpmnServiceTask(serviceTaskEndBpmnId, null, null, null, null, null, null, null, null, null, null, null, null)
    private val diagramServiceTaskStart = ShapeElement(serviceTaskStartDiagramId, bpmnServiceTaskStart.id, BoundsElement(startElemX, startElemY, serviceTaskSize, serviceTaskSize))
    private val diagramServiceTaskEnd = ShapeElement(serviceTaskEndDiagramId, bpmnServiceTaskEnd.id, BoundsElement(endElemX, endElemY, serviceTaskSize, serviceTaskSize))
    private val diagramSubProcess = ShapeElement(subprocessDiagramId, subprocessBpmnId, BoundsElement(subProcessElemX, subProcessElemY, subProcessSize, subProcessSize))
    private val diagramNestedSubProcess = ShapeElement(subprocessInSubProcessDiagramId, subprocessInSubProcessBpmnId, BoundsElement(subProcessElemX, subProcessElemY, subProcessSize / 2.0f, subProcessSize / 2.0f))

    private val icons = mock<IconProvider>()
    private val renderer = spy(DefaultBpmnProcessRenderer(icons))
    private val canvasBuilder = CanvasBuilder(renderer)
    private val canvas = Canvas(DefaultCanvasConstants().copy(baseCursorSize = 3.0f)) // Using small cursor size for clarity
    private var renderResult: Map<DiagramElementId, AreaWithZindex>? = null

    private val basicProcess = BpmnProcessObject(
            BpmnProcess(
                    BpmnElementId("processElem"),
                    "mainProcess",
                    null,
                    null,
                    null,
                    null
            ),
            mutableListOf()
    )

    private val sequenceIcon = mock<Icon>()
    private val graphics = mock<Graphics2D>()
    private val fontMetrics = mock<FontMetrics>()
    private val messageBus = mock<MessageBus>()
    private val messageBusConnection = mock<MessageBusConnection>()
    private val parser = mock<BpmnParser>()
    private val fileCommitter = mock<FileCommitter>()
    private val project = mock<Project>()
    private val virtualFile = mock<VirtualFile>()
    private val columnModel = mock<TableColumnModel>()
    private val tableColumn = mock<TableColumn>()
    private val propertiesTable = mock<JTable>()

    @BeforeEach
    fun setupMocks() {
        textFieldsConstructed.clear()
        boolFieldsConstructed.clear()

        whenever(propertiesTable.columnModel).thenReturn(columnModel)
        whenever(columnModel.getColumn(anyInt())).thenReturn(tableColumn)
        prepareGraphics(graphics)
        whenever(virtualFile.contentsToByteArray()).thenReturn(ByteArray(0))
        whenever(project.messageBus).thenReturn(messageBus)
        whenever(messageBus.connect()).thenReturn(messageBusConnection)

        whenever(sequenceIcon.iconWidth).thenReturn(15)
        whenever(sequenceIcon.iconHeight).thenReturn(15)
        whenever(icons.sequence).thenReturn(sequenceIcon)
        whenever(icons.recycleBin).thenReturn(icon)
        whenever(icons.exclusiveGateway).thenReturn(icon)
        whenever(icons.boundaryErrorEvent).thenReturn(icon)
        whenever(icons.gear).thenReturn(mock())
        whenever(icons.redo).thenReturn(mock())
        whenever(icons.undo).thenReturn(mock())
        whenever(icons.dragToResizeBottom).thenReturn(mock())
        whenever(icons.dragToResizeTop).thenReturn(mock())

        doAnswer {
            val result = it.callRealMethod()!! as Map<DiagramElementId, AreaWithZindex>
            renderResult = result
            return@doAnswer result
        }.whenever(renderer).render(any())
    }

    @Test
    fun `Ui renders service tasks properly`() {
        prepareTwoServiceTaskView()

        verifyServiceTasksAreDrawn()
    }

    @Test
    fun `Action elements are shown when service task is selected`() {
        prepareTwoServiceTaskView()

        clickOnId(serviceTaskStartDiagramId)

        verifyServiceTasksAreDrawn()
        findExactlyOneNewLinkElem().shouldNotBeNull()
        findExactlyOneDeleteElem().shouldNotBeNull()
    }

    @Test
    fun `Service task can be removed`() {
        prepareTwoServiceTaskView()

        clickOnId(serviceTaskStartDiagramId)

        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        renderResult.shouldNotBeNull().shouldNotHaveKey(serviceTaskStartDiagramId)
        findFirstNewLinkElem().shouldBeNull()
        findFirstDeleteElem().shouldBeNull()
        renderResult.shouldNotBeNull().shouldHaveKey(serviceTaskEndDiagramId)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter).executeCommitAndGetHash(any(), capture(), any(), any())
            firstValue.shouldContainSame(listOf(
                    DiagramElementRemovedEvent(serviceTaskStartDiagramId),
                    BpmnElementRemovedEvent(serviceTaskStartBpmnId))
            )
        }
    }

    @Test
    fun `New edge element should be addable`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()

        val intermediateX = 100.0f
        val intermediateY = 100.0f
        val newTaskId = newServiceTask(intermediateX, intermediateY)

        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)
        dragToAndVerifyButDontStop(point, Point2D.Float(intermediateX, intermediateY + serviceTaskSize / 2.0f), lastEndpointId)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(4)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val shapeBpmn = lastValue.filterIsInstance<BpmnShapeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedTo = lastValue.filterIsInstance<DraggedToEvent>().shouldHaveSingleItem()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().shouldHaveSingleItem()
            lastValue.shouldContainSame(listOf(edgeBpmn, shapeBpmn, draggedTo, propUpdated))

            shapeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnServiceTask>()
            shapeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)

            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            draggedTo.diagramElementId.shouldBe(lastEndpointId)
            draggedTo.dx.shouldBeNear(intermediateX - point.x, 0.1f)
            draggedTo.dy.shouldBeNear(intermediateY + serviceTaskSize / 2.0f - point.y, 0.1f)

            propUpdated.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBeEqualTo(newTaskId.id)
        }
    }

    @Test
    fun `New service task can be added and sequence flow can be dropped directly on it`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)

        // Move to the end of service task
        dragToAndVerifyButDontStop(point, Point2D.Float(endElemX + serviceTaskSize, endElemMidY), lastEndpointId)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedTo = lastValue.filterIsInstance<DraggedToEvent>().shouldHaveSingleItem()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().shouldHaveSingleItem()
            lastValue.shouldContainSame(listOf(edgeBpmn, draggedTo, propUpdated))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            draggedTo.diagramElementId.shouldBe(lastEndpointId)
            draggedTo.dx.shouldBeNear(endElemX + serviceTaskSize - point.x, 0.1f)
            draggedTo.dy.shouldBeNear(0.0f, 0.1f)

            propUpdated.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBe(serviceTaskEndBpmnId.id)
        }
    }

    @Test
    fun `For new edge ending waypoint element can be directly dragged to target`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)

        dragToAndVerifyButDontStop(point, Point2D.Float(endElemX + serviceTaskSize, endElemMidY), lastEndpointId)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedTo = lastValue.filterIsInstance<DraggedToEvent>().shouldHaveSingleItem()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().shouldHaveSingleItem()
            lastValue.shouldContainSame(listOf(edgeBpmn, draggedTo, propUpdated))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            draggedTo.diagramElementId.shouldBe(lastEndpointId)
            draggedTo.dx.shouldBeNear(endElemX + serviceTaskSize - point.x, 0.1f)
            draggedTo.dy.shouldBeNear(0.0f, 0.1f)

            propUpdated.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBe(serviceTaskEndBpmnId.id)
        }
    }

    @Test
    fun `For new edge ending waypoint element can be dragged with intermediate stop to target`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)

        val midPoint = Point2D.Float(endElemX / 2.0f, 100.0f)
        dragToAndVerifyButDontStop(point, midPoint, lastEndpointId)
        canvas.stopDragOrSelect()
        dragToAndVerifyButDontStop(midPoint, Point2D.Float(endElemX, endElemMidY), lastEndpointId)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(5)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedToMid = lastValue.filterIsInstance<DraggedToEvent>().first().shouldNotBeNull()
            val intermediateTargetChangeToParentPropUpd = lastValue.filterIsInstance<StringValueUpdatedEvent>().first().shouldNotBeNull()
            val draggedToTarget = lastValue.filterIsInstance<DraggedToEvent>().last().shouldNotBeNull()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().last().shouldNotBeNull()
            lastValue.shouldContainSame(listOf(edgeBpmn, draggedToMid, intermediateTargetChangeToParentPropUpd, draggedToTarget, propUpdated))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            draggedToMid.diagramElementId.shouldBeEqualTo(lastEndpointId)
            draggedToMid.dx.shouldBeNear(midPoint.x - point.x, 0.1f)
            draggedToMid.dy.shouldBeNear(midPoint.y - point.y, 0.1f)

            intermediateTargetChangeToParentPropUpd.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            intermediateTargetChangeToParentPropUpd.property.shouldBe(PropertyType.TARGET_REF)
            intermediateTargetChangeToParentPropUpd.newValue.shouldBe(basicProcess.process.id.id)

            draggedToTarget.diagramElementId.shouldBeEqualTo(lastEndpointId)
            draggedToTarget.dx.shouldBeNear(endElemX - point.x - draggedToMid.dx, 0.1f)
            draggedToTarget.dy.shouldBeNear(-draggedToMid.dy, 0.1f)

            propUpdated.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBe(serviceTaskEndBpmnId.id)
        }
    }

    @Test
    fun `For new edge intermediate waypoint can be added`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val newWaypointAnchor = addedEdge.edge.waypoint.first { !it.physical }.id
        val point = clickOnId(newWaypointAnchor)
        dragToAndVerifyButDontStop(point, Point2D.Float(100.0f, 100.0f), newWaypointAnchor)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(2)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val newWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().shouldHaveSingleItem()
            lastValue.shouldContainSame(listOf(edgeBpmn, newWaypoint))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            newWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)
            newWaypoint.waypoints.shouldHaveSize(3)
            newWaypoint.waypoints.filter { it.physical }.shouldHaveSize(3)
            newWaypoint.waypoints.map { it.x }.shouldContainSame(listOf(60.0f, 100.0f, 600.0f))
            newWaypoint.waypoints.map { it.y }.shouldContainSame(listOf(30.0f, 100.0f, 30.0f))
        }
    }

    @Test
    fun `For new edge two new intermediate waypoints can be added using coordinates clicks`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val edgeStart = addedEdge.edge.waypoint.first()
        val edgeEnd = addedEdge.edge.waypoint.last()
        val dragDelta = Point2D.Float(0.0f, 20.0f)
        val startAtMidpoint = Point2D.Float(edgeStart.x + (edgeEnd.x - edgeStart.x) / 2.0f, edgeStart.y + (edgeEnd.y - edgeStart.y) / 2.0f)
        val afterDragQuarter = Point2D.Float(edgeStart.x + (startAtMidpoint.x - edgeStart.x - dragDelta.x) / 2.0f, edgeStart.y + (startAtMidpoint.y - edgeStart.y - dragDelta.y) / 2.0f)
        // select edge
        canvas.click(startAtMidpoint)
        canvas.paintComponent(graphics)
        // select waypoint
        canvas.click(startAtMidpoint)
        canvas.paintComponent(graphics)
        // drag midpoint
        dragToButDontStop(startAtMidpoint, Point2D.Float(startAtMidpoint.x - dragDelta.x, startAtMidpoint.y - dragDelta.y))
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
        // select edge again
        canvas.click(afterDragQuarter)
        canvas.paintComponent(graphics)
        // select quarter waypoint
        canvas.click(afterDragQuarter)
        canvas.paintComponent(graphics)
        // drag quarter
        dragToButDontStop(afterDragQuarter, Point2D.Float(afterDragQuarter.x - dragDelta.x, afterDragQuarter.y - dragDelta.y))
        canvas.stopDragOrSelect()
        // as a result 2 new waypoints should exist

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val newMidWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().first()
            val newQuarterWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, newMidWaypoint, newQuarterWaypoint))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            newMidWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)
            newMidWaypoint.waypoints.shouldHaveSize(3)
            newMidWaypoint.waypoints.filter { it.physical }.shouldHaveSize(3)
            newMidWaypoint.waypoints.map { it.x }.shouldContainSame(listOf(60.0f, 330.0f, 600.0f))
            newMidWaypoint.waypoints.map { it.y }.shouldContainSame(listOf(30.0f, 10.0f, 30.0f))

            newQuarterWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)
            newQuarterWaypoint.waypoints.shouldHaveSize(4)
            newQuarterWaypoint.waypoints.filter { it.physical }.shouldHaveSize(4)
            newQuarterWaypoint.waypoints.map { it.x }.shouldContainSame(listOf(60.0f, 195.0f, 330.0f, 600.0f))
            newQuarterWaypoint.waypoints.map { it.y }.shouldContainSame(listOf(30.0f, 0.0f, 10.0f, 30.0f))
        }
    }

    @Test
    fun `Dragging service task with sequences attached cascade updates location`() {
        prepareTwoServiceTaskView()

        val dragDelta = Point2D.Float(100.0f, 100.0f)
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne()
        val point = clickOnId(serviceTaskStartDiagramId)
        dragToButDontStop(point, Point2D.Float(point.x + dragDelta.x, point.y + dragDelta.y))
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val dragTask = lastValue.filterIsInstance<DraggedToEvent>().first()
            val dragEdge = lastValue.filterIsInstance<DraggedToEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, dragTask, dragEdge))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            dragTask.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            dragTask.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragTask.dy.shouldBeNear(dragDelta.y, 0.1f)

            dragEdge.diagramElementId.shouldBeEqualTo(addedEdge.edge.waypoint.first().id)
            dragEdge.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragEdge.dy.shouldBeNear(dragDelta.y, 0.1f)
        }
    }

    @Test
    fun `Renaming element ID cascades to sourceRef and targetRef when changed via PropertiesVisualizer`() {
        prepareTwoServiceTaskView()

        val newId = UUID.randomUUID().toString()
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne()
        changeIdViaPropertiesVisualizer(serviceTaskStartDiagramId, serviceTaskStartBpmnId, newId)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val origIdUpdate = lastValue.filterIsInstance<StringValueUpdatedEvent>().first()
            val cascadeIdUpdate = lastValue.filterIsInstance<StringValueUpdatedEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, origIdUpdate, cascadeIdUpdate))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            origIdUpdate.bpmnElementId.shouldBeEqualTo(serviceTaskStartBpmnId)
            origIdUpdate.property.shouldBeEqualTo(PropertyType.ID)
            origIdUpdate.newValue.shouldBeEqualTo(newId)
            origIdUpdate.newIdValue?.id.shouldBeEqualTo(newId)

            cascadeIdUpdate.bpmnElementId.shouldBeEqualTo(addedEdge.edge.bpmnElement)
            cascadeIdUpdate.property.shouldBeEqualTo(PropertyType.SOURCE_REF)
            cascadeIdUpdate.newValue.shouldBeEqualTo(newId)
        }
    }

    @Test
    fun `Cascading position after renaming element ID updates works too`() {
        prepareTwoServiceTaskView()

        val newId = UUID.randomUUID().toString()
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne()
        changeIdViaPropertiesVisualizer(serviceTaskStartDiagramId, serviceTaskStartBpmnId, newId)
        val dragDelta = Point2D.Float(100.0f, 100.0f)
        val point = clickOnId(serviceTaskStartDiagramId)
        dragToButDontStop(point, Point2D.Float(point.x + dragDelta.x, point.y + dragDelta.y))
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(5)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val origIdUpdate = lastValue.filterIsInstance<StringValueUpdatedEvent>().first()
            val cascadeIdUpdate = lastValue.filterIsInstance<StringValueUpdatedEvent>().last()
            val dragTask = lastValue.filterIsInstance<DraggedToEvent>().first()
            val dragEdge = lastValue.filterIsInstance<DraggedToEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, origIdUpdate, cascadeIdUpdate, dragTask, dragEdge))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            origIdUpdate.bpmnElementId.shouldBeEqualTo(serviceTaskStartBpmnId)
            origIdUpdate.property.shouldBeEqualTo(PropertyType.ID)
            origIdUpdate.newValue.shouldBeEqualTo(newId)
            origIdUpdate.newIdValue?.id.shouldBeEqualTo(newId)

            cascadeIdUpdate.bpmnElementId.shouldBeEqualTo(addedEdge.edge.bpmnElement)
            cascadeIdUpdate.property.shouldBeEqualTo(PropertyType.SOURCE_REF)
            cascadeIdUpdate.newValue.shouldBeEqualTo(newId)

            dragTask.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            dragTask.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragTask.dy.shouldBeNear(dragDelta.y, 0.1f)

            dragEdge.diagramElementId.shouldBeEqualTo(addedEdge.edge.waypoint.first().id)
            dragEdge.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragEdge.dy.shouldBeNear(dragDelta.y, 0.1f)
        }
    }

    @Test
    fun `Selecting with rectangle of all elements and dragging them works`() {
        prepareTwoServiceTaskView()

        val begin = Point2D.Float(startElemX - 10.0f, startElemY - 10.0f)
        canvas.paintComponent(graphics)
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(endElemX + serviceTaskSize, endElemY  + serviceTaskSize))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        val startCenterX = startElemX + serviceTaskSize / 2.0f
        val startCenterY = startElemY
        val dragDelta = Point2D.Float(100.0f, 100.0f)
        canvas.startSelectionOrDrag(Point2D.Float(startCenterX, startCenterY))
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(startCenterX + dragDelta.x, startCenterY + dragDelta.y))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(1)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(2)
            val dragStart = lastValue.filterIsInstance<DraggedToEvent>().first()
            val dragEnd = lastValue.filterIsInstance<DraggedToEvent>().last()
            lastValue.shouldContainSame(listOf(dragStart, dragEnd))

            dragStart.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            dragStart.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragStart.dy.shouldBeNear(dragDelta.y, 0.1f)

            dragEnd.diagramElementId.shouldBeEqualTo(serviceTaskEndDiagramId)
            dragEnd.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragEnd.dy.shouldBeNear(dragDelta.y, 0.1f)
        }
    }

    @Test
    fun `Selecting with rectangle of single and ref on it and dragging them works`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()

        val begin = Point2D.Float(startElemX - 10.0f, startElemY - 10.0f)
        canvas.click(begin) // unselect service task
        canvas.paintComponent(graphics)
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(startElemX + serviceTaskSize + 10.0f, startElemY + serviceTaskSize + 10.0f))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        val startCenterX = startElemX + serviceTaskSize / 2.0f
        val startCenterY = startElemY
        val dragDelta = Point2D.Float(100.0f, 100.0f)
        canvas.startSelectionOrDrag(Point2D.Float(startCenterX, startCenterY))
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(startCenterX + dragDelta.x, startCenterY + dragDelta.y))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val dragStart = lastValue.filterIsInstance<DraggedToEvent>().first()
            val dragEdge = lastValue.filterIsInstance<DraggedToEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, dragStart, dragEdge))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            dragStart.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            dragStart.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragStart.dy.shouldBeNear(dragDelta.y, 0.1f)

            dragEdge.diagramElementId.shouldBeEqualTo(addedEdge.edge.waypoint.first().id)
            dragEdge.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragEdge.dy.shouldBeNear(dragDelta.y, 0.1f)
        }
    }

    @Test
    fun `Selecting element with boundary event using rectangle and dragging them works`() {
        prepareServiceTaskWithAttachedBoundaryEventView()

        selectRectDragAndVerify()
    }

    @Test
    fun `Dragging service task with attached boundary event using rectangle inside subprocess should maintain correct parents`() {
        prepareOneSubProcessServiceTaskWithAttachedBoundaryEventView()

        selectRectDragAndVerify()
    }

    private fun selectRectDragAndVerify() {
        val begin = Point2D.Float(startElemX - 10.0f, startElemY - 10.0f)
        canvas.paintComponent(graphics)
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(endElemX + serviceTaskSize, endElemY + serviceTaskSize))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        val startCenterX = startElemX + serviceTaskSize / 2.0f
        val startCenterY = startElemY
        val dragDelta = Point2D.Float(100.0f, 100.0f)
        canvas.startSelectionOrDrag(Point2D.Float(startCenterX, startCenterY))
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(startCenterX + dragDelta.x, startCenterY + dragDelta.y))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(1)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(2)
            val dragStart = lastValue.filterIsInstance<DraggedToEvent>().first()
            val dragEnd = lastValue.filterIsInstance<DraggedToEvent>().last()
            lastValue.shouldContainSame(listOf(dragStart, dragEnd))

            dragStart.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            dragStart.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragStart.dy.shouldBeNear(dragDelta.y, 0.1f)

            dragEnd.diagramElementId.shouldBeEqualTo(optionalBoundaryErrorEventDiagramId)
            dragEnd.dx.shouldBeNear(dragDelta.x, 0.1f)
            dragEnd.dy.shouldBeNear(dragDelta.y, 0.1f)
        }
    }

    @Test
    fun `Removing edge element works`() {
        prepareTwoServiceTaskView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)

        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val removeEdgeBpmn = lastValue.filterIsInstance<BpmnElementRemovedEvent>().first()
            val removeEdgeDiagram = lastValue.filterIsInstance<DiagramElementRemovedEvent>().first()
            lastValue.shouldContainSame(listOf(edgeBpmn, removeEdgeDiagram, removeEdgeBpmn))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            removeEdgeBpmn.elementId.shouldBeEqualTo(addedEdge.edge.bpmnElement)
            removeEdgeDiagram.elementId.shouldBeEqualTo(addedEdge.edge.id)
        }
    }

    @Test
    fun `Removing waypoint works`() {
        prepareTwoServiceTaskView()

        val newLocation = Point2D.Float(100.0f, 100.0f)
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val newWaypointAnchor = addedEdge.edge.waypoint.first { !it.physical }.id
        val point = clickOnId(newWaypointAnchor)
        dragToAndVerifyButDontStop(point, newLocation, newWaypointAnchor)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        // New renderer does not keep edge selected
        // Select edge:
        canvas.click(newLocation)
        canvas.paintComponent(graphics)
        // Select waypoint:
        canvas.click(newLocation)
        canvas.paintComponent(graphics)
        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val newWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().first()
            val removeWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().last()
            lastValue.shouldContainSame(listOf(edgeBpmn, newWaypoint, removeWaypoint))

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            newWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)
            newWaypoint.waypoints.shouldHaveSize(3)
            newWaypoint.waypoints.filter { it.physical }.shouldHaveSize(3)
            newWaypoint.waypoints.map { it.x }.shouldContainSame(listOf(60.0f, 100.0f, 600.0f))
            newWaypoint.waypoints.map { it.y }.shouldContainSame(listOf(30.0f, 100.0f, 30.0f))

            removeWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)
            removeWaypoint.waypoints.shouldHaveSize(2)
            removeWaypoint.waypoints.filter { it.physical }.shouldHaveSize(2)
            removeWaypoint.waypoints.map { it.x }.shouldContainSame(listOf(60.0f, 600.0f))
            removeWaypoint.waypoints.map { it.y }.shouldContainSame(listOf(30.0f, 30.0f))
        }
    }

    @Test
    fun `Removing with rectangle works`() {
        prepareTwoServiceTaskView()

        val begin = Point2D.Float(-10.0f, -10.0f)
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        canvas.click(begin) // de-select rectangle
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)

        canvas.startSelectionOrDrag(begin)
        // Stepped selection to allow waypoints to reveal
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(startElemX + serviceTaskSize + 10.0f, startElemX + serviceTaskSize + 10.0f))
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(endElemX + serviceTaskSize + 10.0f, endElemY + serviceTaskSize + 10.0f))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(7)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val diagramRemoved = lastValue.filterIsInstance<DiagramElementRemovedEvent>()
            val bpmnRemoved = lastValue.filterIsInstance<BpmnElementRemovedEvent>()
            lastValue.shouldContainSame(listOf(edgeBpmn) + diagramRemoved + bpmnRemoved)

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)
            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            diagramRemoved.map { it.elementId.id }.shouldContainSame(
                    listOf("DIAGRAM-startServiceTask", addedEdge.edge.id.id, "DIAGRAM-endServiceTask")
            )
            bpmnRemoved.map { it.elementId.id }.shouldContainSame(
                    listOf("startServiceTask", addedEdge.bpmnObject.id.id, "endServiceTask")
            )
        }
    }

    @Test
    fun `Subprocess is selected as parent`() {
        prepareOneSubProcessView()

        canvas.paintComponent(graphics)

        canvas
                .parentableElementAt(Point2D.Float(subProcessElemX + subProcessSize / 2.0f, subProcessElemY + subProcessSize / 2.0f))
                .shouldBe(subprocessBpmnId)
    }

    @Test
    fun `Removing element from subprocess works`() {
        prepareOneSubProcessWithTwoServiceTasksView()

        clickOnId(serviceTaskStartDiagramId)
        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        renderResult.shouldNotBeNull().shouldNotHaveKey(serviceTaskStartDiagramId)
        findFirstNewLinkElem().shouldBeNull()
        findFirstDeleteElem().shouldBeNull()
        renderResult.shouldNotBeNull().shouldHaveKey(serviceTaskEndDiagramId)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter).executeCommitAndGetHash(any(), capture(), any(), any())
            firstValue.shouldContainSame(listOf(
                    DiagramElementRemovedEvent(serviceTaskStartDiagramId),
                    BpmnElementRemovedEvent(serviceTaskStartBpmnId))
            )
        }
    }

    @Test
    fun `Adding link to element in subprocess works`() {
        prepareOneSubProcessWithTwoServiceTasksView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()

        val intermediateX = 100.0f
        val intermediateY = 100.0f
        val newTaskId = newServiceTask(intermediateX, intermediateY)

        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)
        dragToAndVerifyButDontStop(point, Point2D.Float(intermediateX, intermediateY + serviceTaskSize / 2.0f), lastEndpointId)
        canvas.stopDragOrSelect()

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(4)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val shapeBpmn = lastValue.filterIsInstance<BpmnShapeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedTo = lastValue.filterIsInstance<DraggedToEvent>().shouldHaveSingleItem()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().shouldHaveSingleItem()
            lastValue.shouldContainSame(listOf(edgeBpmn, shapeBpmn, draggedTo, propUpdated))

            shapeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnServiceTask>()
            shapeBpmn.bpmnObject.parent.shouldBe(basicProcess.process.id)

            val sequence = edgeBpmn.bpmnObject.element.shouldBeInstanceOf<BpmnSequenceFlow>()
            edgeBpmn.bpmnObject.parent.shouldBe(subprocessBpmnId)

            sequence.sourceRef.shouldBe(serviceTaskStartBpmnId.id)
            sequence.targetRef.shouldBe("")

            draggedTo.diagramElementId.shouldBe(lastEndpointId)
            draggedTo.dx.shouldBeNear(intermediateX - point.x, 0.1f)
            draggedTo.dy.shouldBeNear(intermediateY + serviceTaskSize / 2.0f - point.y, 0.1f)

            propUpdated.bpmnElementId.shouldBe(edgeBpmn.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBeEqualTo(newTaskId.id)
        }
    }

    @Test
    fun `Subprocess dragging cascades to service tasks and edges`() {
        val dx = 30.0f
        val dy = 30.0f
        val midDx = 20.0f
        val midDy = 20.0f

        prepareOneSubProcessWithTwoServiceTasksView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        // Add midpoint
        clickOnId(addedEdge.edge.id)
        val newWaypointAnchor = addedEdge.edge.waypoint.first { !it.physical }.id
        val midPoint = clickOnId(newWaypointAnchor)
        dragToAndVerifyButDontStop(midPoint, Point2D.Float(midPoint.x + midDx, midPoint.y + midDy), newWaypointAnchor)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
        // Click on subprocess
        val subProcessPoint = clickOnId(subprocessDiagramId)
        dragToAndVerifyButDontStop(subProcessPoint, Point2D.Float(subProcessPoint.x + dx, subProcessPoint.y + dy), subprocessDiagramId)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(3)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(8)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val newWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().first()
            val allDraggeds = lastValue.filterIsInstance<DraggedToEvent>()
            val cascadedDrags = allDraggeds.subList(0, allDraggeds.size).shouldHaveSize(6)
            val subprocessDragSelf = cascadedDrags.filter { it.diagramElementId == subprocessDiagramId }.shouldHaveSingleItem()
            val subprocessDragStartServiceTask = cascadedDrags.filter { it.diagramElementId == serviceTaskStartDiagramId }.shouldHaveSingleItem()
            val subprocessDragEndServiceTask = cascadedDrags.filter { it.diagramElementId == serviceTaskEndDiagramId }.shouldHaveSingleItem()
            val subprocessDragEdgeStart = cascadedDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 0 }.shouldHaveSingleItem()
            val subprocessDragEdgeMid = cascadedDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 1 }.shouldHaveSingleItem()
            val subprocessDragEdgeEnd = cascadedDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 2 }.shouldHaveSingleItem()

            edgeBpmn.bpmnObject.parent.shouldBe(subprocessBpmnId)
            edgeBpmn.bpmnObject.id.shouldBe(addedEdge.bpmnObject.id)
            edgeBpmn.props[PropertyType.SOURCE_REF].shouldNotBeNull().value.shouldBe(serviceTaskStartBpmnId.id)

            newWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)

            setOf(subprocessDragSelf, subprocessDragStartServiceTask, subprocessDragEndServiceTask, subprocessDragEdgeStart, subprocessDragEdgeMid, subprocessDragEdgeEnd)
                    .forEach {
                        it.dx.shouldBeEqualTo(dx)
                        it.dy.shouldBeEqualTo(dy)
                    }
        }
    }

    @Test
    fun `Single element can be draggable in subprocess`() {
        val dx = 30.0f
        val dy = 30.0f
        prepareOneSubProcessWithTwoServiceTasksView()

        val serviceTaskPoint = clickOnId(serviceTaskStartDiagramId)
        dragToAndVerifyButDontStop(serviceTaskPoint, Point2D.Float(serviceTaskPoint.x + dx, serviceTaskPoint.y + dy), serviceTaskStartDiagramId)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(1)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(1)
            val serviceTaskDragged = lastValue.filterIsInstance<DraggedToEvent>().filter { it.diagramElementId == serviceTaskStartDiagramId }.shouldHaveSingleItem()
            serviceTaskDragged.dx.shouldBeEqualTo(dx)
            serviceTaskDragged.dy.shouldBeEqualTo(dy)
        }
    }

    @Test
    fun `Rectangle selection dragging in subprocess`() {
        val dx = 30.0f
        val dy = 30.0f
        val midDx = 10.0f
        val midDy = 10.0f

        prepareOneSubProcessWithTwoServiceTasksView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        // Link to second sub-task
        clickOnId(addedEdge.edge.id)
        val lastEndpointId = addedEdge.edge.waypoint.last().id
        val point = clickOnId(lastEndpointId)
        dragToAndVerifyButDontStop(point, Point2D.Float(endElemX + serviceTaskSize, endElemMidY), lastEndpointId)
        canvas.stopDragOrSelect()
        // Add midpoint
        clickOnId(addedEdge.edge.id)
        val newWaypointAnchor = addedEdge.edge.waypoint.first { !it.physical }.id
        val midPoint = clickOnId(newWaypointAnchor)
        dragToAndVerifyButDontStop(midPoint, Point2D.Float(midPoint.x + midDx, midPoint.y + midDy), newWaypointAnchor)
        canvas.stopDragOrSelect()
        // Select rectangle
        val begin = Point2D.Float(-10.0f, -10.0f)
        canvas.click(begin)
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(endElemX + serviceTaskSize + 10.0f, endElemY + serviceTaskSize + 10.0f))
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
        // Drag rectangle
        val dragStart = elementCenter(serviceTaskStartDiagramId)
        canvas.startSelectionOrDrag(dragStart)
        canvas.dragOrSelectWithLeftButton(dragStart, Point2D.Float(dragStart.x + dx, dragStart.y + dy))
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(4)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(9)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val draggedToEdge = lastValue.filterIsInstance<DraggedToEvent>().first()
            val propUpdated = lastValue.filterIsInstance<StringValueUpdatedEvent>().shouldHaveSingleItem()
            val newWaypoint = lastValue.filterIsInstance<NewWaypointsEvent>().first()
            val allDraggeds = lastValue.filterIsInstance<DraggedToEvent>()
            val rectDrags = allDraggeds.subList(1, allDraggeds.size).shouldHaveSize(5)

            val rectangleDragStartServiceTask = rectDrags.filter { it.diagramElementId == serviceTaskStartDiagramId }.shouldHaveSingleItem()
            val rectangleDragEndServiceTask = rectDrags.filter { it.diagramElementId == serviceTaskEndDiagramId }.shouldHaveSingleItem()
            val rectangleDragEdgeStart = rectDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 0 }.shouldHaveSingleItem()
            val rectangleDragEdgeMid = rectDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 1 }.shouldHaveSingleItem()
            val rectangleDragEdgeEnd = rectDrags.filter { it.parentElementId == addedEdge.edge.id && it.internalPos == 2 }.shouldHaveSingleItem()

            edgeBpmn.bpmnObject.parent.shouldBe(subprocessBpmnId)
            edgeBpmn.bpmnObject.id.shouldBe(addedEdge.bpmnObject.id)
            edgeBpmn.props[PropertyType.SOURCE_REF].shouldNotBeNull().value.shouldBe(serviceTaskStartBpmnId.id)

            draggedToEdge.parentElementId.shouldBe(addedEdge.edge.id)

            propUpdated.bpmnElementId.shouldBe(addedEdge.bpmnObject.id)
            propUpdated.property.shouldBe(PropertyType.TARGET_REF)
            propUpdated.newValue.shouldBeEqualTo(serviceTaskEndBpmnId.id)

            newWaypoint.edgeElementId.shouldBeEqualTo(addedEdge.edge.id)

            setOf(rectangleDragStartServiceTask, rectangleDragEndServiceTask, rectangleDragEdgeStart, rectangleDragEdgeMid, rectangleDragEdgeEnd)
                    .forEach {
                        it.dx.shouldBeEqualTo(dx)
                        it.dy.shouldBeEqualTo(dy)
                    }
        }
    }

    @Test
    fun `Rectangle selection dragging in subprocess within subprocess`() {
        val dx = 30.0f
        val dy = 30.0f

        prepareOneSubProcessThenNestedSubProcessWithOneServiceTaskView()

        // Select rectangle
        val begin = Point2D.Float(-10.0f, -10.0f)
        canvas.click(begin)
        canvas.startSelectionOrDrag(begin)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(endElemX + serviceTaskSize + 10.0f, endElemY + serviceTaskSize + 10.0f))
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
        // Drag rectangle
        val dragStart = elementCenter(serviceTaskStartDiagramId)
        canvas.startSelectionOrDrag(dragStart)
        canvas.dragOrSelectWithLeftButton(dragStart, Point2D.Float(dragStart.x + dx, dragStart.y + dy))
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(1)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(1)
            val draggedToEdge = lastValue.filterIsInstance<DraggedToEvent>().first()

            draggedToEdge.diagramElementId.shouldBeEqualTo(serviceTaskStartDiagramId)
            draggedToEdge.dx.shouldBeEqualTo(dx)
            draggedToEdge.dy.shouldBeEqualTo(dy)
        }
    }

    @Test
    fun `Subprocess should ignore its child anchors`() {
        val dx = 30.0f
        val dy = 30.0f

        prepareOneSubProcessThenNestedSubProcessWithOneServiceTaskView()

        // Click on parent subprocess
        val begin = Point2D.Float(subProcessSize - 10.0f, subProcessSize - 10.0f)
        canvas.click(begin)
        canvas.paintComponent(graphics)
        // Drag rectangle
        canvas.startSelectionOrDrag(begin)
        canvas.dragOrSelectWithLeftButton(begin, Point2D.Float(begin.x + dx, begin.y + dy))
        canvas.paintComponent(graphics)

        argumentCaptor<RenderContext>().apply {
            verify(renderer, atLeastOnce()).render(this.capture())
            lastValue.interactionContext.anchorsHit!!.anchors.shouldBeEmpty()
        }
    }

    @Test
    fun `Removing link from element in subprocess works`() {
        prepareOneSubProcessWithTwoServiceTasksView()

        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        clickOnId(addedEdge.edge.id)
        val deleteElem = findExactlyOneDeleteElem().shouldNotBeNull()
        clickOnId(deleteElem)

        argumentCaptor<List<Event>>().apply {
            verify(fileCommitter, times(2)).executeCommitAndGetHash(any(), capture(), any(), any())
            lastValue.shouldHaveSize(3)
            val edgeBpmn = lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
            val removeShapeBpmn = lastValue.filterIsInstance<BpmnElementRemovedEvent>().shouldHaveSingleItem()
            val removeDiagramBpmn = lastValue.filterIsInstance<DiagramElementRemovedEvent>().shouldHaveSingleItem()

            edgeBpmn.bpmnObject.parent.shouldBe(subprocessBpmnId)
            removeShapeBpmn.elementId.shouldBe(addedEdge.edge.bpmnElement)
            removeDiagramBpmn.elementId.shouldBe(addedEdge.edge.id)
        }
    }

    @Test
    fun `Virtual (midpoint) edge anchors does not attract other elements`() {
        prepareOneSubProcessWithTwoServiceTasksView()

        val firstEdge = addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne()
        val secondEdge = addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne()
        // Click on sequence element midpoint
        val start = clickOnId(firstEdge.edge.waypoint[1].id)
        val virtualMidpointLocation = elementCenter(secondEdge.edge.waypoint[1].id)
        dragToButDontStop(start, elementCenter(secondEdge.edge.waypoint[1].id))
        canvas.paintComponent(graphics)

        argumentCaptor<RenderContext>().apply {
            verify(renderer, atLeastOnce()).render(capture())
            lastValue.interactionContext.anchorsHit!!.anchors[AnchorType.POINT].shouldBeNull()
            lastValue.interactionContext.anchorsHit!!.anchors[AnchorType.HORIZONTAL].shouldBeNull()
            lastValue.interactionContext.anchorsHit!!.anchors[AnchorType.VERTICAL]!!.distance(virtualMidpointLocation).shouldBeGreaterThan(10.0)
        }
    }

    @Test
    fun `Dragged subprocess element is always visible`() {
        prepareOneSubProcessThenNestedSubProcessWithReversedChildParentOrder()

        val start = clickOnId(diagramNestedSubProcess.id)
        dragToButDontStop(start, Point2D.Float(1000.0f, 1000.0f))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        val draggedStart = clickOnId(diagramNestedSubProcess.id)
        dragToButDontStop(draggedStart, elementCenter(subprocessDiagramId))
        val capturingGraphics = mock<Graphics2D>()
        prepareGraphics(capturingGraphics)
        canvas.paintComponent(capturingGraphics)

        // Child component is rendered after parent (they have different sizes)
        argumentCaptor<Shape>().apply {
            verify(capturingGraphics, atLeastOnce()).fill(capture())
            val parent = allValues
                    .mapIndexedNotNull {pos, it ->
                        if (it.bounds.width == diagramSubProcess.rectBounds().width.toInt()
                                && it.bounds.height == diagramSubProcess.rectBounds().height.toInt()) {
                            return@mapIndexedNotNull pos
                        }
                        return@mapIndexedNotNull null
                    }.shouldHaveSingleItem()

            val subProcess = allValues
                    .mapIndexedNotNull {pos, it ->
                        if (it.bounds.width == diagramNestedSubProcess.rectBounds().width.toInt()
                                && it.bounds.height == diagramNestedSubProcess.rectBounds().height.toInt()) {
                            return@mapIndexedNotNull pos
                        }
                        return@mapIndexedNotNull null
                    }.shouldHaveSingleItem()

            (parent < subProcess).shouldBeTrue()
        }
    }

    @Test
    fun `Name should be rendered on sequence element`() {
        prepareTwoServiceTaskView()
        val addedEdge = addSequenceElementOnFirstTaskAndValidateCommittedExactOnce()
        updateEventsRegistry().addPropertyUpdateEvent(StringValueUpdatedEvent(addedEdge.bpmnObject.id, PropertyType.NAME, "test"))

        val capturingGraphics = mock<Graphics2D>()
        prepareGraphics(capturingGraphics)
        canvas.paintComponent(capturingGraphics)

        argumentCaptor<GlyphVector>().apply {
            verify(capturingGraphics, atLeastOnce()).drawGlyphVector(capture(), any(), any())
            lastValue.numGlyphs.shouldBeEqualTo(4)
        }
    }

    private fun prepareGraphics(graphics2D: Graphics2D) {
        whenever(graphics2D.fontMetrics).thenReturn(fontMetrics)
        val frc = mock<FontRenderContext>()
        whenever(frc.transform).thenReturn(AffineTransform())
        whenever(graphics2D.fontRenderContext).thenReturn(frc)
        whenever(graphics2D.transform).thenReturn(AffineTransform())
        whenever(fontMetrics.getStringBounds(any(), eq(graphics2D))).thenReturn(Rectangle2D.Float())
        whenever(graphics2D.create()).thenReturn(graphics2D)
    }

    private fun changeIdViaPropertiesVisualizer(diagramElementId: DiagramElementId, elementId: BpmnElementId, newId: String) {
        val id = Pair(elementId, PropertyType.ID)
        clickOnId(diagramElementId)
        propertiesVisualizer().visualize(
                currentStateProvider().currentState().elemPropertiesByStaticElementId,
                elementId
        )
        whenever(textFieldsConstructed[id]!!.text).thenReturn(newId)
        propertiesVisualizer().clear()
    }

    private fun newServiceTask(intermediateX: Float, intermediateY: Float): BpmnElementId {
        val task = bpmnServiceTaskStart.copy(id = BpmnElementId("sid-" + UUID.randomUUID().toString()))
        val shape = diagramServiceTaskStart.copy(
                id = DiagramElementId("sid-" + UUID.randomUUID().toString()),
                bounds = BoundsElement(intermediateX, intermediateY, serviceTaskSize, serviceTaskSize)
        )
        updateEventsRegistry().addObjectEvent(
                BpmnShapeObjectAddedEvent(WithParentId(basicProcess.process.id, task), shape, mapOf(PropertyType.ID to Property(task.id)))
        )

        return task.id
    }

    private fun dragToAndVerifyButDontStop(point: Point2D.Float, target: Point2D.Float, lastEndpointId: DiagramElementId) {
        dragToButDontStop(point, target)
        val dragAboutToFinishArea = elemAreaById(lastEndpointId)
        dragAboutToFinishArea.area.bounds2D.centerX.shouldBeNear(target.x.toDouble(), 0.1)
        dragAboutToFinishArea.area.bounds2D.centerY.shouldBeNear(target.y.toDouble(), 0.1)
    }

    private fun dragToButDontStop(point: Point2D.Float, target: Point2D.Float) {
        canvas.startSelectionOrDrag(point)
        canvas.paintComponent(graphics)
        canvas.dragOrSelectWithLeftButton(point, target)
        canvas.paintComponent(graphics)
    }

    private fun addSequenceElementOnFirstTaskAndValidateCommittedExactOnce(): BpmnEdgeObjectAddedEvent {
        addSequenceElementOnFirstTask()

        argumentCaptor<List<Event>>().let {
            verify(fileCommitter).executeCommitAndGetHash(any(), it.capture(), any(), any())
            it.firstValue.shouldHaveSize(1)
            return it.firstValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().shouldHaveSingleItem()
        }
    }

    private fun addSequenceElementOnFirstTaskAndValidateCommittedAtLeastOnceAndSelectOne(): BpmnEdgeObjectAddedEvent {
        clickOnId(serviceTaskStartDiagramId)
        verifyServiceTasksAreDrawn()
        val newLink = findExactlyOneNewLinkElem().shouldNotBeNull()
        val newLinkLocation = clickOnId(newLink)
        dragToButDontStop(newLinkLocation, elementCenter(serviceTaskEndDiagramId))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)

        argumentCaptor<List<Event>>().let {
            verify(fileCommitter, atLeastOnce()).executeCommitAndGetHash(any(), it.capture(), any(), any())
            return it.lastValue.filterIsInstance<BpmnEdgeObjectAddedEvent>().last().shouldNotBeNull()
        }
    }

    private fun addSequenceElementOnFirstTask() {
        clickOnId(serviceTaskStartDiagramId)
        verifyServiceTasksAreDrawn()
        val newLink = findExactlyOneNewLinkElem().shouldNotBeNull()
        val newLinkLocation = clickOnId(newLink)
        dragToButDontStop(newLinkLocation, elementCenter(serviceTaskEndDiagramId))
        canvas.paintComponent(graphics)
        canvas.stopDragOrSelect()
        canvas.paintComponent(graphics)
    }

    private fun clickOnId(elemId: DiagramElementId): Point2D.Float {
        val point = elementCenter(elemId)
        canvas.click(point)
        canvas.paintComponent(graphics)
        return point
    }

    private fun elementCenter(elemId: DiagramElementId): Point2D.Float {
        val area = renderResult?.get(elemId).shouldNotBeNull()
        val bounds = area.area.bounds2D.shouldNotBeNull()
        return Point2D.Float(bounds.centerX.toFloat(), bounds.centerY.toFloat())
    }

    private fun initializeCanvas() {
        canvasBuilder.build({ fileCommitter }, parser, propertiesTable, comboboxFactory, editorFactory, editorFactory, editorFactory, checkboxFieldFactory, canvas, project, virtualFile)
        canvas.paintComponent(graphics)
    }

    private fun verifyServiceTasksAreDrawn() {
        renderResult.shouldNotBeNull().shouldHaveKey(serviceTaskStartDiagramId)
        renderResult.shouldNotBeNull().shouldHaveKey(serviceTaskEndDiagramId)
        renderResult?.get(serviceTaskStartDiagramId)!!.area.bounds2D.shouldBeEqualTo(Rectangle2D.Float(startElemX, startElemY, serviceTaskSize, serviceTaskSize))
        renderResult?.get(serviceTaskEndDiagramId)!!.area.bounds2D.shouldBeEqualTo(Rectangle2D.Float(endElemX, endElemY, serviceTaskSize, serviceTaskSize))
    }

    private fun prepareOneSubProcessView() {
        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setSubProcess(listOf(bpmnSubProcess))
                                .create()
                ),
                listOf(DiagramElement(
                        diagramMainElementId,
                        PlaneElement(diagramMainPlaneElementId, basicProcess.process.id, listOf(diagramSubProcess), listOf()))
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun prepareOneSubProcessWithTwoServiceTasksView() {
        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setServiceTask(listOf(bpmnServiceTaskStart, bpmnServiceTaskEnd))
                                .setSubProcess(listOf(bpmnSubProcess))
                                .create(),
                        children = mapOf(
                                subprocessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .setServiceTask(listOf(bpmnServiceTaskStart, bpmnServiceTaskEnd))
                                        .create()
                        )
                ),
                listOf(
                        DiagramElement(
                                diagramMainElementId,
                                PlaneElement(
                                        diagramMainPlaneElementId,
                                        basicProcess.process.id,
                                        listOf(diagramSubProcess, diagramServiceTaskStart, diagramServiceTaskEnd),
                                        listOf()
                                )
                        )
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun prepareOneSubProcessThenNestedSubProcessWithOneServiceTaskView() {
        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setServiceTask(listOf(bpmnServiceTaskStart, bpmnServiceTaskEnd))
                                .setSubProcess(listOf(bpmnSubProcess))
                                .create(),
                        children = mapOf(
                                subprocessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .setSubProcess(listOf(bpmnNestedSubProcess))
                                        .create(),
                                subprocessInSubProcessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .setServiceTask(listOf(bpmnServiceTaskStart))
                                        .create()
                        )
                ),
                listOf(
                        DiagramElement(
                                diagramMainElementId,
                                PlaneElement(
                                        diagramMainPlaneElementId,
                                        basicProcess.process.id,
                                        listOf(diagramSubProcess, diagramNestedSubProcess, diagramServiceTaskStart),
                                        listOf()
                                )
                        )
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun prepareOneSubProcessThenNestedSubProcessWithReversedChildParentOrder() {
        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setSubProcess(listOf(bpmnSubProcess))
                                .create(),
                        children = mapOf(
                                subprocessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .setSubProcess(listOf(bpmnNestedSubProcess))
                                        .create(),
                                subprocessInSubProcessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .create()
                        )
                ),
                listOf(
                        DiagramElement(
                                diagramMainElementId,
                                PlaneElement(
                                        diagramMainPlaneElementId,
                                        basicProcess.process.id,
                                        listOf(diagramNestedSubProcess, diagramSubProcess),
                                        listOf()
                                )
                        )
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }


    private fun prepareTwoServiceTaskView() {
        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setServiceTask(listOf(bpmnServiceTaskStart, bpmnServiceTaskEnd))
                                .create()
                ),
                listOf(DiagramElement(
                        diagramMainElementId,
                        PlaneElement(diagramMainPlaneElementId, basicProcess.process.id, listOf(diagramServiceTaskStart, diagramServiceTaskEnd), listOf()))
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun prepareServiceTaskWithAttachedBoundaryEventView() {
        val boundaryEventOnServiceTask = BpmnBoundaryErrorEvent(optionalBoundaryErrorEventBpmnId, null, serviceTaskStartBpmnId, null)
        val boundaryEventOnServiceTaskShape = ShapeElement(
                optionalBoundaryErrorEventDiagramId,
                optionalBoundaryErrorEventBpmnId,
                BoundsElement(startElemX + serviceTaskSize / 2.0f, startElemX + serviceTaskSize / 2.0f, boundaryEventSize, boundaryEventSize)
        )

        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setServiceTask(listOf(bpmnServiceTaskStart))
                                .setBoundaryErrorEvent(listOf(boundaryEventOnServiceTask))
                                .create()
                ),
                listOf(DiagramElement(
                        diagramMainElementId,
                        PlaneElement(diagramMainPlaneElementId, basicProcess.process.id, listOf(diagramServiceTaskStart, boundaryEventOnServiceTaskShape), listOf()))
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun prepareOneSubProcessServiceTaskWithAttachedBoundaryEventView() {
        val boundaryEventOnServiceTask = BpmnBoundaryErrorEvent(optionalBoundaryErrorEventBpmnId, null, serviceTaskStartBpmnId, null)
        val boundaryEventOnServiceTaskShape = ShapeElement(
                optionalBoundaryErrorEventDiagramId,
                optionalBoundaryErrorEventBpmnId,
                BoundsElement(startElemX + serviceTaskSize / 2.0f, startElemX + serviceTaskSize / 2.0f, boundaryEventSize, boundaryEventSize)
        )

        val process = basicProcess.copy(
                basicProcess.process.copy(
                        body = BpmnProcessBodyBuilder.builder()
                                .setSubProcess(listOf(bpmnSubProcess))
                                .create(),
                        children = mapOf(
                                subprocessBpmnId to BpmnProcessBodyBuilder.builder()
                                        .setServiceTask(listOf(bpmnServiceTaskStart))
                                        .setBoundaryErrorEvent(listOf(boundaryEventOnServiceTask))
                                        .create()
                        )
                ),
                listOf(DiagramElement(
                        diagramMainElementId,
                        PlaneElement(diagramMainPlaneElementId, basicProcess.process.id, listOf(diagramSubProcess, diagramServiceTaskStart, boundaryEventOnServiceTaskShape), listOf()))
                )
        )
        whenever(parser.parse("")).thenReturn(process)
        initializeCanvas()
    }

    private fun elemAreaById(id: DiagramElementId) = renderResult?.get(id)!!

    private fun findFirstNewLinkElem() = renderResult?.keys?.firstOrNull { it.id.contains(newLink) }
    private fun findFirstDeleteElem() = renderResult?.keys?.firstOrNull { it.id.contains(doDel) }

    private fun findExactlyOneNewLinkElem() = renderResult?.keys?.filter { it.id.contains(newLink) }?.shouldHaveSize(1)?.first()
    private fun findExactlyOneDeleteElem() = renderResult?.keys?.filter { it.id.contains(doDel) }?.shouldHaveSize(1)?.first()

    private fun String.asResource(): String? = UiEditorLightE2ETest::class.java.classLoader.getResource(this)?.readText(StandardCharsets.UTF_8)
}