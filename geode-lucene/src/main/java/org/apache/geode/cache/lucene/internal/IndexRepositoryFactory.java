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
package org.apache.geode.cache.lucene.internal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;

import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.EntryDestroyedException;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.lucene.LuceneSerializer;
import org.apache.geode.cache.lucene.internal.directory.RegionDirectory;
import org.apache.geode.cache.lucene.internal.partition.BucketTargetingMap;
import org.apache.geode.cache.lucene.internal.repository.IndexRepository;
import org.apache.geode.cache.lucene.internal.repository.IndexRepositoryImpl;
import org.apache.geode.distributed.DistributedLockService;
import org.apache.geode.internal.cache.BucketRegion;
import org.apache.geode.internal.cache.EntrySnapshot;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.PartitionRegionConfig;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.PartitionedRegionHelper;
import org.apache.geode.internal.logging.LogService;

public class IndexRepositoryFactory {
  private static final Logger logger = LogService.getLogger();
  public static final String APACHE_GEODE_INDEX_COMPLETE = "APACHE_GEODE_INDEX_COMPLETE";

  public IndexRepositoryFactory() {}

  public IndexRepository computeIndexRepository(final Integer bucketId, LuceneSerializer serializer,
      InternalLuceneIndex index, PartitionedRegion userRegion, final IndexRepository oldRepository)
      throws IOException {
    System.out.println(this+".computeIndexRepository bucketId: "+bucketId);
    return ((LuceneIndexForPartitionedRegion) index).computeIndexRepository(bucketId, serializer,
        userRegion, oldRepository);
  }



  /**
   * Find the bucket in region2 that matches the bucket id from region1.
   */
  protected BucketRegion getMatchingBucket(PartitionedRegion region, Integer bucketId) {
    // Force the bucket to be created if it is not already
    region.getOrCreateNodeForBucketWrite(bucketId, null);

    return region.getDataStore().getLocalBucketById(bucketId);
  }
}
