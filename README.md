# VmBix
VmBix is a multi-thread TCP server written in java, it accepts connection from zabbix server or zabbix get (supports some custom zabbix checks) and translates them to vmware api calls.

This project is a fork of the original VmBix by ihryamzik (https://code.google.com/p/vmbix/) 

## Build from source
Note: you'll need to intall jdk and ant to follow this article. Sources could also be compiled manually, without ant.

### Download source code
```
git clone https://github.com/dav3860/vmbix.git
```

### Compile
```
cd vmbix
ant
```
Ant should download required jargs and vijava libraries, compile the sources and place compiled code to VmBix folder.

## Installation
### Copy files
Your VmBix folder should look like this:
```
find VmBix-beta-0.0.1/ -type f
VmBix-beta-0.0.1/rmp-based/etc/init.d/vmbixd          # init script, will start vmbixd as daemon
VmBix-beta-0.0.1/rmp-based/etc/vmbix/vmbix.conf       # config file
VmBix-beta-0.0.1/rmp-based/usr/local/sbin/vmbix       # sbin script to run the tool
VmBix-beta-0.0.1/rmp-based/usr/local/sbin/vmbixd      # like vmbix but will start in background
VmBix-beta-0.0.1/rmp-based/usr/local/vmbix/vmbix.jar  # jar file itself
VmBix-beta-0.0.1/zabbix_templates/Datastore.xml       # Datastore zabbix template
VmBix-beta-0.0.1/zabbix_templates/Host.xml            # Host zabbix template 
VmBix-beta-0.0.1/zabbix_templates/VM.xml              # Virtual Machine zabbix template
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
It is strongly recommended to check you parameters before writing them down to a config file. Run vmbix with you username, password and service url:

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
  * Set host ip to 127.0.0.1 or to the ip of the server where VmBix runs. Set "Connect to" to "IP address". Set port to 12050 or the one you've set in vmbix config file.
  * Set port to 12050 or the one you've set in vmbix config file. Use iptables rule to redirect all outgoing connections to port 12050 to localhost (assumes you run vmbix and zabbix server on the same server):
```
iptables -A OUTPUT -t nat -p tcp --dport 12050 -j DNAT --to 127.0.0.1:12050
```
Edit ports and "--to" parameter if needed. Ensure that iptables service is started.

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
