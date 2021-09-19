/**
 * Meross Smart Dimmer Plug
 *
 * Author: Todd Pike
 * Last updated: 2021-09-18
 *
 * Based on Smart Plug Mini by Daniel Tijerina
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

 import java.security.MessageDigest

metadata {
    definition(
        name: 'Meross Smart Dimmer Plug',
        namespace: 'bobted',
        author: 'Todd Pike'
    ) {
        capability 'Actuator'
        capability 'Switch'
        ////capability 'Dimmer'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Configuration'
    }
    preferences {
        section('Device Selection') {
            input('deviceIp', 'text', title: 'Device IP Address', description: '', required: true, defaultValue: '')
            input('key', 'text', title: 'Key', description: 'Required for firmware version 3.2.3 and greater', required: false, defaultValue: '')
            input('messageId', 'text', title: 'Message ID', description: '', required: true, defaultValue: '')
            input('uuid', 'text', title: 'Unique ID', description: '', required: true, defaultValue: '')
            input('timestamp', 'number', title: 'Timestamp', description: '', required: true, defaultValue: '')
            input('sign', 'text', title: 'Sign', description: '', required: true, defaultValue: '')
            input('dimmerLevel','number',title: 'Dimmer level', description:'', required: true, defaultValue: 75)
            input('DebugLogging', 'bool', title: 'Enable debug logging', defaultValue: true)
        }
    }
}

def getDriverVersion() {
    1
}

def sendCommand(int onoff, int channel) {

    def currentVersion = device.currentState('version')?.value ? device.currentState('version')?.value.replace(".","").toInteger() : 232

    log.info("currentVersion: " + currentVersion)
    log.info("settings: " + settings)
    log.info("channel: " + channel)
    log.info("onoff: " + onoff)

    // Firmware version 3.2.3 and greater require different data for request
    if (!settings.deviceIp || !settings.uuid || (currentVersion >= 323 && !settings.key) || (currentVersion < 323 && (!settings.messageId || !settings.sign || !settings.timestamp))) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }

    sendEvent(name: 'switch', value: onoff ? 'on' : 'off', isStateChange: true)

    try {
        def payloadData = currentVersion >= 323 ? getPayload() : [MessageId: settings.messageId, Sign: settings.sign, CurrentTime: settings.timestamp]

        def hubAction = new hubitat.device.HubAction([
            method: 'POST',
            path: '/config',
            headers: [
                'HOST': settings.deviceIp,
                'Content-Type': 'application/json',
            ],
            body: """
            {
                "payload": {
                    "togglex": {
                        "onoff": ${onoff},
                        "channel": ${channel}
                    }
                },
                "header": {
                    "messageId": "${payloadData.get('MessageId')}"",
                    "method": "SET",
                    "from": "http://${settings.deviceIp}/config",
                    "timestamp": ${payloadData.get('CurrentTime')},
                    "namespace": "Appliance.Control.ToggleX",
                    "uuid": "${settings.uuid}",
                    "sign": "${payloadData.get('Sign')}",
                    "triggerSrc": "iOSLocal",
                    "payloadVersion": 1
                }
            }
            """
        ])
        log hubAction
        return hubAction
    } catch (e) {
        log "runCmd hit exception ${e} on ${hubAction}"
    }
}

def refresh() {
    def currentVersion = device.currentState('version')?.value ? device.currentState('version')?.value.replace(".","").toInteger() : 232

    // Firmware version 3.2.3 and greater require different data for request
    if (!settings.deviceIp || !settings.uuid || (currentVersion >= 323 && !settings.key) || (currentVersion < 323 && (!settings.messageId || !settings.sign || !settings.timestamp))) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log 'missing setting configuration'
        return
    }

    try {
        def payloadData = currentVersion >= 323 ? getPayload() : [MessageId: settings.messageId, Sign: settings.sign, CurrentTime: settings.timestamp]

        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{},"header":{"messageId":"'+payloadData.get('MessageId')+'","method":"GET","from":"http://'+settings.deviceIp+'/subscribe","sign":"'+ payloadData.get('Sign') +'","namespace": "Appliance.System.All","triggerSrc":"iOSLocal","timestamp":' + payloadData.get('CurrentTime') + ',"payloadVersion":1}}'
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

    if (msg.status != 200) {
         log.error("Request failed")
         return
    }

    if(body.header.method == "SETACK") return

    if (body.payload.all) {
        def parent = body.payload.all.digest.togglex[0].onoff
        sendEvent(name: 'switch', value: parent ? 'on' : 'off', isStateChange: true)
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)
        sendEvent(name: 'model', value: body.payload.all.system.hardware.type, isStateChange: false)
    } else {
        //refresh()
        log.error ("Request failed")
    }
}

def getPayload(int stringLength = 16){

    // Generate a random string
    def chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
    def randomString = new Random().with { (0..stringLength).collect { chars[ nextInt(chars.length() ) ] }.join()}

    int currentTime = new Date().getTime() / 1000
    messageId = MessageDigest.getInstance("MD5").digest((randomString + currentTime.toString()).bytes).encodeHex().toString()
    sign = MessageDigest.getInstance("MD5").digest((messageId + settings.key + currentTime.toString()).bytes).encodeHex().toString()

    def requestData = [
         CurrentTime: currentTime,
         MessageId: messageId,
         Sign: sign
    ]

    return requestData
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
