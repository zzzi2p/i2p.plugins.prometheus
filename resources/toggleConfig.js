var expandConfig = null;
var collapseConfig = null;
var config = null;

function initToggleConfig()
{
    expandConfig = document.getElementById("expandConfig");
    collapseConfig = document.getElementById("collapseConfig");
    config = document.getElementById("configuration");
    hideConfig();
}

function hideConfig() {
  if (!collapseConfig)
    config.style.display = "none";
}

function showConfig() {
  if (collapseConfig)
    config.style.display = "block";
}

function clean() {
  if (expandConfig) {
      expandConfig.remove();
  }
  if (collapseConfig) {
      collapseConfig.remove();
  }
}

function expand() {
  clean();
  var x = document.createElement("link");
  x.type="text/css";
  x.rel="stylesheet";
  x.href="/prometheus/resources/expand.css";
  x.setAttribute("id", "expandConfig");
  document.head.appendChild(x);
  showConfig();
}

function collapse() {
  clean();
  var c = document.createElement("link");
  c.type="text/css";
  c.rel="stylesheet";
  c.href="/prometheus/resources/collapse.css";
  c.setAttribute("id", "collapseConfig");
  document.head.appendChild(c);
  hideConfig();
}

function copyText() {
  document.execCommand("copy");
}

document.addEventListener("DOMContentLoaded", function() {
    initToggleConfig();
}, true);
