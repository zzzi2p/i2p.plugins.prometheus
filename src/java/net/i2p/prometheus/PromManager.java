package net.i2p.prometheus;
/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import java.net.Socket;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import io.prometheus.metrics.core.metrics.CounterWithCallback;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.snapshots.Unit;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import net.i2p.I2PAppContext;
import net.i2p.app.*;
import static net.i2p.app.ClientAppState.*;
import net.i2p.data.DataHelper;
import net.i2p.router.RouterVersion;
import net.i2p.stat.Frequency;
import net.i2p.stat.FrequencyStat;
import net.i2p.stat.Rate;
import net.i2p.stat.RateStat;
import net.i2p.stat.StatManager;
import net.i2p.util.Log;
import net.i2p.util.SimpleTimer2;

/**
 *
 * @author zzz
 */
public class PromManager implements ClientApp {
    private final I2PAppContext _context;
    private final Log _log;
    private final ClientAppManager _mgr;
    private final Set<String> _registered;
    private int i2pCount, jvmCount;
    private SimpleTimer2.TimedEvent _timer;

    private ClientAppState _state = UNINITIALIZED;

    public static final String VERSION = "0.4";

    public PromManager(I2PAppContext ctx, ClientAppManager mgr, String args[]) {
        _context = ctx;
        _log = ctx.logManager().getLog(PromManager.class);
        _mgr = mgr;
        _registered = new HashSet<String>(256);
        _state = INITIALIZED;
    }

    public int getJVMCount() { return jvmCount; }
    public int getI2PCount() { return i2pCount; }

    /**
     *  Not supported
     */
    public synchronized static void main(String args[]) {
        throw new UnsupportedOperationException("Must use ClientApp interface");
    }

    /**
     *  This adds the rate and frequency stats present at plugin startup.
     *  Also run by the timer to add new stats later.
     */
    private void addStats() {
        StatManager sm = _context.statManager();
        Map<String, SortedSet<String>> groups = sm.getStatsByGroup();
        int n = 0;
        String pfx = "i2p.";
        for (Map.Entry<String, SortedSet<String>> e : groups.entrySet()) {
            //String pfx = "i2p." + e.getKey() + '.';
            for (String s : e.getValue()) {
                if (_registered.contains(s))
                    continue;
                RateStat rs = sm.getRate(s);
                FrequencyStat fs = null;
                Rate rate = null;
                long per = 0;
                String desc;

                if (rs != null) {
                    desc = rs.getDescription();
                    if (desc == null)
                        desc = "";
                    long[] pers = rs.getPeriods();
                    per = pers[0];
                    rate = rs.getRate(per);
                    if (rate == null)
                        continue;
                } else {
                    fs = _context.statManager().getFrequency(s);
                    if (fs == null)
                        continue;
                    desc = fs.getDescription();
                }

                String name = pfx + s;
                name = name.replace(".", "_");
                name = name.replace("-", "_");
                name = name.replace(" ", "_");  // tunnel names
                name = name.replace("(", "_");  // (falsePos)
                name = name.replace(")", "");   // (falsePos)
                name = name.replace("^", "_");  // (Hx^HI)
                // https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
                // Prevent IllegalArgumentExceptions
                if (name.replaceAll("[a-zA-Z0-9_]", "").length() != 0) {
                    if (_log.shouldWarn())
                        _log.warn("skipping stat with illegal chars: " + name);
                    continue;
                }

                if (rate != null) {
                    if (_log.shouldDebug())
                        _log.debug("adding gauge " + name);
                    if (addStat(rate, per, name, desc))
                        n++;
                } else {
                    if (_log.shouldDebug())
                        _log.debug("adding counter " + name);
                    addFreq(fs, name, desc);
                }
                _registered.add(s);
                n++;
            }
        }
        if (n > 0) {
            i2pCount += n;
            if (_log.shouldDebug())
                _log.info(n + " PromManager I2P metrics registered");
        }
    }

