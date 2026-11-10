// Tapnod service worker: shows payment notifications even when the page is closed.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch { data = { title: 'Tapnod', body: event.data && event.data.text() }; }
  event.waitUntil((async () => {
    // If the Tapnod page is already on screen, it shows and speaks the payment itself.
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const visible = windows.some((w) => w.visibilityState === 'visible');
    windows.forEach((w) => w.postMessage({ type: 'tapnod-push', data }));
    if (visible && data.request_id) return;
    await self.registration.showNotification(data.title || 'Tapnod', {
      body: data.body || '',
      tag: data.request_id ? 'tapnod-payment' : 'tapnod',
      renotify: true,
      requireInteraction: Boolean(data.request_id),
      vibrate: [200, 100, 200, 100, 200],
      icon: '/icon.svg',
      badge: '/icon.svg',
      data: { url: data.url || '/app' },
    });
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data && event.notification.data.url || '/app', self.location.origin).href;
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const w of windows) {
      if (w.url.startsWith(self.location.origin)) {
        await w.focus();
        w.postMessage({ type: 'tapnod-open' });
        return;
      }
    }
    await self.clients.openWindow(url);
  })());
});
