# The vmbix-object-sync script
The default [VmBix vCenter template](https://github.com/dav3860/vmbix/blob/master/zabbix/templates) uses Zabbix Low-Level Discovery to automatically create Zabbix hosts. This is quite efficient but the created hosts are mostly read-only and you cannot monitor them using a Zabbix agent or link them to different templates.

The vmbix-object-sync Python script allows to import VMWare vSphere objects (VMs, ESX hosts, datastores) as regular Zabbix hosts. It also makes an extensive use of conditional filters to import the objects. This makes possible to link Linux templates to Linux VMs, skip powered-off hosts, or filter the VMs with a name starting with "TEST". The conditional filters or assignments are based on VmBix methods. Last, the script sends a report by mail if something changed.

## Installation
First, considering that the script is in /usr/share/vmbix/zabbix/addons/, install the requirements dependencies :
```
cd /usr/share/vmbix/zabbix/addons/
pip install -r requirements.txt
```
Then, copy the sample configuration file to /etc/vmbix, for example :
```
cp /usr/share/vmbix/zabbix/vmbix-object-sync.yaml.sample /etc/vmbix/vmbix-object-sync.yaml
```
Edit the configuration file to suit your needs.

**Disable** the required Low-Level Discovery rules of the *Template VmBix vCenter Loadable Module* template in Zabbix :

![](https://github.com/dav3860/vmbix/blob/master/screenshots/lld_rules.png)

For example, if you want to use *vmbix-object-sync* to import the virtual machines, disable the VM LLD rule in Zabbix.

Start the script with :
```
./vmbix-object-sync -c /etc/zabbix/vmbix-object-sync.yaml
```

## Supported parameters :
```
Usage: vmbix-object-sync [-d] [-f] -c <config> [-a <delete|disable|simulate>]
       vmbix-object-sync -v
       vmbix-object-sync -h

Options:
  -h, --help                                         Display this usage info
  -v, --version                                      Display version and exit
  -d, --debug                                        Debug mode, be more verbose
  -f, --force                                        Force action even if an anomaly is detected
  -c <config>, --config <config>                     Configuration file to use
  -a <action>, --action <action>                     Action for extra hosts [default: simulate]
```

Explanation:
- The *-a* parameter specifies how to handle the hosts that should be removed from Zabbix (disabled, deleted, or just simulate it)
- The *-f* parameter force the deletion/disabling of Zabbix hosts, even if more that 10% of the hosts should be removed. **USE WITH CAUTION**

*vmbix-object-sync* reuses code from the [vPoller](https://github.com/dnaeon/py-vpoller) project.
