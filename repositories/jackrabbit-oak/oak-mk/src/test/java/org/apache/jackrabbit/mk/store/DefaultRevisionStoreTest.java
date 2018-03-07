/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.mk.store;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.blobs.MemoryBlobStore;
import org.apache.jackrabbit.mk.core.MicroKernelImpl;
import org.apache.jackrabbit.mk.core.Repository;
import org.apache.jackrabbit.mk.json.fast.Jsop;
import org.apache.jackrabbit.mk.json.fast.JsopArray;
import org.apache.jackrabbit.mk.model.Id;
import org.apache.jackrabbit.mk.model.StoredCommit;
import org.apache.jackrabbit.mk.persistence.GCPersistence;
import org.apache.jackrabbit.mk.persistence.InMemPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests verifying the inner workings of <code>DefaultRevisionStore</code>.
 */
public class DefaultRevisionStoreTest {

    /* avoid synthetic accessor */  DefaultRevisionStore rs;
    private MicroKernel mk;
    
    @Before
    public void setup() throws Exception {
        rs = new DefaultRevisionStore(createPersistence()) {
            @Override
            protected Id markCommits() throws Exception {
                // Keep head commit only
                StoredCommit commit = getHeadCommit();
                markCommit(commit);
                return commit.getId();
            }
        };
        rs.initialize();

        mk = new MicroKernelImpl(new Repository(rs, new MemoryBlobStore()));
    }
    
    protected GCPersistence createPersistence() throws Exception {
        return new InMemPersistence();
    }

    @After
    public void tearDown() throws Exception {
        if (mk != null) {
            mk.dispose();
        }
    }
    
    /**
     * Verify revision history works with garbage collection.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    public void testRevisionHistory() {
        mk.commit("/", "+\"a\" : { \"c\":{}, \"d\":{} }", mk.getHeadRevision(), null);
        mk.commit("/", "+\"b\" : {}", mk.getHeadRevision(), null);
        mk.commit("/b", "+\"e\" : {}", mk.getHeadRevision(), null);
        mk.commit("/a/c", "+\"f\" : {}", mk.getHeadRevision(), null);
        
        String headRevision = mk.getHeadRevision();
        String contents = mk.getNodes("/", headRevision);

        rs.gc();
        
        assertEquals(headRevision, mk.getHeadRevision());
        assertEquals(contents, mk.getNodes("/", headRevision));
        
        String history = mk.getRevisionHistory(Long.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(1, ((JsopArray) Jsop.parse(history)).size());
    }

    /**
     * Verify branch and merge works with garbage collection.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    public void testBranchMerge() throws Exception {
        mk.commit("/", "+\"a\" : { \"b\":{}, \"c\":{} }", mk.getHeadRevision(), null);
        String branchRevisionId = mk.branch(mk.getHeadRevision());

        mk.commit("/a", "+\"d\" : {}", mk.getHeadRevision(), null);
        branchRevisionId = mk.commit("/a", "+\"e\" : {}", branchRevisionId, null);
        
        rs.gc();

        branchRevisionId = mk.commit("/a", "+\"f\" : {}", branchRevisionId, null);
        mk.merge(branchRevisionId, null);

        rs.gc();

        String history = mk.getRevisionHistory(Long.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(1, ((JsopArray) Jsop.parse(history)).size());
    }
    
    /**
     * Verify garbage collection can run concurrently with commits.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    public void testConcurrentGC() throws Exception {
        ScheduledExecutorService gcExecutor = Executors.newScheduledThreadPool(1);
        gcExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                rs.gc();
            }
        }, 100, 20, TimeUnit.MILLISECONDS);

        mk.commit("/", "+\"a\" : { \"b\" : { \"c\" : { \"d\" : {} } } }",
                mk.getHeadRevision(), null);

        try {
            for (int i = 0; i < 20; i++) {
                mk.commit("/a/b/c/d", "+\"e\" : {}", mk.getHeadRevision(), null);
                Thread.sleep(10);
                mk.commit("/a/b/c/d/e", "+\"f\" : {}", mk.getHeadRevision(), null);
                Thread.sleep(30);
                mk.commit("/a/b/c/d", "-\"e\"", mk.getHeadRevision(), null);
            }
        } finally {
            gcExecutor.shutdown();
        }
    }

    /**
     * Verify garbage collection can run concurrently with branch & merge.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    public void testConcurrentMergeGC() throws Exception {
        ScheduledExecutorService gcExecutor = Executors.newScheduledThreadPool(1);
        gcExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                rs.gc();
            }
        }, 100, 20, TimeUnit.MILLISECONDS);

        mk.commit("/", "+\"a\" : { \"b\" : { \"c\" : { \"d\" : {} } } }",
                mk.getHeadRevision(), null);

        try {
            for (int i = 0; i < 20; i++) {
                String branchId = mk.branch(mk.getHeadRevision());
                if ((i & 1) == 0) {
                    /* add some data in even runs */
                    branchId = mk.commit("/a/b/c/d", "+\"e\" : {}", branchId, null);
                    Thread.sleep(10);
                    branchId = mk.commit("/a/b/c/d/e", "+\"f\" : {}", branchId, null);
                } else {
                    /* remove added data in odd runs */
                    branchId = mk.commit("/a/b/c/d", "-\"e\"", branchId, null);
                }
                Thread.sleep(30);
                mk.merge(branchId, null);
            }
        } finally {
            gcExecutor.shutdown();
        }
    }
}
