// Mocha's default is two seconds a test, and the common suite was written against a JVM: some of it
// plays whole matches against search-based opponents, which `tto-core` measured at about three
// times slower under wasm. The trials are what those tests measure, so the budget moves rather than
// the trial counts.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
