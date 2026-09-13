// The same guard `tto-core` needed at v0.8.6, for the same reason: Mocha's budget
// (`mocha-timeout.js`) is not the only clock a long test runs against. Karma talks to the browser
// over a socket that answers pings from the page's main thread, and a wasm test is synchronous —
// while the common suite plays a match against a search-based opponent nothing else on that thread
// runs, the pings go unanswered, and Karma drops the browser ("reconnect failed before timeout of
// 2000ms (ping timeout)"). The test has not failed; the harness has stopped listening, and it does
// so more readily on a shared CI runner than on a developer's machine.
//
// So every clock that can expire during one synchronous test gets the same budget as Mocha's.
config.pingTimeout = 60000;
config.browserDisconnectTimeout = 60000;
config.browserNoActivityTimeout = 120000;
config.captureTimeout = 120000;
