package server

import groovy.json.JsonOutput
import support.ToolSpecBase
import spock.lang.Unroll

class ToolRuleStructureSpec extends ToolSpecBase {
    private void sources(List order = ['7', '2', '8'], Map extra = [:]) {
        settingsMap.enableRead = true
        script.metaClass.getAllGlobalVars = { -> [HiddenGlobal: [type: 'string', value: 'NEVER_GLOBAL']] }
        def config = [app: [id: 35], configPage: [sections: [[body: [
            [element: 'href', page: 'selectActions', description: 'NEVER_WHOLE_ACTION_TEXT'],
            [element: 'href', page: 'STPage', description: 'Illuminance of Lux(53) is <= 20<span>(F) [FALSE]</span>'],
            [element: 'href', page: 'selectTriggers', description: 'Motion motion reports active']
        ]]]], settings: ['actType.7': 'switchActs', 'actSubType.7': 'getOnOffSwitch',
            'actType.2': 'messageActs', 'actSubType.2': 'getMsg',
            'actType.8': 'switchActs', 'actSubType.8': 'getOnOffSwitch',
            'actType.999': 'condActs', 'actSubType.999': 'getIfThen', password: 'NEVER_PASSWORD']]
        def compiled = [broken: false, hasPredicate: true, actionList: order,
                        actions: ['7': 'On: Lamp', '2': 'NEVER_NOTIFICATION\nOff: Lamp', '8': 'Off: Lamp', '999': 'IF stale THEN']]
        compiled.putAll(extra)
        hubGet.register('/installedapp/configure/json/35') { params -> JsonOutput.toJson(config) }
        hubGet.register('/app/ruleBuilderJson/35') { params -> JsonOutput.toJson(compiled) }
        hubGet.register('/installedapp/statusJson/35') { params -> JsonOutput.toJson([appState: [[name: 'allLocalVars', value: [
            Flag: [type: 'boolean', value: true], SecretLocal: [type: 'string', value: 'NEVER_LOCAL'],
            ServicePassword: [type: 'integer', value: 9876]]]]]) }
    }

    @Unroll
    def "comparison and real markup conversion: #input"() {
        expect:
        script.stripAppConfigHtml(input) == expected
        where:
        input | expected
        'x < 20' | 'x < 20'
        'x <= 20<span>(F)</span>' | 'x <= 20(F)'
        'x > 20' | 'x > 20'
        'x >= 20' | 'x >= 20'
        'x &lt;= 20 &amp; y &gt; 5' | 'x <= 20 & y > 5'
        'x &#60; 20 &#x3e; 5' | 'x < 20 > 5'
        'Illuminance of Lux(53) is <= 20<span style="color:orange">(F) [FALSE]</span>' | 'Illuminance of Lux(53) is <= 20(F) [FALSE]'
        '<b>text</b><br>next' | 'text\nnext'
        '<span title="a > b">x < 2</span>' | 'x < 2'
        '<style>p {color:red}</style><script>bad()</script>x {keep: this}' | 'x {keep: this}'
        'x < 2 <span' | 'x < 2 <span'
        'x <script>unfinished' | 'x'
        '&lt;script&gt;literal&lt;/script&gt;' | '<script>literal</script>'
        '&amp;lt;' | '&lt;'
    }

    @Unroll
    def "rule projection selects ordered bounded sources and no private payload (dispatch=#dispatch)"() {
        given:
        sources()
        settingsMap.useGateways = true
        when:
        def result = dispatch ? mcpDriver.parseInner(mcpDriver.callTool('hub_get_app_config', [appId: '35', projection: 'ruleStructure'])) :
            script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        result.success
        result.contractVersion == 1
        result.actions.order == [7, 2, 8]
        result.actions.rows*.index == [7, 2, 8]
        result.actions.rows[1] == [index: 2, actType: 'messageActs', actSubType: 'getMsg', status: 'withheld', category: 'notification']
        result.actions.rows[0].text == 'On: Lamp'
        result.requiredExpression.text.contains('<= 20')
        result.localVariables == [[name: 'Flag', type: 'boolean']]
        !JsonOutput.toJson(result).contains('NEVER_')
        !JsonOutput.toJson(result).contains('SecretLocal')
        !JsonOutput.toJson(result).contains('ServicePassword')
        !result.containsKey('settings')
        where:
        dispatch << [false, true]
    }

    @Unroll
    def "unreadable order is unavailable, genuine empty order ignores stale rows (#order)"() {
        given:
        sources(order)
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        result.actions.status == expected
        if (expected == 'available') assert result.actions.rows == []
        where:
        order | expected
        null | 'unavailable'
        ['7', null] | 'unavailable'
        ['7', '7'] | 'unavailable'
        ['-1'] | 'unavailable'
        [] | 'available'
    }

    def "missing individual display map never falls back to whole action paragraph"() {
        given:
        sources(['7'], [actions: null])
        expect:
        script.toolGetAppConfig([appId: '35', projection: 'ruleStructure']).actions.rows[0].status == 'unavailable'
    }

    def "private local reference withholds only its bounded field"() {
        given:
        sources(['7','8'], [actions: ['7':'SecretLocal NEVER_VALUE', '8':'Off: Lamp']])
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        result.actions.rows[0].status == 'withheld'
        !result.actions.rows[0].containsKey('text')
        result.actions.rows[1].text == 'Off: Lamp'
    }

    def "source read failure is a fixed failure not a false empty rule"() {
        given:
        sources()
        hubGet.register('/installedapp/statusJson/35') { params -> throw new RuntimeException('NEVER_EXCEPTION') }
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        !result.success
        !JsonOutput.toJson(result).contains('NEVER_')
    }

    @Unroll
    def "unknown local scope fails closed (#state)"() {
        given:
        sources()
        hubGet.register('/installedapp/statusJson/35') { params -> JsonOutput.toJson([appState: state]) }
        expect:
        !script.toolGetAppConfig([appId: '35', projection: 'ruleStructure']).success
        where:
        state << [null, [:], [[name: 'allLocalVars', value: 'bad']],
                  [[name: 'allLocalVars', value: [:]], [name: 'allLocalVars', value: [:]]]]
    }

    def "projection rejects broad or navigational options before reading"() {
        when:
        script.toolGetAppConfig([appId: '35', projection: 'ruleStructure', includeSettings: true])
        then:
        thrown(IllegalArgumentException)
    }
}
