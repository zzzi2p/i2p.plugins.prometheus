package net.i2p.prometheus.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.i2p.app.ClientAppManager;
import net.i2p.app.ClientAppState;
import net.i2p.data.DataHelper;
import net.i2p.prometheus.PromManager;
import net.i2p.util.I2PAppThread;
import net.i2p.util.PortMapper;
import net.i2p.util.Translate;

import net.i2p.I2PAppContext;

/**
 *  From socksoutproxy
 */
public class PrometheusServlet extends BasicServlet {
    private String _contextPath;
    private String _contextName;
    private volatile PromManager _manager;
    private volatile boolean _isRunning;
    private static long _nonce;

    private static final String DEFAULT_NAME = "prometheus";
    private static final String DOCTYPE = "<!DOCTYPE HTML>\n";
    private static final String FOOTER = "</table>\n</div>\n<span id=\"endOfPage\" data-iframe-height></span>\n</body>\n</html>";
    // for now, use console bundle, hope to pick up a few translations for free
    private static final String BUNDLE = "net.i2p.router.web.messages";
    private static final String RESOURCES = "/prometheus/resources/";
    private static final String VERSION = PromManager.VERSION;

    public PrometheusServlet() {
        super();
    }

    @Override
    public void init(ServletConfig cfg) throws ServletException {
        super.init(cfg);
        String cpath = getServletContext().getContextPath();
        _contextPath = cpath == "" ? "/" : cpath;
        _contextName = cpath == "" ? DEFAULT_NAME : cpath.substring(1).replace("/", "_");
        _nonce = _context.random().nextLong();
        _isRunning = true;
        (new Starter()).start();
    }

    /**
     *  Wait for the ClientAppManager
     */
    private class Starter extends I2PAppThread {
        public Starter() {
            super("Prometheus Starter");
        }

        public void run() {
            try {
                run2();
            } catch (Throwable t) {
                // class problems, old router version, ...
                _log.error("Unable to start Prometheus", t);
                _isRunning = false;
            }
        }

        private void run2() throws Exception {
            File f = new File(_context.getConfigDir(), "plugins");
            f = new File(f, _contextName);
            String[] args = new String[] { f.toString() };
            while (_isRunning) {
                ClientAppManager cam = _context.clientAppManager();
                if (cam != null) {
                    _manager = new PromManager(_context, cam, args);
                    _manager.startup();
                    break;
                } else {
                    try {
                        Thread.sleep(10*1000);
                    } catch (InterruptedException ie) {}
                }
            }
        }
    }

    @Override
    public void destroy() {
        _isRunning = false;
        if (_manager != null)
            _manager.shutdown(null);
        super.destroy();
    }

    /**
     *  Handle what we can here, calling super.doGet() for the rest.
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGetAndPost(request, response);
    }

    /**
     *  Handle what we can here, calling super.doPost() for the rest.
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGetAndPost(request, response);
    }

    /**
     * Handle all here
     */
    private void doGetAndPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PromManager c = _manager;
        String method = req.getMethod();
        String msg = null;
        if (c != null) {
                if (c.getState() != ClientAppState.RUNNING) {
                    try {
                        c.startup();
                        msg = "Prometheus started";
                    } catch (Exception e) {
                        msg = "Prometheus failure: " + e;
                    }
                }
        }

        // this is the part after /orchid
        String path = req.getServletPath();
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");

        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=UTF-8");

