Prometheus Metrics Plugin
-------------------------

This is a Prometheus Metrics client ("target").

It provides ("exports") I2P and JVM statistics ("metrics")
for scraping by the Prometheus server.

You must install prometheus server:

  sudo apt install prometheus

And then add i2p to the prometheus configuration.
Edit /etc/prometheus/prometheus.yml and add:

  - job_name: i2p
    scrape_interval: 60s
    metrics_path: /prometheus/metrics
    static_configs:
      - targets: ['localhost:7657']

And then tell prometheus to reload the config:

  sudo killall -HUP prometheus

You should then see i2p listed on the prometheus targets page:

  http://localhost:9090/classic/targets

and see i2p_* and jvm_* metrics to graph at:

  http://localhost:9090/classic/graph

For a nicer dashboard, use Grafana, and add Prometheus Server as a data source.
