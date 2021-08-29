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

import java.security.MessageDigest

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
            input('key', 'text', title: 'Key', description: 'Required for firmware version 3.2.3 and greater', required: false, defaultValue: '')
            input('messageId', 'text', title: 'Message ID', description: '', required: true, defaultValue: '')
            input('timestamp', 'number', title: 'Timestamp', description: '', required: true, defaultValue: '')
            input('sign', 'text', title: 'Sign', description: '', required: true, defaultValue: '')
            input('uuid', 'text', title: 'UUID', description: '', required: true, defaultValue: '')
            input('uuid', 'text', title: 'UUID', description: '', required: true, defaultValue: '')
            input('channel', 'number', title: 'Garage Door Port', description: '', required: true, defaultValue: 1)
            input('garageOpenCloseTime','number',title: 'Garage Open/Close time (in seconds)', description:'', required: true, defaultValue: 5)
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

def sendCommand(int open) {
    
    def currentVersion = device.currentState('version')?.value ? device.currentState('version')?.value.replace(".","").toInteger() : 0

    // Firmware version 3.2.3 and greater require different data for request
    if (!settings.deviceIp || !settings.uuid || (currentVersion >= 323 && !settings.key) || (currentVersion < 323 && (!settings.messageId || !settings.sign || !settings.timestamp))) {
        sendEvent(name: 'door', value: 'unknown', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }
    sendEvent(name: 'door', value: open ? 'opening' : 'closing', isStateChange: true)

    try {
        def payloadData = currentVersion >= 323 ? getSign() : [MessageId: settings.messageId, Sign: settings.sign, CurrentTime: settings.timestamp]
        
        def hubAction = new hubitat.device.HubAction([
        method: 'POST',
        path: '/config',
        headers: [
            'HOST': settings.deviceIp,
            'Content-Type': 'application/json',
        ],
        body: '{"payload":{"state":{"open":' + open + ',"channel":' + settings.channel + ',"uuid":"' + settings.uuid + '"}},"header":{"messageId":"'+payloadData.get('MessageId')+'","method":"SET","from":"http://'+settings.deviceIp+'/config","sign":"'+payloadData.get('Sign')+'","namespace":"Appliance.GarageDoor.State","triggerSrc":"AndroidLocal","timestamp":' + payloadData.get('CurrentTime') + ',"payloadVersion":1' + ',"uuid":"' + settings.uuid + '"}}'
    ])
        runIn(settings.garageOpenCloseTime, "refresh")
        return hubAction
    } catch (e) {
        log.error("runCmd hit exception ${e} on ${hubAction}")
    }
}


def refresh() {
    def currentVersion = device.currentState('version')?.value ? device.currentState('version')?.value.replace(".","").toInteger() : 0

    // Firmware version 3.2.3 and greater require different data for request
    if (!settings.deviceIp || !settings.uuid || (currentVersion >= 323 && !settings.key) || (currentVersion < 323 && (!settings.messageId || !settings.sign || !settings.timestamp))) {
        sendEvent(name: 'door', value: 'unknown', isStateChange: false)
        log.warn('missing setting configuration')
        return
    }
    try {
        def payloadData = currentVersion >= 323 ? getSign() : [MessageId: settings.messageId, Sign: settings.sign, CurrentTime: settings.timestamp]

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
        log hubAction
        return hubAction
    } catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
    }
}

def open() {
    log.info('Opening Garage')
    return sendCommand(1)
}

def close() {
    log.info('Closing Garage')
    return sendCommand(0)
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
    
    // Close/Open request was sent
    if(body.header.method == "SETACK") return
    
    if (body.payload.all) {
        def state = body.payload.all.digest.garageDoor[settings.channel.intValue() - 1].open
        sendEvent(name: 'door', value: state ? 'open' : 'closed')
        sendEvent(name: 'contact', value: state ? 'open' : 'closed')
        sendEvent(name: 'version', value: body.payload.all.system.firmware.version, isStateChange: false)
        sendEvent(name: 'model', value: body.payload.all.system.hardware.type, isStateChange: false)
    } else {
        //refresh()
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