/**
 * Meross Smart Dimmer Plug
 *
 * Author: Todd Pike
 * Last updated: 2023-01-02
 *
 * Based on Smart Plug Mini by Daniel Tijerina with firmware update fix from coKaliszewski
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
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'Refresh'
        capability 'Configuration'

        command 'toggle'

        attribute 'level', 'number'
        attribute 'capacity', 'number'

        attribute 'model', 'string'
        attribute 'device uuid', 'string'
        attribute 'mac', 'string'
        attribute 'firmware', 'string'
        attribute 'userid', 'string'
        attribute 'modified', 'string'
    }
    preferences {
        section('Device Selection') {
            input('deviceIp', 'text', title: 'Device IP Address', description: '', required: true, defaultValue: '')
            input('key', 'text', title: 'Key', description: 'Encryption key for generating message payload', required: false, defaultValue: '')
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

def on() {
    log.info('Turning on')
    sendCommand(1, 0)
}

def off() {
    log.info('Turning off')
    sendCommand(0, 0)
}

def toggle() {
    log "toggling"

    if (device.currentValue("switch") == "on") {
        off()
    }
    else {
        on()
    }
}

def updated() {
    log.info('Updated')
    initialize()
}

def setLevel(level, duration) {
    setLevel(level)
}

def setLevel(level) {
    log.info("Setting level to ${level}")

    if (!settings.deviceIp || !settings.uuid || !settings.key) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }

    try {
        def payloadData = getPayload()

        def postBody = [
            payload: [
                light: [
                    channel: 0,
                    luminance: level,
                    capacity: 4
                ]
            ],
            header: [
                messageId: "${payloadData.get('MessageId')}",
                method: "SET",
                from: "http://${settings.deviceIp}/config",
                timestamp: payloadData.get('CurrentTime'),
                namespace: "Appliance.Control.Light",
                uuid: "${settings.uuid}",
                sign: "${payloadData.get('Sign')}",
                triggerSrc: "hubitat",
                payloadVersion: 1
            ]
        ]

        def params = [
            uri: "http://${settings.deviceIp}",
            path: "/config",
            contentType: "application/json",
            body: postBody,
            headers: [Connection: "keep-alive"]
        ]

        def callbackData = [:]
        callbackData.put(payloadData.get('MessageId'), level)
        asynchttpPost("setLevelRespons", params, callbackData)
    } catch (e) {
        log.error "setLevel hit an exception '${e}'"
    }
}

def setLevelRespons(resp, data) {
    def response = new groovy.json.JsonSlurper().parseText(resp.data)
    def level = data[response.header.messageId]

    if (resp.getStatus() != 200) {
        log.error "Received status code of '${resp.getStatus()}'. Could not set state to '${level}'."
        return
    }

    sendEvent(name: 'level', value: level, isStateChange: true)

    runInMillis(1000, 'refresh')
}

def sendCommand(int onoff, int channel) {
    if (!settings.deviceIp || !settings.uuid || !settings.key) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }

    try {
        def payloadData = getPayload()

        def postBody = [
            payload: [
                togglex: [
                    onoff: onoff,
                    channel: channel
                ]
            ],
            header: [
                messageId: "${payloadData.get('MessageId')}",
                method: "SET",
                from: "http://${settings.deviceIp}/config",
                timestamp: payloadData.get('CurrentTime'),
                namespace: "Appliance.Control.ToggleX",
                uuid: "${settings.uuid}",
                sign: "${payloadData.get('Sign')}",
                triggerSrc: "hubitat",
                payloadVersion: 1
            ]
        ]

        def params = [
            uri: "http://${settings.deviceIp}",
            path: "/config",
            contentType: "application/json",
            body: postBody,
            headers: [Connection: "keep-alive"]
        ]

        def callbackData = [:]
        callbackData.put(payloadData.get('MessageId'), onoff)
        asynchttpPost("onoffResponse", params, callbackData)
    } catch (e) {
        log.error "sendCommand hit exception '${e}'"
    }
}

def onoffResponse(resp, data) {
    try {
        if (resp?.data?.trim()) {
            def response = new groovy.json.JsonSlurper().parseText(resp.data)
            def onoff = data[response.header.messageId]

            def state = onoff ? 'on' : 'off'
            if (resp.getStatus() != 200) {
                log.error "Received status code of '${resp.getStatus()}'. Could not set state to '${state}'."
                return
            }

            sendEvent(name: 'switch', value: state, isStateChange: true)
        }
    }
    catch (Exception e) {
        if (DebugLogging) {
            log.error "Error in response: '${e}'"
        }
    }

    runInMillis(500, 'refresh')
}

def refresh() {
    if (!settings.deviceIp || !settings.uuid || !settings.key) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log 'missing setting configuration'
        return
    }

    try {
        def payloadData = getPayload()

        def postBody = [
            payload: [ ],
            header: [
                messageId: "${payloadData.get('MessageId')}",
                method: "GET",
                from: "http://${settings.deviceIp}/config",
                timestamp: payloadData.get('CurrentTime'),
                namespace: "Appliance.System.All",
                sign: "${payloadData.get('Sign')}",
                triggerSrc: "hubitat",
                payloadVersion: 1
            ]
        ]

        def params = [
            uri: "http://${settings.deviceIp}",
            path: "/config",
            contentType: "application/json",
            body: postBody,
            headers: [Connection: "keep-alive"]
        ]

        def callbackData = [:]
        callbackData.put("messageId", payloadData.get('MessageId'))
        asynchttpPost("refreshResponse", params, callbackData)
    } catch (Exception e) {
        log.error "refresh hit exception '${e}'"
    }
}

def refreshResponse(resp, data) {
    try {
        if (resp?.data?.trim()) {
            def response = new groovy.json.JsonSlurper().parseText(resp.data)
            def messageId = response.header.messageId
            def callbackId = data["messageId"]

            if (messageId != callbackId) {
                log.error "MessageId in refresh callback, '${callbackId}', does not match request, '${messageId}'. Skipping parse of refresh response."
                return
            }

            parse(resp.data)
        }
    }
    catch (Exception e) {
        if (DebugLogging) {
            log.error "Error in response: '${e}'"
        }
    }
}

def parse(String description) {
    def msg = new groovy.json.JsonSlurper().parseText(description)

    if (msg.header.method == "SETACK") return

    if (msg.payload.all) {
        def system = msg.payload.all.system
        def hardware = system.hardware
        sendEventOnChange('model', hardware.type)
        sendEventOnChange('device uuid', hardware.uuid)
        sendEventOnChange('mac', hardware.macAddress)

        def firmware = system.firmware
        sendEventOnChange('firmware', firmware.version)
        sendEventOnChange('userId', firmware.userId)

        def digest = msg.payload.all.digest
        def light = digest.light
        sendEventOnChange('level', light.luminance)
        sendEventOnChange('capacity', light.capacity)

        def status = digest.togglex[0]
        sendEventOnChange('modified', status.lmTime)
        sendEventOnChange('switch', status.onoff ? 'on' : 'off')
    } else {
        log.error ("Request failed")
    }
}

def sendEventOnChange(String name, value) {
    def current = getDataValue(name)
    if (current != value) {
        sendEvent(name: name, value: value, isStateChange: true)
    }

    updateDataValue(name, (value as String))
}

def getPayload(int stringLength = 16) {

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
