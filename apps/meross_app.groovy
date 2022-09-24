import groovy.json.*
import java.net.URLEncoder
import java.security.MessageDigest

def appVersion() { return "1.3.0" }

definition(
	name: "Meross Garage Door Manager",
	namespace: "ajardolino3",
	author: "Art Ardolino",
	description: "Manages the addition and removal of Meross Garage Door Devices",
	category: "Bluetooth",
	iconUrl: "",
	iconX2Url: "",
	singleInstance: true,
    importUrl: ""
)

preferences() {
    page name: "mainPage"
    page name: "addGarageDoorStep1"
    page name: "addGarageDoorStep2"
    page name: "addGarageDoorStep3"
    page name: "addGarageDoorStep4"
    page name: "listGarageDoorPage"
}

def mainPage() {
    return dynamicPage(name: "mainPage", title: "Meross Garage Door Manager", uninstall: true, install: true) {
        section(){ 
            paragraph("The Meross Garage Door Manager assists with the configration of Meross Garage Door Opener devices.")
			href "addGarageDoorStep1", title: "<b>Add New Garage Doors</b>", description: "Adds new garage door devices."
			href "listGarageDoorPage", title: "<b>List Garage Doors</b>", description: "Lists added garage door devices."
			input "debugLog", "bool", title: "Enable debug logging", submitOnChange: true, defaultValue: false
        }
    }
}

def listGarageDoorPage() {
    def devices = getChildDevices()
    def message = ""
    devices.each{ device ->
        message += "\t${device.label} (ID: ${device.getDeviceNetworkId()})\n"
    }
    return dynamicPage(name: "listGarageDoorPage", title: "List Garage Doors", install: false, nextPage: mainPage) {
        section() {
            paragraph "The following devices were added using the 'Add New Garage Doors' feature."
            paragraph message
        }
    }
}

def addGarageDoorStep1() {
	def newBeacons = [:]
	state.beacons.each { beacon ->
		def isChild = getChildDevice(beacon.value.dni)
		if (!isChild && beacon.value.present) {
            newBeacons["${beacon.value.dni}"] = "${beacon.value.type}: ${beacon.value.dni}"
		}
	}
    
    return dynamicPage(name: "addGarageDoorStep1", title: "Add New Garage Doors (Step 1)", install: false, nextPage: addGarageDoorStep2) {
        section(){
            input "merossUsername", "string", required: true, title: "Enter your Meross username"
            input "merossPassword", "password", required: true, title: "Enter your Meross password"
            input "merossIP", "string", required: true, title: "Enter the IP address of your Meross device"
        }
    }
}

def addGarageDoorStep2() {
    def response = loginMeross(merossUsername,merossPassword)
    if(response.code == 200) {
        state.data = getMerossData(response.token)
        state.merossKey = response.key
        def devices = [:]
        state.data.each{ device ->
            devices["${device.uuid}"] = device.devName
        }
        return dynamicPage(name: "addGarageDoorStep2", title: "Add New Garage Doors (Step 2)", install: false, nextPage: addGarageDoorStep3) {
            section(){
    			input ("selectedDevice", "enum",
				   required: true,
				   multiple: false,
				   title: "Select a device to add (${devices.size() ?: 0} devices detected)",
				   description: "Use the dropdown to select a device.",
                   options: devices)
            }
        }
    }
    else {
        return dynamicPage(name: "addGarageDoorStep2", title: "Login Failed", install: false, nextPage: mainPage) {
        section(){
            paragraph response.error
            }
        }
    }
}

def addGarageDoorStep3() {
    def doors = [:]
    state.data.each { device ->
        if(device.uuid == selectedDevice) {
            for(i=1; i<device.channels.size(); i++){
                def dni = selectedDevice + ":${i}"
                def isChild = getChildDevice(dni)
                if(!isChild) {
                    doors["${i}"] = device.channels[i].devName
                }
            }
        }
    }
    return dynamicPage(name: "addGarageDoorStep3", title: "Add New Garage Doors (Step 3)", install: false, nextPage: addGarageDoorStep4) {
        section(){
            input ("selectedDoors", "enum",
                   required: true,
                   multiple: true,
                   title: "Select one or more garage doors to add (${doors.size() ?: 0} new doors detected)",
                   description: "Use the dropdown to select the door(s).",
                   options: doors)
        }
    }
}

