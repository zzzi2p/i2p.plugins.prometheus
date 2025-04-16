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
    basic_auth:                                     # only if console is password protected, see below
      username: xxxxxxx                             # only if console is password protected, see below
      password: 00000000000000000000000000000000    # only if console is password protected, see below
    scheme: https                                   # only if console is SSL, see below
    tls_config:                                     # only if console is SSL, see below
      insecure_skip_verify: true                    # only if console is SSL, see below
    static_configs:
      - targets: ['localhost:7657']                 # or 7667 or other SSL port

Then verify your configuration changes:

  promtool check config /etc/prometheus/prometheus.yml

And then tell prometheus to reload the config:

  sudo killall -HUP prometheus

You should then see i2p listed on the prometheus targets page:

  http://localhost:9090/classic/targets

and see i2p_* and jvm_* metrics to graph at:

  http://localhost:9090/classic/graph

For a nicer dashboard, use Grafana, and add Prometheus Server as a data source.


Grafana Tips
------------

After adding Prometheus Server as a data source, go to Drilldown -> Metrics.
Set "View By" to "i2p_".
Set Time Range to "Last 12 hours"
Look for interesting metrics, or type something in the "Search metrics" box.


Password protected console
--------------------------

This workaround is necessary because Prometheus Server does not
support Digest authentication, and Jetty does not support both
Digest and Basic authentication at the same time.

Requires router 2.8.2-3 or higher.

The username is the same as your regular console username.
The password is the 32-digit MD5 hash of your password.

You may find the MD5 hash in ~/.i2p/router.config in the line:

  routerconsole.auth.i2prouter.username.md5=00000000000000000000000000000000

or generate the MD5 hash as follows:

  echo -n 'console:i2prouter:yourpassword' | md5sum

When you install the plugin for the first time, your browser will ask for
the MD5 password to access the plugin. Enter the 32-digit hash.

For remote router instances, SSL console is recommended so that
the Basic authentication is protected.


SSL console
-----------

To enable SSL console, edit the console command line on /configclients
to add -s port, for example:

  net.i2p.router.web.RouterConsoleRunner 7657 0.0.0.0 -s 7667 127.0.0.1 ./webapps/

This requires routerconsole.advanced=true in router.config.
Alternatively, edit the file

  ~/.i2p/clients.config.d/00-net.i2p.router.web.RouterConsoleRunner-clients.config

Restart required in either case.

Then add the prometheus config as above.