        PrintWriter out = resp.getWriter();
        out.write(DOCTYPE + "<html>\n<head>\n<title>");
        out.write(_t("Prometheus Metrics Client"));
        out.write("</title>\n");
        out.write("<script src=\"" + RESOURCES + "toggleConfig.js\" type=\"application/javascript\"></script>\n");
        out.write("<meta http-equiv=\"Content-Security-Policy\" content=\"script-src \'self\' \'unsafe-inline\';\">\n");
        out.write("<link rel=\"icon\" href=\"" + RESOURCES + "images/prometheus.svg\">\n");
        out.write("<link href=\"" + RESOURCES + "prometheus.css?" + VERSION + "\" rel=\"stylesheet\" type=\"text/css\">\n");
        out.write("<noscript><style>.script, #expand, #collapse {display: none !important} #configuration {display: table !important} " +
                  "*::selection {color: #fff; background: #77f}");
        out.write("</style></noscript>\n</head>\n");
        out.write("<body id=\"prometheus\">\n<div id=\"container\">\n<table id=\"main\" width=\"100%\">\n" +
                  "<thead><tr><th id=\"title\" align=\"left\">" + _t("Prometheus Metrics Client") + "</th></tr></thead>\n");
        out.write("<tbody>\n<tr><td>\n<hr>\n<table id=\"status\" width=\"100%\">\n<tr class=\"subtitle\">" +
                  "<th width=\"20%\">" + _t("Status") + "</th>" +
                  "<th width=\"20%\">" + _t("Registered with I2P") + "</th>" +
                  "<th width=\"20%\">" + _t("Plugin Version") + "</th>" +
                  "<th width=\"20%\">" + _t("I2P Metrics") + "</th>" +
                  "<th width=\"20%\">" + _t("Java Metrics") + "</th>");
        out.write("</tr>\n<tr><td align=\"center\">");
        if (c != null) {
            ClientAppState status = c.getState();
            if (status == ClientAppState.RUNNING)
                out.write("<span id=\"running\">" + _t("Running") + "</span>");
            else if (status == ClientAppState.STARTING)
                out.write("<span id=\"starting\">" + _t("Starting") + "...</span>");
            else if (status == ClientAppState.START_FAILED)
                out.write("<span id=\"notregistered\">" + _t("Start failed") + "</span>");
            else
                out.write(status.toString());
        } else {
            out.write(_t("Not initialized"));
        }
        out.write("</td><td align=\"center\">");
        ClientAppManager cam = _context.clientAppManager();
        if (c != null && cam != null && cam.getRegisteredApp(DEFAULT_NAME) == c) {
            out.write("<span id=\"registered\">" + _t("Yes") + "</span>");
        } else {
            out.write("<span id=\"notregistered\">" + _t("No") + "</span>");
        }
        out.write("</td><td align=\"center\">" + VERSION + "</td>\n");
        if (c != null)
            out.write("</td><td align=\"center\">" + c.getI2PCount() + "</td>\n");
        else
            out.write("</td><td align=\"center\">--</td>\n");
        if (c != null)
            out.write("</td><td align=\"center\">" + c.getJVMCount() + "</td>\n");
        else
            out.write("</td><td align=\"center\">--</td></tr>\n");
        if (msg != null)
            out.write("<tr id=\"message\"><td colspan=\"5\" align=\"center\"><b>" + msg + "</b></td></tr>\n");
        out.write("</table>\n");
        if (c != null) {
            out.write("<p><a href=\"/prometheus/metrics\">View raw metrics</a></p>\n");
            out.write("<tr id=\"configsection\"><td>\n<hr>\n<div id=\"configtitle\"><b>Configuration</b>&nbsp;\n" +
                      "<a class=\"script\" id=\"expand\" href=\"#\" onclick=\"clean();expand();\">" +
                      "<img alt=\"Expand\" src=\"/prometheus/resources/images/expand.png\" title=\"Expand\"></a>\n" +
                      "<a class=\"script\" id=\"collapse\" href=\"#\" onclick=\"clean();collapse();\">" +
                      "<img alt=\"Collapse\" src=\"/prometheus/resources/images/collapse.png\" title=\"Collapse\"></a></div>\n");
            out.write(getHTMLConfig(c));
        }
        out.write(FOOTER);
    }

    private String getHTMLConfig(PromManager tc) {
        StringBuilder buf = new StringBuilder(1024);
        buf.append("<hr>\n<div id=\"configuration\" width=\"100%\">\n");
        boolean full = _context.getBooleanProperty("stat.full");
        if (full)
            buf.append("<p>Full stats are enabled.</p>\n");
        else
            buf.append("<p>For more metrics, <a href=\"/configstats\">enable full stats</a> and restart.</p>\n");
        buf.append("<p>Prometheus server configuration: add to <code>/etc/prometheus/prometheus.yml</code>:</p>\n");
        int port = _context.portMapper().getPort(PortMapper.SVC_CONSOLE);
        if (port <= 0)
            port = 7657;
        buf.append("<pre>" +
                   "  - job_name: i2p\n" +
                   "    scrape_interval: 60s\n" +
                   "    metrics_path: /prometheus/metrics\n" +
                   "    static_configs:\n" +
                   "      - targets: ['localhost:").append(port).append("']\n" +
                   "</pre>\n");
        buf.append("<p>And then: <code>sudo killall -HUP prometheus</code> to force reload of the config.</p>\n");
        buf.append("<p>For password-protected or SSL consoles, see the file <code>")
           .append(_context.getConfigDir())
           .append("/plugins/prometheus/README.txt</code> for instructions.</p>\n");
        buf.append("</div>\n");
        return buf.toString();
    }

    /** translate */
    private String _t(String s) {
        return Translate.getString(s, _context, BUNDLE);
    }

    /** translate */
    private String _t(String s, Object o) {
        return Translate.getString(s, o, _context, BUNDLE);
    }

    /** translate */
    private String _t(String s, Object o, Object o2) {
        return Translate.getString(s, o, o2, _context, BUNDLE);
    }

    /** translate (ngettext) @since 0.7.14 */
    private String ngettext(String s, String p, int n) {
        return Translate.getString(n, s, p, _context, BUNDLE);
    }

    /** dummy for tagging */
    private static String ngettext(String s, String p) {
        return null;
    }

}
