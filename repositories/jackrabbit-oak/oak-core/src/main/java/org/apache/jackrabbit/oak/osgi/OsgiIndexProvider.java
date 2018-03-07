/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.osgi;

import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.oak.spi.QueryIndex;
import org.apache.jackrabbit.oak.spi.QueryIndexProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This index provider combines all indexes of all available OSGi index
 * providers.
 */
public class OsgiIndexProvider implements ServiceTrackerCustomizer, QueryIndexProvider {

    private BundleContext context;

    private ServiceTracker tracker;

    private final Map<ServiceReference, QueryIndexProvider> providers =
        new HashMap<ServiceReference, QueryIndexProvider>();

    public void start(BundleContext bundleContext) throws Exception {
        context = bundleContext;
        tracker = new ServiceTracker(
                bundleContext, QueryIndexProvider.class.getName(), this);
        tracker.open();
    }

    public void stop() throws Exception {
        tracker.close();
    }

    @Override
    public Object addingService(ServiceReference reference) {
        Object service = context.getService(reference);
        if (service instanceof QueryIndexProvider) {
            QueryIndexProvider provider = (QueryIndexProvider) service;
            providers.put(reference, provider);
            return service;
        } else {
            context.ungetService(reference);
            return null;
        }
    }

    @Override
    public void modifiedService(ServiceReference reference, Object service) {
        // nothing to do
    }

    @Override
    public void removedService(ServiceReference reference, Object service) {
        providers.remove(reference);
        context.ungetService(reference);
    }

    @Override
    public List<QueryIndex> getQueryIndexes(MicroKernel mk) {
        if (providers.isEmpty()) {
            return Collections.emptyList();
        } else if (providers.size() == 1) {
            return providers.get(0).getQueryIndexes(mk);
        } else {
            // TODO combine indexes
            return null;
        }
    }

}
