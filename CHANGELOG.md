1.2.2
* Improved the processing of performance counter values. Percents are now displayed as rounded percents (ie 28 for 28.33% instead of 2833).
* Fixed remaining references to vmbixget.py in the templates.

1.2.1
* Removed debug.
* Cosmetics.
* Replaced the python script method by a [Zabbix loadable module](https://github.com/dav3860/vmbix_zabbix_module).

1.2
* Added the UUID to the JSON formatted output for the esx.discovery method. 
* Added the UUID to the JSON formatted output for the vm.discovery method.
* VMs and ESX hosts can now be queried by UUID instead of the name. The useuuid parameter must be set to "true" in the configuration file. Example :

```
zabbix_get -s localhost -p 12050 -k esx.status["32333536-3030-5a43-3244-3238314c319b"]
1
```

* Added caching of the virtual machines, ESX hosts and performance counters objects.
* Added the methods vm.name and esx.name to query the display names when using UUID and the primary identifier.
* Added the method vm.guest.os.short to get the OS family.
* Added the method vm.annotation to get the annotations.
* Do not print 0 when we cannot query VM disks.
* Decreased verbosity.

1.1.8
* Use the toolsVersionStatus2 instead of toolsVersionStatus property to get the VM Tools version status of a virtual machine. Needs VSphere 5.x.
* The status codes now are :
```
vm.guest.tools.version :
0 -> guestToolsNotInstalled
1 -> guestToolsCurrent
2 -> guestToolsNeedUpgrade
3 -> guestToolsUnmanaged
4 -> guestToolsBlacklisted
5 -> guestToolsSupportedNew
6 -> guestToolsSupportedOld
7 -> guestToolsTooNew
8 -> guestToolsTooOld
9 -> unknown
```

1.1.7
* Improved the perf counter methods performance
* Added a python script using Zabbix_API (https://github.com/gescheit/scripts/tree/master/zabbix) to create the VMs in Zabbix without host prototypes

1.1.6
* Updated the templates.

1.1.5
* The status codes for the vm.guest.tools.version and vm.guest.tools.running methods are now numeric :

```
vm.guest.tools.version :
0 -> guestToolsNotInstalled
1 -> guestToolsCurrent
2 -> guestToolsNeedUpgrade
3 -> guestToolsUnmanaged
4 -> unknown

vm.guest.tools.running :
0 -> guestToolsNotRunning
1 -> guestToolsRunning
2 -> guestToolsExecutingScripts
3 -> unknown
```
You may create mapping tables in Zabbix for this purpose.

1.1.4
* Better error handling for performance counters
* The counter[name] method was removed. You can now get the list of the available performance counters with these new methods :
```
esx.counter.list[name]
vm.counter.list[name]
```
* Two new methods to get the instance list of a specific performance counter. The output is JSON-formatted for Zabbix Low-Level Discovery :
```
esx.counter.discovery[name,counter,[interval]]
vm.counter.discovery[name,counter,[interval]]
```
For example, to get the read latency on a VM vDisks :
```
# zabbix_get -s 127.0.0.1 -p 12050 -k vm.counter.discovery[VMNAME,virtualDisk.totalReadLatency]
{"data":[{"{#METRICINSTANCE}":"scsi2:2"},{"{#METRICINSTANCE}":"scsi2:1"},{"{#METRICINSTANCE}":"scsi2:0"},{"{#METRICINSTANCE}":"scsi2:6"},{"{#METRICINSTANCE}":"scsi2:5"},{"{#METRICINSTANCE}":"scsi2:4"},{"{#M$
# zabbix_get -s 127.0.0.1 -p 12050 -k vm.counter[VMNAME,virtualDisk.totalReadLatency,scsi2:4,300]
6
```
1.1.1
* Added performance counters for ESX hosts.
* Added new methods for datastores (danrog):
```
 datastore.size[name,provisioned]
 datastore.size[name,uncommitted]
```
vmbix now displays a version number when called without arguments.
1.1.0
* Fixed the unnecessary carriage return in the output
* Added Zabbix Low-Level Discovery methods to automatically create datastores, hosts, virtual machines, and VM disks. A JSON-formatted output is displayed (using Google GSON), ex:
```
 # zabbix_get -s localhost -p 12050 -k esx.discovery[*]
 {"data":[{"{#ESXHOST}":"esx0.domain.local"},{"{#ESXHOST}":"esx1.domain.local"}]}
```
* Added several items:
  * VMWare Performance Manager counters:
```
  counters[name]: list the available counters for an entity (VM, host, datastore)
  vm.counter[name,counter,[instance,interval]]: displays the value of the counter with optional interval/instance
```
  The method outputs an aggregated sum or average of real-time values, ex:
```
  # zabbix_get -s localhost -p 12050 -k vm.counter[VMNAME,cpu.ready,,200] 
  491
```
  An additional interval parameter was added to the configuration file to specify the default interval for the performance counter queries.
  * VM Tools status methods
  * ping : always returns 1, to check vmbix availability
  * about : display vCenter SDK version
  * guest IP/hostname methods
  * etc

* The status/powerstate methods now return an integer value instead of a string (ex: "poweredOff"). This is better to store integers than strings in Zabbix and allows for graphing. Typically :
```
 Running State:
 0 -> poweredOff
 1 -> poweredOn
 2 -> suspended
 3 -> unknown 

 Status:
 0 -> grey
 1 -> green
 2 -> yellow
 3 -> red
 4 -> unknown
```
* The Zabbix templates haven't been updated yet.

1.0.1
* Fixed host used memory checks(returned cpu used MHz instead of memory used MB), fixed a custom multiplier for the same item.
* Added several items:
  * Average private memory usage in % for all powered on vms.
  * Average shared memory usage in % for all powered on vms.
  * Average swapped memory usage in % for all powered on vms.
  * Average compressed memory usage in % for all powered on vms.
  * Average overheadConsumed memory usage in % for all powered on vms.
  * Average consumed memory usage in % for all powered on vms.
  * Average balooned memory usage in % for all powered on vms.
  * Average active memory usage in % for all powered on vms.

1.0.0
First release


