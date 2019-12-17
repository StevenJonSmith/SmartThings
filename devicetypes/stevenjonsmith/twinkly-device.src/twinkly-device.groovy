/**
 *
 *  Twinkly Device Handler
 *
 *  Copyright 2019 Steven Jon Smith
 *
 *  Please read carefully the following terms and conditions and any accompanying documentation
 *  before you download and/or use this software and associated documentation files (the "Software").
 *
 *  The authors hereby grant you a non-exclusive, non-transferable, free of charge right to copy,
 *  modify, merge, publish, distribute, and sublicense the Software for the sole purpose of performing
 *  non-commercial scientific research, non-commercial education, or non-commercial artistic projects.
 *
 *  Any other use, in particular any use for commercial purposes, is prohibited. This includes, without
 *  limitation, incorporation in a commercial product, use in a commercial service, or production of other
 *  artefacts for commercial purposes.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 *  OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  You understand and agree that the authors are under no obligation to provide either maintenance services,
 *  update services, notices of latent defects, or corrections of defects with regard to the Software. The authors
 *  nevertheless reserve the right to update, modify, or discontinue the Software at any time.
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions
 *  of the Software. You agree to cite the Steven Jon Smith in your notices.
 *
 */

metadata {
	definition (name: "Twinkly Device", namespace: "StevenJonSmith", author: "Steven Jon Smith") 
    {
        capability "Color Control"
        capability "Polling"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        
        attribute "authToken", "string"
        attribute "authTimeout", "number"
	}
    
    preferences {
        input("deviceIP", "string", title:"Device IP Address", description: "Device's IP address", required: true, displayDuringSetup: true)
	}

	simulator 
    {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2)
    {
    	multiAttributeTile(name:"main", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
        	tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.Seasonal Winter.seasonal-winter-011", backgroundColor:"#00a0dc", nextState:"off"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.Seasonal Winter.seasonal-winter-011", backgroundColor:"#ffffff", nextState:"on"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
		}
        
        standardTile("refresh", "", width: 2, height: 2, decoration: "flat") {
            state "off", label: 'Refresh', action: "poll", icon: "", backgroundColor: "#ffffff"
        }
        
        main (["main"])
		details(["main", "refresh"])
	}
}

def installed()
{
 	unschedule()
 	runEvery5Minutes(poll)
 	runIn(2, poll)
}

def updated()
{
 	unschedule()
 	runEvery5Minutes(poll)
 	runIn(2, poll)
}

def poll()
{
	login()
    sendHubCommand(createAction("query", "led/mode"))
    sendHubCommand(createAction("query", "led/out/brightness"))
}

def on()
{
	log.debug "Executing 'On'"
	login()
    sendHubCommand(createAction("action", "led/mode", "{\"mode\":\"movie\"}"))
	pause(500)
    poll()
}

def off()
{
	log.debug "Executing 'Off'"
	login()
    sendHubCommand(createAction("action", "led/mode", "{\"mode\":\"off\"}"))
	pause(500)
    poll()
}

def setLevel(brightness) {
	log.debug "Executing 'setLevel'"
	login()
    sendHubCommand(createAction("action", "led/out/brightness", "{\"value\":${brightness},\"type\":\"A\",\"mode\":\"enabled\"}"))
}

def login() {
	sendHubCommand(createAction("auth"))
    pause(1000)
    sendHubCommand(createAction("verify"))
    pause(500)
}

def reset() {
	sendHubCommand(createAction("action", "led/reset"))
}

def createAction(String cmd, String endpoint = null, body = null) {
    def path = "/xled/v1/"
    def httpRequest = [
        headers: [
			HOST: "$deviceIP:80"
		]
	]
    
    if (cmd == "query") {
    	httpRequest.put('method', "GET")
    } else {
		httpRequest.put('method', "POST")
    }
    
    if (cmd == "auth") {
    	def challenge = generateChallenge()
        path = path + "login"
        httpRequest.put('path', path)
        httpRequest.put('body', "{\"challenge\":\"$challenge\"}")
    } else if (cmd == "verify") {
    	path = path + "verify"
        httpRequest.put('path', path)
        httpRequest['headers'].put("X-Auth-Token", device.currentValue("authToken"))
    } else if (cmd == "action") {
    	path = "$path$endpoint"
        httpRequest.put('path', path)
        httpRequest['headers'].put("X-Auth-Token", device.currentValue("authToken"))
        httpRequest.put('body', "$body")
    } else if (cmd == "query") {
    	path = "$path$endpoint"
        httpRequest.put('path', path)
        httpRequest['headers'].put("X-Auth-Token", device.currentValue("authToken"))
    }
    
    try 
    {
    	def hubAction = new physicalgraph.device.HubAction(httpRequest, device.deviceNetworkId, [callback: parse])
        log.debug "Created action: $hubAction"
        return hubAction
    }
    catch (Exception e) 
    {
		log.debug "Hit Exception $e on $hubAction"
	}
}

def parse(output) {
	log.debug "Starting response parsing on ${output}"

	def headers = ""
	def parsedHeaders = ""
    
    def msg = output

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body   // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

	log.debug "headers: ${headerMap}, status: ${status}, body: ${body}, data: ${data}"
    
    body = new groovy.json.JsonSlurper().parseText(body)
    log.debug "$body"
    
    if (status == 200) {
    	if (body.containsKey("authentication_token")) {
   			def token = null
            token = body['authentication_token']

            if (token != null && token != "") {
                log.debug "Auth Token: $token"
                sendEvent(name:'authToken', value:token, displayed:false)
    		}
        }
        if (body.containsKey("mode")) {
        	if (body['mode'] == "off" || body['mode'] == "disabled") {
            	sendEvent(name:'switch', value:"off", displayed:true)
            } else {
            	sendEvent(name:'switch', value:"on", displayed:true)
            }
        }
        if (body.containsKey("value")) {
            sendEvent(name:'level', value:body['value'], displayed:true)
        }
    } else {
    	log.debug "Unable to locate device on your network"
    }
}

private def generateChallenge() {
	def pool = ['a'..'z','A'..'Z',0..9].flatten()
    def rand = new Random()
    def challenge = ""
    
    for (def i = 0; i < 32; i++) {
    	challenge = challenge + pool[rand.nextInt(pool.size())]
	}
    log.debug "Created challenge: $challenge"
    
    challenge = challenge.bytes.encodeBase64()
    log.debug "Encoded challenge: $challenge"

	return challenge
}

private def pause(millis) {
   def passed = 0
   def now = new Date().time

   if ( millis < 18000 ) {
       while ( passed < millis ) {
           passed = new Date().time - now
       }
   }
}