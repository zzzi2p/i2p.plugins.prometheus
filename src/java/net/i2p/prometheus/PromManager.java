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
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import net.i2p.I2PAppContext;
import net.i2p.app.*;
import static net.i2p.app.ClientAppState.*;
import net.i2p.data.DataHelper;
import net.i2p.stat.Rate;
import net.i2p.stat.RateStat;
import net.i2p.stat.StatManager;
import net.i2p.util.Log;

/**
 *
 * @author zzz
 */
public class PromManager implements ClientApp {
    private final I2PAppContext _context;
    private final Log _log;
    private final ClientAppManager _mgr;
    private int i2pCount, jvmCount;

    private ClientAppState _state = UNINITIALIZED;

    public PromManager(I2PAppContext ctx, ClientAppManager mgr, String args[]) {
        _context = ctx;
        _log = ctx.logManager().getLog(PromManager.class);
        _mgr = mgr;
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
     *  This adds the stats present at plugin startup.
     *  TODO: add a monitor to add stats that appear later.
     */
    private void addStats() {
        StatManager sm = _context.statManager();
        Map<String, SortedSet<String>> groups = sm.getStatsByGroup();
        int n = 0;
        for (Map.Entry<String, SortedSet<String>> e : groups.entrySet()) {
            //String pfx = "i2p." + e.getKey() + '.';
            String pfx = "i2p.";
            for (String s : e.getValue()) {
                RateStat rs = sm.getRate(s);
                if (rs == null)
                    continue;
                String desc = rs.getDescription();
                if (desc == null)
                    desc = "";
                long[] pers = rs.getPeriods();
                final long per = pers[0];
                final Rate rate = rs.getRate(per);
                if (rate == null)
                    continue;

                String name = pfx + s + '.' + DataHelper.formatDuration(per);
                name = name.replace(".", "_");
                name = name.replace("-", "_");
                // https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
                // Prevent IllegalArgumentExceptions
                if (name.replaceAll("[a-zA-Z0-9_]", "").length() != 0) {
                    if (_log.shouldWarn())
                        _log.warn("skipping stat with illegal chars: " + name);
                    continue;
                }

                if (_log.shouldDebug())
                    _log.debug("adding gauge " + name);

                GaugeWithCallback.builder()
                    .name(name)
                    .help(desc)
                    .labelNames("state")
                    .callback(callback -> {
                        callback.call(rate.getAvgOrLifetimeAvg(), "average");
                    })
                    .register();
                n++;
            }
        }
        i2pCount = n;
        if (_log.shouldDebug())
            _log.info(n + " PromManager I2P metrics registered");
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
        changeState(RUNNING);
        _mgr.register(this);
    }

    public synchronized void shutdown(String[] args) {
        _log.warn("PromManager shutdown");
        if (_state == STOPPED)
            return;
        changeState(STOPPING);
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
