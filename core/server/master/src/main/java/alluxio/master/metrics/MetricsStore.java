/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.master.metrics;

import alluxio.collections.IndexDefinition;
import alluxio.collections.IndexedSet;
import alluxio.metrics.Metric;
import alluxio.metrics.MetricsSystem;
import alluxio.metrics.MetricsSystem.InstanceType;

import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A store of metrics containing the metrics collected from workers and clients.
 */
@ThreadSafe
public class MetricsStore {
  private static final IndexDefinition<Metric> FULL_NAME_INDEX = new IndexDefinition<Metric>(true) {
    @Override
    public Object getFieldValue(Metric o) {
      return o.getFullMetricName();
    }
  };

  private static final IndexDefinition<Metric> NAME_INDEX = new IndexDefinition<Metric>(false) {
    @Override
    public Object getFieldValue(Metric o) {
      return o.getName();
    }
  };

  private static final IndexDefinition<Metric> HOSTNAME_ID_INDEX =
      new IndexDefinition<Metric>(false) {
        @Override
        public Object getFieldValue(Metric o) {
          return getHostnameAndId(o.getHostname(), o.getInstanceId());
        }
      };

  private static String getHostnameAndId(String hostname, String id) {
    String str = hostname == null ? "" : hostname;
    str += id == null ? "" : ":" + id;
    return str;
  }

  private final IndexedSet<Metric> mWorkerMetrics =
      new IndexedSet<>(FULL_NAME_INDEX, NAME_INDEX, HOSTNAME_ID_INDEX);
  private final IndexedSet<Metric> mClientMetrics =
      new IndexedSet<>(FULL_NAME_INDEX, NAME_INDEX, HOSTNAME_ID_INDEX);

  /**
   * Gets all the metrics by instance type. The supported instance types are worker and client.
   *
   * @param instanceType the instance type
   * @return the metrics stored in {@link IndexedSet};
   */
  private IndexedSet<Metric> getMetricsByInstanceType(MetricsSystem.InstanceType instanceType) {
    if (instanceType == InstanceType.WORKER) {
      return mWorkerMetrics;
    } else {
      throw new IllegalArgumentException("Unsupported instance type " + instanceType);
    }
  }

  /**
   * Put the metrics from a worker with a hostname. If all the old metrics associated with this
   * instance will be removed and then replaced by the latest.
   *
   * @param hostname the hostname of the instance
   * @param metrics the new worker metrics
   */
  public synchronized void putWorkerMetrics(String hostname, List<Metric> metrics) {
    mWorkerMetrics.removeByField(HOSTNAME_ID_INDEX, hostname);
    for (Metric metric : metrics) {
      mWorkerMetrics.add(metric);
    }
  }

  /**
   * Put the metrics from a client with a hostname and a client id. If all the old metrics
   * associated with this instance will be removed and then replaced by the latest.
   *
   * @param hostname the hostname of the client
   * @param clientId the id of the client
   * @param metrics the new metrics
   */
  public synchronized void putClientMetrics(String hostname, String clientId,
      List<Metric> metrics) {
    mClientMetrics.removeByField(HOSTNAME_ID_INDEX, getHostnameAndId(hostname, clientId));
    for (Metric metric : metrics) {
      if (metric.getHostname() == null) {
        continue; // ignore metrics who hostname is null
      }
      mClientMetrics.add(metric);
    }
  }

  /**
   * Gets all the metrics by instance type and the metric name. The supported instance types are
   * worker and client.
   *
   * @param instanceType the instance type
   * @param name the metric name
   * @return the set of matched metrics
   */
  public synchronized Set<Metric> getMetricsByInstanceTypeAndName(
      MetricsSystem.InstanceType instanceType, String name) {
    return getMetricsByInstanceType(instanceType).getByField(NAME_INDEX, name);
  }

  /**
   * Clears all the metrics.
   */
  public synchronized void clear() {
    mWorkerMetrics.clear();
    mClientMetrics.clear();
  }
}
