package com.valb3r.bpmn.intellij.activiti.plugin.popupmenu

import ClipboardCopier
import ClipboardCutter
import ClipboardPaster
import ShapeCreator
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.IconLoader
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.activities.BpmnCallActivity
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.begin.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.boundary.*
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateMessageCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateSignalCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.catching.BpmnIntermediateTimerCatchingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.end.BpmnEndCancelEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.end.BpmnEndErrorEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.end.BpmnEndEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.end.BpmnEndTerminateEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.throwing.BpmnIntermediateNoneThrowingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.events.throwing.BpmnIntermediateSignalThrowingEvent
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnEventGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnExclusiveGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnInclusiveGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways.BpmnParallelGateway
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.subprocess.BpmnEventSubprocess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.subprocess.BpmnSubProcess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.*
import com.valb3r.bpmn.intellij.plugin.core.copypaste.copyPasteActionHandler
import com.valb3r.bpmn.intellij.plugin.core.render.lastRenderedState
import com.valb3r.bpmn.intellij.plugin.core.ui.components.popupmenu.CanvasPopupMenuProvider
import java.awt.event.ActionListener
import java.awt.geom.Point2D
import javax.swing.Icon
import javax.swing.JMenu
import javax.swing.JPopupMenu

class ActivitiCanvasPopupMenuProvider : CanvasPopupMenuProvider {

    // Functional
    private val COPY = IconLoader.getIcon("/icons/actions/copy.png")
    private val CUT = IconLoader.getIcon("/icons/actions/cut.png")
    private val PASTE = IconLoader.getIcon("/icons/actions/paste.png")

    // Events
    // Start
    private val START_EVENT = IconLoader.getIcon("/icons/popupmenu/start-event.png")
    private val START_MESSAGE_EVENT = IconLoader.getIcon("/icons/popupmenu/message-start-event.png")
    private val START_ERROR_EVENT = IconLoader.getIcon("/icons/popupmenu/error-start-event.png")
    private val START_SIGNAL_EVENT = IconLoader.getIcon("/icons/popupmenu/signal-start-event.png")
    private val START_TIMER_EVENT = IconLoader.getIcon("/icons/popupmenu/timer-start-event.png")
    // End
    private val END_EVENT = IconLoader.getIcon("/icons/popupmenu/end-event.png")
    private val CANCEL_END_EVENT = IconLoader.getIcon("/icons/popupmenu/cancel-end-event.png")
    private val ERROR_END_EVENT = IconLoader.getIcon("/icons/popupmenu/error-end-event.png")
    private val TERMINATE_END_EVENT = IconLoader.getIcon("/icons/popupmenu/terminate-end-event.png")
    // Boundary
    private val BOUNDARY_CANCEL_EVENT = IconLoader.getIcon("/icons/popupmenu/cancel-boundary-event.png")
    private val BOUNDARY_COMPENSATION_EVENT = IconLoader.getIcon("/icons/popupmenu/compensation-boundary-event.png")
    private val BOUNDARY_ERROR_EVENT = IconLoader.getIcon("/icons/popupmenu/error-boundary-event.png")
    private val BOUNDARY_MESSAGE_EVENT = IconLoader.getIcon("/icons/popupmenu/message-boundary-event.png")
    private val BOUNDARY_SIGNAL_EVENT = IconLoader.getIcon("/icons/popupmenu/signal-boundary-event.png")
    private val BOUNDARY_TIMER_EVENT = IconLoader.getIcon("/icons/popupmenu/timer-boundary-event.png")
    // Intermediate events
    // Catch
    private val INTERMEDIATE_TIMER_CATCHING = IconLoader.getIcon("/icons/popupmenu/timer-catch-event.png")
    private val INTERMEDIATE_MESSAGE_CATCHING = IconLoader.getIcon("/icons/popupmenu/message-catch-event.png")
    private val INTERMEDIATE_SIGNAL_CATCHING = IconLoader.getIcon("/icons/popupmenu/signal-catch-event.png")
    // Throw
    private val INTERMEDIATE_NONE_THROWING = IconLoader.getIcon("/icons/popupmenu/none-throw-event.png")
    private val INTERMEDIATE_SIGNAL_THROWING = IconLoader.getIcon("/icons/popupmenu/signal-throw-event.png")

    // Service-task alike
    private val SERVICE_TASK = IconLoader.getIcon("/icons/popupmenu/service-task.png")
    private val USER_TASK = IconLoader.getIcon("/icons/popupmenu/user-task.png")
    private val SCRIPT_TASK = IconLoader.getIcon("/icons/popupmenu/script-task.png")
    private val BUSINESS_RULE_TASK = IconLoader.getIcon("/icons/popupmenu/business-rule-task.png")
    private val RECEIVE_TASK = IconLoader.getIcon("/icons/popupmenu/receive-task.png")
    private val MANUAL_TASK = IconLoader.getIcon("/icons/popupmenu/manual-task.png")
    private val CAMEL_TASK = IconLoader.getIcon("/icons/popupmenu/camel-task.png")
    private val MAIL_TASK = IconLoader.getIcon("/icons/popupmenu/mail-task.png")
    private val MULE_TASK = IconLoader.getIcon("/icons/popupmenu/mule-task.png")
    private val DECISION_TASK = IconLoader.getIcon("/icons/popupmenu/decision-task.png")

