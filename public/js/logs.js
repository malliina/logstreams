"use strict";
var webSocket;

var tableContent;

var onconnect = function () {
    setFeedback("Connected.");
};

var onmessage = function (payload) {
    var json = JSON.parse(payload.data);
    var stringified = JSON.stringify(json);
    setFeedback(stringified);
    var table = document.getElementById("logTable");
    var row = table.insertRow(1);
    var cell = row.insertCell(0);
    cell.innerHTML = stringified;
};

var onclose = function (payload) {
    setFeedback("Connection closed.");
};

var onerror = function (payload) {
    setFeedback("Connection error.");
};

var setFeedback = function (fb) {
    document.getElementById("status").innerHTML = fb;
};

function initAll() {
    tableContent = document.getElementById("logTable");
}

window.onload = initAll();
