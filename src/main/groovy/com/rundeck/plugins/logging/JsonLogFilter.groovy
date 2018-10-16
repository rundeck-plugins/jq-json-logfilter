package com.rundeck.plugins.logging

import com.dtolabs.rundeck.core.execution.workflow.OutputContext
import com.dtolabs.rundeck.core.logging.LogEventControl
import com.dtolabs.rundeck.core.logging.LogLevel
import com.dtolabs.rundeck.core.logging.PluginLoggingContext
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.logging.LogFilterPlugin
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope

@Plugin(name = JsonLogFilter.PROVIDER_NAME, service = 'LogFilter')
@PluginDescription(title = 'JSON jq key/value mapper',
                   description = 'Map a json logoutput on key/value to context data using jq filters')
class JsonLogFilter implements LogFilterPlugin{
    public static final String PROVIDER_NAME = 'json-mapper'

    @PluginProperty(
            title = 'jq Filter',
            description = '''Add a jq Filter''',
            defaultValue=".",
            required=true

    )
    String filter

    @PluginProperty(
            title = 'Prefix',
            description = '''(Optional) Result prefix''',
            defaultValue="result"
    )
    String prefix

    @PluginProperty(
            title = 'Log Data',
            description = '''If true, log the captured data''',
            defaultValue = 'false'
    )
    Boolean logData

    private StringBuffer buffer;
    OutputContext outputContext
    Map<String, String> allData
    private ObjectMapper mapper


    @Override
    void init(final PluginLoggingContext context) {
        outputContext = context.getOutputContext()
        buffer = new StringBuffer()
        mapper = new ObjectMapper()
        allData = [:]

    }

    @Override
    void handleEvent(final PluginLoggingContext context, final LogEventControl event) {
        if (event.eventType == 'log' && event.loglevel == LogLevel.NORMAL && event.message?.length() > 0) {
            buffer.append(event.message)
        }

    }

    @Override
    void complete(final PluginLoggingContext context) {

        if(buffer.size()>0) {
            //apply json transform
            this.processJson(context)

            if (logData) {
                context.log(
                        2,
                        mapper.writeValueAsString(allData),
                        [
                                'content-data-type'       : 'application/json',
                                'content-meta:table-title': 'Key Value Data: Results'
                        ]
                )
            }
        }
    }

    void processJson(final PluginLoggingContext context){

        try{

            Scope rootScope = Scope.newEmptyScope()
            rootScope.loadFunctions(Scope.class.getClassLoader())
            JsonQuery q = JsonQuery.compile(filter)
            JsonNode inData = mapper.readTree(buffer.toString());

            List<JsonNode> out = q.apply(rootScope, inData)

            out.each {it->
                //process object
                if(it.getNodeType()==JsonNodeType.OBJECT){
                    def map = it.fields()
                    for (Map.Entry<String, JsonNode> entry : map) {
                        this.iterateJsonObject(entry)
                    }
                }else{
                    if(it.getNodeType()==JsonNodeType.ARRAY){
                        this.iterateArray(it.elements())
                    }else{
                        allData.put(prefix, it.toString())
                    }
                }
            }

            outputContext.addOutput("data",allData)

        }catch(Exception exception){
            context.log(0, "[error] cannot map the json output: " + exception.message)
        }
    }

    def iterateArray(def list){

        Integer i=0
        list.each{it->
            if(it.getNodeType() == JsonNodeType.STRING){
                allData.put(prefix +"."+ i.toString(),it.textValue())
            }else{
                allData.put(prefix +"."+ i.toString(),it.toString())
            }

            i++
        }
    }

    def iterateJsonObject(Map.Entry<String, JsonNode> entry, String path=""){

        def key = entry.getKey()
        def value = entry.getValue()
        def newPath

        if(!path){
            newPath=key
        }else{
            newPath=path + "."+ key
        }

        if(value.getNodeType()== JsonNodeType.OBJECT){
            for (Map.Entry<String, JsonNode> subKey : entry.getValue().fields()) {
                iterateJsonObject(subKey, newPath)
            }
        }else{
            def extractValue
            if(value.getNodeType() == JsonNodeType.STRING){
                extractValue=value.textValue()
            }else{
                extractValue = value.toString()
            }

            allData.put(newPath,extractValue)
        }
    }

}
