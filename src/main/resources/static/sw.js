/* AlertaPrédio: service worker (instalação do PWA, casca offline e Web Push). */
'use strict';

const CACHE = 'alerta-predio-v1';
const CASCA = [
  './',
  'index.html',
  'css/app.css',
  'js/app.js',
  'manifest.json',
  'icons/favicon.svg',
  'icons/icon-192.png',
];
// Dados nunca vêm do cache: num alerta, informação velha é pior que nenhuma
const SEMPRE_REDE = ['/api/', '/actuator/', '/v3/', '/swagger-ui'];

self.addEventListener('install', (evento) => {
  evento.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(CASCA)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nomes) => Promise.all(nomes.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

// Rede primeiro (um deploy novo aparece na hora); o cache só serve a casca quando estiver offline
self.addEventListener('fetch', (evento) => {
  const req = evento.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (SEMPRE_REDE.some((prefixo) => url.pathname.includes(prefixo))) return;

  evento.respondWith(
    fetch(req)
      .then((resposta) => {
        if (resposta.ok) {
          const copia = resposta.clone();
          caches.open(CACHE).then((cache) => cache.put(req, copia));
        }
        return resposta;
      })
      .catch(() => caches.match(req).then((emCache) => emCache || caches.match('index.html'))),
  );
});

self.addEventListener('push', (evento) => {
  let dados = {};
  try {
    dados = evento.data ? evento.data.json() : {};
  } catch (e) {
    dados = { titulo: 'AlertaPrédio', corpo: evento.data ? evento.data.text() : '' };
  }
  const titulo = dados.titulo || '🚨 Alerta no seu prédio';
  const opcoes = {
    body: dados.corpo || 'Toque para responder se está seguro.',
    icon: 'icons/icon-192.png',
    badge: 'icons/icon-192.png',
    tag: dados.alertaId ? 'alerta-' + dados.alertaId : 'alerta',
    renotify: true,
    requireInteraction: true,
    vibrate: [400, 150, 400, 150, 800],
    data: { url: dados.url || './#/' },
  };
  evento.waitUntil(
    self.registration.showNotification(titulo, opcoes).then(() =>
      self.clients.matchAll({ type: 'window', includeUncontrolled: true })
        .then((janelas) => janelas.forEach((j) => j.postMessage({ tipo: 'push', alertaId: dados.alertaId })))),
  );
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const destino = new URL(evento.notification.data && evento.notification.data.url || './#/', self.location.origin).href;
  evento.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((janelas) => {
      // App já aberto: traz para frente e pede para ele abrir o alerta (sem recarregar a página)
      for (const janela of janelas) {
        if (new URL(janela.url).origin === self.location.origin && 'focus' in janela) {
          janela.postMessage({ tipo: 'abrir', url: destino });
          return janela.focus();
        }
      }
      return self.clients.openWindow(destino);
    }),
  );
});
