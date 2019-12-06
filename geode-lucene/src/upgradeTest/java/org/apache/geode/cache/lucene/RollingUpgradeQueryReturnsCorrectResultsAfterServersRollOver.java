/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.cache.lucene;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache30.CacheSerializableRunnable;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.NetworkUtils;
import org.apache.geode.test.dunit.VM;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

public class RollingUpgradeQueryReturnsCorrectResultsAfterServersRollOver
    extends LuceneSearchWithRollingUpgradeDUnit {
  @Test
  public void luceneQueryReturnsCorrectResultsAfterServersRoll()
      throws Exception {
    executeLuceneQueryWithServerRollOvers("partitionedRedundant", oldVersion);
  }

  @Override
  void executeLuceneQueryWithServerRollOvers(String regionType, String startingVersion)
          throws Exception {
    final Host host = Host.getHost(0);
    VM server1 = host.getVM(startingVersion, 0);
    VM server2 = host.getVM(startingVersion, 1);
    VM server3 = host.getVM(startingVersion, 2);
    VM locator = host.getVM(startingVersion, 3);


    String regionName = "aRegion";
    String shortcutName = null;
    if ((regionType.equals("partitionedRedundant"))) {
      shortcutName = RegionShortcut.PARTITION_REDUNDANT.name();
    } else if ((regionType.equals("persistentPartitioned"))) {
      shortcutName = RegionShortcut.PARTITION_PERSISTENT.name();
      for (int i = 0; i < testingDirs.length; i++) {
        testingDirs[i] = new File(diskDir, "diskStoreVM_" + String.valueOf(host.getVM(i).getId()))
                .getAbsoluteFile();
        if (!testingDirs[i].exists()) {
          System.out.println(" Creating diskdir for server: " + i);
          testingDirs[i].mkdirs();
        }
      }
    }

    int[] locatorPorts = AvailablePortHelper.getRandomAvailableTCPPorts(1);
    String hostName = NetworkUtils.getServerHostName(host);
    String locatorString = getLocatorString(locatorPorts);
    final Properties locatorProps = new Properties();
    // configure all class loaders for each vm

    try {
      locator.invoke(invokeStartLocator(hostName, locatorPorts[0], getTestMethodName(),
              locatorString, locatorProps));
      invokeRunnableInVMs(invokeCreateCache(getSystemProperties(locatorPorts)), server1, server2,
              server3);

      // Create Lucene Index
      server1.invoke(() -> createLuceneIndex(cache, regionName, INDEX_NAME));
      server2.invoke(() -> createLuceneIndex(cache, regionName, INDEX_NAME));
      server3.invoke(() -> createLuceneIndex(cache, regionName, INDEX_NAME));

      // create region
      if ((regionType.equals("persistentPartitioned"))) {
        for (int i = 0; i < testingDirs.length; i++) {
          CacheSerializableRunnable runnable =
                  invokeCreatePersistentPartitionedRegion(regionName, testingDirs[i]);
          invokeRunnableInVMs(runnable, host.getVM(i));
        }
      } else {
        invokeRunnableInVMs(invokeCreateRegion(regionName, shortcutName), server1, server2,
                server3);
      }
      int expectedRegionSize = 10;
      int expectedQueryResultSize = 10;
      putSerializableObjectAndVerifyLuceneQueryResult(server1, regionName, expectedRegionSize,
              expectedQueryResultSize, 0,
              10, server2, server3);

      locator = rollLocatorToCurrent(locator, hostName, locatorPorts[0], getTestMethodName(),
              locatorString);

      server1 = rollServerToCurrentCreateLuceneIndexAndCreateRegion(server1, regionType,
              testingDirs[0], shortcutName, regionName, locatorPorts, reindex);

      expectedRegionSize += 5;
      putSerializableObject(server1, regionName, 5, 15);
      expectedRegionSize += 5;
      putSerializableObject(server2, regionName,10, 20);

      server2 = rollServerToCurrentCreateLuceneIndexAndCreateRegion(server2, regionType,
              testingDirs[1], shortcutName, regionName, locatorPorts, reindex);

      expectedRegionSize += 5;
      putSerializableObject(server2, regionName,15, 25);
      expectedRegionSize += 5;
      putSerializableObject(server3, regionName, 20, 30);

      server3 = rollServerToCurrentCreateLuceneIndexAndCreateRegion(server3, regionType,
              testingDirs[2], shortcutName, regionName, locatorPorts, reindex);
      expectedQueryResultSize = expectedRegionSize;
      verifyLuceneQueryResultInEachVM(regionName, expectedRegionSize, expectedQueryResultSize,
              server3);
      putSerializableObjectAndVerifyLuceneQueryResult(server3, regionName, expectedRegionSize,
              expectedQueryResultSize, 15,
              25, server1, server2);
      putSerializableObjectAndVerifyLuceneQueryResult(server1, regionName, expectedRegionSize,
              expectedQueryResultSize, 20,
              30, server1, server2, server3);


    } finally {
      invokeRunnableInVMs(true, invokeStopLocator(), locator);
      invokeRunnableInVMs(true, invokeCloseCache(), server1, server2, server3);
      if ((regionType.equals("persistentPartitioned"))) {
        deleteDiskStores();
      }
    }
  }


}
