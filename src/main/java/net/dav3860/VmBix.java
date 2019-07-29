/*
  # VmBix - VMWare API communication daemon.
  # This daemon connects to vCenter with VIJAVA SDK
  # and gives access to some statistics over TCP/IP socket.
  #
  # Redistribution and use in source and binary forms, with or without
  # modification, are permitted provided that the following conditions
  # are met:
  # 1. Redistributions of source code must retain the above copyright
  #    notice, this list of conditions and the following disclaimer
  #    in this position and unchanged.
  # 2. Redistributions in binary form must reproduce the above copyright
  #    notice, this list of conditions and the following disclaimer in the
  #    documentation and/or other materials provided with the distribution.
  #
  # THIS SOFTWARE IS PROVIDED BY THE AUTHOR(S) ``AS IS'' AND ANY EXPRESS OR
  # IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  # OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  # IN NO EVENT SHALL THE AUTHOR(S) BE LIABLE FOR ANY DIRECT, INDIRECT,
  # INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  # NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  # DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  # THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  # (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  # THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  #
  # Copyright (c) 2014 <dav3860chom@yahoo.fr>
  # All rights reserved.
  #
  # Initial releases by Roman Belyakovsky (ihryamzik@gmail.com).
  # Release 1.2.3 by tomasz@pawelczak.eu
  # Next releases by dav3860
*/

package net.dav3860;

