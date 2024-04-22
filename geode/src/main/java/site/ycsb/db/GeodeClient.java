/**
 * Copyright (c) 2013 - 2016 YCSB Contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

 package site.ycsb.db;

 import org.apache.geode.cache.GemFireCache;
 import org.apache.geode.cache.Region;
 import org.apache.geode.cache.RegionExistsException;
 import org.apache.geode.cache.client.ClientCache;
 import org.apache.geode.cache.client.ClientCacheFactory;
 import org.apache.geode.cache.client.ClientRegionFactory;
 import org.apache.geode.cache.client.ClientRegionShortcut;
 import org.apache.geode.pdx.JSONFormatter;
 import org.apache.geode.pdx.PdxInstance;
 import org.apache.geode.pdx.PdxInstanceFactory;
 import site.ycsb.ByteArrayByteIterator;
 import site.ycsb.ByteIterator;
 import site.ycsb.DB;
 import site.ycsb.DBException;
 import site.ycsb.Status;
 
 import java.net.InetSocketAddress;
 import java.util.HashMap;
 import java.util.Map;
 import java.util.Properties;
 import java.util.Set;
 import java.util.Vector;
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;
 
 import static org.awaitility.Awaitility.await;
 
 /**
  * Apache Geode client for the YCSB benchmark.<br />
  * <p>By default acts as a Geode client and tries to connect
  * to Geode cache server running on localhost with default
  * cache server port. Hostname and port of a Geode cacheServer
  * can be provided using <code>geode.serverport=port</code> and <code>
  * geode.serverhost=host</code> properties on YCSB command line.
  * A locator may also be used for discovering a cacheServer
  * by using the property <code>geode.locator=host[port]</code></p>
  * <p>
  * <p>To run this client in a peer-to-peer topology with other Geode
  * nodes, use the property <code>geode.topology=p2p</code>. Running
  * in p2p mode will enable embedded caching in this client.</p>
  * <p>
  * <p>YCSB by default does its operations against "usertable". When running
  * as a client this is a <code>ClientRegionShortcut.PROXY</code> region,
  * when running in p2p mode it is a <code>RegionShortcut.PARTITION</code>
  * region. A cache.xml defining "usertable" region can be placed in the
  * working directory to override these region definitions.</p>
  */
 public class GeodeClient extends DB {
   /**
    * property name to specify a Geode locator. This property can be used in both
    * client server and p2p topology
    */
   private static final String LOCATOR_PROPERTY_NAME = "geode.locator";
 
   /**
    * Pattern to split up a locator string in the form host[port].
    */
   private static final Pattern LOCATOR_PATTERN = Pattern.compile("(.+)\\[(\\d+)\\]");
 
   private static volatile GemFireCache cache;
   private static volatile boolean cacheStarted = false;
 
   private void initializeCache() {
     Properties props = getProperties();
     if (props != null && !props.isEmpty()) {
       ClientCacheFactory ccf = getClientCacheFactory(props);
 
       InetSocketAddress locatorAddress = getLocatorAddress(props.getProperty(LOCATOR_PROPERTY_NAME));
       ccf.addPoolLocator(locatorAddress.getHostName(), locatorAddress.getPort());
       ccf.set("name", "geode-client");
           
       synchronized (GeodeClient.class) {
         if (cache == null) {
           System.err.println("Creating Cache");
           cache = ccf.create();
           System.err.println("Created Cache: " + cache.getName());
         }
       }
       cacheStarted = true;
     }
   }
 
   @Override
   public void init() throws DBException {
     if (cache == null) {
       initializeCache();
     }
     await().until(() -> cacheStarted);
   }
 
   private static ClientCacheFactory getClientCacheFactory(Properties props) {

     Properties ccfProps = new Properties();
     // for auth setting if commercial gemfire
     if (props.getProperty("gfsh-username") != null && !props.isEmpty()) {
       ccfProps.setProperty("security-client-auth-init", UserPasswordAuthInit.class.getName()); 
     }
 
     ClientCacheFactory ccf = null;
     if (ccfProps.isEmpty()) {
       ccf= new ClientCacheFactory();
     }else{ // for SSL setting if commercial gemfire
      //  System.out.println("[INFO] GeodeClient.java init(): initializing client using SSL connection ..."); // TODO
       ccf= new ClientCacheFactory(ccfProps);
       ccf.set("log-level", "config");
       ccf.set("cluster-ssl-enabled", props.getProperty("cluster-ssl-enabled"));
       ccf.set("cluster-ssl-require-authentication", props.getProperty("cluster-ssl-require-authentication"));
       ccf.set("cluster-ssl-ciphers", props.getProperty("cluster-ssl-ciphers"));
       ccf.set("cluster-ssl-keystore", props.getProperty("ssl-keystore"));
       ccf.set("cluster-ssl-keystore-password", props.getProperty("ssl-keystore-password"));
       ccf.set("cluster-ssl-truststore", props.getProperty("ssl-truststore"));
       ccf.set("cluster-ssl-truststore-password", props.getProperty("ssl-truststore-password"));
       ccf.set("cluster-ssl-keystore-type", props.getProperty("cluster-ssl-keystore-type"));
     }
     
     ccf.setPdxReadSerialized(true);
     ccf.setPoolMinConnections(1);
     ccf.setPoolMaxConnections(-1);
     
     return ccf;
   }
 
   static InetSocketAddress getLocatorAddress(String locatorStr) {
     Matcher matcher = LOCATOR_PATTERN.matcher(locatorStr);
     if (!matcher.matches()) {
       throw new IllegalStateException("Unable to parse locator: " + locatorStr);
     }
     return new InetSocketAddress(matcher.group(1), Integer.parseInt(matcher.group(2)));
   }
 
   @Override
   public Status read(String table, String key, Set<String> fields,
                      Map<String, ByteIterator> result) {
     Region<String, PdxInstance> r = getRegion(table);
     PdxInstance val = r.get(key);
     if (val != null) {
       if (fields == null) {
         for (String fieldName : val.getFieldNames()) {
           result.put(fieldName, new ByteArrayByteIterator((byte[]) val.getField(fieldName)));
         }
       } else {
         for (String field : fields) {
           result.put(field, new ByteArrayByteIterator((byte[]) val.getField(field)));
         }
       }
       return Status.OK;
     }
     System.out.println("[ERROR] GeodeClient.java read(): PdxInstance ERROR null"); // TODO
     return Status.ERROR;
   }
 
   @Override
   public Status scan(String table, String startkey, int recordcount,
                      Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
     // Geode does not support scan
     return Status.ERROR;
   }
 
   @Override
   public Status update(String table, String key, Map<String, ByteIterator> values) {
     getRegion(table).put(key, convertToBytearrayMap(values));
     return Status.OK;
   }
 
   @Override
   public Status insert(String table, String key, Map<String, ByteIterator> values) {
     getRegion(table).put(key, convertToBytearrayMap(values));
     return Status.OK;
   }
 
   @Override
   public Status delete(String table, String key) {
     getRegion(table).destroy(key);
     return Status.OK;
   }
 
   private PdxInstance convertToBytearrayMap(Map<String, ByteIterator> values) {
     PdxInstanceFactory pdxInstanceFactory = cache.createPdxInstanceFactory(JSONFormatter.JSON_CLASSNAME);
 
     for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
       pdxInstanceFactory.writeByteArray(entry.getKey(), entry.getValue().toArray());
     }
     return pdxInstanceFactory.create();
   }
 
   private Region<String, PdxInstance> getRegion(String table) {
     Region<String, PdxInstance> r = getCacheRegion(table);
     if (r == null) {
       synchronized (GeodeClient.class) {
         try {
           ClientRegionFactory<String, PdxInstance> crf =
               ((ClientCache) getCache()).createClientRegionFactory(ClientRegionShortcut.PROXY);
           r = crf.create(table);
         } catch (RegionExistsException e) {
           // another thread created the region
           r = getCacheRegion(table);
         }
       }
     }
     return r;
   }
 
   private Region<String, PdxInstance> getCacheRegion(String table) {
     return getCache().getRegion(table);
   }
 
   private GemFireCache getCache() {
     return cache;
   }
 }