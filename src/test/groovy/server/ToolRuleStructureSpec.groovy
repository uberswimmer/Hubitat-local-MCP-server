package server

import groovy.json.JsonOutput
import support.ToolSpecBase
import spock.lang.Unroll

class ToolRuleStructureSpec extends ToolSpecBase {
    private void sources(List order = ['7', '2', '8'], Map extra = [:], Map settings = [:]) {
        settingsMap.enableRead = true
        script.metaClass.getAllGlobalVars = { -> [HiddenGlobal: [type: 'string', value: 'NEVER_GLOBAL']] }
        def config = [app: [id: 35], configPage: [sections: [[body: [
            [element: 'href', page: 'selectActions', description: 'NEVER_WHOLE_ACTION_TEXT'],
            [element: 'href', page: 'STPage', description: 'Illuminance of Lux(53) is <= 20<span>(F) [FALSE]</span>'],
            [element: 'href', page: 'selectTriggers', description: 'Motion motion reports active']
        ]]]], settings: ['actType.7': 'switchActs', 'actSubType.7': 'getOnOffSwitch',
            'actType.2': 'messageActs', 'actSubType.2': 'getMsg',
            'actType.8': 'switchActs', 'actSubType.8': 'getOnOffSwitch',
            'onOffSwitch.7': ['17'], 'onOff.7': true, 'onOffSwitch.8': ['17'], 'onOff.8': false,
            'actType.999': 'condActs', 'actSubType.999': 'getIfThen', password: 'NEVER_PASSWORD']]
        config.settings.putAll(settings)
        def compiled = [broken: false, hasPredicate: true, actionList: order,
                        actions: ['7': [wait: null, quick: false, delay: 'NEVER_DELAY', modes: [:],
                            method: 'getOnOffSwitch', indent: '', rule: null, cond: 0],
                            '2': [method: 'getMsg', payload: 'NEVER_NOTIFICATION\nOff: Lamp'],
                            '8': [method: 'getOnOffSwitch']]]
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
        result.contractVersion == 2
        result.actions.order == [7, 2, 8]
        result.actions.rows*.index == [7, 2, 8]
        result.actions.rows[1] == [index: 2, actType: 'messageActs', actSubType: 'getMsg', status: 'withheld', category: 'notification']
        result.actions.rows[0].fields.onOffSwitch == [status: 'available', value: ['17']]
        result.actions.rows[0].fields.onOff == [status: 'available', value: true]
        result.actions.rows[0].fields.delayAct == [status: 'absent']
        result.actions.rows[2].fields.onOff.value == false
        !result.actions.rows.any { it.containsKey('text') }
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

    @Unroll
    def "action text never comes from compiled execution records or a whole paragraph (#actions)"() {
        given:
        sources(['7'], [actions: actions])
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        result.actions.rows[0].fields.onOff.value == true
        !JsonOutput.toJson(result).contains('NEVER_')
        !result.actions.rows[0].containsKey('text')
        where:
        actions << [null, ['7': 'NEVER_GUESSED_TEXT'], ['7': [label: 'NEVER_OBJECT_TEXT']]]
    }

    def "private variable references and malformed fields are withheld without dropping safe neighbors"() {
        given:
        sources(['7','8'], [:], ['xVarD.7': 'SecretLocal', 'onOffSwitch.7': [lockCodes: 'NEVER_CODE'],
            'onOff.7': 'NEVER_BOOLEAN', 'delaySec.7': 'NEVER_SECONDS',
            'delayAct.7': 'NEVER_ENUM', 'xVarD.8': 'Flag'])
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        ['xVarD', 'onOffSwitch', 'onOff', 'delaySec', 'delayAct'].every {
            result.actions.rows[0].fields[it] == [status: 'withheld']
        }
        result.actions.rows[1].fields.onOff.value == false
        result.actions.rows[1].fields.xVarD == [status: 'available', value: 'Flag']
        !JsonOutput.toJson(result).contains('NEVER_')
        !JsonOutput.toJson(result).contains('SecretLocal')
    }

    def "source absence empty values and false remain distinct and defaults are not invented"() {
        given:
        sources(['7'], [:], ['optSwitch.7': '', 'delayAct.7': null, 'cancelAct.7': false,
            'delaySec.7': '0.5', 'randomAct.7': 'false'])
        when:
        def fields = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure']).actions.rows[0].fields
        then:
        fields.optSwitch == [status: 'available', value: '']
        fields.delayAct == [status: 'available', value: null]
        fields.cancelAct == [status: 'available', value: false]
        fields.delaySec == [status: 'available', value: '0.5']
        fields.randomAct == [status: 'available', value: 'false']
        fields.delayHor == [status: 'absent']
    }

    def "lock polarity and separate repeat subtype identities remain source evidence"() {
        given:
        sources(['7', '8', '9'], [:], ['actType.7': 'lockActs', 'actSubType.7': 'getLULock',
            'lockRL.7': true, 'lockLockUnlock.7': ['42'],
            'actType.8': 'repeatActs', 'actSubType.8': 'getEndRepeat',
            'actType.9': 'repeatActs', 'actSubType.9': 'getStopRepeat'])
        when:
        def rows = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure']).actions.rows
        then:
        rows*.actSubType == ['getLULock', 'getEndRepeat', 'getStopRepeat']
        rows[0].fields.lockRL == [status: 'available', value: true]
        rows[0].fields.lockLockUnlock == [status: 'available', value: ['42']]
        !JsonOutput.toJson(rows).contains('command')
    }

    def "local string shadowing blocks a same-name Boolean global reference"() {
        given:
        sources(['7'], [:], ['xVarD.7': 'SecretLocal'])
        script.metaClass.getAllGlobalVars = { -> [SecretLocal: [type: 'boolean', value: false]] }
        when:
        def result = script.toolGetAppConfig([appId: '35', projection: 'ruleStructure'])
        then:
        result.actions.rows[0].fields.xVarD == [status: 'withheld']
        !JsonOutput.toJson(result).contains('SecretLocal')
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