def addGarageDoorStep4() {
    
    def doors = [:]
    state.data.each { device ->
        if(device.uuid == selectedDevice) {
            for(i=1; i<device.channels.size(); i++){
                doors["${i}"] = device.channels[i]
            }
        }
    }
    
    def status = []
    def message = ""
    selectedDoors.each{ door_index -> 
        def door = doors[door_index]
        logDebug("index: " + door_index + ", door:" + door)
        def dni = selectedDevice + ":" + door_index
        def isChild = getChildDevice(dni)
        def success = false
        def err = ""
        if (!isChild) {
            try {
                isChild = addChildDevice("ithinkdancan", "Meross Smart WiFi Garage Door Opener", dni, ["label": door.devName])
                isChild.updateSetting("deviceIp", merossIP)
                isChild.updateSetting("channel", Integer.parseInt(door_index))
                isChild.updateSetting("uuid", selectedDevice)
                isChild.updateSetting("key", state.merossKey)
                isChild.updateSetting("messageId", "N/A")
                isChild.updateSetting("sign", "N/A")
                isChild.updateSetting("timestamp", 0)
                success = true
            }
            catch(exception) {
                err = exception
            }
        }
        if(success) {
            message += "New door added successfully (" + door.devName + ").<br/>"
        } else {
            message += "Unable to add door: " + err + "<br/>";
        }
    }
	app?.removeSetting("selectedDevice")
	app?.removeSetting("selectedDoors")
    
	return dynamicPage(name:"addGarageDoorStep4",
					   title: "Add Garage Door Status",
					   nextPage: mainPage,
					   install: false) {
	 	section() {
            paragraph message
		}
	}
}

def installed() {
    // called when app is installed
}

def updated() {
    // called when settings are updated
}

def uninstalled() {
    // called when app is uninstalled
}

def generator(alphabet,n) {
  return new Random().with {
    (1..n).collect { alphabet[ nextInt( alphabet.length() ) ] }.join()
  }
}

def getMerossData(token) {
    def nonce = generator( (('A'..'Z')+('0'..'9')).join(), 16 )    
    def unix_time = (Integer)Math.floor(new Date().getTime() / 1000);

    def param = "e30=";
    def encoded_param = param;

    def concat_sign = ["23x17ahWarFH6w29", unix_time, nonce, encoded_param].join("")
    MessageDigest digest = MessageDigest.getInstance('MD5')
    digest.update(concat_sign.bytes, 0, concat_sign.length())
    def sign = new BigInteger(1, digest.digest()).toString(16)

    def data = [:]
    data.params = encoded_param
    data.sign = sign
    data.timestamp = unix_time
    data.nonce = nonce
    def json = JsonOutput.toJson(data)
    
    def commandParams = [
		uri: "https://iot.meross.com/v1/Device/devList",
		contentType: "application/json",
		requestContentType: 'application/json',
        headers: ['Authorization':'Basic ' + token],
		body : data
	]
	def respData
	try {
		httpPostJson(commandParams) {resp ->
            if (resp.status == 200) {
                logDebug("meross data: " + resp.data)
                respData = resp.data["data"]
                logDebug("meross data: " + respData)
                logDebug("meross data: " + respData[0]["uuid"])
			} else {
				respData = [code: resp.status, error: "HTTP Protocol Error"]
			}
		}
	} catch (e) {
		def msg = "Error = ${e}\n\n"
		respData = [code: 9999, error: e]
	}
    
    return respData
}

def loginMeross(email, password) {
    def nonce = generator( (('A'..'Z')+('0'..'9')).join(), 16 )    
    def unix_time = (Integer)Math.floor(new Date().getTime() / 1000);

    def param = [:]
    param.email = email
    param.password = password
    def json = JsonOutput.toJson(param)
    def encoded_param = json.bytes.encodeBase64().toString();

    def concat_sign = ["23x17ahWarFH6w29", unix_time, nonce, encoded_param].join("")
    MessageDigest digest = MessageDigest.getInstance('MD5')
    digest.update(concat_sign.bytes, 0, concat_sign.length())
    def sign = new BigInteger(1, digest.digest()).toString(16)
  
    def formBody = "params=${encoded_param}&sign=${sign}&timestamp=${unix_time}&nonce=${nonce}"
      
	def commandParams = [
		uri: "https://iot.meross.com/v1/Auth/login",
		contentType: 'application/x-www-form-urlencoded',
		body : formBody
	]
	def respData
	try {
		httpPost(commandParams) {resp ->
            if (resp.status == 200) {
                respData = resp.data.toString()
                def retobj = [:]
                retobj.code = 200
                retobj.token = ""
                retobj.key = ""
                logDebug("respData:" + respData)
                if(respData.indexOf('"token"')>0)
                {
                    retobj.token = respData.substring(respData.indexOf('"token"')+9)
                    retobj.token = retobj.token.substring(0,retobj.token.indexOf('"'))
                    logDebug("token:" + retobj.token)
                }
                if(respData.indexOf('"key"')>0)
                {
                    retobj.key = respData.substring(respData.indexOf('"key"')+7)
                    retobj.key = retobj.key.substring(0,retobj.key.indexOf('"'))
                    logDebug("key:" + retobj.key)
                }
                if(retobj.token.length()==0 || retobj.key.length()==0)
                {
                    retobj.code = 9999
                    retobj.error = "Invalid username/password"
                }
                respData = retobj
			} else {
				respData = [code: resp.status, error: "HTTP Protocol Error"]
			}
		}
	} catch (e) {
		def msg = "Error = ${e}\n\n"
		respData = [code: 9999, error: e]
	}
    
    return respData
}

def logDebug(msg) {
    if(debugLog) log.debug(msg)
}
