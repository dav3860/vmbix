#!/usr/bin/env python
#
# vmbix-discovery.py : discovery and provisioning of VMWare VMs into Zabbix
#
#

import sys
import json
import logging
from zget import ZGet
from zabbix_api import ZabbixAPI # see https://github.com/gescheit/scripts/tree/master/zabbix

# Constants
ZBXURL = "http://127.0.0.1/zabbix/api_jsonrpc.php" # Zabbix server API URL
ZBXUSER = "apiuser" # Zabbix user to access the API
ZBXPASSWORD = "test123" # Zabbix user password to access the API
VMBIXHOST = "localhost" # VmBix host
VMBIXPORT = 12050 # VmBix port
VMGROUP = [ "Virtual machines" ] # array of Zabbix groups where to put the discovered hosts
VMTEMPLATE = [ "Template VmBix VM" ] # array of Zabbix templates where to link with the discovered hosts
LOGFILE = "/var/log/zabbix/vmbix-discovery.log"

#### MAIN ####
#logging.basicConfig(stream=sys.stdout,
logging.basicConfig(filename=LOGFILE,
                      format='%(asctime)s - %(levelname)s - vmbix-discovery[%(process)s]: %(message)s',
                      level=logging.WARN)
                      

logging.warn("*** Starting discovery ***")
                      
# Get VM list from VMBIX
zg = ZGet(host = VMBIXHOST, port = VMBIXPORT)
data = zg.get("vm.discovery[*]")
try:
  result = json.loads(data)
#  result = json.dumps(result, indent=4)
except:
  print "Cannot decode VMBIX response"
  logging.error("Cannot decode VMBIX response")
  exit(1)
result = result["data"]

# Connection to Zabbix API
zapi = ZabbixAPI(server = ZBXURL, path = "")
zapi.login(ZBXUSER, ZBXPASSWORD)

# Get Zabbix data
if zapi.api_version() <= 1.4:
  logging.error('Example script works only with API 1.4 or higher.')
  exit(1)
hosts = zapi.host.get({"output": "extend", "selectGroups": ["name"], "selectParentTemplates": ["name"]})

try:
  get = zapi.hostgroup.get({
    "output": "extend",
    "filter": {
        "name": VMGROUP
    }
  })
  groupids = [{"groupid": x['groupid']} for x in get]
  logging.info("Groups : %s" %(groupids))
  #groupid = get[0]['groupid']
except:
  logging.error("Cannot resolve VM group")

try:
  get = zapi.template.get({
    "output": "extend", 
    "filter": { 
    "host": VMTEMPLATE
    }
  })
  templateids = [{"templateid": x['templateid']} for x in get]
  logging.info("Templates : %s" % (templateids))
  #templateid = get[0]['templateid']
except:
  logging.error("Cannot resolve Template name")
    
# Loop through discovered VMs
created = 0
updated = 0
for eachItem in result:
  vm = eachItem["{#VIRTUALMACHINE}"]
  try:
    state = zg.get("vm.powerstate[" + vm + "]")
    if state == '1':
      hostname = zg.get("vm.guest.name[" + vm + "]")
      ipaddress = zg.get("vm.guest.ip[" + vm + "]")
      if hostname and hostname != "null" and ipaddress and ipaddress != "null":
        if zapi.host.exists({"host": [vm]}):
          logging.info("Updating VM " + vm +" with DNS name")
          hostid = zapi.host.get({"filter":{"host": [vm]}})[0]['hostid']
          interfaceid = zapi.hostinterface.get({"filter":{"hostid": [hostid],"type": 1, "main": 1}})[0]['interfaceid'],
          zapi.hostinterface.update({ 
            "interfaceid": (interfaceid[0]),
            "type": 1,
            "main": 1,          
            "useip": 0,
            "dns": (hostname),
            "ip": (ipaddress),
            "port": 10050
          })
          updated = updated + 1
        else:
          logging.info("Creating VM " + vm +" with DNS name")
          zapi.host.create({ 
            "host": (vm),
            "interfaces" : [{
              "type": 1,
              "main": 1,
              "useip": 0,
              "dns": (hostname),
              "ip": (ipaddress),
              "port": 10050
            }],      
            "groups": groupids,
            "templates": templateids
          })
          created = created + 1
      else:
        logging.info("Creating VM " + vm +" without DNS name")
        if not zapi.host.exists({"host": [vm]}):
          zapi.host.create({ 
            "host": (vm),
            "interfaces" : [{
              "type": 1,
              "main": 1,
              "useip": 0,
              "dns": (vm),
              "ip": "127.0.0.1",
              "port": 10050
            }],         
            "groups": groupids,
            "templates": templateids
          })
          created = created + 1
    else:
      logging.info("VM: " + vm + " is not powered-on")
  except:
    logging.info("Cannot handle VM : " + vm)
    
logging.warn("Discovered : %s - Created : %s - Updated : %s" % (len(result), created, updated))
logging.warn("*** End of discovery ***")