import java.net.*;
import java.rmi.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import com.vmware.vim25.mo.util.*;
import jargs.gnu.CmdLineParser;
import java.lang.management.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VmBix {
  
  // Constants
  public static final String INTERVAL         = "300";
  public static final String USEUUID          = "false";
  public static final String MAXCONNECTIONS   = "150";
  public static final String CONNECTTIMEOUT   = "30000";
  public static final String READTIMEOUT      = "30000";
  public static final String ESCAPECHARS      = "false";
  public static final String VMCACHETTL       = "15";
  public static final String VMCACHESIZE      = "1000";
  public static final String ESXICACHETTL     = "15";
  public static final String ESXICACHESIZE    = "100";
  public static final String DSCACHETTL       = "15";
  public static final String DSCACHESIZE      = "100";
  public static final String PERFIDCACHETTL   = "5";
  public static final String PERFIDCACHESIZE  = "1000";
  public static final String COUNTERCACHETTL  = "5";
  public static final String COUNTERCACHESIZE = "1000";
  public static final String HRICACHETTL      = "15";
  public static final String HRICACHESIZE     = "100";
  public static final String CLCACHETTL       = "15";
  public static final String CLCACHESIZE      = "100";
  
  
  static ArrayList<Socket> sockets;
  static long requests;
  static ServiceInstance serviceInstance;
  static InventoryNavigator inventoryNavigator;
  static PerformanceManager performanceManager;
  
  static Cache<String, ManagedEntity> vmCache;
  static Cache<String, ManagedEntity> esxiCache;
  static Cache<String, ManagedEntity> dsCache;
  static Cache<String, ManagedEntity> clCache;
  static Cache<String, List> counterCache;
  static Cache<String, PerfMetricId[]> hostPerfCache;
  static Cache<String, HostRuntimeInfo> hriCache;
  
  static String  sdkUrl;
  static String  uname;
  static String  passwd;
  static String  ipaddr;
  static Integer port;
  static String  pidFile;
  static Integer interval         = Integer.parseInt(INTERVAL);
  static Boolean useUuid          = Boolean.parseBoolean(USEUUID);
  static Integer maxConnections   = Integer.parseInt(MAXCONNECTIONS);
  static Integer connectTimeout   = Integer.parseInt(CONNECTTIMEOUT);
  static Integer readTimeout      = Integer.parseInt(READTIMEOUT);
  static Boolean escapeChars      = Boolean.parseBoolean(ESCAPECHARS);
  static Integer vmCacheTtl       = Integer.parseInt(VMCACHETTL);
  static Integer vmCacheSize      = Integer.parseInt(VMCACHESIZE);
  static Integer esxiCacheTtl     = Integer.parseInt(ESXICACHETTL);
  static Integer esxiCacheSize    = Integer.parseInt(ESXICACHESIZE);
  static Integer dsCacheTtl       = Integer.parseInt(DSCACHETTL);
  static Integer dsCacheSize      = Integer.parseInt(DSCACHESIZE);
  static Integer perfIdCacheTtl   = Integer.parseInt(PERFIDCACHETTL);
  static Integer perfIdCacheSize  = Integer.parseInt(PERFIDCACHESIZE);
  static Integer counterCacheTtl  = Integer.parseInt(COUNTERCACHETTL);
  static Integer counterCacheSize = Integer.parseInt(COUNTERCACHESIZE);
  static Integer hriCacheTtl      = Integer.parseInt(HRICACHETTL);
  static Integer hriCacheSize     = Integer.parseInt(HRICACHESIZE);
  static Integer clCacheTtl       = Integer.parseInt(CLCACHETTL);
  static Integer clCacheSize      = Integer.parseInt(CLCACHESIZE);
  
  static final Logger LOG = LoggerFactory.getLogger(VmBix.class);
    
  public static interface IValidationResult {
    int getStatus();
    String getMessage();
  }
  
  public static class ValidationResult implements IValidationResult {
    int status = 0;
    String message = "";
    
    public ValidationResult() {
        this.message = message;
        this.status = status;
    }    

    public ValidationResult(int status, String message) {
        this.message = message;
        this.status = status;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    } 
  }
  
  public static void main(String[] args) {
    try {
      sockets = new ArrayList<Socket>();
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      
      CmdLineParser parser = new CmdLineParser();
      // These parameters don't have a default value
      CmdLineParser.Option oUname  = parser.addStringOption('u', "username");
      CmdLineParser.Option oPasswd = parser.addStringOption('p', "password");
      CmdLineParser.Option oSurl   = parser.addStringOption('s', "serviceurl");
      CmdLineParser.Option oIpAddr = parser.addStringOption('b', "bindaddress");
      CmdLineParser.Option oPort   = parser.addIntegerOption('P', "port");
      CmdLineParser.Option oPid    = parser.addStringOption('f', "pid");
      CmdLineParser.Option oConfig = parser.addStringOption('c', "config");
      
      try {
        parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
        LOG.error(e.toString());
        // usage("");
        System.exit(1);
      }
      
      sdkUrl  = (String) parser.getOptionValue(oSurl);
      uname   = (String) parser.getOptionValue(oUname);
      passwd  = (String) parser.getOptionValue(oPasswd);
      ipaddr  = (String) parser.getOptionValue(oIpAddr);
      port    = (Integer) parser.getOptionValue(oPort);
      pidFile = (String) parser.getOptionValue(oPid);
      
      String config = (String) parser.getOptionValue(oConfig);
      if (config != null) {       // If a configuration file is specified
        Properties prop = new Properties();
        try {
          InputStream is = new FileInputStream(config);
          prop.load(is);
          if (uname == null) {
            uname = prop.getProperty("username");
          }
          if (passwd == null) {
            passwd = prop.getProperty("password");
          }
          if (sdkUrl == null) {
            sdkUrl = prop.getProperty("serviceurl");
          }
          if (ipaddr == null) {
            ipaddr = prop.getProperty("bindaddress");
          }
          if (port == null && prop.getProperty("listenport") != null) {
            port = Integer.parseInt(prop.getProperty("listenport"));
          }
          if (pidFile == null) {
            pidFile = prop.getProperty("pidfile");
          }
          
          // Common parameters
          interval         = Integer.parseInt(prop.getProperty("interval", INTERVAL));
          maxConnections   = Integer.parseInt(prop.getProperty("maxconnections", MAXCONNECTIONS));
          connectTimeout   = Integer.parseInt(prop.getProperty("connecttimeout", CONNECTTIMEOUT));
          readTimeout      = Integer.parseInt(prop.getProperty("readtimeout", READTIMEOUT));
          useUuid          = Boolean.parseBoolean(prop.getProperty("useuuid", USEUUID));
          escapeChars      = Boolean.parseBoolean(prop.getProperty("escapechars", ESCAPECHARS));
          
          // Caching parameters
          vmCacheTtl       = Integer.parseInt(prop.getProperty("vmcachettl", VMCACHETTL));
          esxiCacheTtl     = Integer.parseInt(prop.getProperty("esxicachettl", ESXICACHETTL));
          dsCacheTtl       = Integer.parseInt(prop.getProperty("dscachettl", DSCACHETTL));
          perfIdCacheTtl   = Integer.parseInt(prop.getProperty("perfidcachettl", PERFIDCACHETTL));
          counterCacheTtl  = Integer.parseInt(prop.getProperty("countercachettl", COUNTERCACHETTL));
          hriCacheTtl      = Integer.parseInt(prop.getProperty("hricachettl", HRICACHETTL));
          clCacheTtl       = Integer.parseInt(prop.getProperty("clcachettl", CLCACHETTL));
          
          vmCacheSize      = Integer.parseInt(prop.getProperty("vmcachesize", VMCACHESIZE));
          esxiCacheSize    = Integer.parseInt(prop.getProperty("esxicachesize", ESXICACHESIZE));
          dsCacheSize      = Integer.parseInt(prop.getProperty("dscachesize", DSCACHESIZE));
          perfIdCacheSize  = Integer.parseInt(prop.getProperty("perfidcachesize", PERFIDCACHESIZE));
          counterCacheSize = Integer.parseInt(prop.getProperty("countercachesize", COUNTERCACHESIZE));
          hriCacheSize     = Integer.parseInt(prop.getProperty("hricachesize", HRICACHESIZE));
          clCacheSize      = Integer.parseInt(prop.getProperty("clcachesize", CLCACHESIZE));
          
          } catch (IOException e) {
          LOG.info("There was a problem with the configuration parameters.");
          usage(e.toString());
          System.exit(1);
        }
      }
      
      if (sdkUrl == null || uname == null || passwd == null || port == null) {
        usage("");
        methods();
        System.exit(2);
      }
      
      if (pidFile != null && pid != null) {
        createPid(pidFile, pid);
      }
      
      Shutdown sh = new Shutdown();
      Runtime.getRuntime().addShutdownHook(sh);
      
      vmCache       = CacheBuilder.newBuilder().maximumSize(vmCacheSize).expireAfterWrite(vmCacheTtl, TimeUnit.MINUTES).recordStats().build();
      esxiCache     = CacheBuilder.newBuilder().maximumSize(esxiCacheSize).expireAfterWrite(esxiCacheTtl, TimeUnit.MINUTES).recordStats().build();
      dsCache       = CacheBuilder.newBuilder().maximumSize(dsCacheSize).expireAfterWrite(dsCacheTtl, TimeUnit.MINUTES).recordStats().build();
      hostPerfCache = CacheBuilder.newBuilder().maximumSize(perfIdCacheSize).expireAfterWrite(perfIdCacheTtl, TimeUnit.MINUTES).recordStats().build();
      counterCache  = CacheBuilder.newBuilder().maximumSize(counterCacheSize).expireAfterWrite(counterCacheTtl, TimeUnit.MINUTES).recordStats().build();
      hriCache      = CacheBuilder.newBuilder().maximumSize(hriCacheSize).expireAfterWrite(hriCacheTtl, TimeUnit.MINUTES).recordStats().build();
      clCache       = CacheBuilder.newBuilder().maximumSize(clCacheSize).expireAfterWrite(clCacheTtl, TimeUnit.MINUTES).recordStats().build();
      
      while (true) {
        try {
          server();
          } catch (java.rmi.RemoteException e) {
          LOG.error("RemoteException: " + e.toString());
        }
        VmBix.sleep(1000);
      }
      
      } catch (java.net.UnknownHostException e) {
      LOG.error(e.toString());
      System.exit(1);
      } catch (NumberFormatException e) {
      LOG.error(e.toString());
      System.exit(1);
      } catch (IOException e) {
      LOG.error(e.toString());
      System.exit(1);
    }
    
  }
  
  static void createPid(String pidFile, String pid) {
    try {
      // Create pid file
      FileWriter fstream = new FileWriter(pidFile);
      BufferedWriter out = new BufferedWriter(fstream);
      out.write(pid + "\n");
      LOG.info("creating pid file " + pidFile + " " + pid);
      //Close the output stream
      out.close();
      } catch (Exception e) {
      //Catch exception if any
      LOG.error("Error: " + e.toString());
    }
  }
  
  private static void deletePid() {
    if (pidFile != null) {
      File f1 = new File(pidFile);
      boolean success = f1.delete();
      if (!success) {
        LOG.error("Pid file deletion failed.");
        System.exit(0);
        } else {
        LOG.info("Pid file deleted.");
      }
    }
  }
  
  static void methods() {
    System.out.println(
    "Available methods :                                           \n"
    + "vmbix.ping                                                  \n"
    + "vmbix.version                                               \n"
    + "vmbix.stats[threads]                                        \n"
    + "vmbix.stats[queue]                                          \n"
    + "vmbix.stats[requests]                                       \n"
    + "vmbix.stats[cachesize,(vm|esxi|ds|perf|counter|hri|cluster)]\n"
    + "vmbix.stats[hitrate,(vm|esxi|ds|perf|counter|hri|cluster)]  \n"
    + "about                                                       \n"
    + "cluster.discovery                                           \n"
    + "cluster.cpu[name,free]                                      \n"
    + "cluster.cpu[name,total]                                     \n"
    + "cluster.cpu[name,usage]                                     \n"
    + "cluster.cpu.num[name,threads]                               \n"
    + "cluster.cpu.num[nane,cores]                                 \n"
    + "cluster.mem[name,free]                                      \n"
    + "cluster.mem[name,total]                                     \n"
    + "cluster.mem[name,usage]                                     \n"
    + "cluster.hosts[name,online]                                  \n"
    + "cluster.hosts[name,maint]                                   \n"
    + "cluster.hosts[name,total]                                   \n"
    + "datacenter.discovery                                        \n"
    + "datacenter.status[name,(overall|config)]                    \n"
    + "datastore.discovery                                         \n"
    + "datastore.local[(uuid|name)]                                \n"
    + "datastore.size[(uuid|name),free]                            \n"
    + "datastore.size[(uuid|name),total]                           \n"
    + "datastore.size[(uuid|name),provisioned]                     \n"
    + "datastore.size[(uuid|name),uncommitted]                     \n"
    + "esx.connection[(uuid|name)]                                 \n"
    + "esx.uptime[(uuid|name)]                                     \n"
    + "esx.cpu.load[(uuid|name),cores]                             \n"
    + "esx.cpu.load[(uuid|name),total]                             \n"
    + "esx.cpu.load[(uuid|name),used]                              \n"
    + "esx.discovery                                               \n"
    + "esx.maintenance[(uuid|name)]                                \n"
    + "esx.memory[(uuid|name),total]                               \n"
    + "esx.memory[(uuid|name),used]                                \n"
    + "esx.path[(uuid|name),active]                                \n"
    + "esx.path[(uuid|name),dead]                                  \n"
    + "esx.path[(uuid|name),disabled]                              \n"
    + "esx.path[(uuid|name),standby]                               \n"
    + "esx.status[(uuid|name)]                                     \n"
    + "esx.vms.count[(uuid|name)]                                  \n"
    + "esx.vms.memory[(uuid|name),active]                          \n"
    + "esx.vms.memory[(uuid|name),ballooned]                       \n"
    + "esx.vms.memory[(uuid|name),compressed]                      \n"
    + "esx.vms.memory[(uuid|name),consumed]                        \n"
    + "esx.vms.memory[(uuid|name),overheadConsumed]                \n"
    + "esx.vms.memory[(uuid|name),private]                         \n"
    + "esx.vms.memory[(uuid|name),shared]                          \n"
    + "esx.vms.memory[(uuid|name),swapped]                         \n"
    + "esx.counter[(uuid|name),counter,[instance,interval]]        \n"
    + "esx.counter.discovery[(uuid|name),counter,[interval]]       \n"
    + "esx.counter.list[(uuid|name)]                               \n"
    + "event.latest[*]                                             \n"
    + "vm.consolidation[(uuid|name),needed]                        \n"
    + "vm.cpu.load[(uuid|name),cores]                              \n"
    + "vm.cpu.load[(uuid|name),total]                              \n"
    + "vm.cpu.load[(uuid|name),used]                               \n"
    + "vm.discovery[*]                                             \n"
    + "vm.discovery.full[*]                                        \n"
    + "vm.folder[(uuid|name)]                                      \n"
    + "vm.uptime[(uuid|name)]                                      \n"
    + "vm.name[(uuid|name)]                                        \n"
    + "vm.annotation[(uuid|name)]                                  \n"
    + "vm.guest.disk.discovery[(uuid|name)]                        \n"
    + "vm.guest.disk.capacity[(uuid|name),disk]                    \n"
    + "vm.guest.disk.free[(uuid|name),disk]                        \n"
    + "vm.guest.ip[(uuid|name)]                                    \n"
    + "vm.guest.(uuid|name)[(uuid|name)]                           \n"
    + "vm.guest.os[(uuid|name)]                                    \n"
    + "vm.guest.os.short[(uuid|name)]                              \n"
    + "vm.guest.tools.mounted[(uuid|name)]                         \n"
    + "vm.guest.tools.running[(uuid|name)]                         \n"
    + "vm.guest.tools.version[(uuid|name)]                         \n"
    + "vm.host[(uuid|name)]                                        \n"
    + "vm.memory[(uuid|name),active]                               \n"
    + "vm.memory[(uuid|name),ballooned]                            \n"
    + "vm.memory[(uuid|name),compressed]                           \n"
    + "vm.memory[(uuid|name),consumed]                             \n"
    + "vm.memory[(uuid|name),overheadConsumed]                     \n"
    + "vm.memory[(uuid|name),private]                              \n"
    + "vm.memory[(uuid|name),shared]                               \n"
    + "vm.memory[(uuid|name),swapped]                              \n"
    + "vm.memory[(uuid|name),total]                                \n"
    + "vm.counter[(uuid|name),counter,[instance,interval]]         \n"
    + "vm.counter.discovery[(uuid|name),counter,[interval]]        \n"
    + "vm.counter.list[(uuid|name)]                                \n"
    + "vm.powerstate[(uuid|name)]                                  \n"
    + "vm.status[(uuid|name)]                                      \n"
    + "vm.storage.committed[(uuid|name)]                           \n"
    + "vm.storage.uncommitted[(uuid|name)]                         \n"
    + "vm.storage.unshared[(uuid|name)]                            \n"
    + "vm.snapshot[(uuid|name)]                                    \n"
    + "pool.discovery                                              \n"
    + "pool.cpu[(uuid|name),usage]                                 \n"
    + "pool.mem[(uuid|name),usage]                                 \n"
    
    );
  }
  
  static void usage(String str) {
    String sname = "{-u|--username} \u001B[4musername\u001B[0m";
    String spass = "{-p|--password} \u001B[4mpassword\u001B[0m";
    String ssurl = "{-s|--serviceurl} \u001B[4mhttp[s]://serveraddr/sdk\u001B[0m";
    String sport = "{-P|--port} \u001B[4mlistenPort\u001B[0m";
    
    if (uname == null) {
      sname = "\u001B[5m" + sname + "\u001B[0m";
    }
    if (passwd == null) {
      spass = "\u001B[5m" + spass + "\u001B[0m";
    }
    if (sdkUrl == null) {
      ssurl = "\u001B[5m" + ssurl + "\u001B[0m";
    }
    if (port == null) {
      sport = "\u001B[5m" + sport + "\u001B[0m";
    }
    
    System.out.println(
    "Usage:\nvmbix "
    + sport + " " + ssurl + " " + sname + " " + spass + " [-f|--pid pidfile]" + "\n"
    + "or\nvmbix [-c|--config] config_file  [-f|--pid pidfile]\n"
    + (str != null ? str : "")
    );
  }
  
  public static synchronized void putConnection(Socket socket) throws IOException {
    if (sockets.size() < maxConnections) {
      sockets.add(socket);
      if (sockets.size() > (Thread.activeCount() - 2)) {
        Request request = new Request(serviceInstance, null, inventoryNavigator, performanceManager);
        Connection thread = new Connection(request);
        thread.start();
      }
      } else {
      LOG.warn("Maximum concurrent connections reached, closing connection");
      socket.close();
    }
  }
  
  public static synchronized Request pullConnection() {
    if (sockets.isEmpty()) {
      return null;
      } else {
      
      Request request = new Request(serviceInstance, sockets.remove(0), null, null);
      return request;
    }
  }
  
  public static synchronized Boolean updateConnection() throws IOException {
    try {
      serviceInstance = new ServiceInstance(new URL(sdkUrl), uname, passwd, true, connectTimeout, readTimeout);
      if (serviceInstance == null) {
        LOG.error("serviceInstance in null! Connection failed.");
        return false;
      }
      Folder rootFolder = serviceInstance.getRootFolder();
      inventoryNavigator = new InventoryNavigator(serviceInstance.getRootFolder());
      performanceManager = serviceInstance.getPerformanceManager();
      // retrieve all the available performance counters
      PerfCounterInfo[] pcis = performanceManager.getPerfCounter();
      return true;
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    return false;
  }
  
  public static synchronized void shutdown() {
    try {
      deletePid();
      } catch (Exception e) {
      //Catch exception if any
      LOG.error("Error deleting pid: " + e.toString());
    }
    try {
      serviceInstance.getServerConnection().logout();
      LOG.info("disconnected");
      } catch (Exception e) {
      //Catch exception if any
      LOG.error("Error disconnecting: " + e.toString());
    }
    LOG.info("Shutted down");
  }
  
  public static synchronized Request updateConnectionSafe() {
    try {
      updateConnection();
      } catch (IOException e) {
      LOG.error("Connection update error: " + e.toString());
    }
    return new Request(serviceInstance, null, inventoryNavigator, performanceManager);
  }
  
  static void sleep(int delay) {
    try {
      Thread.sleep(delay);
      } catch (InterruptedException e) {
      LOG.error("thread sleep error: " + e.toString());
    }
  }
  
  static void server() throws IOException {
    ServerSocket listen;
    if (updateConnection() == false) {
      LOG.error("Cannot connect to the VMWare SDK URL");
      System.exit(3);
    }
    if (ipaddr != null) {
      LOG.info("starting server on " + ipaddr + "/" + port.toString());
      InetAddress addr = InetAddress.getByName(ipaddr);
      listen = new ServerSocket(port, 50, addr);//(port, backlog, bindaddr)
      } else {
      LOG.info("starting server on port " + port.toString());
      listen = new ServerSocket(port);//(port)
    }
    LOG.info("server started");
    while (true) {
      Socket connected = listen.accept();
      requests++;
      putConnection(connected);
    }
  }
  
  static class Request {
    
    public Socket socket;
    public ServiceInstance serviceInstance;
    public InventoryNavigator inventoryNavigator;
    public PerformanceManager performanceManager;
    
    Request(ServiceInstance si, Socket socket, InventoryNavigator iv, PerformanceManager pm) {
      this.socket = socket;
      this.serviceInstance = si;
      this.inventoryNavigator = iv;
      this.performanceManager = pm;
    }
  }
  
  static class Connection extends Thread {
    
    Socket connected;
    ServiceInstance serviceInstance;
    InventoryNavigator inventoryNavigator;
    PerformanceManager performanceManager;
    
    Connection(Request request) {
      this.serviceInstance = request.serviceInstance;
      this.inventoryNavigator = request.inventoryNavigator;
      this.performanceManager = request.performanceManager;
    }
    
    static private String checkPattern(Pattern pattern, String string) {
      Matcher matcher = pattern.matcher(string);
      if (matcher.find()) {
        return matcher.group(1);
      }
      return null;
    }
    
    static private String[] checkMultiplePattern(Pattern pattern, String string) {
      Matcher matcher = pattern.matcher(string);
      if (matcher.find()) {
        ArrayList<String> list = new ArrayList<String>();
        for (int m = 1; m < matcher.groupCount() + 1; m++) {
          list.add(matcher.group(m));
        }
        String[] groups = list.toArray(new String[list.size()]);
        return groups;
      }
      return null;
    }
    
    private ValidationResult checkAllPatterns(String string) throws IOException {
      LOG.debug("Parsing this request : " + string);
      
      Pattern pPoolDiscovery             = Pattern.compile("^(?:\\s*ZBXD.)?.*pool\\.(discovery)");
      Pattern pPoolMemUsage              = Pattern.compile("^(?:\\s*ZBXD.)?.*pool\\.mem\\[(.+),usage\\]");
      Pattern pPoolCpuUsage              = Pattern.compile("^(?:\\s*ZBXD.)?.*pool\\.cpu\\[(.+),usage\\]");
      Pattern pPing                      = Pattern.compile("^(?:\\s*ZBXD.)?.*(ping)");
      Pattern pAbout                     = Pattern.compile("^(?:\\s*ZBXD.)?.*(about)");
      Pattern pVersion                   = Pattern.compile("^(?:\\s*ZBXD.)?.*(vmbix\\.version)");
      Pattern pThreadCount               = Pattern.compile("^(?:\\s*ZBXD.)?.*(vmbix\\.stats\\[threads\\])");
      Pattern pConnectionQueue           = Pattern.compile("^(?:\\s*ZBXD.)?.*(vmbix\\.stats\\[queue\\])");
      Pattern pRequestCount              = Pattern.compile("^(?:\\s*ZBXD.)?.*(vmbix\\.stats\\[requests\\])");
      Pattern pCacheSize                 = Pattern.compile("^(?:\\s*ZBXD.)?.*vmbix\\.stats\\[cachesize,(.+)\\]");
      Pattern pCacheHitRate              = Pattern.compile("^(?:\\s*ZBXD.)?.*vmbix\\.stats\\[hitrate,(.+)\\]");
      Pattern pClusters                  = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.(discovery)");
      Pattern pClusterCpuFree            = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.cpu\\[(.+),free\\]");
      Pattern pClusterCpuTotal           = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.cpu\\[(.+),total\\]");
      Pattern pClusterCpuUsage           = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.cpu\\[(.+),usage\\]");
      Pattern pClusterCpuThreads         = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.cpu.num\\[(.+),threads\\]");
      Pattern pClusterCpuCores           = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.cpu.num\\[(.+),cores\\]");
      Pattern pClusterMemFree            = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.mem\\[(.+),free\\]");
      Pattern pClusterMemTotal           = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.mem\\[(.+),total\\]");
      Pattern pClusterMemUsage           = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.mem\\[(.+),usage\\]");
      Pattern pClusterHostsOnline        = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.hosts\\[(.+),online\\]");
      Pattern pClusterHostsMaint         = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.hosts\\[(.+),maint\\]");
      Pattern pClusterHostsTotal         = Pattern.compile("^(?:\\s*ZBXD.)?.*cluster\\.hosts\\[(.+),total\\]");
      Pattern pDatacenters               = Pattern.compile("^(?:\\s*ZBXD.)?.*datacenter\\.(discovery)");
      Pattern pDatacenterStatus          = Pattern.compile("^(?:\\s*ZBXD.)?.*datacenter\\.status\\[(.+),(overall|config)\\]");
      Pattern pLatestEvent               = Pattern.compile("^(?:\\s*ZBXD.)?.*(event\\.latest)");
      Pattern pVMsFullDiscovery          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.(discovery)\\.(full)");
      Pattern pVMs                       = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.(discovery)");
      Pattern pHosts                     = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.(discovery)");
      Pattern pDatastores                = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.(discovery)");
      Pattern pHostConnection            = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.connection\\[(.+)\\]");
      Pattern pHostUptime                = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.uptime\\[(.+)\\]");
      Pattern pHostStatus                = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.status\\[(.+)\\]");
      Pattern pHostName                  = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.name\\[(.+)\\]");
      Pattern pVmStatus                  = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.status\\[(.+)\\]");
      Pattern pHostMaintenance           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.maintenance\\[(.+)\\]");
      Pattern pHostCpuUsed               = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),used\\]");
      Pattern pHostDisabledPaths         = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),disabled\\]");
      Pattern pHostActivePaths           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),active\\]");
      Pattern pHostStandbyPaths          = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),standby\\]");
      Pattern pHostDeadPaths             = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),dead\\]");
      Pattern pHostCpuTotal              = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),total\\]");
      Pattern pHostVMs                   = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms\\.count\\[(.+)\\]");
      Pattern pHostCpuCores              = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),cores\\]");
      Pattern pHostMemUsed               = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),used\\]");
      Pattern pHostMemTotal              = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),total\\]");
      Pattern pHostMemStatsPrivate       = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),private\\]");
      Pattern pHostMemStatsShared        = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),shared\\]");
      Pattern pHostMemStatsSwapped       = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),swapped\\]");
      Pattern pHostMemStatsCompressed    = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),compressed\\]");
      Pattern pHostMemStatsOverhCons     = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),overheadConsumed\\]");
      Pattern pHostMemStatsConsumed      = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),consumed\\]");
      Pattern pHostMemStatsBallooned     = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),ballooned\\]");
      Pattern pHostMemStatsActive        = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),active\\]");
      Pattern pHostAvailablePerfCounters = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.counter\\.list\\[(.+)\\]");
      Pattern pHostPerfCounterValue      = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.counter\\[([^,]+),([^,]+)(?:,([^,]*))?(?:,([^,]*))?\\]");
      Pattern pHostPerfCounterDiscovery  = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.counter\\.discovery\\[([^,]+),([^,]+)(?:,([^,]*))?\\]");
      Pattern pVmName                    = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.name\\[(.+)\\]");
      Pattern pVmCpuUsed                 = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),used\\]");
      Pattern pVmCpuTotal                = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),total\\]");
      Pattern pVmCpuCores                = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),cores\\]");
      Pattern pVmMemPrivate              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),private\\]");
      Pattern pVmMemShared               = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),shared\\]");
      Pattern pVmMemSwapped              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),swapped\\]");
      Pattern pVmMemCompressed           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),compressed\\]");
      Pattern pVmMemOverheadConsumed     = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),overheadConsumed\\]");
      Pattern pVmMemConsumed             = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),consumed\\]");
      Pattern pVmMemBallooned            = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),ballooned\\]");
      Pattern pVmMemActive               = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),active\\]");
      Pattern pVmMemSize                 = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),total\\]");
      Pattern pVmHost                    = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.host\\[(.+)\\]");
      Pattern pVmPowerState              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.powerstate\\[(.+)\\]");
      Pattern pVmFolder                  = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.folder\\[(.+)\\]");
      Pattern pVmUptime                  = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.uptime\\[(.+)\\]");
      Pattern pVmAnnotation              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.annotation\\[(.+)\\]");
      Pattern pVmSnapshot                = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.snapshot\\[(.+)\\]");
      Pattern pVmStorageCommitted        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.committed\\[(.+)\\]");
      Pattern pVmStorageUncommitted      = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.uncommitted\\[(.+)\\]");
      Pattern pVmStorageUnshared         = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.unshared\\[(.+)\\]");
      Pattern pVmGuestShortName          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.os\\.short\\[(.+)\\]");
      Pattern pVmGuestFullName           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.os\\[(.+)\\]");
      Pattern pVmGuestHostName           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.name\\[(.+)\\]");
      Pattern pVmGuestDisks              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.all\\[(.+)\\]");
      Pattern pVmGuestDisksDiscovery     = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.discovery\\[(.+)\\]");
      Pattern pVmGuestDiskCapacity       = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.capacity\\[(.+),(.+)\\]");
      Pattern pVmGuestDiskFreeSpace      = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.free\\[(.+),(.+)\\]");
      Pattern pVmAvailablePerfCounters   = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.counter\\.list\\[(.+)\\]");
      Pattern pVmPerfCounterValue        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.counter\\[([^,]+),([^,]+)(?:,([^,]*))?(?:,([^,]*))?\\]");
      Pattern pVmPerfCounterDiscovery    = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.counter\\.discovery\\[([^,]+),([^,]+)(?:,([^,]*))?\\]");
      Pattern pVmGuestIpAddress          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.ip\\[(.+)\\]");
      Pattern pVmGuestToolsRunningStatus = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.running\\[(.+)\\]");
      Pattern pVmGuestToolsVersionStatus = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.version\\[(.+)\\]");
      Pattern pVmConsolidationNeeded     = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.consolidation\\[(.+),needed\\]");
      Pattern pVmToolsInstallerMounted   = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.mounted\\[(.+)\\]");
      Pattern pDatastoreLocal            = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.local\\[(.+)\\]");
      Pattern pDatastoreFree             = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),free\\]");
      Pattern pDatastoreTotal            = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),total\\]");
      Pattern pDatastoreProvisioned      = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),provisioned\\]");
      Pattern pDatastoreUncommitted      = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),uncommitted\\]");
      
      String found;
      String[] founds;
      ValidationResult result = new ValidationResult();
      
      found = checkPattern(pPoolCpuUsage, string);
      if (found != null) {
        result = getPoolCpuUsage(found);
      }
      found = checkPattern(pPoolMemUsage, string);
      if (found != null) {
        result = getPoolMemUsage(found);
      }
      found = checkPattern(pPoolDiscovery, string);
      if (found != null){
        result = getPools();
      }
      found = checkPattern(pPing, string);
      if (found != null) {
        result = getPing();
      }
      found = checkPattern(pAbout, string);
      if (found != null) {
        result = getAbout();
      }
      found = checkPattern(pVersion, string);
      if (found != null) {
        result = getVersion();
      }
      found = checkPattern(pThreadCount, string);
      if (found != null) {
        result = getThreadCount();
      }
      found = checkPattern(pConnectionQueue, string);
      if (found != null) {
        result = getConnectionQueue();
      }
      found = checkPattern(pRequestCount, string);
      if (found != null) {
        result = getRequestCount();
      }
      found = checkPattern(pCacheSize, string);
      if (found != null) {
        result = getCacheSize(found);
      }
      found = checkPattern(pCacheHitRate, string);
      if (found != null) {
        result = getCacheHitRate(found);
      }
      found = checkPattern(pClusters, string);
      if (found != null) {
        result = getClusters();
      }
      found = checkPattern(pClusterCpuFree, string);
      if (found != null) {
        result = getClusterCpuFree(found);
      }
      found = checkPattern(pClusterCpuTotal, string);
      if (found != null) {
        result = getClusterCpuTotal(found);
      }
      found = checkPattern(pClusterCpuUsage, string);
      if (found != null) {
        result = getClusterCpuUsage(found);
      }
      found = checkPattern(pClusterCpuThreads, string);
      if (found != null) {
        result = getClusterCpuThreads(found);
      }
      found = checkPattern(pClusterCpuCores, string);
      if (found != null) {
        result = getClusterCpuCores(found);
      }
      found = checkPattern(pClusterMemFree, string);
      if (found != null) {
        result = getClusterMemFree(found);
      }
      found = checkPattern(pClusterMemTotal, string);
      if (found != null) {
        result = getClusterMemTotal(found);
      }
      found = checkPattern(pClusterMemUsage, string);
      if (found != null) {
        result = getClusterMemUsage(found);
      }
      found = checkPattern(pClusterHostsOnline, string);
      if (found != null) {
        result = getClusterHostsOnline(found);
      }
      found = checkPattern(pClusterHostsMaint, string);
      if (found != null) {
        result = getClusterHostsMaint(found);
      }
      found = checkPattern(pClusterHostsTotal, string);
      if (found != null) {
        result = getClusterHostsTotal(found);
      }
      found = checkPattern(pDatacenters, string);
      if (found != null) {
        result = getDatacenters();
      }
      founds = checkMultiplePattern(pDatacenterStatus, string);
      if (founds != null) {
        result = getDatacenterStatus(founds[0], founds[1]);
      }
      found = checkPattern(pLatestEvent, string);
      if (found != null) {
        result = getLatestEvent();
      }
      found = checkPattern(pVMsFullDiscovery, string);
      if (found != null) {
        result = getVMsFullDiscovery();
      }
      found = checkPattern(pVMs, string);
      if (found != null) {
        result = getVMs();
      }
      found = checkPattern(pHosts, string);
      if (found != null) {
        result = getHosts();
      }
      found = checkPattern(pDatastores, string);
      if (found != null) {
        result = getDatastores();
      }
      found = checkPattern(pHostConnection, string);
      if (found != null) {
        result = getHostConnection(found);
      }
      found = checkPattern(pHostUptime, string);
      if (found != null) {
        result = getHostUptime(found);
      }
      found = checkPattern(pHostStatus, string);
      if (found != null) {
        result = getHostStatus(found);
      }
      found = checkPattern(pHostName, string);
      if (found != null) {
        result = getHostName(found);
      }
      found = checkPattern(pVmStatus, string);
      if (found != null) {
        result = getVmStatus(found);
      }
      found = checkPattern(pVmName, string);
      if (found != null) {
        result = getVmName(found);
      }
      found = checkPattern(pHostMaintenance, string);
      if (found != null) {
        result = getHostMaintenance(found);
      }
      found = checkPattern(pHostCpuUsed, string);
      if (found != null) {
        result = getHostCpuUsed(found);
      }
      found = checkPattern(pHostCpuTotal, string);
      if (found != null) {
        result = getHostCpuTotal(found);
      }
      found = checkPattern(pHostVMs, string);
      if (found != null) {
        result = getHostVMs(found);
      }
      found = checkPattern(pHostDisabledPaths, string);
      if (found != null) {
        result = getHostDisabledPaths(found);
      }
      found = checkPattern(pHostActivePaths, string);
      if (found != null) {
        result = getHostActivePaths(found);
      }
      found = checkPattern(pHostStandbyPaths, string);
      if (found != null) {
        result = getHostStandbyPaths(found);
      }
      found = checkPattern(pHostDeadPaths, string);
      if (found != null) {
        result = getHostDeadPaths(found);
      }
      found = checkPattern(pHostMemUsed, string);
      if (found != null) {
        result = getHostMemUsed(found);
      }
      found = checkPattern(pHostMemTotal, string);
      if (found != null) {
        result = getHostMemTotal(found);
      }
      found = checkPattern(pHostMemStatsPrivate, string);
      if (found != null) {
        result = getHostVmsStatsPrivate(found);
      }
      found = checkPattern(pHostMemStatsShared, string);
      if (found != null) {
        result = getHostVmsStatsShared(found);
      }
      found = checkPattern(pHostMemStatsSwapped, string);
      if (found != null) {
        result = getHostVmsStatsSwapped(found);
      }
      found = checkPattern(pHostMemStatsCompressed, string);
      if (found != null) {
        result = getHostVmsStatsCompressed(found);
      }
      found = checkPattern(pHostMemStatsOverhCons, string);
      if (found != null) {
        result = getHostVmsStatsOverhCons(found);
      }
      found = checkPattern(pHostMemStatsConsumed, string);
      if (found != null) {
        result = getHostVmsStatsConsumed(found);
      }
      found = checkPattern(pHostMemStatsBallooned, string);
      if (found != null) {
        result = getHostVmsStatsBallooned(found);
      }
      found = checkPattern(pHostMemStatsActive, string);
      if (found != null) {
        result = getHostVmsStatsActive(found);
      }
      found = checkPattern(pHostAvailablePerfCounters, string);
      if (found != null) {
        result = getHostAvailablePerfCounters(found);
      }
      founds = checkMultiplePattern(pHostPerfCounterValue, string);
      if (founds != null) {
        result = getHostPerfCounterValue(founds);
      }
      founds = checkMultiplePattern(pHostPerfCounterDiscovery, string);
      if (founds != null) {
        result = getHostPerfCounterDiscovery(founds);
      }
      found = checkPattern(pVmCpuUsed, string);
      if (found != null) {
        result = getVmCpuUsed(found);
      }
      found = checkPattern(pVmCpuTotal, string);
      if (found != null) {
        result = getVmCpuTotal(found);
      }
      found = checkPattern(pVmCpuCores, string);
      if (found != null) {
        result = getVmCpuCores(found);
      }
      found = checkPattern(pVmMemPrivate, string);
      if (found != null) {
        result = getVmMemPrivate(found);
      }
      found = checkPattern(pVmMemShared, string);
      if (found != null) {
        result = getVmMemShared(found);
      }
      found = checkPattern(pVmMemSwapped, string);
      if (found != null) {
        result = getVmMemSwapped(found);
      }
      found = checkPattern(pVmMemCompressed, string);
      if (found != null) {
        result = getVmMemCompressed(found);
      }
      found = checkPattern(pVmMemOverheadConsumed, string);
      if (found != null) {
        result = getVmMemOverheadConsumed(found);
      }
      found = checkPattern(pVmMemConsumed, string);
      if (found != null) {
        result = getVmMemConsumed(found);
      }
      found = checkPattern(pVmMemBallooned, string);
      if (found != null) {
        result = getVmMemBallooned(found);
      }
      found = checkPattern(pVmMemActive, string);
      if (found != null) {
        result = getVmMemActive(found);
      }
      found = checkPattern(pVmMemSize, string);
      if (found != null) {
        result = getVmMemSize(found);
      }
      found = checkPattern(pVmHost, string);
      if (found != null) {
        result = getVmHost(found);
      }
      found = checkPattern(pVmPowerState, string);
      if (found != null) {
        result = getVmPowerState(found);
      }
      found = checkPattern(pVmFolder, string);
      if (found != null) {
        result = getVmFolder(found);
      }
      found = checkPattern(pVmUptime, string);
      if (found != null) {
        result = getVmUptime(found);
      }
      found = checkPattern(pVmAnnotation, string);
      if (found != null) {
        result = getVmAnnotation(found);
      }
      found = checkPattern(pVmSnapshot, string);
      if (found != null) {
        result = getVmSnapshot(found);
      }
      found = checkPattern(pVmStorageCommitted, string);
      if (found != null) {
        result = getVmStorageCommitted(found);
      }
      found = checkPattern(pVmStorageUncommitted, string);
      if (found != null) {
        result = getVmStorageUncommitted(found);
      }
      found = checkPattern(pVmStorageUnshared, string);
      if (found != null) {
        result = getVmStorageUnshared(found);
      }
      found = checkPattern(pVmGuestShortName, string);
      if (found != null) {
        result = getVmGuestShortName(found);
      }
      found = checkPattern(pVmGuestFullName, string);
      if (found != null) {
        result = getVmGuestFullName(found);
      }
      found = checkPattern(pVmGuestHostName, string);
      if (found != null) {
        result = getVmGuestHostName(found);
      }
      found = checkPattern(pVmGuestIpAddress, string);
      if (found != null) {
        result = getVmGuestIpAddress(found);
      }
      found = checkPattern(pVmGuestDisks, string);
      if (found != null) {
        result = getVmGuestDisks(found);
      }
      found = checkPattern(pVmGuestDisksDiscovery, string);
      if (found != null) {
        result = getVmGuestDisks(found);
      }
      founds = checkMultiplePattern(pVmGuestDiskCapacity, string);
      if (founds != null) {
        result = getVmGuestDiskCapacity(founds[0], founds[1]);
      }
      founds = checkMultiplePattern(pVmGuestDiskFreeSpace, string);
      if (founds != null) {
        result = getVmGuestDiskFreeSpace(founds[0], founds[1]);
      }
      found = checkPattern(pVmAvailablePerfCounters, string);
      if (found != null) {
        result = getVmAvailablePerfCounters(found);
      }
      founds = checkMultiplePattern(pVmPerfCounterValue, string);
      if (founds != null) {
        result = getVmPerfCounterValue(founds);
      }
      founds = checkMultiplePattern(pVmPerfCounterDiscovery, string);
      if (founds != null) {
        result = getVmPerfCounterDiscovery(founds);
      }
      found = checkPattern(pVmGuestToolsRunningStatus, string);
      if (found != null) {
        result = getVmGuestToolsRunningStatus(found);
      }
      found = checkPattern(pVmGuestToolsVersionStatus, string);
      if (found != null) {
        result = getVmGuestToolsVersionStatus(found);
      }
      found = checkPattern(pVmToolsInstallerMounted, string);
      if (found != null) {
        result = getVmToolsInstallerMounted(found);
      }
      found = checkPattern(pVmConsolidationNeeded, string);
      if (found != null) {
        result = getVmConsolidationNeeded(found);
      }
      found = checkPattern(pHostCpuCores, string);
      if (found != null) {
        result = getHostCpuCores(found);
      }
      found = checkPattern(pDatastoreLocal, string);
      if (found != null) {
        result = getDatastoreLocal(found);
      }
      found = checkPattern(pDatastoreFree, string);
      if (found != null) {
        result = getDatastoreSizeFree(found);
      }
      found = checkPattern(pDatastoreTotal, string);
      if (found != null) {
        result = getDatastoreSizeTotal(found);
      }
      found = checkPattern(pDatastoreProvisioned, string);
      if (found != null) {
        result = getDatastoreSizeProvisioned(found);
      }
      found = checkPattern(pDatastoreUncommitted, string);
      if (found != null) {
        result = getDatastoreSizeUncommitted(found);
      }
      
      return result;
    }
    
    private Boolean reconnectRequred(ManagedEntity me) throws IOException {
      Boolean required = false;
      if (me == null) {
        ManagedEntity[] mes = inventoryNavigator.searchManagedEntities("HostSystem");
        if (mes == null || mes.length == 0) {
          LOG.warn("No hosts found, connection seems to be broken, attempting reconnect");
          
          // Clear all caches
          vmCache.invalidateAll();      
          esxiCache.invalidateAll();    
          dsCache.invalidateAll();      
          hostPerfCache.invalidateAll();
          counterCache.invalidateAll(); 
          hriCache.invalidateAll();     
          clCache.invalidateAll();     
          
          // Reconnect to vCenter
          Request request    = VmBix.updateConnectionSafe();
          serviceInstance    = request.serviceInstance;
          inventoryNavigator = request.inventoryNavigator;
          performanceManager = request.performanceManager;
          required = true;
        }
      }
      return required;
    }
    
    private ManagedEntity getManagedEntity(String id, String meType) throws IOException {
      ManagedEntity me = null;
      if (useUuid) {
        me = getManagedEntityByUuid(id, meType);
        if (reconnectRequred(me)) {
          me = getManagedEntityByUuid(id, meType);
        }
        } else {
        me = getManagedEntityByName(id, meType);
        if (reconnectRequred(me)) {
          me = getManagedEntityByName(id, meType);
        }
      }
      return me;
    }
    
    private ManagedEntity getManagedEntityByName(String name, String meType) throws IOException {
      //* extra cache for getManagedEntityByName
      ManagedEntity me = null;
      switch (meType) {
        case "HostSystem":
        me = esxiCache.getIfPresent(name);
        break;
        case "VirtualMachine":
        me = vmCache.getIfPresent(name);
        break;
        case "Datastore":
        me = dsCache.getIfPresent(name);
        break;
        case "ClusterComputeResource":
        me = clCache.getIfPresent(name);
        break;
      }
      //add cache for Datacenters
      if (me != null) {
        LOG.debug("CacheHIT: " + meType + " name: " + name);
        return me;
      }
      me = inventoryNavigator.searchManagedEntity(meType, name);
      if (me != null) {
        switch (meType) {
          case "HostSystem":
          esxiCache.put(name, me);
          break;
          case "VirtualMachine":
          vmCache.put(name, me);
          break;
          case "Datastore":
          dsCache.put(name, me);
          break;
          case "ClusterComputeResource":
          clCache.put(name, me);
          break;
        }
        LOG.debug("CacheMISS: " + meType + " name: " + name);
      }
      return me;
    }
    
    //ClusterComputeResource
    private ManagedEntity getManagedEntityByUuid(String uuid, String meType) throws IOException {
      ManagedEntity me = null;
      switch (meType) {
        case "HostSystem":
        me = esxiCache.getIfPresent(uuid);
        break;
        case "VirtualMachine":
        me = vmCache.getIfPresent(uuid);
        break;
        case "Datastore":
        me = dsCache.getIfPresent(uuid);
        break;
      }
      if (me != null) {
        LOG.debug("CacheHIT: " + meType + " uuid: " + uuid);
        return me;
      }
      ManagedEntity[] mes = getManagedEntities(meType);
      String meUuid = null;
      for (int i = 0; mes != null && i < mes.length; i++) {
        ManagedEntity ent = mes[i];
        if (ent == null) {
          continue;
        }
        switch (meType) {
          case "HostSystem":
          HostSystem host = (HostSystem) ent;
          HostListSummary hs = host.getSummary();
          HostHardwareSummary hd = hs.getHardware();
          meUuid = hd.getUuid();
          break;
          case "VirtualMachine":
          VirtualMachine vm = (VirtualMachine) ent;
          VirtualMachineConfigInfo vmcfg = vm.getConfig();
          meUuid = vmcfg.getUuid();
          break;
          case "Datastore":
          Datastore ds = (Datastore) ent;
          DatastoreInfo dinfo = ds.getInfo();
          meUuid = dinfo.url.substring(19, dinfo.url.length() - 1);
          break;
        }
        if (meUuid == null) {
          continue;
        }
        if (uuid.equals(meUuid)) {
          me = ent;
          switch (meType) {
            case "HostSystem":
            esxiCache.put(meUuid, ent);
            break;
            case "VirtualMachine":
            vmCache.put(meUuid, ent);
            break;
            case "Datastore":
            dsCache.put(meUuid, ent);
            break;
          }
          LOG.debug("CacheMISS: " + meType + " uuid: " + uuid);
          break;
        }
      }
      return me;
    }
    
    private ManagedEntity[] getManagedEntities(String meType) throws IOException {
      ManagedEntity[] mes = inventoryNavigator.searchManagedEntities(meType);
      return mes;
    }
    
    private List getCounterByName(String name) throws IOException {
      List<String> ctrProps = counterCache.getIfPresent(name);
      if (ctrProps != null) {
        LOG.debug("CacheHIT: PerfCounter name: " + name);
        return ctrProps;
      }
      PerfCounterInfo[] pcis = performanceManager.getPerfCounter();
      String perfCounter = "";
      for (int i = 0; i < pcis.length; i++) {
        perfCounter = pcis[i].getGroupInfo().getKey() + "." + pcis[i].getNameInfo().getKey() + "." + pcis[i].getRollupType().toString();
        ctrProps = new ArrayList<String>();
        ctrProps.add(String.valueOf(pcis[i].getKey()));
        ctrProps.add(pcis[i].getUnitInfo().getKey().toString());
        ctrProps.add(pcis[i].getStatsType().toString());
        ctrProps.add(pcis[i].getRollupType().toString());
        counterCache.put(perfCounter, ctrProps);
        if (perfCounter.equals(name)) {
          LOG.debug("CacheMISS: PerfCounter name: " + name);
          return ctrProps;
        }
      }
      return counterCache.getIfPresent(name);
    }
    
    /**
      * Returns cached HostRuntimeInfo or get new from vCenter
    */
    private HostRuntimeInfo getHostRuntimeInfo(String name, HostSystem host) {
      HostRuntimeInfo hri = hriCache.getIfPresent(name);
      if (hri != null) {
        LOG.debug("CacheHIT: HostRuntimeInfo name: " + name);
        return hri;
      }
      hri = host.getRuntime();
      hriCache.put(name, hri);
      LOG.debug("CacheMISS: HostRuntimeInfo name: " + name);
      return hri;
    }
    
    private PerfMetricId[] getHostPerformanceManager(HostSystem host, int interval) throws RemoteException {
      PerfMetricId[] queryAvailablePerfMetric = null;
      String name = host.getName();
      queryAvailablePerfMetric = hostPerfCache.getIfPresent(name);
      if (queryAvailablePerfMetric != null) {
        LOG.debug("CacheHIT: PerfID name: " + name);
        return queryAvailablePerfMetric;
      }
      queryAvailablePerfMetric = performanceManager.queryAvailablePerfMetric(host, null, null, interval);
      hostPerfCache.put(name, queryAvailablePerfMetric);
      LOG.debug("CacheMISS: PerfID name: " + name);
      return queryAvailablePerfMetric;
    }
    
    /**
      * Always return "1"
    */
    private ValidationResult getPing() throws IOException {
      ValidationResult result = new ValidationResult(0, "1");
      return result;
    }
    
    /**
      * Returns VmBix version
    */
    private ValidationResult getVersion() throws IOException {
      ValidationResult result = new ValidationResult();
      String version = null;
      
      if (version == null) {
        Package aPackage = getClass().getPackage();
        if (aPackage != null) {
          version = aPackage.getImplementationVersion();
          if (version == null) {
            version = aPackage.getSpecificationVersion();
          }
        }
      }
      
      if (version == null) {
        result.status = 2;
      }
      
      result.message = version;
      return result;
    }
    
    /**
      * Returns the number of worker threads
    */
    private ValidationResult getThreadCount() throws IOException {
      Integer activeThreads = (Thread.activeCount() - 1);
      ValidationResult result = new ValidationResult(0, activeThreads.toString());
      return result;
    }
    
    /**
      * Returns the number of connections waiting for a worker thread
    */
    private ValidationResult getConnectionQueue() throws IOException {
      Integer size = sockets.size();
      ValidationResult result = new ValidationResult(0, Integer.toString(size));
      return result;
    }
    
    /**
      * Returns the number of requests accepted by VmBix
    */
    private ValidationResult getRequestCount() throws IOException {
      ValidationResult result = new ValidationResult(0, Long.toString(requests));
      return result;
    }
    
    /**
      * Returns the size of a VmBix cache
    */
    private ValidationResult getCacheSize(String cacheName) throws IOException {
      ValidationResult result;
      long size;
      
      switch (cacheName) {
        case "vm":
        size = vmCache.size();
        break;
        case "esxi":
        size = esxiCache.size();
        break;
        case "ds":
        size = dsCache.size();
        break;
        case "perf":
        size = hostPerfCache.size();
        break;
        case "counter":
        size = counterCache.size();
        break;
        case "hri":
        size = hriCache.size();
        break;
        case "cluster":
        size = clCache.size();
        break;
        default:
        result = new ValidationResult(2, String.format("Cache %s does not exist", cacheName));
        return result; 
      }
      
      result = new ValidationResult(0, Long.toString(size)) ;
      return result;
    }
    
    /**
      * Returns the hit rate of a VmBix cache
    */
    private ValidationResult getCacheHitRate(String cacheName) throws IOException {
      ValidationResult result;
      double hitrate;
      
      switch (cacheName) {
        case "vm":
        hitrate = vmCache.stats().hitRate();
        break;
        case "esxi":
        hitrate = esxiCache.stats().hitRate();
        break;
        case "ds":
        hitrate = dsCache.stats().hitRate();
        break;
        case "perf":
        hitrate = hostPerfCache.stats().hitRate();
        break;
        case "counter":
        hitrate = counterCache.stats().hitRate();
        break;
        case "hri":
        hitrate = hriCache.stats().hitRate();
        break;
        case "cluster":
        hitrate = clCache.stats().hitRate();
        break;
        default:
        result = new ValidationResult(2, String.format("Cache %s does not exist", cacheName));
        return result;       
      }
      
      result = new ValidationResult(0, Double.toString(hitrate)) ;
      return result;
    }
    
    /**
      * Returns the CPU power of a host in MHz
    */
    private int getHostMHZ(HostSystem host) throws IOException {
      HostListSummary hls = host.getSummary();
      HostHardwareSummary hosthwi = hls.getHardware();
      Integer mhz = hosthwi.getCpuMhz();
      if (mhz == null) {
        mhz = 0;
      }
      return mhz;
    }
    
    /**
      * Returns the connection state of a host
    */
    private ValidationResult getHostConnection(String hostName) throws IOException {
      ValidationResult result;
      Integer intStatus = 2;
      try {
      	HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
        }
        else {
	        HostRuntimeInfo hrti = host.getRuntime();
	        HostSystemConnectionState hscs = hrti.getConnectionState();
	        if (null != hscs.name()) {
	          switch (hscs.name()) {
	            case "connected":
              intStatus = 0;
              break;
	            case "disconnected":
              intStatus = 1;
              break;
	            default:
              intStatus = 2; // "unknown"
              break;
              }
            }
          }
	      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;       
      }

	    result = new ValidationResult(0, intStatus.toString());
      return result;       
    }
    
    /**
      * Returns the status of a host 0 -> grey 1 -> green 2 -> yellow 3 ->
      * red 4 -> unknown
    */
    private ValidationResult getHostStatus(String hostName) throws IOException {
      ValidationResult result;
	    Integer intStatus = 4;    
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");

	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
        } 
        else {
	        HostListSummary hsum = host.getSummary();
	        String hs = hsum.getOverallStatus().toString();
	        if (null != hs) {
	          switch (hs) {
	            case "grey":
              intStatus = 0;
              break;
	            case "green":
              intStatus = 1;
              break;
	            case "yellow":
              intStatus = 2;
              break;
	            case "red":
              intStatus = 3;
              break;
	            default:
              intStatus = 4; // unknown
              break;
            }
          }
        }
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;       
      }
      
	    result = new ValidationResult(0, intStatus.toString());
      return result; 
    }
    
    /**
      * Returns the display name of a host
    */
    private ValidationResult getHostName(String hostName) throws IOException {
      ValidationResult result;
      String name;
      try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
        } else {
	        name = host.getName();
        }
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }
      
	    result = new ValidationResult(0, name);
      return result;    
    }
    
    /**
      * Returns the number of dead paths to the storage of a host
    */
    private ValidationResult getHostDeadPaths(String hostName) throws IOException {
      ValidationResult result;
      Integer nb = 0;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
        } else {
	        HostConfigInfo hc = host.getConfig();
	        HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
	        for (int m = 0; m < mp.length; m++) {
	          if ("dead".equals(mp[m].getPathState())) {
	            nb++;
            }
          }
        }

	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result; 
      }
      
      result = new ValidationResult(0, nb.toString()); 
	    return result; 
    }
    
    /**
      * Returns the number of active paths to the storage of a host
    */
    private ValidationResult getHostActivePaths(String hostName) throws IOException {
      ValidationResult result;
      Integer nb = 0;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");

	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
        } else {
	        //TODO: cache for all HostConfigInfo
	        HostConfigInfo hc = host.getConfig();
	        HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
	        for (int m = 0; m < mp.length; m++) {
	          if ("active".equals(mp[m].getPathState())) {
	            nb++;
            }
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;   
      }

      result = new ValidationResult(0, nb.toString()); 
	    return result;
    }
    
    /**
      * Returns the number of standby paths to the storage of a host
    */
    private ValidationResult getHostStandbyPaths(String hostName) throws IOException {
      ValidationResult result;
      Integer nb = 0;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
        } else {
	        HostConfigInfo hc = host.getConfig();
	        HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
	        for (int m = 0; m < mp.length; m++) {
	          if ("standby".equals(mp[m].getPathState())) {
	            nb++;
            }
          }
        }
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }
      
      result = new ValidationResult(0, nb.toString()); 
	    return result;      
    }
    
    /**
      * Returns the number of disabled paths to the storage of a host
    */
    private ValidationResult getHostDisabledPaths(String hostName) throws IOException {
      ValidationResult result;
      Integer nb = 0;
      try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostConfigInfo hc = host.getConfig();
	        HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
	        for (int m = 0; m < mp.length; m++) {
	          if ("disabled".equals(mp[m].getPathState())) {
	            nb++;
            }
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }
      
      result = new ValidationResult(0, nb.toString()); 
	    return result;   
    }
    
    /**
      * Returns the display name of a VM
    */
    private ValidationResult getVmName(String vmName) throws IOException {
      ValidationResult result;
    	String name;
      try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
          result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;
          
          } else {
	        name = vm.getName();
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }
      
      result = new ValidationResult(0, name); 
      return result;
    }
    
    /**
      * Returns the status of a virtual machine
    */
    private ValidationResult getVmStatus(String vmName) throws IOException {
      ValidationResult result;
      Integer intStatus = 4;
      try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
          result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;
          
          } else {
	        VirtualMachineSummary vsum = vm.getSummary();
	        String vs = vsum.getOverallStatus().toString();
	        if (null != vs) {
	          switch (vs) {
	            case "grey":
              intStatus = 0;
              break;
	            case "green":
              intStatus = 1;
              break;
	            case "yellow":
              intStatus = 2;
              break;
	            case "red":
              intStatus = 3;
              break;
	            default:
              intStatus = 4;
              break;
            }
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }

      result = new ValidationResult(0, intStatus.toString()); 
      return result;      
    }
    
    /**
      * Returns the maintenance state of a host
    */
    private ValidationResult getHostMaintenance(String hostName) throws IOException {
      ValidationResult result;
	    Boolean is = false;
      try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");

	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          } else {
	        HostRuntimeInfo hrti = host.getRuntime();
	        is = hrti.isInMaintenanceMode();
	        if (is == null) {
	          is = false;
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;      
      }
      
      result = new ValidationResult(0, (is ? "1" : "0"));
      return result;
    }
    
    /**
      * Returns a JSON-formatted array with the virtual machines list for use
      * with Zabbix low-level discovery
    */
    private ValidationResult getVMs() throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();   
      
    	try {
	      ManagedEntity[] vms = getManagedEntities("VirtualMachine");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < vms.length; j++) {
	        VirtualMachine vm = (VirtualMachine) vms[j];
	        VirtualMachineConfigInfo vmcfg = vm.getConfig();
	        if (vm != null && vmcfg != null) {
	          JsonObject jObject = new JsonObject();
	          String vmName = vm.getName();
	          String vmUuid = vmcfg.getUuid();
	          jObject.addProperty("{#VIRTUALMACHINE}", vmName);
	          jObject.addProperty("{#UUID}", vmUuid);
	          jArray.add(jObject);
          }
        }
	      jOutput.add("data", jArray);
	      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    /**
      * Returns a JSON-formatted array with the virtual machines list for use
      * with Zabbix low-level discovery
    */
    private ValidationResult getVMsFullDiscovery() throws IOException {
      ValidationResult result;
	    JsonObject jOutput = new JsonObject();   
      
    	try {
	      ManagedEntity[] vms = getManagedEntities("VirtualMachine");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < vms.length; j++) {
	        VirtualMachine vm = (VirtualMachine) vms[j];
	        VirtualMachineConfigInfo vmcfg = vm.getConfig();
	        if (vm != null && vmcfg != null) {
	          Integer intStatus = 3;
	          JsonObject jObject = new JsonObject();
	          String vmName = vm.getName();
	          String vmUuid = vmcfg.getUuid();
	          jObject.addProperty("{#VIRTUALMACHINE}", vmName);
	          jObject.addProperty("{#UUID}", vmUuid);
	          VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	          String pState = vmrti.getPowerState().toString();
	          if (null != pState) {
	            switch (pState) {
	              case "poweredOff":
                intStatus = 0;
                break;
	              case "poweredOn":
                intStatus = 1;
                break;
	              case "suspended":
                intStatus = 2;
                break;
	              default:
                intStatus = 3;
                break;
              }
            }
	          jObject.addProperty("{#POWERSTATE}", intStatus);
	          jArray.add(jObject);
          }
        }
	      jOutput.add("data", jArray);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    /**
      * Returns a JSON-formatted array with the hosts list for use with
      * Zabbix low-level discovery
    */
    private ValidationResult getHosts() throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
      
    	try {
	      ManagedEntity[] hs = getManagedEntities("HostSystem");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < hs.length; j++) {
	        HostSystem h = (HostSystem) hs[j];
	        if (h != null) {
	          HostListSummary hsum = h.getSummary();
	          HostHardwareSummary hd = hsum.getHardware();
	          if (hsum != null && hd != null) {
	            JsonObject jObject = new JsonObject();
	            jObject.addProperty("{#ESXHOST}", h.getName());
	            jObject.addProperty("{#UUID}", hd.getUuid());
	            jObject.addProperty("{#CLUSTER}", h.getParent().getName());
	            jArray.add(jObject);
            }
          }
        }
	      jOutput.add("data", jArray);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    /**
      * Returns a JSON-formatted array with the clusters list for use with
      * Zabbix low-level discovery
    */
    private ValidationResult getClusters() throws IOException {
      ValidationResult result;
	    JsonObject jOutput = new JsonObject();    
      
    	try {
	      ManagedEntity[] cl = getManagedEntities("ClusterComputeResource");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < cl.length; j++) {
	        ClusterComputeResource c = (ClusterComputeResource) cl[j];
	        String name = c.getName();
          
	        ComputeResourceSummary s = c.getSummary();
	        JsonObject jObject = new JsonObject();
	        jObject.addProperty("{#CLUSTER}", name);
	        jArray.add(jObject);
        }
	      jOutput.add("data", jArray);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    /**
      * Returns a JSON-formatted array with the datacenter list for use with
      * Zabbix low-level discovery
    */
    private ValidationResult getDatacenters() throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
      
    	try {
	      ManagedEntity[] dc = getManagedEntities("Datacenter");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < dc.length; j++) {
	        Datacenter d = (Datacenter) dc[j];
	        if (d != null) {
	          ManagedEntityStatus status = d.getOverallStatus();
            
	          JsonObject jObject = new JsonObject();
	          jObject.addProperty("{#DATACENTER}", d.getName());
	          jArray.add(jObject);
          }
        }
	      jOutput.add("data", jArray);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    private ValidationResult getDatacenterStatus(String dcName, String type) throws IOException {
      ValidationResult result;
      Integer intStatus = 4;
      
      try {
	      ManagedEntity dc = getManagedEntity(dcName, "Datacenter");
	      String status = null;
	      if (dc != null) {
	        if ("overall".equals(type)) {
	          status = dc.getOverallStatus().toString();
            } else if ("config".equals(type)) {
	          status = dc.getConfigStatus().toString();
          }
          
	        if (null != status) {
	          switch (status) {
	            case "gray":
              intStatus = 0;
              break;
	            case "green":
              intStatus = 1;
              break;
	            case "yellow":
              intStatus = 2;
              break;
	            case "red":
              intStatus = 3;
              break;
	            default:
              intStatus = 4;
              break;
            }
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;  
      }
      result = new ValidationResult(0, intStatus.toString());
      return result;
    }
    
    /**
      * Returns a JSON-formatted array with the datastores list for use with
      * Zabbix low-level discovery
    */
    private ValidationResult getDatastores() throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
    	try {
	      ManagedEntity[] ds = getManagedEntities("Datastore");
	      JsonArray jArray = new JsonArray();
	      for (int j = 0; j < ds.length; j++) {
	        Datastore d = (Datastore) ds[j];
	        if (d != null) {
	          if (d.getInfo() instanceof NasDatastoreInfo)
	          {
	            NasDatastoreInfo dsInfo = (NasDatastoreInfo) d.getInfo();
	            if ( dsInfo != null ) {
	              HostNasVolume naaName = dsInfo.getNas();
	              JsonObject jObject = new JsonObject();
	              jObject.addProperty("{#DATASTORE}", d.getName());
	              jObject.addProperty("{#UUID}", dsInfo.url.substring(19, dsInfo.url.length() - 1) );
	              jObject.addProperty("{#CLUSTER}", d.getParent().getName());
	              jObject.addProperty("{#LOCAL}", !d.getSummary().multipleHostAccess);
	              jObject.addProperty("{#NAA}", naaName.getName());
	              jArray.add(jObject);
              }
            }
	          else
	          {
	            VmfsDatastoreInfo dsInfo = (VmfsDatastoreInfo) d.getInfo();
	            if ( dsInfo != null ) {
	              HostScsiDiskPartition[] naaName = dsInfo.getVmfs().extent;
	              JsonObject jObject = new JsonObject();
	              jObject.addProperty("{#DATASTORE}", d.getName());
	              jObject.addProperty("{#UUID}", dsInfo.getVmfs().getUuid());
	              jObject.addProperty("{#CLUSTER}", d.getParent().getName());
	              jObject.addProperty("{#LOCAL}", !d.getSummary().multipleHostAccess);
	              jObject.addProperty("{#NAA}", naaName[0].getDiskName());
	              jArray.add(jObject);
              }
            }
          }
        }
	      jOutput.add("data", jArray);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result; 
      }
      
      result = new ValidationResult(0, jOutput.toString());
	    return result;
    }
    
    /**
      * Returns the uptime of a host in seconds
    */
    private ValidationResult getHostUptime(String hostName) throws IOException {
      ValidationResult result;
      Integer uptime = 0;
      try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostListSummary hostSummary = host.getSummary();
	        HostListSummaryQuickStats hostQuickStats = hostSummary.getQuickStats();
          
	        if (hostQuickStats != null) {
	          uptime = hostQuickStats.getUptime();
	          if (uptime == null) {
	            uptime = 0;
            }
          }
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result; 
      }
            
      result = new ValidationResult(0, uptime.toString());
      return result;
    }
    
    /**
      * Returns the CPU usage of a host
    */
    private ValidationResult getHostCpuUsed(String hostName) throws IOException {
      ValidationResult result;
      Integer usedMhz;
    	try {
        HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
          result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostListSummary hostSummary = host.getSummary();
	        HostListSummaryQuickStats hostQuickStats = hostSummary.getQuickStats();
          
	        usedMhz = hostQuickStats.getOverallCpuUsage();
	        //if (usedMhz == null) {
	        //  usedMhz;
        //}
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }

      result = new ValidationResult(0, usedMhz.toString());
      return result;    
    }

    /**
      * Returns the total CPU power of a host
    */
    private ValidationResult getHostCpuTotal(String hostName) throws IOException {
      ValidationResult result;
      Integer totalMhz;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        totalMhz = getHostMHZ(host);
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;
      }
      
      result = new ValidationResult(0, totalMhz.toString());
      return result;
    }
    
    /**
      * Returns the number of CPU cores of a host
    */
    private ValidationResult getHostCpuCores(String hostName) throws IOException {
      ValidationResult result;
      Short cores;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostListSummary hls = host.getSummary();
	        HostHardwareSummary hosthwi = hls.getHardware();
	        cores = hosthwi.getNumCpuCores();
	        //if (cores == null) {
	        //  cores = 0;
        //}
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result;       
      }
      
      result = new ValidationResult(0, cores.toString());
      return result;
    }
    
    /**
      * Returns the memory usage of a host
    */
    private ValidationResult getHostMemUsed(String hostName) throws IOException {
      ValidationResult result;
      Integer usedMB;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostListSummary hostSummary = host.getSummary();
	        HostListSummaryQuickStats hostQuickStats = hostSummary.getQuickStats();
          
	        usedMB = hostQuickStats.getOverallMemoryUsage();
	        //if (usedMB == null) {
	        //  usedMB = 0;
        //}
        }
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
        return result; 
      }
      
      result = new ValidationResult(0, usedMB.toString());
      return result;      
    }
    
    /**
      * Returns the total memory of a host
    */
    private ValidationResult getHostMemTotal(String hostName) throws IOException {
      ValidationResult result;
      Long totalMemBytes;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        HostListSummary hls = host.getSummary();
	        HostHardwareSummary hosthwi = hls.getHardware();
          
	        totalMemBytes = hosthwi.getMemorySize();
	        if (totalMemBytes == null) {
	          totalMemBytes = new Long(0);
          }
        }
        result = new ValidationResult(0, totalMemBytes.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result; 
    }
    
    /**
      * Returns the number of VMs running on a host
    */
    private ValidationResult getHostVMs(String hostName) throws IOException {
      ValidationResult result;
      Integer nbVM = 0;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;
          
          } else {
	        VirtualMachine[] vms = host.getVms();
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            nbVM++;
            }
          }
        }
        result = new ValidationResult(0, nbVM.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result; 
    }
    
    /**
      * Returns the virtual machines private memory usage of a host
    */
    private ValidationResult getHostVmsStatsPrivate(String hostName) throws IOException {
      ValidationResult result;
      Integer amount;
			try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getPrivateMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines shared memory usage of a host
    */
    private ValidationResult getHostVmsStatsShared(String hostName)throws IOException {
      ValidationResult result;
    	Integer amount;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getSharedMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines swapped memory usage of a host
    */
    private ValidationResult getHostVmsStatsSwapped(String hostName)throws IOException {
      ValidationResult result;
      Integer amount;
			try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        int sharedMb;
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sharedMb = (int) (vmSummary.getQuickStats().getSwappedMemory() / 1024);
	            sum += sharedMb * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines compressed memory usage of a host
    */
    private ValidationResult getHostVmsStatsCompressed(String hostName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += (vmSummary.getQuickStats().getCompressedMemory() / 1024) * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines overhead memory usage of a host
    */
    private ValidationResult getHostVmsStatsOverhCons(String hostName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getConsumedOverheadMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines memory usage of a host
    */
    private ValidationResult getHostVmsStatsConsumed(String hostName)throws IOException {
      ValidationResult result;
	    Integer amount;    
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getHostMemoryUsage() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines ballooned memory usage of a host
    */
    private ValidationResult getHostVmsStatsBallooned(String hostName)throws IOException {
      ValidationResult result;
	    Integer amount;    
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getBalloonedMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machines active memory usage of a host
    */
    private ValidationResult getHostVmsStatsActive(String hostName)throws IOException {
      ValidationResult result;
      Integer amount;    
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      int sum = 0;
	      int activeVms = 0;
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        VirtualMachine[] vms = (host.getVms());
	        for (VirtualMachine vm : vms) {
	          VirtualMachineSummary vmSummary = vm.getSummary();
	          if ("poweredOn".equals(vm.getRuntime().getPowerState().name())) {
	            sum += vmSummary.getQuickStats().getGuestMemoryUsage() * 100 / vmSummary.getConfig().getMemorySizeMB();
	            activeVms++;
            }
          }
	        amount = activeVms == 0 ? 0 : sum / activeVms;
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the CPU usage of a virtual machine
    */
    private ValidationResult getVmCpuUsed(String vmName)throws IOException {
      ValidationResult result;
      Integer usedMhz;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
	        usedMhz = vmQuickStats.getOverallCpuUsage();
	        if (usedMhz == null) {
	          usedMhz = 0;
          }
        }
        result = new ValidationResult(0, usedMhz.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the total CPU of a virtual machine in MHz
    */
    private ValidationResult getVmCpuTotal(String vmName)throws IOException {
      ValidationResult result;
      Integer mhz = 0;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	        ManagedObjectReference hostMor = vmrti.getHost();
          if (hostMor != null) {
            ManagedEntity me = MorUtil.createExactManagedEntity(serviceInstance.getServerConnection(), hostMor);
            HostSystem host = (HostSystem) me;
            
            mhz = getHostMHZ(host);
          }
        }
        result = new ValidationResult(0, mhz.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of CPU cores of a virtual machine
    */
    private ValidationResult getVmCpuCores(String vmName)throws IOException {
      ValidationResult result;
	    Integer cores = 0;    
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineConfigInfo vmcfg = vm.getConfig();
	        VirtualHardware vmhd = vmcfg.getHardware();
	        if (vmhd != null) {
	          cores = vmhd.getNumCPU();
	          if (cores == null) {
	            cores = 0;
            }
          }
        }
        result = new ValidationResult(0, cores.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the annotation of a virtual machine
    */
    private ValidationResult getVmAnnotation(String vmName)throws IOException {
      ValidationResult result;
      String an;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineConfigInfo vmcfg = vm.getConfig();
	        an = vmcfg.getAnnotation();
        }
        result = new ValidationResult(0, an);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns 1 if the virtual machine has at least one snapshot
    */
    private ValidationResult getVmSnapshot(String vmName)throws IOException {
      ValidationResult result;
      Integer snapshot = 0;    
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSnapshotInfo vmsi = vm.getSnapshot();
	        if (vmsi != null) {
	          snapshot = 1;
          }
        }
        result = new ValidationResult(0, snapshot.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the power state of a virtual machine
    */
    private ValidationResult getVmPowerState(String vmName)throws IOException {
      ValidationResult result;
      Integer intStatus = 3;    
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	        String pState = vmrti.getPowerState().toString();
	        if (null != pState) {
	          switch (pState) {
	            case "poweredOff":
              intStatus = 0;
              break;
	            case "poweredOn":
              intStatus = 1;
              break;
	            case "suspended":
              intStatus = 2;
              break;
	            default:
              intStatus = 3;
              break;
            }
          }
        }
        result = new ValidationResult(0, intStatus.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a JSON-formatted array with the list of performance metric
      * instances for a vm for use with Zabbix low-level discovery The params
      * array contains : - the vm name - the performance counter name - an
      * optional query interval (default is defined in the configuration file
      * with the "interval" keyword)
    */
    private ValidationResult getVmPerfCounterDiscovery(String[] params)throws IOException {
      ValidationResult result;
    	JsonObject jOutput = new JsonObject();
      try {
	      String vmName = params[0];
	      String perfCounterName = params[1];
	      Integer newInterval = interval;
	      if (params[2] != null) {
	        newInterval = Integer.parseInt(params[2]);
        }
	      JsonArray jArray = new JsonArray();
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	        String pState = vmrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          // Check if counter exists
	          List counter = getCounterByName(perfCounterName);
	          if (counter == null) {
              result = new ValidationResult(2, String.format("Metric %s doesn't exist for vm %s", perfCounterName, vmName));
              return result;               
              } else {
	            // The counter exists
	            Integer perfCounterId = Integer.valueOf((String) counter.get(0));
	            PerfMetricId[] queryAvailablePerfMetric = performanceManager.queryAvailablePerfMetric(vm, null, null, 20);
	            for (int i2 = 0; i2 < queryAvailablePerfMetric.length; i2++) {
	              PerfMetricId pMetricId = queryAvailablePerfMetric[i2];
	              if (perfCounterId == pMetricId.getCounterId()) {
	                JsonObject jObject = new JsonObject();
	                jObject.addProperty("{#METRICINSTANCE}", pMetricId.getInstance());
	                jArray.add(jObject);
                }
              }
            }
          } else {
            result = new ValidationResult(2, String.format("VM %s is not powered on. Performance counters unavailable.", vmName));
            return result;            
          }
        }
	      jOutput.add("data", jArray);
        result = new ValidationResult(0, jOutput.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a performance counter value for a virtual machine The params
      * array contains : - the VM name - the performance counter name - an
      * optional instance name (for example "nic0") - an optional query
      * interval (default is defined in the configuration file with the
      * "interval" keyword) We don't query the vCenter for historical, but
      * real-time data. So as to collect the most accurate data, we gather a
      * series of real-time (20s interval) values over the defined interval.
      * For rate and absolute values, we calculate the average value. For
      * delta values, we sum the results.
    */
    private ValidationResult getVmPerfCounterValue(String[] params)throws IOException {
      ValidationResult result;
      Integer value = 0;
    	try {
	      String vmName = params[0];
	      String perfCounterName = params[1];
	      String instanceName = "";
	      if (params[2] != null) {
	        instanceName = params[2];
        }
	      Integer newInterval = interval;
	      if (params[3] != null) {
	        newInterval = Integer.parseInt(params[3]);
        }
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

        } 
        else {
	        VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	        String pState = vmrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          // Check if counter exists
	          List counter = getCounterByName(perfCounterName);
	          if (counter == null) {
              result = new ValidationResult(2, String.format("Metric %s doesn't exist for vm %s", perfCounterName, vmName));
              return result;
              
              } else {
	            // The counter exists
	            Integer perfCounterId = Integer.valueOf((String) counter.get(0));
	            String perfCounterUnitInfo = (String) counter.get(1);
	            String perfCounterStatsType = (String) counter.get(2);
	            String perfCounterRollupType = (String) counter.get(3);
              
	            ArrayList<PerfMetricId> perfMetricIds = new ArrayList<PerfMetricId>();
              
	            PerfMetricId metricId = new PerfMetricId();
	            metricId.setCounterId(perfCounterId);
	            metricId.setInstance(instanceName);
	            perfMetricIds.add(metricId);
	            PerfMetricId[] pmi = perfMetricIds.toArray(new PerfMetricId[perfMetricIds.size()]);
              
	            PerfQuerySpec qSpec = new PerfQuerySpec();
	            Calendar previous = Calendar.getInstance();
	            Calendar current = (Calendar) previous.clone();
	            Date old = new Date(previous.getTimeInMillis() - (newInterval * 1000));
	            previous.setTime(old);
              
	            qSpec.setEntity(vm.getMOR());
	            qSpec.setStartTime(previous);
	            qSpec.setEndTime(current);
	            qSpec.setMetricId(pmi);
	            qSpec.setIntervalId(20); // real-time values
              
	            PerfEntityMetricBase[] pValues = performanceManager.queryPerf(new PerfQuerySpec[]{qSpec});
	            if (pValues != null) {
	              for (int i = 0; i < pValues.length; ++i) {
	                if (pValues[i] instanceof PerfEntityMetric) {
	                  PerfEntityMetric pem = (PerfEntityMetric) pValues[i];
	                  PerfMetricSeries[] vals = pem.getValue();
	                  value = getPerfCounterValue(vals, perfCounterName, perfCounterUnitInfo, perfCounterStatsType, perfCounterRollupType);
                  }
                }
	            }
            }          
          } else {
            result = new ValidationResult(2, String.format("VM %s is not powered on. Performance counters unavailable.", vmName));
            return result;
          }
        }
        result = new ValidationResult(0, value.toString());          
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Processes performance values and calculates sum, avg, max, min,
      * percent.
    */
    private int getPerfCounterValue(PerfMetricSeries[] vals, String perfCounterName, String perfCounterUnitInfo, String perfCounterStatsType, String perfCounterRollupType)throws IOException {
      float value = 0;
      Pattern pattern;
      Matcher matcher;
      for (int j = 0; vals != null && j < vals.length; ++j) {
        PerfMetricIntSeries val = (PerfMetricIntSeries) vals[j];
        long[] serie = val.getValue();
        if (perfCounterRollupType.equals("average") || perfCounterRollupType.equals("latest") || perfCounterRollupType.equals("summation")) {
          for (int k = 0; k < serie.length; k++) {
            value = value + serie[k];
          }
          } else if (perfCounterRollupType.equals("maximum")) { // this is a maximum
          for (int k = 0; k < serie.length; k++) {
            value = Math.max(value, serie[k]);
          }
          } else if (perfCounterRollupType.equals("minimum")) { // this is a minimum
          value = serie[0];
          for (int k = 0; k < serie.length; k++) {
            value = Math.min(value, serie[k]);
          }
        } 
        //else {
        //  return null;        
        //}
        
        if (!perfCounterStatsType.equals("delta")) {
          value = value / serie.length;
        }
        
        if (perfCounterUnitInfo.equals("percent")) {
          // convert to percent
          value = value / 100;
        }
      }
      return (int) value;
    }
    
    /**
      * Returns a JSON-formatted array with the list of performance metric
      * instances for a host for use with Zabbix low-level discovery The
      * params array contains : - the host name - the performance counter
      * name - an optional query interval (default is defined in the
      * configuration file with the "interval" keyword)
    */
    private ValidationResult getHostPerfCounterDiscovery(String[] params)throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
    	try {
	      String hostName = params[0];
	      String perfCounterName = params[1];
	      Integer newInterval = interval;
	      if (params[2] != null) {
	        newInterval = Integer.parseInt(params[2]);
        }
	      JsonArray jArray = new JsonArray();
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        HostRuntimeInfo hostrti = host.getRuntime();
	        String pState = hostrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          // Check if counter exists
	          List counter = getCounterByName(perfCounterName);
	          if (counter == null) {
              result = new ValidationResult(2, String.format("Metric %s doesn't exist for host %s", perfCounterName, hostName));
              return result;
              } else {
	            // The counter exists
	            Integer perfCounterId = Integer.valueOf((String) counter.get(0));
	            PerfMetricId[] queryAvailablePerfMetric = getHostPerformanceManager(host, 20);
	            for (int i2 = 0; i2 < queryAvailablePerfMetric.length; i2++) {
	              PerfMetricId pMetricId = queryAvailablePerfMetric[i2];
	              if (perfCounterId == pMetricId.getCounterId()) {
	                JsonObject jObject = new JsonObject();
	                jObject.addProperty("{#METRICINSTANCE}", pMetricId.getInstance());
	                Datastore ds = (Datastore) getManagedEntityByUuid(pMetricId.getInstance(), "Datastore");
	                if (ds != null) {
	                  jObject.addProperty("{#METRICNAME}", ds.getName());
                  }
	                jArray.add(jObject);
                }
              }
            }
            } else {
            result = new ValidationResult(2, String.format("Host %s is not powered on. Performance counters unavailable.", hostName));
            return result;
          }
        }
	      jOutput.add("data", jArray);
        result = new ValidationResult(0, jOutput.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a performance counter value for a host The params array
      * contains : - the host name - the performance counter name - an
      * optional instance name (for example "nic0") - an optional query
      * interval (default is defined in the configuration file with the
      * "interval" keyword) We don't query the vCenter for historical, but
      * real-time data. So as to collect the most accurate data, we gather a
      * series of real-time (20s interval) values over the defined interval.
      * For rate and absolute values, we calculate the average value. For
      * delta values, we sum the results.
    */
    private ValidationResult getHostPerfCounterValue(String[] params)throws IOException {
      ValidationResult result;
      Integer value = 0;
    	try {
	      String hostName = params[0];
	      String perfCounterName = params[1];
	      String instanceName = "";
	      if (params[2] != null) {
	        instanceName = params[2];
        }
	      Integer newInterval = interval;
	      if (params[3] != null) {
	        newInterval = Integer.parseInt(params[3]);
        }
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        HostRuntimeInfo hostrti = getHostRuntimeInfo(hostName, host);
	        String pState = hostrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          // Check if counter exists
	          List counter = getCounterByName(perfCounterName);
	          if (counter == null) {
              result = new ValidationResult(2, String.format("Metric %s doesn't exist for host %s", perfCounterName, hostName));
              return result;
              } else {
	            // The counter exists
	            Integer perfCounterId = Integer.valueOf((String) counter.get(0));
	            String perfCounterUnitInfo = (String) counter.get(1);
	            String perfCounterStatsType = (String) counter.get(2);
	            String perfCounterRollupType = (String) counter.get(3);
	            ArrayList<PerfMetricId> perfMetricIds = new ArrayList<PerfMetricId>();
	            PerfMetricId metricId = new PerfMetricId();
	            metricId.setCounterId(perfCounterId);
	            metricId.setInstance(instanceName);
	            perfMetricIds.add(metricId);
              
	            PerfMetricId[] pmi = perfMetricIds.toArray(new PerfMetricId[perfMetricIds.size()]);
              
	            PerfQuerySpec qSpec = new PerfQuerySpec();
	            Calendar previous = Calendar.getInstance();
	            Calendar current = (Calendar) previous.clone();
	            Date old = new Date(previous.getTimeInMillis() - (newInterval * 1000));
	            previous.setTime(old);
              
	            qSpec.setEntity(host.getMOR());
	            qSpec.setStartTime(previous);
	            qSpec.setEndTime(current);
	            qSpec.setMetricId(pmi);
	            qSpec.setIntervalId(20); // real-time values
              
	            PerfEntityMetricBase[] pValues = performanceManager.queryPerf(new PerfQuerySpec[]{qSpec});
	            if (pValues != null) {
	              for (int i = 0; i < pValues.length; ++i) {
	                if (pValues[i] instanceof PerfEntityMetric) {
	                  PerfEntityMetric pem = (PerfEntityMetric) pValues[i];
	                  PerfMetricSeries[] vals = pem.getValue();
	                  value = getPerfCounterValue(vals, perfCounterName, perfCounterUnitInfo, perfCounterStatsType, perfCounterRollupType);
                  }
                }
	            }
            }
            } else {
            result = new ValidationResult(2, String.format("Host %s is not powered on. Performance counters unavailable.", hostName));
            return result;
          }
        }
        result = new ValidationResult(0, value.toString());          
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the list of available performance counters of a host
    */
    private ValidationResult getHostAvailablePerfCounters(String hostName)throws IOException {
      ValidationResult result;
      String perfCounterString = "";
    	try {
	      HostSystem host = (HostSystem) getManagedEntity(hostName, "HostSystem");
	      if (host == null) {
	        result = new ValidationResult(2, String.format("No host named %s found", hostName));
	        return result;

          } else {
	        HostRuntimeInfo hostrti = host.getRuntime();
	        String pState = hostrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          PerfMetricId[] metricIdList = performanceManager.queryAvailablePerfMetric(host, null, null, 20);
            
	          // Get the counter ids of the available metrics
	          ArrayList<Integer> counterIds = new ArrayList<Integer>();
	          for (int i2 = 0; i2 < metricIdList.length; i2++) {
	            PerfMetricId perfMetricId = metricIdList[i2];
	            if (!counterIds.contains(perfMetricId.getCounterId())) {
	              counterIds.add(perfMetricId.getCounterId());
              }
            }
	          int[] cIds = new int[counterIds.size()];
	          for (int i = 0; i < counterIds.size(); i++) {
	            cIds[i] = counterIds.get(i);
            }
	          Arrays.sort(cIds);
	          PerfCounterInfo[] ciList = performanceManager.queryPerfCounter(cIds);
            
	          for (int i = 0; i < ciList.length; i++) {
	            PerfCounterInfo perfCounterInfo = ciList[i];
	            perfCounterString = perfCounterInfo.getKey() + " : " + perfCounterInfo.getGroupInfo().getKey() + "." + perfCounterInfo.getNameInfo().getKey() + "." + perfCounterInfo.getRollupType().toString() + " : " + perfCounterInfo.getNameInfo().getLabel() + " in " + perfCounterInfo.getUnitInfo().getLabel() + " (" + perfCounterInfo.getStatsType().toString() + ")";
            }
	        } else {
            result = new ValidationResult(2, String.format("Host %s is not powered on. Performance counters unavailable.", hostName));
            return result;
          }
        }
        result = new ValidationResult(0, perfCounterString);           
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the list of available performance counters of a vm
    */
    private ValidationResult getVmAvailablePerfCounters(String vmName)throws IOException {
      ValidationResult result;
      String perfCounterString = "";
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
	        String pState = vmrti.getPowerState().toString();
	        if (pState.equals("poweredOn")) {
	          PerfMetricId[] metricIdList = performanceManager.queryAvailablePerfMetric(vm, null, null, 20);
            
	          // Get the counter ids of the available metrics
	          ArrayList<Integer> counterIds = new ArrayList<Integer>();
	          for (int i2 = 0; i2 < metricIdList.length; i2++) {
	            PerfMetricId perfMetricId = metricIdList[i2];
	            if (!counterIds.contains(perfMetricId.getCounterId())) {
	              counterIds.add(perfMetricId.getCounterId());
              }
            }
	          int[] cIds = new int[counterIds.size()];
	          for (int i = 0; i < counterIds.size(); i++) {
	            cIds[i] = counterIds.get(i);
            }
	          Arrays.sort(cIds);
	          PerfCounterInfo[] ciList = performanceManager.queryPerfCounter(cIds);
            
	          for (int i = 0; i < ciList.length; i++) {
	            PerfCounterInfo perfCounterInfo = ciList[i];
	            perfCounterString = perfCounterInfo.getKey() + " : " + perfCounterInfo.getGroupInfo().getKey() + "." + perfCounterInfo.getNameInfo().getKey() + "." + perfCounterInfo.getRollupType().toString() + " : " + perfCounterInfo.getNameInfo().getLabel() + " in " + perfCounterInfo.getUnitInfo().getLabel() + " (" + perfCounterInfo.getStatsType().toString() + ")";
            }
	        } else {
            result = new ValidationResult(2, String.format("VM %s is not powered on. Performance counters unavailable.", vmName));
            return result;
          }
        }
        result = new ValidationResult(0, perfCounterString);           
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the about info of the VMWare API
    */
    private ValidationResult getAbout()throws IOException {
      ValidationResult result;
      String fullName;
    	try {
	      AboutInfo about = serviceInstance.getAboutInfo();
	      fullName = about.getFullName();
        result = new ValidationResult(0, fullName);   
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }

    /**
     * Returns a byte[] array of the Zabbix header and data.
    */
    private byte[] makeZabbixPacket(ValidationResult result) {
      byte[] data = result.message.getBytes();
      byte[] err = new byte[] {};
      
      if (result.status == 1) {
        err = new byte[] { 
          'Z', 'B', 'X', '_', 'N', 'O',
          'T', 'S', 'U', 'P', 'P', 'O',
          'R', 'T', 'E', 'D', '\0' 
        };
      }
      
      if (result.status == 2) {
        err = new byte[] { 
          'Z', 'B', 'X', '_', 'E', 'R',
          'R', 'O', 'R', '\0'
        };
      }
      
      byte[] payload = new byte[err.length + data.length];
      System.arraycopy(err, 0, payload, 0, err.length);
      System.arraycopy(data, 0, payload, err.length, data.length);
      
      byte[] header = new byte[] {
        'Z', 'B', 'X', 'D', '\1',
            (byte)(payload.length & 0xFF),
            (byte)((payload.length >> 8) & 0xFF),
            (byte)((payload.length >> 16) & 0xFF),
            (byte)((payload.length >> 24) & 0xFF),
            '\0', '\0', '\0', '\0'
      };
      
      byte[] packet = new byte[header.length + payload.length];
      System.arraycopy(header, 0, packet, 0, header.length);
      System.arraycopy(payload, 0, packet, header.length, payload.length);

      try {
        String s = new String(packet, "UTF-8");
        LOG.debug(s);
        LOG.debug(Arrays.toString(packet));
      }
      catch (Exception ex) {}
      
      return packet;
    }
    
    /*
    private byte[] makeZabbixPacket(String message) {
      byte[] data = message.getBytes();
      
      byte[] header = new byte[] {
        'Z', 'B', 'X', 'D', '\1',
            (byte)(data.length & 0xFF),
            (byte)((data.length >> 8) & 0xFF),
            (byte)((data.length >> 16) & 0xFF),
            (byte)((data.length >> 24) & 0xFF),
            '\0', '\0', '\0', '\0'
      };

      byte[] packet = new byte[header.length + data.length];
      System.arraycopy(header, 0, packet, 0, header.length);
      System.arraycopy(data, 0, packet, header.length, data.length);

      return packet;
    }    
    */
    
    /**
     * Sends the formatted Zabbix packet to the Zabbix server
     */
    private void sendZabbixPacket(byte[] packet, OutputStream out)throws IOException {
      try {
        out.write(packet);
        out.flush();
      }
      catch (Exception ex) {
        LOG.error(String.format("An error occurred : %s", ex.toString()));
      }
    }
    
    /**
      * Returns the latest event on the vCenter
    */
    private ValidationResult getLatestEvent()throws IOException {
      ValidationResult result;
      String latestEvent;
    	try {
	      EventManager eventManager = serviceInstance.getEventManager();
	      latestEvent = eventManager.getLatestEvent().getFullFormattedMessage();
        result = new ValidationResult(0, latestEvent);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the vCenter folder of a virtual machine
    */
    private ValidationResult getVmFolder(String vmName)throws IOException {
      ValidationResult result;
      String vmFolder = "";
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        ManagedEntity fd = vm.getParent();
	        while (fd instanceof Folder) {
	          vmFolder = "/" + fd.getName() + vmFolder;
	          fd = fd.getParent();
          }
        }
        result = new ValidationResult(0, vmFolder);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the uptime in seconds of a virtual machine
    */
    private ValidationResult getVmUptime(String vmName)throws IOException {
      ValidationResult result;
      Integer uptime = 0;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        if (vmQuickStats != null) {
	          uptime = vmQuickStats.getUptimeSeconds();
	          //if (uptime == null) {
	          //  uptime = 0;
          //}
          }
        }
        result = new ValidationResult(0, uptime.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the private memory of a virtual machine
    */
    private ValidationResult getVmMemPrivate(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getPrivateMemory();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the shared memory of a virtual machine
    */
    private ValidationResult getVmMemShared(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getSharedMemory();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the swapped memory of a virtual machine
    */
    private ValidationResult getVmMemSwapped(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getSwappedMemory();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the compressed memory of a virtual machine
    */
    private ValidationResult getVmMemCompressed(String vmName)throws IOException {
      ValidationResult result;
      Long amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getCompressedMemory();
	        //if (amount == null) {
	        //  amount = new Long(0);
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the overhead memory of a virtual machine
    */
    private ValidationResult getVmMemOverheadConsumed(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getConsumedOverheadMemory();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the consumed memory of a virtual machine
    */
    private ValidationResult getVmMemConsumed(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getHostMemoryUsage();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the ballooned memory of a virtual machine
    */
    private ValidationResult getVmMemBallooned(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getBalloonedMemory();
		        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the active memory of a virtual machine
    */
    private ValidationResult getVmMemActive(String vmName)throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();
          
	        amount = vmQuickStats.getGuestMemoryUsage();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the consolidation status of a virtual machine
    */
    private ValidationResult getVmConsolidationNeeded(String vmName)throws IOException {
      ValidationResult result;
      String isConsolidationNeeded;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
        Boolean is = false;
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

        } 
        else {
	        VirtualMachineRuntimeInfo vmRuntime = vm.getRuntime();
	        is = vmRuntime.getConsolidationNeeded();
	        if (is == null) {
	          is = false;
          }
        }
	      isConsolidationNeeded = (is ? "1" : "0");
        result = new ValidationResult(0, isConsolidationNeeded);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a true if the VM Tools installer is mounted of a virtual
      * machine Returns false if not
    */
    private ValidationResult getVmToolsInstallerMounted(String vmName)throws IOException {
      ValidationResult result;
      String isToolsInstallerMounted;
			try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      Boolean is = false;
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineRuntimeInfo vmRuntime = vm.getRuntime();
	        is = vmRuntime.isToolsInstallerMounted();
	        if (is == null) {
	          is = false;
          }
        }
	      isToolsInstallerMounted = (is ? "1" : "0");
        result = new ValidationResult(0, isToolsInstallerMounted);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the running host of a virtual machine
    */
    private ValidationResult getVmHost(String vmName)throws IOException {
      ValidationResult result;
      String vmHost;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

        } else {
	        VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
	        ManagedObjectReference hmor = vmRuntimeInfo.getHost();
	        HostSystem host = new HostSystem(vm.getServerConnection(), hmor);
	        vmHost = host.getName();
        }
        result = new ValidationResult(0, vmHost);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the guest OS full description of a virtual machine
    */
    private ValidationResult getVmGuestFullName(String vmName)throws IOException {
      ValidationResult result;
       String guestFullName;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
	        if (vmGuest == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
          } else {
	          guestFullName = vmGuest.getGuestFullName();
          }
        }
        result = new ValidationResult(0, guestFullName);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the guest OS short description of a virtual machine
    */
    private ValidationResult getVmGuestShortName(String vmName)throws IOException {
      ValidationResult result;
      String guestShortName;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        GuestInfo gInfo = vm.getGuest();
	        if (gInfo == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
          } else {
	          guestShortName = gInfo.getGuestFamily();
          }
        }
        result = new ValidationResult(0, guestShortName);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the guest OS hostname of a virtual machine
    */
    private ValidationResult getVmGuestHostName(String vmName)throws IOException {
      ValidationResult result;
      String guestHostName;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
	        if (vmGuest == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
          } else {
	          guestHostName = vmGuest.getHostName();
          }
        }
        result = new ValidationResult(0, guestHostName);
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the list of the guest OS disks of a virtual machine Formatted
      * in JSON for use with Zabbix LLD
    */
    private ValidationResult getVmGuestDisks(String vmName)throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
    	try {
      	VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
      	JsonArray jArray = new JsonArray();
        if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;
        } 
        else {
          GuestInfo gInfo = vm.getGuest();
          if (gInfo == null) {
            result = new ValidationResult(2, String.format("Cannot query guest OS for VM %s", vmName));
            return result;
          } else {
            GuestDiskInfo[] vmDisks = gInfo.getDisk();
            if (vmDisks == null) {
              result = new ValidationResult(2, String.format("Cannot query disks OS for VM %s", vmName));
              return result;              
            }
            else {
              for (int j = 0; j < vmDisks.length; j++) {
                JsonObject jObject = new JsonObject();
                String disk = vmDisks[j].getDiskPath();
                if (escapeChars == true && disk.endsWith("\\")) {
                  LOG.debug("The disk '" + disk + "' of the VM '" + vmName + "' ends with a backslash and will be sanitized");
                  disk = disk.concat(" ");
                }
                
                jObject.addProperty("{#GUESTDISK}", disk);
                jArray.add(jObject);
              }
              jOutput.add("data", jArray);
            }
          }
        }
        result = new ValidationResult(0, jOutput.toString());
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a disk capacity for the guest OS of a virtual machine
    */
    private ValidationResult getVmGuestDiskCapacity(String vmName, String vmDisk)throws IOException {
      ValidationResult result;
      Long size = 0L;
      
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;
          
        } else {
	        GuestInfo gInfo = vm.getGuest();
	        if (gInfo == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
            
          } else {
	          GuestDiskInfo[] vmDisks = gInfo.getDisk();
	          if (vmDisks != null) {
	            if (escapeChars == true && vmDisk.endsWith(" ")) {
	              LOG.debug("The disk '" + vmDisk + "' of the VM '" + vmName + "' ends with a space and will be sanitized");
	              vmDisk = vmDisk.substring(0,vmDisk.length()-1);
              }
	            for (int j = 0; j < vmDisks.length; j++) {
	              if (vmDisks[j].getDiskPath().equals(vmDisk)) {
	                size = vmDisks[j].getCapacity();
                }
              }
	          } else {
              result = new ValidationResult(1, String.format("Cannot query guest disks for VM %s", vmName));
	            return result;
            }
          }
        }
        result = new ValidationResult(0, size.toString());
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a disk free space for the guest OS of a virtual machine
    */
    private ValidationResult getVmGuestDiskFreeSpace(String vmName, String vmDisk)throws IOException {
      ValidationResult result;
      Long size = 0L;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        GuestInfo gInfo = vm.getGuest();
	        if (gInfo == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
          } else {
	          GuestDiskInfo[] vmDisks = gInfo.getDisk();
	          if (vmDisks != null) {
	            if (escapeChars == true && vmDisk.endsWith(" ")) {
	              LOG.debug("The disk '" + vmDisk + "' of the VM '" + vmName + "' ends with a space and will be sanitized");
	              vmDisk = vmDisk.substring(0,vmDisk.length()-1);
              }
	            for (int j = 0; j < vmDisks.length; j++) {
	              if (vmDisks[j].getDiskPath().equals(vmDisk)) {
	                size = vmDisks[j].getFreeSpace();
                }
              }
	          } else {
              result = new ValidationResult(1, String.format("Cannot query guest disks for VM %s", vmName));
	            return result;
            }
          }
        }
        result = new ValidationResult(0, size.toString());
	    }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the guest OS IP address of a virtual machine
    */
    private ValidationResult getVmGuestIpAddress(String vmName)throws IOException {
      ValidationResult result;
      String guestIpAddress;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
	        if (vmGuest == null) {
            result = new ValidationResult(1, String.format("Cannot query guest OS for VM %s", vmName));
	          return result;
          } else {
	          guestIpAddress = vmGuest.getIpAddress();
          }
        }
        result = new ValidationResult(0, guestIpAddress);
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the committed storage of a virtual machine
    */
    private ValidationResult getVmStorageCommitted(String vmName)throws IOException {
      ValidationResult result;
      Long size;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
	        size = vmStorage.getCommitted();
        }
        result = new ValidationResult(0, size.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the uncommitted storage of a virtual machine
    */
    private ValidationResult getVmStorageUncommitted(String vmName)throws IOException {
      ValidationResult result;
      Long size;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
	        size = vmStorage.getUncommitted();
        }
        result = new ValidationResult(0, size.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the unshared storage of a virtual machine
    */
    private ValidationResult getVmStorageUnshared(String vmName)throws IOException {
      ValidationResult result;
      Long size;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
	        size = vmStorage.getUnshared();
        }
        result = new ValidationResult(0, size.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns a status of the virtual machine VM Tools version 0 ->
      * guestToolsNotInstalled 1 -> guestToolsCurrent 2 ->
      * guestToolsNeedUpgrade 3 -> guestToolsUnmanaged 4 -> other
    */
    private ValidationResult getVmGuestToolsVersionStatus(String vmName)throws IOException {
      ValidationResult result;
      Integer intStatus = 9;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
	        if (vmGuest == null) {
	          LOG.info("Cannot query guest OS for VM '" + vmName);
            } else {
	          String guestToolsVersionStatus = vmGuest.getToolsVersionStatus2();
	          if (null != guestToolsVersionStatus) {
	            switch (guestToolsVersionStatus) {
	              case "guestToolsNotInstalled":
                intStatus = 0;
                break;
	              case "guestToolsCurrent":
                intStatus = 1;
                break;
	              case "guestToolsNeedUpgrade":
                intStatus = 2;
                break;
	              case "guestToolsUnmanaged":
                intStatus = 3;
                break;
	              case "guestToolsBlacklisted":
                intStatus = 4;
                break;
	              case "guestToolsSupportedNew":
                intStatus = 5;
                break;
	              case "guestToolsSupportedOld":
                intStatus = 6;
                break;
	              case "guestToolsTooNew":
                intStatus = 7;
                break;
	              case "guestToolsTooOld":
                intStatus = 8;
                break;
	              default:
                intStatus = 9;
                break;
              }
            }
          }
        }
        result = new ValidationResult(0, intStatus.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the virtual machine VM Tools running state 0 ->
      * guestToolsNotRunning 1 -> guestToolsRunning 2 ->
      * guestToolsExecutingScripts 3 -> other
    */
    private ValidationResult getVmGuestToolsRunningStatus(String vmName) throws IOException {
      ValidationResult result;
      Integer intStatus = 3;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
	        if (vmGuest == null) {
	          LOG.info("Cannot query guest OS for VM '" + vmName);
            } else {
	          String guestToolsRunningStatus = vmGuest.getToolsRunningStatus();
	          if (null != guestToolsRunningStatus) {
	            switch (guestToolsRunningStatus) {
	              case "guestToolsNotRunning":
                intStatus = 0;
                break;
	              case "guestToolsRunning":
                intStatus = 1;
                break;
	              case "guestToolsExecutingScripts":
                intStatus = 2;
                break;
	              default:
                intStatus = 3;
                break;
              }
            }
          }
        }
        result = new ValidationResult(0, intStatus.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the memory size of a virtual machine
    */
    private ValidationResult getVmMemSize(String vmName) throws IOException {
      ValidationResult result;
      Integer amount;
    	try {
	      VirtualMachine vm = (VirtualMachine) getManagedEntity(vmName, "VirtualMachine");
	      if (vm == null) {
	        result = new ValidationResult(2, String.format("No vm named %s found", vmName));
	        return result;

          } else {
	        VirtualMachineSummary vmSummary = vm.getSummary();
	        VirtualMachineConfigSummary vmConfigSum = vmSummary.getConfig();
          
	        amount = vmConfigSum.getMemorySizeMB();
	        //if (amount == null) {
	        //  amount = 0;
        //}
        }
        result = new ValidationResult(0, amount.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns 1 if the datastore is local
    */
    private ValidationResult getDatastoreLocal(String dsName) throws IOException {
      ValidationResult result;
      Integer local;
    	try {
	      Datastore ds = (Datastore) getManagedEntity(dsName, "Datastore");
	      if (ds == null) {
	        result = new ValidationResult(2, String.format("No datastore named %s found", dsName));
	        return result;

        } else {
	        local = (ds.getSummary().multipleHostAccess == true) ? 0 : 1; // return 1 for local datastores
        }
        result = new ValidationResult(0, local.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the free space of a datastore
    */
    private ValidationResult getDatastoreSizeFree(String dsName) throws IOException {
      ValidationResult result;
      Long freeSpace;
    	try {
	      Datastore ds = (Datastore) getManagedEntity(dsName, "Datastore");
	      if (ds == null) {
	        result = new ValidationResult(2, String.format("No datastore named %s found", dsName));
	        return result;
        } else {
	        DatastoreSummary dsSum = ds.getSummary();
	        freeSpace = dsSum.getFreeSpace();
	        //if (freeSpace == null) {
	        //  freeSpace = new Long(0);
        //}
        }
        result = new ValidationResult(0, freeSpace.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the size of a datastore
    */
    private ValidationResult getDatastoreSizeTotal(String dsName)throws IOException {
      ValidationResult result;
      Long capacity;
    	try {
	      Datastore ds = (Datastore) getManagedEntity(dsName, "Datastore");
	      if (ds == null) {
	        result = new ValidationResult(2, String.format("No datastore named %s found", dsName));
	        return result;
        } else {
	        DatastoreSummary dsSum = ds.getSummary();
	        capacity = dsSum.getCapacity();
	        //if (capacity == null) {
	        //  capacity = new Long(0);
        //}
        }
        result = new ValidationResult(0, capacity.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the provisioned size of a datastore
    */
    private ValidationResult getDatastoreSizeProvisioned(String dsName)throws IOException {
      ValidationResult result;
      Long provSpace;
    	try {
	      Datastore ds = (Datastore) getManagedEntity(dsName, "Datastore");
	      if (ds == null) {
	        result = new ValidationResult(2, String.format("No datastore named %s found", dsName));
	        return result;
        } else {
	        DatastoreSummary dsSum = ds.getSummary();
	        long total = dsSum.getCapacity();
	        long free = dsSum.getFreeSpace();
	        long uncom = dsSum.getUncommitted();
	        long temp = total - free + uncom;
	        provSpace = temp;
	        //if (provSpace == null) {
	        //  provSpace = new Long(0);
        //}
        }
        result = new ValidationResult(0, provSpace.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the uncommitted size of a datastore
    */
    private ValidationResult getDatastoreSizeUncommitted(String dsName)throws IOException {
      ValidationResult result;
      Long freeSpace;
    	try {
	      Datastore ds = (Datastore) getManagedEntity(dsName, "Datastore");
	      if (ds == null) {
	        result = new ValidationResult(2, String.format("No datastore named %s found", dsName));
	        return result;
        } else {
	        DatastoreSummary dsSum = ds.getSummary();
	        freeSpace = dsSum.getUncommitted();
	        if (freeSpace == null) {
	          freeSpace = new Long(0);
          }
        }
        result = new ValidationResult(0, freeSpace.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the CPU free of a cluster
    */
    private ValidationResult getClusterCpuFree(String name)throws IOException {
      ValidationResult result;
      long cpuFree;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        cpuFree = cl.getSummary().effectiveCpu;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;          
        }
        result = new ValidationResult(0, Long.toString(cpuFree));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the total CPU of a cluster
    */
    private ValidationResult getClusterCpuTotal(String name)throws IOException {
      ValidationResult result;
      long cpuTotal;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        cpuTotal = cl.getSummary().totalCpu;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;     
        }
        result = new ValidationResult(0, Long.toString(cpuTotal));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the CPU usage of a cluster
    */
    private ValidationResult getClusterCpuUsage(String name)throws IOException {
      ValidationResult result;
      long cpuUsage;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        cpuUsage = cl.getSummary().totalCpu - cl.getSummary().effectiveCpu;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, Long.toString(cpuUsage));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of CPU threads of a cluster
    */
    private ValidationResult getClusterCpuThreads(String name)throws IOException {
      ValidationResult result;
      short numCpuThreads;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        numCpuThreads = cl.getSummary().numCpuThreads;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, Short.toString(numCpuThreads));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of CPU cores of a cluster
    */
    private ValidationResult getClusterCpuCores(String name)throws IOException {
      ValidationResult result;
      short numCpuCores;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        numCpuCores = cl.getSummary().numCpuCores;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, Short.toString(numCpuCores));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the free memory of a cluster
    */
    private ValidationResult getClusterMemFree(String name)throws IOException {
      ValidationResult result;
      long memFree;
    	try {
	      //effectiveMemory returned in MB
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        memFree = cl.getSummary().effectiveMemory * 1024 * 1024;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, Long.toString(memFree));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    private ValidationResult getPools()throws IOException {
      ValidationResult result;
      JsonObject jOutput = new JsonObject();
      try {
        ManagedEntity[] cl = getManagedEntities("ResourcePool");
        JsonArray jArray = new JsonArray();
        for (int j = 0; j < cl.length; j++) {
          ResourcePool c = (ResourcePool) cl[j];
          String name = c.getName();
          
          ResourcePoolSummary s = c.getSummary();
          JsonObject jObject = new JsonObject();
          jObject.addProperty("{#POOL}", name);
          jArray.add(jObject);
        }
        jOutput.add("data", jArray);
        result = new ValidationResult(0, jOutput.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    private ValidationResult getPoolMemUsage(String name)throws IOException {
      ValidationResult result;
      long memUsage;
      try {
        ResourcePool rp = (ResourcePool) getManagedEntityByName(name, "ResourcePool");
        if (rp != null) {
          ResourcePoolSummary rpSummary = rp.getSummary();
          ResourcePoolRuntimeInfo rpInfo = rpSummary.getRuntime();
          ResourcePoolResourceUsage rpMemory = rpInfo.getMemory();
          memUsage = rpMemory.overallUsage;
          } else {
	        result = new ValidationResult(2, String.format("No ResourcePool named %s found", name));
	        return result;            
        }
        result = new ValidationResult(0, Long.toString(memUsage));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    private ValidationResult getPoolCpuUsage(String name)throws IOException {
      ValidationResult result;
      long CpuUsage;
      try {
        ResourcePool rp = (ResourcePool) getManagedEntityByName(name, "ResourcePool");
        if (rp != null) {
          ResourcePoolSummary rpSummary = rp.getSummary();
          ResourcePoolRuntimeInfo rpInfo = rpSummary.getRuntime();
          ResourcePoolResourceUsage rpCpu = rpInfo.getCpu();
          CpuUsage = rpCpu.overallUsage;
          } else {
	        result = new ValidationResult(2, String.format("No ResourcePool named %s found", name));
	        return result;
        }
        result = new ValidationResult(0, Long.toString(CpuUsage));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the total memory of a cluster
    */
    private ValidationResult getClusterMemTotal(String name)throws IOException {
      ValidationResult result;
      long memTotal;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        memTotal = cl.getSummary().totalMemory;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;
        }
        result = new ValidationResult(0, Long.toString(memTotal));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the memory usage of a cluster
    */
    private ValidationResult getClusterMemUsage(String name)throws IOException {
      ValidationResult result;
      long memUsage;
    	try {
	      //effectiveMemory returned in MB
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        memUsage = cl.getSummary().totalMemory - (cl.getSummary().effectiveMemory * 1024 * 1024);
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, Long.toString(memUsage));
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of online ESX hosts of a cluster
    */
    private ValidationResult getClusterHostsOnline(String name)throws IOException {
      ValidationResult result;
      Integer hostOnline;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        hostOnline = cl.getSummary().numEffectiveHosts;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, hostOnline.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of ESX hosts in maintenance of a cluster
    */
    private ValidationResult getClusterHostsMaint(String name)throws IOException {
      ValidationResult result;
      Integer hostMaint;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        hostMaint = cl.getSummary().numHosts - cl.getSummary().numEffectiveHosts;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, hostMaint.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    /**
      * Returns the number of ESX hosts of a cluster
    */
    private ValidationResult getClusterHostsTotal(String name)throws IOException {
      ValidationResult result;
      Integer hostTotal;
    	try {
	      ClusterComputeResource cl = (ClusterComputeResource) getManagedEntityByName(name, "ClusterComputeResource");
	      if (cl != null) {
	        hostTotal = cl.getSummary().numHosts;
          } else {
	        result = new ValidationResult(2, String.format("No cluster named %s found", name));
	        return result;   
        }
        result = new ValidationResult(0, hostTotal.toString());
      }
      catch (Exception ex) {
        result = new ValidationResult(1, String.format("An error occurred : %s", ex.toString()));
      }
      return result;
    }
    
    public void run() {
      LOG.debug("Thread created, collecting data in " + (Thread.activeCount() - 1) + " threads");
      ValidationResult result;
      int reincornate = 1;
      final int lifeTime = 2000;
      int alive = 0;
      
      while (reincornate == 1) {
        Request request = VmBix.pullConnection();
        if (request == null) {
          VmBix.sleep(10);
          alive += 10;
          } else {
          connected = request.socket;
          serviceInstance = request.serviceInstance;
          alive = 0;
          try {
            OutputStream outputStream = connected.getOutputStream();
            DataInputStream in = new DataInputStream( connected.getInputStream());

            /* Start reading in the request here.  Grabbing just the first four
             * bytes should let us know if it is zabbix 4+ (first four bytes
             * will be "ZBXD".
             */
            byte[] protoCheck = new byte[4];
            in.read(protoCheck, 0, protoCheck.length);
            String headerStr = new String(protoCheck, "UTF-8");
            String msgBodyStr = "";
            
            /* Check for a Zabbix 4+ header */
            if (Objects.equals(headerStr, new String("ZBXD"))) {
              /* Zabbix 4+ */

              byte[] zbxFlags = new byte[1];
              in.read(zbxFlags);

              byte[] msgLenBin = new byte[4];
              in.read(msgLenBin);
              int msgLen = java.nio.ByteBuffer.wrap(msgLenBin).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();

              byte[] reservedBin = new byte[4];
              in.read(reservedBin);

              byte[] msgBodyBin = new byte[msgLen];
              in.read(msgBodyBin);
              msgBodyStr = new String(msgBodyBin);
            }
            else { // Zabbix pre-4.0
              BufferedReader next = new BufferedReader(new InputStreamReader(connected.getInputStream()));
              String nextBodyStr = next.readLine();
              msgBodyStr = headerStr + nextBodyStr;
            }

            int continues = 1;
            while (continues == 1) {            
              if (msgBodyStr != null) {
                long timerStart = System.currentTimeMillis();

                result = checkAllPatterns(msgBodyStr);
                
                LOG.debug(String.format("Returned error status : %d", result.status));
                LOG.debug(String.format("Returned message : %s", result.message));
      
                if (result.status == 1) {
                  LOG.error(result.message);
                }
                if (result.status == 2) {
                  LOG.warn(result.message);
                }                

                /* Get the byte array for the packet */
                byte[] packet = makeZabbixPacket(result);

                /* Send the packet */
                sendZabbixPacket(packet, outputStream);

                long timerEnd = System.currentTimeMillis();
                LOG.debug("Request took " + (timerEnd - timerStart) + " ms");
              }
              continues = 0;
              
            }
            in.close();
            connected.close();
            } catch (IOException e) {
            LOG.info("thread I/O error: "
            + e.toString() + ". closing socket"
            );
            try {
              connected.close();
              } catch (IOException ee) {
              LOG.info("thread I/O error, can't close socket: "
              + ee.toString()
              );
            }
          }
        }
        if (alive > lifeTime) {
          LOG.debug("Thread closed, collecting data in " + (Thread.activeCount() - 2) + " threads");
          reincornate = 0;
        }
      }
    }
  }
  
  static class Shutdown extends Thread {
    
    public void run() {
      LOG.info("Shutting down");
      VmBix.shutdown();
    }
  }
}
