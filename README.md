# VmBix
VmBix is a multi-thread TCP server written in java, it accepts connection from zabbix server or zabbix get (supports some custom zabbix checks) and translates them to vmware api calls.

This project is a fork of the original VmBix by ihryamzik (https://code.google.com/p/vmbix/) 

## Get the latest release binaries
Check the [Releases](https://github.com/dav3860/vmbix/releases) section for the latest ZIP archive of VmBix. Extract the archive and install the files (see below).

## Or build from source
Note: you'll need to install JDK and Apache Ant to follow this article. Sources could also be compiled manually, without ant.

### Download source code
```
git clone https://github.com/dav3860/vmbix.git
```

### Compile
```
cd vmbix
ant
```
Ant should download required jargs, gson and vijava libraries, compile the sources and place compiled code to VmBix folder.

## Installation
### Copy files
Your VmBix folder should look like this:
```
find VmBix-x.y.z/ -type f
VmBix-x.y.z/rmp-based/etc/init.d/vmbixd                      # init script, will start vmbixd as daemon
VmBix-x.y.z/rmp-based/etc/vmbix/vmbix.conf                   # config file
VmBix-x.y.z/rmp-based/usr/local/sbin/vmbix                   # sbin script to run the tool
VmBix-x.y.z/rmp-based/usr/local/sbin/vmbixd                  # like vmbix but will start in background
VmBix-x.y.z/rmp-based/usr/local/vmbix/vmbix.jar              # jar file itself
VmBix-x.y.z/zabbix_templates/new/template_vmbix_vcenter.xml  # vCenter and datastore zabbix template
VmBix-x.y.z/zabbix_templates/new/template_vmbix_esx.xml      # Host zabbix template 
VmBix-x.y.z/zabbix_templates/new/template_vmbix_vm.xml       # Virtual Machine zabbix template
[...]
```
Copy files from VmBix/rmp-based/ folder to you system accordingly to there paths. Make shell scripts executable:

```
chmod +x /etc/init.d/vmbixd
chmod +x /usr/local/sbin/vmbix
chmod +x /usr/local/sbin/vmbixd
```
### Check that VmBix runs on your system
Note: To run VmBix you'll have to install jre, OpenJDK should suite but not tested. All the shell scripts are tested on rhel 6 but thould work on other nix distributions as well. Init script (etc/init.d/vmbixd) might need some modifications to work on debian based or bsd systems.

Try to run /usr/local/sbin/vmbix, you should get a "usage" output.

```
# /usr/local/sbin/vmbix
Usage:
vmbix {-P|--port} listenPort {-s|--serviceurl} http[s]://serveraddr/sdk {-u|--username} username {-p|--password} password [-f|--pid pidfile]
or
vmbix [-c|--config] config_file  [-f|--pid pidfile]
```
Note: if some option is presented both in command line and in config file, command line one will be used. If any of required options is not presented at all you'll see the usage output with blinking missing option.

User name, password and vsphere service url are the required option.

## Configure daemon
It is strongly recommended to check your parameters before writing them down to a config file. Run vmbix with your username, password and service url:

```
$ vmbix -P 12050 -u username -p password -s https://vcenter.mycompany.com/sdk
starting server on port
port opened
time taken:5450 #this line means that you got connected to vcenter/esx
```
If you find your output similar to one listed here, feel free to set parameters at /etc/vmbix/vmbix.conf.

To install as daemon:
```
chkconfig --add vmbixd
```
Now you may start the daemon:
```
service vmbixd start
```
And configure autostart if you wish:
```
chkconfig vmbixd on
```
For logs, check:
```
tail -f /var/log/messages|grep vmbix
```
## Configure host in zabbix UI
1. Import any template from zabbix_templates.
2. Create a host based on imported template. There are at least two ways of configuring host connection:
  * Set host ip to 127.x.y.z or to the ip of the server where VmBix runs. Set "Connect to" to "IP address" and set port to 12050 or the one you've set in vmbix config file.
  * Set port to 12050 or the one you've set in vmbix config file. Use iptables rule to redirect all outgoing connections to port 12050 to localhost (assumes you run vmbix and zabbix server on the same server):
```
iptables -A OUTPUT -t nat -p tcp --dport 12050 -j DNAT --to 127.x.y.z:12050
```
Edit ports and "--to" parameter if needed. Ensure that iptables service is started.

## Querying VmBix in CLI
You can query VmBix like a Zabbix agent using the zabbix_get tool :
```
# zabbix_get -s 127.x.y.z -p 12050 -k about[*]
VMware vCenter Server 5.1.0 build-1364037
# zabbix_get -s 127.x.y.z -p 12050 -k esx.status[esx01.domain.local]
1
# zabbix_get -s 127.x.y.z -p 12050 -k vm.guest.os[MYVM01]
CentOS 4/5/6 (64 bits)
# zabbix_get -s 127.x.y.z -p 12050 -k esx.discovery[*]
{"data":[{"{#ESXHOST}":"esx01.domain.local"},{"{#ESXHOST}":"esx02.domain.local"}]}
# zabbix_get -s 127.x.y.z -p 12050 -k vm.counter[MYVM01,virtualDisk.totalReadLatency,scsi0:1,300]
2
```

## Supported zabbix checks
```
about
datastore.discovery
datastore.size[name,free]
datastore.size[name,total]
datastore.size[name,provisioned]
datastore.size[name,uncommitted]
esx.connection[name]
esx.cpu.load[name,cores]
esx.cpu.load[name,total]
esx.cpu.load[name,used]
esx.discovery
esx.maintenance[name]
esx.memory[name,total]
esx.memory[name,used]
esx.path[name,active]
esx.path[name,dead]
esx.path[name,disabled]
esx.path[name,standby]
esx.status[name]
esx.vms.count[name]
esx.vms.memory[name,active]
esx.vms.memory[name,ballooned]
esx.vms.memory[name,compressed]
esx.vms.memory[name,consumed]
esx.vms.memory[name,overheadConsumed]
esx.vms.memory[name,private]
esx.vms.memory[name,shared]
esx.vms.memory[name,swapped]
esx.counter[name,counter,[instance,interval]]
esx.counter.discovery[name,counter,[interval]]
esx.counter.list[name]
event.latest[*]
ping
vm.consolidation[name,needed]
vm.cpu.load[name,cores]
vm.cpu.load[name,total]
vm.cpu.load[name,used]
vm.discovery[*]
vm.folder[name]
vm.guest.disk.all[name]
vm.guest.disk.capacity[name,disk]
vm.guest.disk.free[name,disk]
vm.guest.ip[name]
vm.guest.name[name]
vm.guest.os[name]
vm.guest.tools.mounted[name]
vm.guest.tools.running[name]
vm.guest.tools.version[name]
vm.host[name]
vm.memory[name,active]
vm.memory[name,ballooned]
vm.memory[name,compressed]
vm.memory[name,consumed]
vm.memory[name,overheadConsumed]
vm.memory[name,private]
vm.memory[name,shared]
vm.memory[name,swapped]
vm.memory[name,total]
vm.counter[name,counter,[instance,interval]]
vm.counter.discovery[name,counter,[interval]]
vm.counter.list[name]
vm.powerstate[name]
vm.status[name]
vm.storage.committed[name]
vm.storage.uncommitted[name]
vm.storage.unshared[name]
```

## How to implement your own checks
1. Find a function called
```
private void checkAllPatterns                (String string, PrintWriter out  )
```
2. Add your own pattern. For example this string:
```
Pattern pHostCpuUsed            = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),used\\]"             );        // :checks host cpu usage
```
will be responsible for this item:
```
esx.cpu.load[{HOST.DNS},used]
```
3. Scroll down to the next block of code in the same function starting with "String found;", add you own "found=" block:
```
found = checkPattern(pHostCpuUsed           ,string); if (found != null) { getHostCpuUsed           (found, out); return; }
```
This one calls a function called "getHostCpuUsed" with {HOST.DNS} as an a first argument and a PrintWriter instance as a second one.

4. Your function should accept String and PrintWriter arguments. It should return values like that:
```
out.print(value);
out.flush();
```
5. Add a usage line to the methods() function :
```
static void methods() {
        System.err.print(
            "Available methods :                                  \n" 
            [...]
          + "esx.cpu.load[name,used]                              \n"
```

## Version history
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
