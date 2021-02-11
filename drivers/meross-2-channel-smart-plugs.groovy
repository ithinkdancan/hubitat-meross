/**
 * Meross 2 Channel Smart Plug
 *
 * Author: Daniel Tijerina
 * Last updated: 2021-02-03
 *
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

metadata {
    definition(
        name: 'Meross 2 Channel Smart Plug',
        namespace: 'ithinkdancan',
        author: 'Daniel Tijerina'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Configuration'

        command 'childOn', [[name:'Channel Number', type:'STRING', description:'Provide the channel number to turn on.']]
        command 'childOff', [[name:'Channel Number', type:'STRING', description:'Provide the channel number to turn off.']]
        command 'childRefresh', [[name:'Channel Number', type:'STRING', description:'Provide the channel number to refresh.']]
        command 'componentOn'
        command 'componentOff'
        command 'componentRefresh'
    }
    preferences {
        section('Device Selection') {
            input('deviceIp', 'text', title: 'Device IP Address', description: '', required: true, defaultValue: '')
            input('DebugLogging', 'bool', title: 'Enable debug logging', defaultValue: true)
        }
    }
}

def getDriverVersion() {
    1
}

def sendCommand(int onoff, int channel) {
    try {
        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{"togglex":{"onoff":' + onoff + ',"channel":' + channel + '}},"header":{"messageId":"926aabad9487c580d9c8821ecf2d704d","method":"SET","from":"http://'+settings.deviceIp+'/config","sign":"40fc38fc665fe340297e91907f842e3e","namespace":"Appliance.Control.ToggleX","triggerSrc":"iOSLocal","timestamp":1612399570,"payloadVersion":1}}'
    ])
        log.debug hubAction
        return hubAction
    } catch (e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}

def refresh() {
    log.info('Refreshing')
    try {
        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{},"header":{"messageId":"926aabad9487c580d9c8821ecf2d704d","method":"GET","from":"http://'+settings.deviceIp+'/config","sign":"40fc38fc665fe340297e91907f842e3e","namespace": "Appliance.System.All","triggerSrc":"iOSLocal","timestamp":1612399570,"payloadVersion":1}}'
    ])
        log.debug hubAction
        return hubAction
    } catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}

def on() {
    log.info('Turning on')
    return sendCommand(1, 0)
}

def off() {
    log.info('Turning off')
    return sendCommand(0, 0)
}

def childOn(String dni) {
    log.debug "childOn($dni)"
    return sendCommand(1, dni.toInteger())
}

def childOff(String dni) {
    log.debug "childOff($dni)"
    return sendCommand(0, dni.toInteger())
}

def childRefresh(String dni) {
    log.debug "childRefresh($dni)"
    refresh()
}

def componentOn(cd) {
    log "${device.label?device.label:device.name}: componentOn($cd)"
    return childOn(channelNumber(cd.deviceNetworkId))
}

def componentOff(cd) {
    log "${device.label?device.label:device.name}: componentOff($cd)"
    return childOff(channelNumber(cd.deviceNetworkId))
}

def componentRefresh(cd) {
    log "${device.label?device.label:device.name}: componentRefresh($cd)"
    return childRefresh(cd.deviceNetworkId)
}

def updated() {
    log.info('Updated')
    initialize();
}

def parse(String description) {
    log "description is: $description"

    def msg = parseLanMessage(description)
    def body = parseJson(msg.body)
    if (body.payload.all) {
        def parent = body.payload.all.digest.togglex[0].onoff
        sendEvent(name: 'switch', value: parent ? 'on' : 'off', isStateChange: true)
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)

        childDevices.each {
            childDevice ->
            def channel = channelNumber(childDevice.deviceNetworkId) as Integer
            def childState = body.payload.all.digest.togglex[channel].onoff
            log "channel $channel:  $childState"
                childDevice.sendEvent(name: 'switch', value: childState ? 'on' : 'off')
        }
    } else {
        refresh()
    }
}

def log(msg) {
    if (DebugLogging) {
        log.debug(msg)
    }
}

def initialize() {
    log 'initialize()'
    if (!childDevices) {
        createChildDevices()
    }
    refresh()
    
    log 'scheduling()'
    unschedule(refresh)
    runEvery1Minute(refresh)
}

def configure() {
    log 'configure()'
    initialize()
}

private channelNumber(String dni) {
    dni.split('-ep')[-1]
}

private void createChildDevices() {
    state.oldLabel = device.label
    for (i in 1..2) {
        addChildDevice('hubitat', 'Generic Component Switch', "${device.deviceNetworkId}-ep${i}", [completedSetup: true, label: "${device.displayName} (CH${i})",
            isComponent: false, componentName: "ep$i", componentLabel: "Channel $i"
        ])
    }
}
