### v1.1.5
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

### v1.1.4
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
{"data":[{"{#METRICINSTANCE}":"scsi2:2"},{"{#METRICINSTANCE}":"scsi2:1"},{"{#METRICINSTANCE}":"scsi2:0"},{"{#METRICINSTANCE}":"scsi2:6"},{"{#METRICINSTANCE}":"scsi2:5"},{"{#METRICINSTANCE}":"scsi2:4"},{"{#METRICINSTANCE}":"scsi2:3"},{"{#METRICINSTANCE}":"scsi2:8"},{"{#METRICINSTANCE}":"scsi0:0"},{"{#METRICINSTANCE}":"scsi1:0"},{"{#METRICINSTANCE}":"scsi1:1"},{"{#METRICINSTANCE}":"scsi1:2"},{"{#METRICINSTANCE}":"scsi1:3"}]}
# zabbix_get -s 127.0.0.1 -p 12050 -k vm.counter[VMNAME,virtualDisk.totalReadLatency,scsi2:4,300]
6
```

