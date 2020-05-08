# What is this

This project provides plugin for [Flowable BPMN engine](https://github.com/flowable/flowable-engine) integration into IntelliJ,
aiming for code navigation support between BPMN diagram and your classes including Spring beans.

Currently, it is work-in-progress.

# Installation

Install a plugin from:
[0.0.1 MVP](https://github.com/valb3r/flowable-bpmn-intellij-plugin/releases/tag/0.0.1-alpha) release page

Installation guideline video:

[![Install plugin](https://img.youtube.com/vi/tfSAEMSIrqA/0.jpg)](https://www.youtube.com/watch?v=tfSAEMSIrqA)


# Workflow

MVP workflow demo:

[![Work with plugin](https://img.youtube.com/vi/9DkhMtDXf44/0.jpg)](https://www.youtube.com/watch?v=9DkhMtDXf44)


# Navigation guideline

1. To open BPMN diagram in the plugin - left mouse button on XML file and select `View BPMN Diagram`
1. To move diagram up/down/left/right - click mouse wheel and start moving your mouse - diagram will follow 
(like dragging with mouse wheel)
1. Zoom in/out - mouse wheel rotation
1. To select element click on it with mouse


# Flowable XML subset support

<details><summary>View summary table</summary>


|            XML element           | Supported |
|:--------------------------------:|:---------:|
|          adHocSubProcess         |           |
|            association           |           |
|           boundaryEvent          |           |
|        bpmndi:BPMNDiagram        |     Y     |
|          bpmndi:BPMNEdge         |     Y     |
|         bpmndi:BPMNPlane         |     Y     |
|         bpmndi:BPMNShape         |     Y     |
|         businessRuleTask         |           |
|           callActivity           |     Y     |
|       cancelEventDefinition      |           |
|     compensateEventDefinition    |           |
|        completionCondition       |           |
|             condition            |           |
|    conditionalEventDefinition    |           |
|        conditionExpression       |           |
|            dataObject            |           |
|            definitions           |           |
|           documentation          |           |
|             endEvent             |     Y     |
|       errorEventDefinition       |           |
|     escalationEventDefinition    |           |
|         eventBasedGateway        |           |
|         exclusiveGateway         |     Y     |
|         extensionElements        |           |
|      flowable:eventListener      |           |
|    flowable:executionListener    |           |
|          flowable:field          |           |
|            flowable:in           |           |
|           flowable:out           |           |
|          flowable:string         |           |
|          flowable:value          |           |
|         inclusiveGateway         |           |
|      intermediateCatchEvent      |           |
|      intermediateThrowEvent      |           |
|          loopCardinality         |           |
|              message             |           |
|      messageEventDefinition      |           |
| multiInstanceLoopCharacteristics |           |
|           omgdc:Bounds           |     Y     |
|          omgdi:waypoint          |     Y     |
|          parallelGateway         |           |
|              process             |           |
|            receiveTask           |           |
|              script              |           |
|            scriptTask            |           |
|           sequenceFlow           |     Y     |
|            serviceTask           |     Y     |
|       signalEventDefinition      |           |
|            startEvent            |     Y     |
|            subProcess            |           |
|     terminateEventDefinition     |           |
|               text               |           |
|          textAnnotation          |           |
|             timeDate             |           |
|       timerEventDefinition       |           |
|            transaction           |           |
|             userTask             |           |

</details>