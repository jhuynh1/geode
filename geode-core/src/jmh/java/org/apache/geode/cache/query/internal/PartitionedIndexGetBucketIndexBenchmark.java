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
package org.apache.geode.cache.query.internal;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.query.internal.index.PartitionedIndex;
import org.apache.geode.distributed.DistributedSystem;

/**
 * Test spins up threads that constantly do computeIfAbsent
 * The tests will measure throughput
 * The benchmark tests computeIfAbsent in the presence of other threads contending for the same key
 */

@State(Scope.Thread)
@Fork(1)
public class PartitionedIndexGetBucketIndexBenchmark {


  PartitionedIndex index;
  /*
   * After load is established, how many measurements shall we take?
   */
  private static final double BENCHMARK_ITERATIONS = 10;

  private static final int TIME_TO_QUIESCE_BEFORE_SAMPLING = 1;

  private static final int THREAD_POOL_PROCESSOR_MULTIPLE = 2;

  private ScheduledThreadPoolExecutor loadGenerationExecutorService;

  private static boolean testRunning = true;

  @Setup(Level.Trial)
  public void trialSetup() throws InterruptedException {
    DistributedSystem mockDS = mock(DistributedSystem.class);
    Cache mockCache = mock(Cache.class);
    Region mockRegion = mock(Region.class);
    when(mockRegion.getCache()).thenReturn(mockCache);
    when(mockCache.getDistributedSystem()).thenReturn(mockDS);
    index = new PartitionedIndex(null, null, "", mockRegion, "", "", "");
    // Index subIndex = mock(Index.class);
    // when(subIndex.getName()).thenReturn("Name");
    index.addToBucketIndexes(mockRegion, mock(Index.class));


    final int numberOfThreads =
        THREAD_POOL_PROCESSOR_MULTIPLE * Runtime.getRuntime().availableProcessors();

    loadGenerationExecutorService =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(
            numberOfThreads);

    System.out.println(String.format("Pool has %d threads", numberOfThreads));

    loadGenerationExecutorService.setRemoveOnCancelPolicy(true);

    generateLoad(
        loadGenerationExecutorService, numberOfThreads);

    // allow system to quiesce
    Thread.sleep(TIME_TO_QUIESCE_BEFORE_SAMPLING);
  }

  @TearDown(Level.Trial)
  public void trialTeardown() {
    testRunning = false;
    loadGenerationExecutorService.shutdownNow();
  }

  @Benchmark
  @Measurement(iterations = (int) BENCHMARK_ITERATIONS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  // @Warmup we don't warm up because our @Setup warms us up
  public Object computeIfAbsent() {
    return index.getBucketIndex();
  }

  private void generateLoad(final ScheduledExecutorService executorService, int numThreads) {
    for (int i = 0; i < numThreads; i++) {
      executorService.schedule(() -> {
        while (testRunning) {
          index.getBucketIndex();
        }
      }, 0, TimeUnit.MILLISECONDS);
    }
  }

}
