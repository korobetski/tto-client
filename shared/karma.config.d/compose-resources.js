// Serves the Compose resource bundle to the test page, which Karma does not do on its own.
//
// The build copies it beside the test bundle, under `kotlin/composeResources/`, but Karma serves only
// what `files` names — and under `/base/`, not at the root where `Res.readBytes` fetches
// `./composeResources/…` from. Without both halves every resource read in a wasm test is a 404,
// reported as `MissingResourceException`. `CatalogLoadingTest` is the test that depends on it.
config.files.push({
  pattern: 'kotlin/composeResources/**/*',
  included: false,
  served: true,
  watched: false,
  nocache: true,
});
config.proxies = Object.assign({}, config.proxies, {
  '/composeResources/': '/base/kotlin/composeResources/',
});
