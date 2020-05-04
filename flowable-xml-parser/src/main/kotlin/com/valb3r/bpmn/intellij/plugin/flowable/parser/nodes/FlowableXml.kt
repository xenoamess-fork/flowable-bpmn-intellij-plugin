package com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes

import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnProcess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElement
import com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.diagram.BPMNPlane
import com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.process.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers


data class Definitions(
        val process: ProcessNode,
        @JacksonXmlProperty(localName = "BPMNDiagram") val bpmnDiagram: DiagramNode
)

// For mixed lists in XML we need to have JsonSetter/JsonMerge on field
// https://github.com/FasterXML/jackson-dataformat-xml/issues/363
// unfortunately this has failed with Kotlin 'data' classes
class ProcessNode(): BpmnMappable<BpmnProcess> {

    @JacksonXmlProperty(isAttribute = true) var id: String? = null // it is false - it is non-null
    @JacksonXmlProperty(isAttribute = true) var name: String? = null // it is false - it is non-null
    var documentation: String? = null
    @JacksonXmlProperty(isAttribute = true) var isExecutable: Boolean? = null
    var startEvent: StartEventNode? = null  // it is false - it is non-null
    val endEvent: EndEventNode? = null  // it is false - it is non-null

    // Particularly problematic element section
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) var callActivity: List<CallActivity>? = null
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) var serviceTask: List<ServiceTask>? = null
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) var sequenceFlow: List<SequenceFlow>? = null
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) var exclusiveGateway: List<ExclusiveGateway>? = null

    override fun toElement(): BpmnProcess {
        return Mappers.getMapper(Mapping::class.java).convertToDto(this)
    }

    @Mapper
    interface Mapping {
        fun convertToDto(input: ProcessNode): BpmnProcess
    }
}

data class DiagramNode(
        @JacksonXmlProperty(isAttribute = true) val id: String,
        @JacksonXmlProperty(localName = "BPMNPlane") val bpmnPlane: BPMNPlane
) : BpmnMappable<DiagramElement> {

    override fun toElement(): DiagramElement {
        return Mappers.getMapper(Mapping::class.java).convertToDto(this)
    }

    @Mapper
    interface Mapping {
        fun convertToDto(input: DiagramNode): DiagramElement
    }
}