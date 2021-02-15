/**
 * Meross Smart Plug Mini
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
        name: 'Meross Smart Plug Mini',
        namespace: 'ithinkdancan',
        author: 'Daniel Tijerina'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Configuration'
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
        log hubAction
        return hubAction
    } catch (e) {
        log "runCmd hit exception ${e} on ${hubAction}"
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
        log "runCmd hit exception ${e} on ${hubAction}"
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

def updated() {
    log.info('Updated')
    initialize()
}

def parse(String description) {
    log "description is: $description"

    def msg = parseLanMessage(description)
    def body = parseJson(msg.body)
    if (body.payload.all) {
        def parent = body.payload.all.digest.togglex[0].onoff
        sendEvent(name: 'switch', value: parent ? 'on' : 'off', isStateChange: true)
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)
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
    refresh()

    log 'scheduling()'
    unschedule(refresh)
    runEvery1Minute(refresh)
}

def configure() {
    log 'configure()'
    initialize()
}