    // Sub process alike
    private val CALL_ACTIVITY = IconLoader.getIcon("/icons/popupmenu/call-activity.png")
    private val SUB_PROCESS = IconLoader.getIcon("/icons/popupmenu/subprocess.png")
    private val EVENT_SUB_PROCESS = IconLoader.getIcon("/icons/popupmenu/event-subprocess.png")

    // Gateway
    private val EXCLUSIVE_GATEWAY = IconLoader.getIcon("/icons/popupmenu/exclusive-gateway.png")
    private val PARALLEL_GATEWAY = IconLoader.getIcon("/icons/popupmenu/parallel-gateway.png")
    private val INCLUSIVE_GATEWAY = IconLoader.getIcon("/icons/popupmenu/inclusive-gateway.png")
    private val EVENT_GATEWAY = IconLoader.getIcon("/icons/popupmenu/event-gateway.png")

    override fun popupMenu(sceneLocation: Point2D.Float, parent: BpmnElementId): JBPopupMenu {
        val popup = JBPopupMenu()

        addCopyAndpasteIfNeeded(popup, sceneLocation, parent)
        popup.add(startEvents(sceneLocation, parent))
        popup.add(activities(sceneLocation, parent))
        popup.add(structural(sceneLocation, parent))
        popup.add(gateways(sceneLocation, parent))
        popup.add(boundaryEvents(sceneLocation, parent))
        popup.add(intermediateCatchingEvents(sceneLocation, parent))
        popup.add(intermediateThrowingEvents(sceneLocation, parent))
        popup.add(endEvents(sceneLocation, parent))
        return popup
    }

    private fun addCopyAndpasteIfNeeded(popup: JBPopupMenu, sceneLocation: Point2D.Float, parent: BpmnElementId) {
        val renderedState = lastRenderedState()
        if (true == renderedState?.canCopyOrCut()) {
            addItem(popup, "Copy", COPY, ClipboardCopier())
            addItem(popup, "Cut", CUT, ClipboardCutter())
        }

        if (copyPasteActionHandler().hasDataToPaste()) {
            addItem(popup, "Paste", PASTE, ClipboardPaster(sceneLocation, parent))
        }
    }

    private fun startEvents(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Start events")
        addItem(menu, "Start event", START_EVENT, ShapeCreator(BpmnStartEvent::class, sceneLocation, parent))
        addItem(menu, "Start timer event", START_TIMER_EVENT, ShapeCreator(BpmnStartTimerEvent::class, sceneLocation, parent))
        addItem(menu, "Start signal event", START_SIGNAL_EVENT, ShapeCreator(BpmnStartSignalEvent::class, sceneLocation, parent))
        addItem(menu, "Start message event", START_MESSAGE_EVENT, ShapeCreator(BpmnStartMessageEvent::class, sceneLocation, parent))
        addItem(menu, "Start error event", START_ERROR_EVENT, ShapeCreator(BpmnStartErrorEvent::class, sceneLocation, parent))
        return menu
    }

    private fun activities(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Activities")
        addItem(menu, "User task", USER_TASK, ShapeCreator(BpmnUserTask::class, sceneLocation, parent))
        addItem(menu, "Service task", SERVICE_TASK, ShapeCreator(BpmnServiceTask::class, sceneLocation, parent))
        addItem(menu, "Script task", SCRIPT_TASK, ShapeCreator(BpmnScriptTask::class, sceneLocation, parent))
        addItem(menu, "Business rule task", BUSINESS_RULE_TASK, ShapeCreator(BpmnBusinessRuleTask::class, sceneLocation, parent))
        addItem(menu, "Receive task", RECEIVE_TASK, ShapeCreator(BpmnReceiveTask::class, sceneLocation, parent))
        addItem(menu, "Manual task", MANUAL_TASK, ShapeCreator(BpmnManualTask::class, sceneLocation, parent))
        addItem(menu, "Mail task", MAIL_TASK, ShapeCreator(BpmnMailTask::class, sceneLocation, parent))
        addItem(menu, "Camel task", CAMEL_TASK, ShapeCreator(BpmnCamelTask::class, sceneLocation, parent))
        addItem(menu, "Mule task", MULE_TASK, ShapeCreator(BpmnMuleTask::class, sceneLocation, parent))
        addItem(menu, "Decision task", DECISION_TASK, ShapeCreator(BpmnDecisionTask::class, sceneLocation, parent))
        return menu
    }

