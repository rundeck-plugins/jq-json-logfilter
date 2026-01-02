package com.rundeck.plugins.logging

import com.dtolabs.rundeck.core.dispatcher.ContextView
import com.dtolabs.rundeck.core.execution.workflow.DataOutput
import com.dtolabs.rundeck.core.logging.LogEventControl
import com.dtolabs.rundeck.core.logging.LogLevel
import com.dtolabs.rundeck.core.logging.PluginLoggingContext
import spock.lang.Specification

class JsonLogFilterSpec extends Specification {

    def "simple test"() {
        given:
        def plugin = new JsonLogFilter()
        plugin.filter = filter
        plugin.prefix = "result"
        plugin.logData = dolog
        plugin.extraQuotes = false
        def sharedoutput = new DataOutput(ContextView.global())
        def context = Mock(PluginLoggingContext) {
            getOutputContext() >> sharedoutput
        }
        def events = []
        lines.each { line ->
            events << Mock(LogEventControl) {
                getMessage() >> line
                getEventType() >> 'log'
                getLoglevel() >> LogLevel.NORMAL
            }
        }
        when:
        plugin.init(context)
        events.each {
            plugin.handleEvent(context, it)
        }
        plugin.complete(context)
        then:

        sharedoutput.getSharedContext().getData(ContextView.global())?.getData() == (expect ? ['data': expect] : null)
        if (expect) {
            if (dolog) {
                1 * context.log(2, _, _)
            } else {
                0 * context.log(*_)
            }
        }


        where:
        dolog | filter          | lines                                         | expect
        true  | "."             | ['{"test":"value"}']                          | [test: 'value']
        true  | ".| keys"       | ['{"test":"value","something2":"value1"}']    |
        [
                'result.0'  : 'something2',
                'result.1'  : 'test'
        ]
        false | ".| length"     | ['{"test":"value","something2":"value1"}']    | [result: '2']
        true  | "."             | ['{"test":"value","something2":"value1"}']    |
        [
                'test'  : 'value',
                'something2' : 'value1'
        ]
        false | ".id"           | ['{"id":"abc12345"}']                         | [result: 'abc12345']
    }

    def "simple test legacy quoting"() {
        given:
        def plugin = new JsonLogFilter()
        plugin.filter = filter
        plugin.prefix = "result"
        plugin.logData = dolog
        plugin.extraQuotes = quoteVal
        def sharedoutput = new DataOutput(ContextView.global())
        def context = Mock(PluginLoggingContext) {
            getOutputContext() >> sharedoutput
        }
        def events = []
        lines.each { line ->
            events << Mock(LogEventControl) {
                getMessage() >> line
                getEventType() >> 'log'
                getLoglevel() >> LogLevel.NORMAL
            }
        }
        when:
        plugin.init(context)
        events.each {
            plugin.handleEvent(context, it)
        }
        plugin.complete(context)
        then:

        sharedoutput.getSharedContext().getData(ContextView.global())?.getData() == (expect ? ['data': expect] : null)
        if (expect) {
            if (dolog) {
                1 * context.log(2, _, _)
            } else {
                0 * context.log(*_)
            }
        }


        where:

        quoteVal | dolog | filter | lines                | expect
        false    | true  | "."    | ['{"test":"value"}'] | [test: 'value']
        null     | true  | "."    | ['{"test":"value"}'] | [test: '"value"']
        true     | true  | "."    | ['{"test":"value"}'] | [test: '"value"']
        true     | true  | "."    | ['["value"]']        | ['result.0': '"value"']
        null     | true  | "."    | ['["value"]']        | ['result.0': '"value"']
        false    | true  | "."    | ['["value"]']        | ['result.0': 'value']

    }

    def "array test"() {
        given:
        def filter = ".[]"
        def dolog = true
        def plugin = new JsonLogFilter()
        plugin.filter = filter
        plugin.prefix = "result"
        plugin.logData = dolog
        plugin.extraQuotes = false
        def sharedoutput = new DataOutput(ContextView.global())
        def context = Mock(PluginLoggingContext) {
            getOutputContext() >> sharedoutput
        }
        def events = []

        def lines = "{\n" +
                "  \"result\": {\n" +
                "    \"parent\": {\n" +
                "      \"link\": \"https://rundeck-test/api\",\n" +
                "      \"value\": \"123345\"\n" +
                "    },\n" +
                "    \"user\": \"rundeck\",\n" +
                "    \"created_by\": {\n" +
                "      \"link\": \"https://rundeck-test/user/1\",\n" +
                "      \"value\": \"123345\"\n" +
                "    },\n" +
                "    \"created_date\": \"2018-10-03 19:17:54\",\n" +
                "    \"status\": \"1\",\n" +
                "    \"closed_date\": \"2018-10-03 19:46:49\"\n" +
                "  }\n" +
                "}"

        def expect = [
                'parent.link'  : 'https://rundeck-test/api',
                'parent.value' : '123345',
                'user' : 'rundeck',
                'created_by.link' : 'https://rundeck-test/user/1',
                'created_by.value' : '123345',
                'created_date' : '2018-10-03 19:17:54',
                'status' : '1',
                'closed_date' : '2018-10-03 19:46:49',
        ]

        lines.each { line ->
            events << Mock(LogEventControl) {
                getMessage() >> line
                getEventType() >> 'log'
                getLoglevel() >> LogLevel.NORMAL
            }
        }
        when:
        plugin.init(context)
        events.each {
            plugin.handleEvent(context, it)
        }
        plugin.complete(context)
        then:



        sharedoutput.getSharedContext().getData(ContextView.global())?.getData() == (expect ? ['data': expect] : null)
        if (expect) {
            if (dolog) {
                1 * context.log(2, _, _)
            } else {
                0 * context.log(*_)
            }
        }


    }

}
