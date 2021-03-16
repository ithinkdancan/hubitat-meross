/**
 * Meross Smart WiFi Garage Door Opener
 *
 * Author: Daniel Tijerina
 * Last updated: 2021-03-16
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
        name: 'Meross Smart WiFi Garage Door Opener',
        namespace: 'ithinkdancan',
        author: 'Daniel Tijerina'
    ) {
        capability 'DoorControl'
        capability 'GarageDoorControl'
        capability 'Actuator'
        capability 'ContactSensor'
        capability 'Refresh'

        attribute 'model', 'string'
        attribute 'version', 'string'
    }
    preferences {
        section('Device Selection') {
            input('deviceIp', 'text', title: 'Device IP Address', description: '', required: true, defaultValue: '')
            input('messageId', 'text', title: 'Message ID', description: '', required: true, defaultValue: '')
            input('timestamp', 'number', title: 'Timestamp', description: '', required: true, defaultValue: '')
            input('sign', 'text', title: 'Sign', description: '', required: true, defaultValue: '')
            input('uuid', 'text', title: 'UUID', description: '', required: true, defaultValue: '')
            input('channel', 'number', title: 'Garage Door Port', description: '', required: true, defaultValue: 1)
            input('DebugLogging', 'bool', title: 'Enable debug logging', defaultValue: true)
        }
    }
}

def getDriverVersion() {
    1
}

def sendCommand(int open) {
    if (!settings.messageId || !settings.deviceIp || !settings.sign || !settings.timestamp) {
        sendEvent(name: 'door', value: 'unknown', isStateChange: false)
        log 'missing setting configuration'
        return
    }

    sendEvent(name: 'door', value: open ? 'opening' : 'closing', isStateChange: true)

    try {
        log 'do sendCommand'
        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{"state":{"open":' + open + ',"channel":' + settings.channel + ',"uuid":"' + settings.uuid + '"}},"header":{"messageId":"'+settings.messageId+'","method":"SET","from":"http://'+settings.deviceIp+'/config","sign":"'+settings.sign+'","namespace":"Appliance.GarageDoor.State","triggerSrc":"iOSLocal","timestamp":' + settings.timestamp + ',"payloadVersion":1}}'
    ])
        log hubAction
        // runIn(5000, refresh())
        return hubAction
    } catch (e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}

def refresh() {
    log.info('Refreshing')
    if (!settings.messageId || !settings.deviceIp || !settings.sign || !settings.timestamp) {
        sendEvent(name: 'door', value: 'unknown', isStateChange: false)
        log 'missing setting configuration'
        return
    }
    try {
        log 'do refresh'
        def hubAction = new hubitat.device.HubAction([
            method: 'POST',
            path: '/config',
            headers: [
                'HOST': settings.deviceIp,
                'Content-Type': 'application/json',
            ],
            body: '{"payload":{},"header":{"messageId":"'+settings.messageId+'","method":"GET","from":"http://'+settings.deviceIp+'/config","sign":"'+settings.sign+'","namespace": "Appliance.System.All","triggerSrc":"iOSLocal","timestamp":' + settings.timestamp + ',"payloadVersion":1}}'
        ])
        log hubAction
        return hubAction
    } catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}

def open() {
    log.info('Turning on')
    return sendCommand(1)
}

def close() {
    log.info('Turning off')
    return sendCommand(0)
}

def updated() {
    log.info('Updated')
    initialize()
}

def parse(String description) {
    log "description is: $description"

    def msg = parseLanMessage(description)
    def body = parseJson(msg.body)
    log body
    if (body.payload.all) {
        log "channel is: $settings.channel"
        def state = body.payload.all.digest.garageDoor[settings.channel.intValue() - 1].open
        sendEvent(name: 'door', value: state ? 'open' : 'closed', isStateChange: true)
        sendEvent(name: 'contact', value: state ? 'open' : 'closed', isStateChange: true)
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)
        sendEvent(name: 'model', value: body.payload.all.system.hardware.type, isStateChange: false)
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
    runEvery5Minutes(refresh)
}

