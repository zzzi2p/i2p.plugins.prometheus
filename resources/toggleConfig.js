let config;
let debug = false;

function initToggleConfig() {
  const configurationElement = document.getElementById("configuration");
  if (configurationElement) {
    config = configurationElement;
    hideConfig();
  } else if (debug) {
    console.error("Configuration element not found.");
  }
}

function hideConfig() {
  if (config) {
    config.style.display = "none";
  } else if (debug && !document.querySelector("#expandConfig")) {
    console.error("ExpandConfig link element not found.");
  }
}

function showConfig() {
  if (document.querySelector("#collapseConfig")) {
    config.style.display = "block";
  } else if (debug) {
    console.error("CollapseConfig link element not found.");
  }
}

function clean() {
  const expandConfigElement = document.getElementById("expandConfig");
  const collapseConfigElement = document.getElementById("collapseConfig");

  if (expandConfigElement) {
    expandConfigElement.remove();
  }

  if (collapseConfigElement) {
    collapseConfigElement.remove();
  }
}

function expand() {
  clean();
  const x = document.createElement("link");
  x.type = "text/css";
  x.rel = "stylesheet";
  x.href = "/prometheus/resources/expand.css";
  x.setAttribute("id", "expandConfig");
  document.head.appendChild(x);
  showConfig();
}

function collapse() {
  clean();
  const c = document.createElement("link");
  c.type = "text/css";
  c.rel = "stylesheet";
  c.href = "/prometheus/resources/collapse.css";
  c.setAttribute("id", "collapseConfig");
  document.head.appendChild(c);
  hideConfig();
}

function copyText() {
  navigator.clipboard.writeText("Some text to copy").then(
    () => console.log('Copy successful'),
    err => console.error('Copy failed: ', err)
  );
}

document.addEventListener("DOMContentLoaded", function () {
  initToggleConfig();
}, true);
