// use a cacheName for cache versioning; appExtraJs carries the build parameter,
// so every build gets its own cache
var cacheName = 'v{{ flags.uiVersion }}:static:{{appExtraJs}}';

// during the install phase you usually want to cache static assets
self.addEventListener('install', function(e) {
    // once the SW is installed, go ahead and fetch the resources to make this work offline
    e.waitUntil(
        caches.open(cacheName).then(function(cache) {
            return cache.addAll([
                {{# cssUrls }}
                '{{.}}',
                {{/ cssUrls }}
                {{# jsUrls }}
                '{{.}}',
                {{/ jsUrls}}
                '{{appExtraJs}}'
            ]).then(function() {
                self.skipWaiting();
            });
        })
    );
});

// drop the caches of previous builds
self.addEventListener('activate', function(e) {
    e.waitUntil(
        caches.keys().then(function(names) {
            return Promise.all(names.filter(function(name) {
                return name !== cacheName;
            }).map(function(name) {
                return caches.delete(name);
            }));
        })
    );
});

// when the browser fetches a url
self.addEventListener('fetch', function(event) {
    // either respond with the cached object or go ahead and fetch the actual url
    event.respondWith(
        caches.match(event.request).then(function(response) {
            if (response) {
                // retrieve from cache
                return response;
            }
            // fetch as normal
            return fetch(event.request);
        })
    );
});