    /**
     *  This adds a single stat.
     *  It adds bytes or seconds units based on some heuristics.
     *  If the units cannot be determined, it also adds an event counter.
     *  @return true if we added an event counter also.
     */
    private static boolean addStat(final Rate rate, long period, String name, String desc) {
        boolean rv = false;
        GaugeWithCallback.Builder gwcb = GaugeWithCallback.builder()
            .help(desc);
        // heuristics
        String nlc = name.toLowerCase(Locale.US);
        if (nlc.contains("time") || nlc.contains("delay") || nlc.contains("lag") ||
            nlc.contains("_skew_")) {
            // All our stats are in ms
            gwcb.unit(Unit.SECONDS)
                .callback(callback -> {
                    callback.call(rate.getAvgOrLifetimeAvg() / 1000);
                });
        } else if (nlc.contains("size") || nlc.contains("memory") ||
                   nlc.contains("sendrate") || nlc.contains("recvrate") ||
                   nlc.contains("bps") || nlc.contains("bandwidth")) {
            gwcb.unit(Unit.BYTES)
                .callback(callback -> {
                    callback.call(rate.getAvgOrLifetimeAvg());
                });
        } else {
            gwcb.callback(callback -> {
                callback.call(rate.getAvgOrLifetimeAvg());
            });
            // events
            // Add a _count unit
            // Prometheus adds _total
            CounterWithCallback.builder()
                .name(name + "_count")
                .help(desc)
                .callback(callback -> {
                    callback.call(rate.getLifetimeEventCount());
                })
                .register();
            rv = true;
        }
        name += '_' + DataHelper.formatDuration(period);
        gwcb.name(name)
            .register();
        return rv;
    }

    /**
     *  This adds a single counter.
     */
    private static void addFreq(final FrequencyStat fs, String name, String desc) {
        // Add a _count unit
        // Prometheus adds _total
        CounterWithCallback.builder()
            .name(name + "_count")
            .help(desc)
            .callback(callback -> {
                callback.call(fs.getEventCount());
            })
            .register();
    }

    /**
     *  Add some constant "Info" metrics
     */
    private static void addInfos() {
        addInfo("i2p_info", "I2P info", "version", RouterVersion.FULL_VERSION);
        addInfo("i2p_plugin_info", "I2P Plugin Info", "version", VERSION);
    }

    /**
     *  Add some constant "Info" metrics
     */
    private static void addInfo(String name, String help, String label, String value) {
        Info.builder()
            .name(name)
            .help(help)
            .labelNames(label)
            .register()
            .addLabelValues(value);
    }

    /**
     *  Monitor to add stats that appear later.
     */
    private class Adder extends SimpleTimer2.TimedEvent {

        public Adder() {
            super(_context.simpleTimer2());
        }

        public void timeReached() {
            addStats();
            // TODO no way to remove stats, may be a small memory leak
            schedule(30*60*1000);
        }
    }


    /////// ClientApp methods

    public synchronized void startup() throws Exception {
        if (_state != STOPPED && _state != INITIALIZED && _state != START_FAILED) {
            _log.error("Start while state = " + _state);
            return;
        }
        _log.info("PromManager startup");
        JvmMetrics.builder().register();
        jvmCount = PrometheusRegistry.defaultRegistry.scrape().size();
        if (_log.shouldInfo())
            _log.info(jvmCount + " PromManager JVM metrics registered");

        addStats();
        addInfos();
        changeState(RUNNING);
        _mgr.register(this);
        _timer = new Adder();
        // relatively soon to catch the stats we missed at startup.
        // subsequent runs will be less frequent.
        _timer.schedule(5*60*1000);
    }

    public synchronized void shutdown(String[] args) {
        _log.warn("PromManager shutdown");
        if (_state == STOPPED)
            return;
        changeState(STOPPING);
        if (_timer != null) {
            _timer.cancel();
            _timer = null;
        }
        _registered.clear();
        i2pCount = 0;
        jvmCount = 0;
        // clear() supported as of v1.3.2
        PrometheusRegistry.defaultRegistry.clear();
        _mgr.unregister(this);
        changeState(STOPPED);
    }

    public ClientAppState getState() {
        return _state;
    }

    public String getName() {
        return "prometheus";
    }

    public String getDisplayName() {
        return "PromManager Metrics";
    }

    /////// end ClientApp methods

    private synchronized void changeState(ClientAppState state) {
        if (state == _state)
            return;
        _state = state;
        _mgr.notify(this, state, null, null);
    }

    private synchronized void changeState(ClientAppState state, String msg, Exception e) {
        if (state == _state)
            return;
        _state = state;
        _mgr.notify(this, state, msg, e);
    }
}
