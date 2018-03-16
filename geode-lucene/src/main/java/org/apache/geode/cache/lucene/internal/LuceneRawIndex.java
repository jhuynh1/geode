/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
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

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.analysis.Analyzer;

import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.lucene.LuceneSerializer;
import org.apache.geode.cache.lucene.internal.repository.RepositoryManager;
import org.apache.geode.cache.lucene.internal.repository.serializer.HeterogeneousLuceneSerializer;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.PartitionedRegion;

public class LuceneRawIndex extends LuceneIndexImpl {

  private ExecutorService waitingThreadPool;

  protected LuceneRawIndex(String indexName, String regionPath, InternalCache cache) {
    super(indexName, regionPath, cache);
  }

  public LuceneRawIndex(String indexName, String regionPath, InternalCache cache, Analyzer analyzer,
                        Map<String, Analyzer> fieldAnalyzers,
                        LuceneSerializer serializer, RegionAttributes attributes, String aeqId,
                        String[] fields,
                        ExecutorService waitingThreadPool) {
    super(indexName, regionPath, cache);
    this.waitingThreadPool = waitingThreadPool;
    this.setSearchableFields(fields);
    this.setAnalyzer(analyzer);
    this.setFieldAnalyzers(fieldAnalyzers);
    this.setLuceneSerializer(serializer);
    this.setupRepositoryManager(serializer);
    this.createAEQ(attributes, aeqId);
  }

  @Override
  protected RepositoryManager createRepositoryManager(LuceneSerializer luceneSerializer) {
    HeterogeneousLuceneSerializer mapper = (HeterogeneousLuceneSerializer) luceneSerializer;
    if (mapper == null) {
      mapper = new HeterogeneousLuceneSerializer();
    }
    RawLuceneRepositoryManager rawLuceneRepositoryManager =
        new RawLuceneRepositoryManager(this, mapper);
    return rawLuceneRepositoryManager;
  }

  @Override
  protected void createLuceneListenersAndFileChunkRegions(
      PartitionedRepositoryManager partitionedRepositoryManager) {
    partitionedRepositoryManager.setUserRegionForRepositoryManager((PartitionedRegion) dataRegion);
  }

  @Override
  public void dumpFiles(String directory) {
    return;
  }

  @Override
  public void destroy(boolean initiator) {
  }

  @Override
  public boolean isIndexAvailable(int id) {
    return true;
  }
}