    private fun structural(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Structural")
        addItem(menu, "Sub process", SUB_PROCESS, ShapeCreator(BpmnSubProcess::class, sceneLocation, parent))
        addItem(menu, "Event sub process", EVENT_SUB_PROCESS, ShapeCreator(BpmnEventSubprocess::class, sceneLocation, parent))
        addItem(menu, "Call activity", CALL_ACTIVITY, ShapeCreator(BpmnCallActivity::class, sceneLocation, parent))
        return menu
    }

    private fun gateways(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Gateways")
        addItem(menu, "Exclusive gateway", EXCLUSIVE_GATEWAY, ShapeCreator(BpmnExclusiveGateway::class, sceneLocation, parent))
        addItem(menu, "Parallel gateway", PARALLEL_GATEWAY, ShapeCreator(BpmnParallelGateway::class, sceneLocation, parent))
        addItem(menu, "Inclusive gateway", INCLUSIVE_GATEWAY, ShapeCreator(BpmnInclusiveGateway::class, sceneLocation, parent))
        addItem(menu, "Event gateway", EVENT_GATEWAY, ShapeCreator(BpmnEventGateway::class, sceneLocation, parent))
        return menu
    }

    private fun boundaryEvents(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Boundary events")
        addItem(menu, "Boundary error event", BOUNDARY_ERROR_EVENT, ShapeCreator(BpmnBoundaryErrorEvent::class, sceneLocation, parent))
        addItem(menu, "Boundary timer event", BOUNDARY_TIMER_EVENT, ShapeCreator(BpmnBoundaryTimerEvent::class, sceneLocation, parent))
        addItem(menu, "Boundary signal event", BOUNDARY_SIGNAL_EVENT, ShapeCreator(BpmnBoundarySignalEvent::class, sceneLocation, parent))
        addItem(menu, "Boundary message event", BOUNDARY_MESSAGE_EVENT, ShapeCreator(BpmnBoundaryMessageEvent::class, sceneLocation, parent))
        addItem(menu, "Boundary cancel event", BOUNDARY_CANCEL_EVENT, ShapeCreator(BpmnBoundaryCancelEvent::class, sceneLocation, parent))
        addItem(menu, "Boundary compensation event", BOUNDARY_COMPENSATION_EVENT, ShapeCreator(BpmnBoundaryCompensationEvent::class, sceneLocation, parent))
        return menu
    }

    private fun intermediateCatchingEvents(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Intermediate catching events")
        addItem(menu, "Intermediate timer catching event", INTERMEDIATE_TIMER_CATCHING, ShapeCreator(BpmnIntermediateTimerCatchingEvent::class, sceneLocation, parent))
        addItem(menu, "Intermediate message catching event", INTERMEDIATE_MESSAGE_CATCHING, ShapeCreator(BpmnIntermediateMessageCatchingEvent::class, sceneLocation, parent))
        addItem(menu, "Intermediate signal catching event", INTERMEDIATE_SIGNAL_CATCHING, ShapeCreator(BpmnIntermediateSignalCatchingEvent::class, sceneLocation, parent))
        return menu
    }

    private fun intermediateThrowingEvents(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("Intermediate throwing events")
        addItem(menu, "Intermediate none throwing event", INTERMEDIATE_NONE_THROWING, ShapeCreator(BpmnIntermediateNoneThrowingEvent::class, sceneLocation, parent))
        addItem(menu, "Intermediate signal throwing event", INTERMEDIATE_SIGNAL_THROWING, ShapeCreator(BpmnIntermediateSignalThrowingEvent::class, sceneLocation, parent))
        return menu
    }

    private fun endEvents(sceneLocation: Point2D.Float, parent: BpmnElementId): JMenu {
        val menu = JMenu("End events")
        addItem(menu, "End event", END_EVENT, ShapeCreator(BpmnEndEvent::class, sceneLocation, parent))
        addItem(menu, "End error event", ERROR_END_EVENT, ShapeCreator(BpmnEndErrorEvent::class, sceneLocation, parent))
        addItem(menu, "End cancel event", CANCEL_END_EVENT, ShapeCreator(BpmnEndCancelEvent::class, sceneLocation, parent))
        addItem(menu, "End terminate event", TERMINATE_END_EVENT, ShapeCreator(BpmnEndTerminateEvent::class, sceneLocation, parent))
        return menu
    }

    private fun addItem(menu: JMenu, text: String, icon: Icon, listener: ActionListener) {
        val item = JBMenuItem(text, icon)
        item.addActionListener(listener)
        menu.add(item)
    }

    private fun addItem(menu: JPopupMenu, text: String, icon: Icon, listener: ActionListener) {
        val item = JBMenuItem(text, icon)
        item.addActionListener(listener)
        menu.add(item)
    }
}