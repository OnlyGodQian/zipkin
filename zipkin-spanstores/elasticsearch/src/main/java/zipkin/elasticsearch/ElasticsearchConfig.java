/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.elasticsearch;

import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import static zipkin.internal.Util.checkNotNull;

public class ElasticsearchConfig {

  public static final class Builder {

    private String cluster = "elasticsearch";
    private List<String> hosts = Collections.singletonList("localhost:9300");
    private String index = "zipkin";

    /**
     * The elasticsearch cluster to connect to, defaults to "elasticsearch".
     */
    public Builder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    /**
     * A comma separated list of elasticsearch hostnodes to connect to, in host:port format. The
     * port should be the transport port, not the http port. Defaults to "localhost:9300".
     */
    public Builder hosts(List<String> hosts) {
      this.hosts = hosts;
      return this;
    }

    /**
     * The index prefix to use when generating daily index names. Defaults to zipkin.
     */
    public Builder index(String index) {
      this.index = index;
      return this;
    }

    public ElasticsearchConfig build() {
      return new ElasticsearchConfig(this);
    }
  }

  final String clusterName;
  final List<String> hosts;
  final String indexTemplate;
  final IndexNameFormatter indexNameFormatter;

  ElasticsearchConfig(Builder builder) {
    clusterName = checkNotNull(builder.cluster, "builder.cluster");
    hosts = checkNotNull(builder.hosts, "builder.hosts");
    String index = checkNotNull(builder.index, "builder.index");
    try {
      indexTemplate = Resources.toString(
          Resources.getResource("zipkin/elasticsearch/zipkin_template.json"),
          StandardCharsets.UTF_8)
          .replace("${__INDEX__}", index);
    } catch (IOException e) {
      throw new AssertionError("Error reading jar resource, shouldn't happen.", e);
    }
    indexNameFormatter = new IndexNameFormatter(index);
  }

  /**
   * Temporarily exposed until we make a storage component
   *
   * <p>See https://github.com/openzipkin/zipkin-java/issues/135
   */
  public Client connect() {
    Settings settings = Settings.builder()
        .put("cluster.name", clusterName)
        .put("client.transport.sniff", true)
        .build();

    TransportClient client = TransportClient.builder()
        .settings(settings)
        .build();
    for (String host : hosts) {
      HostAndPort hostAndPort = HostAndPort.fromString(host);
      try {
        client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
            hostAndPort.getHostText()), hostAndPort.getPort()));
      } catch (UnknownHostException e) {
        // Hosts may be down transiently, we should still try to connect. If all of them happen
        // to be down we will fail later when trying to use the client when checking the index
        // template.
        continue;
      }
    }
    return client;
  }
}
