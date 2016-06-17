# VmBix [![Build Status](https://travis-ci.org/dav3860/vmbix.svg?branch=master)](https://travis-ci.org/dav3860/vmbix)

VmBix is a multi-thread TCP server written in java, it accepts connections from a Zabbix server/proxy/agent or zabbix_get and translates them to VMWare API calls.

Starting from version 2.2, Zabbix can natively monitor a VMWare environment. But there are a few drawbacks :
* The monitored items are not all very relevant.
* The created ESX and VM hosts are mostly read-only. You cannot attach them different templates or monitor them with an agent.
VmBix helps you to overcome this limitations, with very good performance. It is multi-threaded and can be queried using a Zabbix loadable module. VmBix also exposes VMWare API methods that are not included in Zabbix, for example the [Performance Counters](http://fr.slideshare.net/alanrenouf/vsphere-apis-for-performance-monitoring). You can use these VmBix methods to query interesting VMWare metrics, for example :

```
TEST# esx.counter[esx01.domain.local,cpu.ready.summation]
1135
```

```
TEST# vm.counter.discovery[VM01,virtualDisk.totalReadLatency.average]
{
    "data": [
        {
            "{#METRICINSTANCE}": "scsi2:2"
        },
        {
            "{#METRICINSTANCE}": "scsi2:1"
        },
        {
            "{#METRICINSTANCE}": "scsi2:0"
        },
        {
            "{#METRICINSTANCE}": "scsi2:6"
        },
        {
            "{#METRICINSTANCE}": "scsi2:5"
        },
        {
            "{#METRICINSTANCE}": "scsi2:4"
        },
        {
            "{#METRICINSTANCE}": "scsi2:3"
        }
    ]
}
```

```
TEST# vm.counter[VM01,virtualDisk.totalReadLatency.average,scsi2:4,300]
2
```

## Installation
Get the latest version [here](https://bintray.com/dav3860/generic/vmbix/view/files).

## Or build from source
Note: you'll need to install JDK and Maven to follow this article. Sources could also be compiled manually.
* Install Maven
Follow the instructions on this page to install Maven (https://maven.apache.org/install.html).
* Compile
```
tar -zxvf vmbix-x.x.tar.gz
cd vmbix-x.x
mvn package
```

## Quick start
Note: To run VmBix you'll have to install jre, OpenJDK should suite but not tested. All the shell scripts are tested on Centos 7 but thould work on other nix distributions as well.
### Test the binary

Try to run /usr/local/sbin/vmbix, you should get a "usage" output.

```
# /usr/local/sbin/vmbix
Usage:
vmbix {-P|--port} listenPort {-s|--serviceurl} http[s]://serveraddr/sdk {-u|--username} username {-p|--password} password [-f|--pid pidfile] [-i|--interval interval] [-U|--useuuid (true|false)]
or
vmbix [-c|--config] config_file  [-f|--pid pidfile] [-i|--interval interval] [-U|--useuuid (true|false)]
```

### Configure daemon

It is strongly recommended to check your parameters before writing them down to a config file. Run VmBix with your username, password and service url:

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
tail -f /var/log/vmbix.log
```
### Configure Zabbix

All the ESX servers, datastores and virtual machines will automatically be discovered and created in Zabbix. Here is how to configure Zabbix :

1. Import the templates from [zabbix_templates](https://github.com/dav3860/vmbix/tree/master/zabbix_templates) (import the vCenter templates after the others). There are two template flavours : with or without the Zabbix loadable module. The Zabbix loadable module method is useful if you want to monitor your virtual machines with a Zabbix agent in addition to VmBix.
  * Without the module, the VmBix items in Zabbix are configured with the "Zabbix agent" type. So Zabbix directly talks to VmBix using the Zabbix agent protocol on port 12050 by default. This is good, but it means that the hosts cannot also be monitored using the Zabbix agent as all the created hosts will have an IP of 127.0.0.1 and a port of 12050 by default for their "agent" interface. This is a limitation for virtual machines for example.
  * With the loadable module, the VmBix items in Zabbix are configured with an "Simple Check" type. Zabbix uses a loadable module to talk to VmBix. So it is still possible to use a Zabbix agent to monitor the hosts. The vmbix.so [loadable module](https://github.com/dav3860/vmbix_zabbix_module) must be installed on your server/proxy.

2. Create a host named "VmBix" for example and link it with the VmBix vCenter template (with or without the module). The host must be configured like this :
  * Set host ip to 127.0.0.1 or IP of the server where VmBix runs.
  * Set "Connect to" to "IP address" and set port to 12050 or the one you've set in VmBix config file.

Wait for the ESX servers, datastores and virtual machines to be discovered and created. The VM and ESX hosts will be automatically linked to the VmBix ESX or VM template.

You can also link additional templates to the created hosts by editing the corresponding host prototype in the VmBix vCenter template discovery rules.

As these hosts are created using the host prototype mechanism in Zabbix, they will be almost read-only. For example, you can't edit one host to link it to a specific template. This must be made at the host prototype level, which can be a limitation if your virtual machines are different.
To overcome this limitation, you can disable the VM discovery rule in the VmBix vCenter template and create your virtual machines manually in Zabbix (I do it using the API and a script of my own). Then, link them to the VmBix VM template (preferably with the loadable module method). You can then edit them as any other host.

You may need to increase the Timeout parameter in Zabbix configuration file.

### Querying VmBix in CLI
You can query VmBix like a Zabbix agent using the zabbix_get tool :
```
# zabbix_get -s 127.x.y.z -p 12050 -k about[*]
VMware vCenter Server 5.1.0 build-1364037
# zabbix_get -s 127.x.y.z -p 12050 -k esx.status[esx01.domain.local]
1
# zabbix_get -s 127.x.y.z -p 12050 -k vm.guest.os[MYVM01]
CentOS 4/5/6 (64 bits)
# zabbix_get -s 127.x.y.z -p 12050 -k esx.discovery[*]
{"data":[{"{#ESXHOST}":"esx01.domain.local"},{"{#ESXHOST}":"esx02.domain.local"}]}
# zabbix_get -s 127.x.y.z -p 12050 -k vm.counter[MYVM01,virtualDisk.totalReadLatency.average,scsi0:1,300]
2
```
If useuuid is set to true in the configuration file, objects are identified using their UUID :
```
# zabbix_get -s 127.x.y.z -p 12050 -k vm.guest.os[421448c4-8970-28f0-05a5-90a20724bd08]
CentOS 4/5/6 (64 bits)
```

## Supported zabbix checks
```
vmbix.about
vmbix.ping
vmbix.version
cluster.discovery
cluster.cpu[name,free]
cluster.cpu[name,total]
cluster.cpu[name,usage]
cluster.cpu.num[name,threads]
cluster.cpu.num[nane,cores]
cluster.mem[name,free]
cluster.mem[name,total]
cluster.mem[name,usage]
cluster.hosts[name,online]
cluster.hosts[name,maint]
cluster.hosts[name,total]
datacenter.discovery
datacenter.status[name,(overall|config)]
datastore.discovery
datastore.size[(uuid|name),free]
datastore.size[(uuid|name),total]
datastore.size[(uuid|name),provisioned]
datastore.size[(uuid|name),uncommitted]
esx.connection[(uuid|name)]
esx.uptime[(uuid|name)]
esx.cpu.load[(uuid|name),cores]
esx.cpu.load[(uuid|name),total]
esx.cpu.load[(uuid|name),used]
esx.discovery
esx.maintenance[(uuid|name)]
esx.memory[(uuid|name),total]
esx.memory[(uuid|name),used]
esx.path[(uuid|name),active]
esx.path[(uuid|name),dead]
esx.path[(uuid|name),disabled]
esx.path[(uuid|name),standby]
esx.status[(uuid|name)]
esx.vms.count[(uuid|name)]
esx.vms.memory[(uuid|name),active]
esx.vms.memory[(uuid|name),ballooned]
esx.vms.memory[(uuid|name),compressed]
esx.vms.memory[(uuid|name),consumed]
esx.vms.memory[(uuid|name),overheadConsumed]
esx.vms.memory[(uuid|name),private]
esx.vms.memory[(uuid|name),shared]
esx.vms.memory[(uuid|name),swapped]
esx.counter[(uuid|name),counter,[instance,interval]]
esx.counter.discovery[(uuid|name),counter,[interval]]
esx.counter.list[(uuid|name)]
event.latest[*]
vm.consolidation[(uuid|name),needed]
vm.cpu.load[(uuid|name),cores]
vm.cpu.load[(uuid|name),total]
vm.cpu.load[(uuid|name),used]
vm.discovery[*]
vm.discovery.full[*]
vm.folder[(uuid|name)]
vm.uptime[(uuid|name)]
vm.name[(uuid|name)]
vm.annotation[(uuid|name)]
vm.guest.disk.discovery[(uuid|name)]
vm.guest.disk.capacity[(uuid|name),disk]
vm.guest.disk.free[(uuid|name),disk]
vm.guest.ip[(uuid|name)]
vm.guest.(uuid|name)[(uuid|name)]
vm.guest.os[(uuid|name)]
vm.guest.os.short[(uuid|name)]
vm.guest.tools.mounted[(uuid|name)]
vm.guest.tools.running[(uuid|name)]
vm.guest.tools.version[(uuid|name)]
vm.host[(uuid|name)]
vm.memory[(uuid|name),active]
vm.memory[(uuid|name),ballooned]
vm.memory[(uuid|name),compressed]
vm.memory[(uuid|name),consumed]
vm.memory[(uuid|name),overheadConsumed]
vm.memory[(uuid|name),private]
vm.memory[(uuid|name),shared]
vm.memory[(uuid|name),swapped]
vm.memory[(uuid|name),total]
vm.counter[(uuid|name),counter,[instance,interval]]
vm.counter.discovery[(uuid|name),counter,[interval]]
vm.counter.list[(uuid|name)]
vm.powerstate[(uuid|name)]
vm.status[(uuid|name)]
vm.storage.committed[(uuid|name)]
vm.storage.uncommitted[(uuid|name)]
vm.storage.unshared[(uuid|name)]
```

## Caching
To disable the cache set CacheTtl variables to 0.

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
See [CHANGELOG](https://github.com/dav3860/VmBix/blob/master/CHANGELOG.md)


This project is a fork of the original VmBix by [@hryamzik](https://github.com/hryamzik).
