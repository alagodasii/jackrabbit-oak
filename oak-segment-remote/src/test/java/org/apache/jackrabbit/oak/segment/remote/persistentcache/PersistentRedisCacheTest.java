/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.jackrabbit.oak.segment.remote.persistentcache;

import org.apache.jackrabbit.oak.commons.Buffer;
import org.apache.jackrabbit.oak.segment.spi.monitor.IOMonitorAdapter;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import redis.embedded.RedisServer;
import redis.embedded.core.ExecutableProviderBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PersistentRedisCacheTest extends AbstractPersistentCacheTest {

    private static final String REDIS_HOST = "127.0.0.1";

    private RedisServer redisServer;
    private IOMonitorAdapter ioMonitorAdapter;

    @Before
    public void setUp() throws Exception {
        Path redisTempExecutable = new ExecutableProviderBuilder().addProvidedVersions().build().get().toPath();
        Path redisTargetExecutable = new File("target", redisTempExecutable.getFileName().toString()).toPath();
        Files.copy(redisTempExecutable, redisTargetExecutable, StandardCopyOption.REPLACE_EXISTING);
        redisServer = RedisServer.newRedisServer().setting("maxmemory 768mb").bind(REDIS_HOST).executableProvider(redisTargetExecutable::toFile).build();
        try {
            redisServer.start();
        } catch (IOException e) {
            Assume.assumeNoException(e);
        }
        int port = redisServer.ports().get(0);
        ioMonitorAdapter = mock(IOMonitorAdapter.class);

        persistentCache = new PersistentRedisCache(
                REDIS_HOST,
                port,
                -1,
                10000,
                1000,
                10,
                2000,
                200000,
                0,
                ioMonitorAdapter
        );
    }

    @After
    public void tearDown() throws IOException {
        redisServer.stop();
    }

    @Test
    public void testIOMonitor() throws InterruptedException {

        UUID segmentUUID = UUID.randomUUID();
        long msb = segmentUUID.getMostSignificantBits();
        long lsb = segmentUUID.getLeastSignificantBits();

        persistentCache.readSegment(msb, lsb, () -> null);

        //Segment not in cache, monitor methods not invoked
        verify(ioMonitorAdapter, never()).afterSegmentRead(any(), anyLong(), anyLong(), anyInt(), anyLong());

        persistentCache.writeSegment(msb, lsb, Buffer.wrap("segment_content".getBytes()));

        Thread.sleep(300);

        persistentCache.readSegment(msb, lsb, () -> null);

        verify(ioMonitorAdapter, times(1)).afterSegmentRead(any(), eq(msb), eq(lsb), anyInt(), anyLong());
    }
}