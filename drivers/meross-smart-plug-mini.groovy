/**
 * Meross Smart Plug Mini
 *
 * Author: Daniel Tijerina
 * Last updated: 2021-10-06
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

import java.security.MessageDigest

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

        attribute 'model', 'string'
        attribute 'version', 'string'
    }
    preferences {
        section('Device Selection') {
            input('deviceIp', 'text', title: 'Device IP Address', description: '', required: true, defaultValue: '')
            input('key', 'text', title: 'Key', description: 'Key from login.py', required: true, defaultValue: '')
            input('DebugLogging', 'bool', title: 'Enable debug logging', defaultValue: true)
        }
    }
}

def getDriverVersion() {
    1
}

def initialize() {
    log 'Initializing Device'
    refresh()

    unschedule(refresh)
    runEvery5Minutes(refresh)
}

def sendCommand(int onoff, int channel) {
    if (!settings.deviceIp || !settings.key) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }
    try {
        def payloadData = getSign()
        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{"togglex":{"onoff":' + onoff + ',"channel":' + channel + '}},"header":{"messageId":"'+payloadData.get('MessageId')+'","method":"SET","from":"http://'+settings.deviceIp+'/config","sign":"'+payloadData.get('Sign')+'","namespace":"Appliance.Control.ToggleX","triggerSrc":"iOSLocal","timestamp":' +  payloadData.get('CurrentTime') + ',"payloadVersion":1}}'
    ])
        log hubAction
        runIn(0, "refresh")
        return hubAction
    } catch (e) {
        log "runCmd hit exception ${e} on ${hubAction}"
    }
}

def refresh() {
    log.info('Refreshing')
     if (!settings.deviceIp || !settings.key) {
        sendEvent(name: 'switch', value: 'offline', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }
    try {
        def payloadData = getSign()

        log.info('Refreshing')

        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{},"header":{"messageId":"'+payloadData.get('MessageId')+'","method":"GET","from":"http://'+settings.deviceIp+'/subscribe","sign":"'+ payloadData.get('Sign') +'","namespace": "Appliance.System.All","triggerSrc":"AndroidLocal","timestamp":' + payloadData.get('CurrentTime') + ',"payloadVersion":1}}'
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
    

    def msg = parseLanMessage(description)
    def body = parseJson(msg.body)

    if(msg.status != 200) {
         log.error("Request failed")
         return
    }

    if (body.payload.all) {
        def parent = body.payload.all.digest.togglex[0].onoff
        sendEvent(name: 'switch', value: parent ? 'on' : 'off', isStateChange: true)
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)
        sendEvent(name: 'model', value: body.payload.all.system.hardware.type, isStateChange: false)
    } else {
        log.error ("Request failed")
    }
}

def getSign(int stringLength = 16){

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

def configure() {
    log 'configure()'
    initialize()
}
