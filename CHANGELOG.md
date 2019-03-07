2.8-beta
* First attempt to support Zabbix 4+ message header (thank you *whosgonna*)

2.7
* Clear all caches on reconnect (in case of a vCenter HA failover)
* Fix pattern matching of sync script
* Added conditional Zabbix proxy assignment in sync script
* Added resource pool discovery and related cpu/memory usage items (thanks to Spuun1)

2.6
* Fixed a bug that crashed VmBix if it was started on command line without specifiying a configuration file.
* Fixed the default timeout values (set them in ms)
* Fail the daemon startup if the VMWare SDK is not available

2.5
* **BREAKING CHANGE** : all parameters in the configuration file vmbix.conf and on the command line must now be in lowercase. The possible arguments in command line have also changed. See the "usage" output.
* Improved the script vmbix-object-sync
* Hacked the vm.guest.disk.* methods with a workaround for ZBX-10590. If a disk name ends with *\*, a space will be added at the end of the disk name. This is controlled by the parameter *escapechars* in the configuration file. It is set to false by default.
* Fixed the ESX usage item in the template.
* Code cleanup and better error handling
* Added the vm.stats methods :
  - vm.stats[threads] indicates the number of working threads
  - vm.stats[queue] indicates the size of the connection queue
  - vm.stats[requests] indicates the number of requests received by VmBix
  - vmbix.stats[cachesize,(vm|esxi|ds|perf|counter|hri|cluster)] indicates the size of each cache
  - vmbix.stats[hitrate,(vm|esxi|ds|perf|counter|hri|cluster)] indicates the hit rate of each cache (1.0 = 100% hits)
* Exposed the following parameters in the configuration
  - connecttimeout : the VmWare API connect timeout
  - readtimeout : the VmWare API read timeout
  - maxconnections : the maximum number of concurrent connections accepted by Vmbix

2.4
* Fixed the UUID in the discovery of NAS datastores
* Added a vm.snapshot method that replies *1* if a VM has at least one snapshot.

2.3
* Added a datastore.local method to check if a datastore is local
* Replaced the Python import script by a new one : vmbix-object-sync. Check the [README](https://github.com/dav3860/vmbix/tree/master/zabbix/addons) for details.
* Included the *zabbix* folder into the packages. This folder is installed in /usr/share/vmbix/
* Improved the documentation

2.2
* **BREAKING CHANGE** : Refactored the performance counter methods to include the rollup type in the counter name. Instead of querying a performance
  counter like this for example :

```
zabbix_get -s localhost -p 12050 -k vm.counter[VM01,cpu.usagemhz]
```

  It now has be be queried like this :

```
zabbix_get -s localhost -p 12050 -k vm.counter[VM01,cpu.usagemhz.average]
```

  The \*.counter.list and \*.counter.discovery methods have been updated in the same way.
* The project now uses Travis-CI and Bintray to automate the building and distribute the packages.
* *Packages* for Debian/Ubuntu and RedHat/Centos are automatically created. You can get them [here](https://bintray.com/dav3860/generic/vmbix/view/files) (thank you kireevco).
* A new method vm.discovery.full[*] was added. It returns a JSON array of VMs including their power state, if you need to filter the LLD rule on the power state of the VMs.
* The methods cluster.cpu[name,usage] and cluster.mem[name,free] had issues. They have been fixed.

2.1
* Added the esx.uptime and vm.uptime methods (not always relevant)
* Added the version method to query for VmBix version
* Fixes

2.0
* Switched VMWare Java API to YAViJava 6.0.3 instead of ViJava 5.1. This adds support for VMWare API 5.5 and 6.0.
* Switched from ant/ivy to Maven.
* Added Logback for logging (and removed the verbose parameter). The logging configuration is now defined in the /etc/vmbix/logback.xml file.
* vmbix command now logs to console and vmbixd logs to file (/var/log/vmbix.log by default).
* Added support for NAS datastores (thank you Andrea).
* Slightly refactored the code.

1.2.3
* Improved caching
* Added cache config options in config file
* Added cluster checks
* Added Ivy Package Manager

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
