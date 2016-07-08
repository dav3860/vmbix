# VmBix [![Build Status](https://travis-ci.org/dav3860/vmbix.svg?branch=master)](https://travis-ci.org/dav3860/vmbix)

VmBix is a multi-threaded TCP proxy for the VMWare Sphere API written in Java. It accepts connections from a [Zabbix](http://www.zabbix.com/) server/proxy/agent or the zabbix_get binary and translates them to VMWare API calls.

Starting from version 2.2, Zabbix can natively monitor a VMWare environment. But there are a few drawbacks :
* The monitored items are not all very relevant
* This is not easily extensible
* The created ESX and VM hosts are mostly read-only. You cannot attach them different templates, put them into different groups, or use a Zabbix agent to monitor their OS or apps

VmBix helps you to overcome these limitations, with very good performance. It is multi-threaded, implements objects caching, and can be queried using a Zabbix [loadable module](https://www.zabbix.com/documentation/3.0/manual/config/items/loadablemodules).

VmBix comes with a set of templates adding several monitored items, triggers and graphs in Zabbix. Here are a few screenshots of what you can expect in Zabbix :
![](https://github.com/dav3860/vmbix/blob/master/screenshots/latest_data.png)

![](https://github.com/dav3860/vmbix/blob/master/screenshots/triggers.png)

![](https://github.com/dav3860/vmbix/blob/master/screenshots/graph.png)

You can use VmBix methods to query interesting VMWare metrics, for example :

```
esx.counter[esx01.domain.local,cpu.ready.summation]
1135
```

```
vm.counter.discovery[VM01,virtualDisk.totalReadLatency.average]
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
vm.counter[VM01,virtualDisk.totalReadLatency.average,scsi2:4,300]
2
```

## Installation
Get the latest version of the server [here](https://bintray.com/dav3860/generic/vmbix/view/files). RPM & DEB packages are provided.

You will also need the Zabbix [loadable module](https://www.zabbix.com/documentation/3.0/manual/config/items/loadablemodules) corresponding to your zabbix version. See https://github.com/dav3860/vmbix_zabbix_module for installation details.

The VmBix server can be installed on the same machine as a Zabbix server or proxy. The loadable module must be installed on the Zabbix machine that will monitor the VMWare environment.

## Or build from source
Note: you will need to install JDK and Maven to compile VmBix.
* Install Maven

Follow the instructions on [this](https://maven.apache.org/install.html) page to install Maven.
* Compile
```
tar -zxvf vmbix-x.x.tar.gz
cd vmbix-x.x
mvn package
```

See the instructions [here](https://github.com/dav3860/vmbix_zabbix_module) to compile the loadable module.

## Quick start

Note: to run VmBix you'll have to install a JRE (OpenJDK should suite but not tested). All the scripts are tested on Centos 7 but should work on other \*NIX distributions as well.

### Test the binary

Try to run /usr/local/sbin/vmbix, you should get a *usage* output :

```
# /usr/local/sbin/vmbix
Usage:
vmbix {-P|--port} listenPort {-s|--serviceurl} http[s]://serveraddr/sdk {-u|--username} username {-p|--password} password [-f|--pid pidfile] [-i|--interval interval] [-U|--useuuid (true|false)]
or
vmbix [-c|--config] config_file  [-f|--pid pidfile] [-i|--interval interval] [-U|--useuuid (true|false)]
```

### Configure the daemon

It is strongly recommended to check your parameters before writing them down to a config file. Run VmBix with your username, password and service url:

```
$ vmbix -P 12050 -u "MYDOMAIN\\myvmwareuser" -p "mypassword"" -s "https://myvcenter.mydomain.local/sdk"
log4j:WARN No appenders could be found for logger (com.vmware.vim25.ws.XmlGenDom).
log4j:WARN Please initialize the log4j system properly.
log4j:WARN See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.
17:39:08.430 [main] INFO net.dav3860.VmBix - starting server on port 12050
17:39:08.433 [main] INFO net.dav3860.VmBix - server started

```
You should see a similar ouput. Once you have validated that you can start VmBix, you can edit the configuration file /etc/vmbix/vmbix.conf and run the daemon. You can still run the process in foreground for real-time troubleshooting if necessary.

To install as a daemon:
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
For logs, check the file :
```
tail -f /var/log/vmbix.log
```

### Configure Zabbix

All the ESX servers, datastores and virtual machines will automatically be discovered and created in Zabbix. See the following instructions to configure Zabbix.

#### Import the templates
Import the templates from [here](https://github.com/dav3860/vmbix/tree/master/zabbix) or from /usr/share/vmbix/zabbix/templates if you installed a package (import the vCenter template after the others). At the moment, only Zabbix 3.0.x templates are provided. The VmBix items in Zabbix are configured with an "Simple Check" type as Zabbix uses a loadable module to talk to VmBix. So it is still possible to use a Zabbix agent in parallel to monitor the hosts. The vmbix.so [loadable module](https://github.com/dav3860/vmbix_zabbix_module) must be installed on your server/proxy.

#### Discover the objects
VmBix can discover and create your VMWare environment (hypervisors, VMs, datastores) in too ways :
- using Zabbix Low-Level Discovery (LLD) and host prototypes
- using a provided script talking to the Zabbix API to create regular Zabbix hosts (*recommended*)

#### Using Zabbix LLD
Create a host named "VmBix" for example and link it with the VmBix vCenter template. The IP address and port are not used, but it is necessary to make it monitored by the server/proxy running the loadable module.

Wait for the ESX servers, datastores and virtual machines to be discovered and created. They will be automatically linked to the VmBix ESX, datastore or VM template. You may need to increase the Timeout parameter in the Zabbix configuration file if VSphere takes too long to respond.

You can also link additional templates to the created hosts by editing the corresponding host prototype in the VmBix vCenter template discovery rules.

As these hosts are created using the host prototype mechanism in Zabbix, they will be almost read-only. For example, you can't edit one host to link it to a specific template. This must be made at the host prototype level, which can be a limitation if your virtual machines are different.

#### Using VMWare objects as regular hosts in Zabbix
To overcome this limitation, you can disable the VM discovery rule in the VmBix vCenter template and create your virtual machines manually in Zabbix. Then, link them to the VmBix VM template (preferably with the loadable module method). You can then edit them as any other host.

Note: if the parameter useuuid is set to *true* in the VmBix configuration file, the objects must be referenced using their VMWare UUID. So if you create a host manually, you must set its name to the UUID and its visible name to the name of the VM. You can use the \*.discovery[\*] methods to get the UUID of an object :

```
# zabbix_get -s 127.0.0.1 -p 12050 -k "vm.discovery[*]"
{
  "data": [
    {
      "{#VIRTUALMACHINE}": "MYVM01",
      "{#UUID}": "4214811c-1bab-f0fb-363b-9698a2dc607c"
    },
    {
      "{#VIRTUALMACHINE}": "MYVM02",
      "{#UUID}": "4214c939-18f1-2cd5-928a-67d83bc2f503"
    }
  ]
}
```

As it would be a pain to create all your virtual machines/ESX/datastores manually, a sample import script (vmbix-object-sync) is provided for this purpose. This is the recommended way to discover your environment with VmBix. Check the instructions [here](https://github.com/dav3860/vmbix/tree/master/zabbix/addons) to setup and configure the script.

If you installed VmBix from a package, the script is located in /usr/share/vmbix/zabbix/addons.

### Querying VmBix in CLI
You can query VmBix like a Zabbix agent using the zabbix_get tool :
```
# zabbix_get -s 127.0.0.1 -p 12050 -k about[*]
VMware vCenter Server 5.1.0 build-1364037
# zabbix_get -s 127.0.0.1 -p 12050 -k esx.status[esx01.domain.local]
1
# zabbix_get -s 127.0.0.1 -p 12050 -k vm.guest.os[4214811c-1bab-f0fb-363b-9698a2dc607c]
CentOS 4/5/6 (64 bits)
# zabbix_get -s 127.0.0.1 -p 12050 -k esx.discovery[*]
{
  "data": [
    {
      "{#ESXHOST}": "esx01.domain.local"
    },
    {
      "{#ESXHOST}": "esx02.domain.local"
    }
  ]
}# zabbix_get -s 127.0.0.1 -p 12050 -k vm.counter[MYVM01,virtualDisk.totalReadLatency.average,scsi0:1,300]
2
```
Again, if useuuid is set to true in the configuration file, objects are identified using their UUID :
```
# zabbix_get -s 127.0.0.1 -p 12050 -k vm.guest.os[421448c4-8970-28f0-05a5-90a20724bd08]
CentOS 4/5/6 (64 bits)
```

## Supported Zabbix checks
There a a complete description of the VmBix supported methods in the [Wiki](https://github.com/dav3860/vmbix/wiki) section.

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

## Querying multiple vCenters
At the moment, VmBix does not support multiple vCenters. If you still want to query multiple vCenters, you need to install VmBix on different Zabbix proxies, pointing to different vCenters. Then select the right proxy in each Zabbix host configuration page.
- If you use Zabbix LLD to discover the VMWare environment, you will need to create multiple VmBix hosts, one for each proxy/VmBix installation.
- If you use the Python [script](https://github.com/dav3860/vmbix/tree/master/zabbix/addons), you will have to edit its configuration file to assign a Zabbix proxy to the discovered hosts.

## Version history
See [CHANGELOG](https://github.com/dav3860/VmBix/blob/master/CHANGELOG.md)


This project is a fork of the original VmBix by [@hryamzik](https://github.com/hryamzik).
