/* Docspell JS */
function forEachIn(obj, fn) {
    var index = 0;
    for (var key in obj) {
        if (obj.hasOwnProperty(key)) {
            fn(obj[key], key, index++);
        }
    }
}

function extend() {
    var result = {};
    for (var i = 0; i < arguments.length; i++) {
        forEachIn(arguments[i],
                  function(obj, key) {
                      result[key] = obj;
                  });
    }
    return result;
}


var elmApp = Elm.Main.init({
    node: document.getElementById("docspell-app"),
    flags: elmFlags
});

elmApp.ports.internalSetUiTheme.subscribe(function(themeName) {
    var body = document.getElementsByTagName("body");
    if (body && body.length > 0) {
        var bodyClasses = body[0].classList;
        // seems that body attributes cannot be set from inside Elm.
        if (themeName && themeName.toLowerCase() === 'dark') {
            bodyClasses.add("bg-slate-800");
            bodyClasses.add("dark");
        } else {
            bodyClasses.remove("bg-slate-800");
            bodyClasses.remove("dark");
        }
    }
});

elmApp.ports.setAccount.subscribe(function(authResult) {
    console.log("Add account to local storage");
    localStorage.setItem("account", JSON.stringify(authResult));

    if (!dsWebSocket) {
        initWS();
    }
});

elmApp.ports.removeAccount.subscribe(function() {
    console.log("Remove account from local storage");
    localStorage.removeItem("account");
    closeWS();
});


var docspell_clipboards = {};

elmApp.ports.initClipboard.subscribe(function(args) {
    var page = args[0];
    if (!docspell_clipboards[page]) {
        var sel = args[1];
        docspell_clipboards[page] = new ClipboardJS(sel);
    }
});

elmApp.ports.checkSearchQueryString.subscribe(function(args) {
    var qStr = args;
    if (qStr && DsItemQueryParser && DsItemQueryParser['parseToFailure']) {
        var result = DsItemQueryParser.parseToFailure(qStr);
        var answer;
        if (result) {
            answer =
                { success: false,
                  input: result.input,
                  index: result.failedAt,
                  messages: result.messages
                };

        } else {
            answer =
                { success: true,
                  input: qStr,
                  index: 0,
                  messages: []
                };
        }
        elmApp.ports.receiveCheckQueryResult.send(answer);
    }
});

elmApp.ports.printElement.subscribe(function(id) {
    if (id) {
        var el = document.getElementById(id);
        var head = document.getElementsByTagName('head');
        if (head && head.length > 0) {
            head = head[0];
        }
        if (el) {
            var w = window.open();
            w.document.write('<html>');
            if (head) {
                w.document.write('<head>');
                ['title', 'meta'].forEach(function(el) {
                    var headEls = head.getElementsByTagName(el);
                    for (var i=0; i<headEls.length; i++) {
                        w.document.write(headEls.item(i).outerHTML);
                    }
                });
                w.document.write('</head>');
            }
            w.document.write('<body>');
            w.document.write('<div id="print-qr" style="width: 300px; height: 300px; padding: 5px; border: 1px solid black;">');
            w.document.write(el.outerHTML);
            w.document.write('</div>');
            w.document.write('<script type="application/javascript">window.print();</script>');
            w.document.write('</body></html>');
        }
    }
});

elmApp.ports.refreshFileView.subscribe(function(id) {
    var el = document.getElementById(id);
    if (el) {
        var tag = el.tagName;
        if (tag === "EMBED" || tag === "IFRAME") {
            var url = el.src;
            el.src = url;
        }
    }
});

var dsWebSocket = null;
function closeWS() {
    if (dsWebSocket) {
        console.log("Closing websocket connection");
        dsWebSocket.close(1000, "Done");
        dsWebSocket = null;
    }
}
function initWS() {
    closeWS();
    var protocol = (window.location.protocol === 'https:') ? 'wss:' : 'ws:';
    var url = protocol + '//' + window.location.host + '/api/v1/sec/ws';
    console.log("Initialize websocket at " + url);
    dsWebSocket = new WebSocket(url);
    dsWebSocket.addEventListener("message", function(event) {

        if (event.data) {
            var dataJSON = JSON.parse(event.data);
            if (dataJSON.tag !== "keep-alive") {
                elmApp.ports.receiveWsMessage.send(dataJSON);
            } else {
                dsWebSocket.send(event.data);
            }
        }
    });
}

// Websockets are not used yet for communicating to the server
// elmApp.ports.sendWsMessage.subscribe(function(msg) {
//     socket.send(msg);
// });

// Comp.Dropdown has no per-instance DOM id, so Elm cannot focus its filter
// input. Instead, react to its menu being shown or hidden.
function dsIsVisible(el) {
    return el && el.getClientRects().length > 0;
}

function dsDropdownMenuToggled(menu, wasHidden) {
    var isHidden = menu.classList.contains("hidden");
    var dropdown = menu.parentElement;
    var input = dropdown.querySelector("input.ds-dropdown-filter");
    if (wasHidden && !isHidden) {
        if (dsIsVisible(input)) {
            input.focus();
        }
    } else if (!wasHidden && isHidden) {
        // keep focus inside the dropdown when the focused element got hidden
        var active = document.activeElement;
        if (dropdown.contains(active) && !dsIsVisible(active)) {
            var target = dsIsVisible(input) ? input : dropdown.querySelector("a[tabindex]");
            if (target) {
                target.focus();
            }
        }
    }
}

new MutationObserver(function(mutations) {
    mutations.forEach(function(m) {
        if (m.target.classList.contains("ds-dropdown-menu")) {
            var wasHidden = (m.oldValue || "").split(/\s+/).indexOf("hidden") >= 0;
            dsDropdownMenuToggled(m.target, wasHidden);
        }
    });
}).observe(document.body, {
    subtree: true,
    attributes: true,
    attributeFilter: ["class"],
    attributeOldValue: true
});

// Close popup menus on a click outside of them or on Escape. Elm marks open
// menu roots with ds-menu-open and listens for ds-close (see Util.Html.menuRoot).
function dsCloseMenus(shouldClose) {
    document.querySelectorAll(".ds-menu-open").forEach(function(menu) {
        if (shouldClose(menu)) {
            menu.dispatchEvent(new CustomEvent("ds-close"));
        }
    });
}

document.addEventListener("pointerdown", function(event) {
    dsCloseMenus(function(menu) {
        return !(menu.closest(".ds-menu-scope") || menu).contains(event.target);
    });
}, true);

document.addEventListener("keydown", function(event) {
    if (event.key === "Escape") {
        // ds-menu-keys components handle Escape themselves while focused
        dsCloseMenus(function(menu) {
            return !(menu.classList.contains("ds-menu-keys") && menu.contains(document.activeElement));
        });
    }
});
