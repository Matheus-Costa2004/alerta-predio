/* AlertaPrédio: PWA sem framework, servido pelo próprio Spring Boot e falando com a API REST. */
'use strict';

const CHAVE_TOKEN = 'alertaPredio.token';

const TIPOS = {
  INCENDIO: { rotulo: 'Incêndio', icone: '🔥', exemplo: 'Ex.: fumaça saindo do apto 304' },
  GAS: { rotulo: 'Vazamento de gás', icone: '💨', exemplo: 'Ex.: cheiro forte de gás no hall do 2º andar' },
  ALAGAMENTO: { rotulo: 'Alagamento', icone: '🌊', exemplo: 'Ex.: água entrando pela garagem' },
  SOCORRO_MEDICO: { rotulo: 'Socorro médico', icone: '🚑', exemplo: 'Ex.: vizinho desmaiou na escada do 4º andar' },
  ELEVADOR: { rotulo: 'Preso no elevador', icone: '🛗', exemplo: 'Ex.: elevador social parado entre o 5º e o 6º' },
};
const GRAVIDADES = { BAIXA: 'baixa', MEDIA: 'média', ALTA: 'alta', CRITICA: 'crítica' };
const MOTIVOS = {
  REAL: ['Emergência real', 'O alerta era verdadeiro.'],
  FALSO_ENGANO: ['Falso alarme por engano', 'Sem advertência para quem disparou.'],
  FALSO_MA_FE: ['Falso alarme de má-fé', 'Gera advertência: na 2ª o botão fica suspenso por 30 dias, na 3ª a pessoa sai do prédio.'],
};
const MOTIVOS_CURTOS = { REAL: 'Real', FALSO_ENGANO: 'Engano', FALSO_MA_FE: 'Má-fé' };
const STATUS_MORADOR = {
  ATIVO: ['Ativo', 'ok'],
  ADVERTIDO: ['Advertido', 'atencao'],
  SUSPENSO: ['Suspenso', 'perigo'],
  REMOVIDO: ['Removido', 'perigo'],
};
const PENALIDADES = {
  ADVERTENCIA: 'Quem disparou recebeu uma advertência.',
  SUSPENSAO: 'Quem disparou teve o botão de alerta suspenso por 30 dias.',
  REMOCAO: 'Quem disparou foi removido do prédio (3ª advertência).',
};
const EVENTOS = {
  PREDIO_CRIADO: 'Prédio criado',
  MORADOR_ENTROU: 'Morador entrou',
  CONSENTIMENTO_REGISTRADO: 'Consentimento LGPD',
  ALERTA_DISPARADO: 'Alerta disparado',
  DISPARO_BLOQUEADO: 'Disparo bloqueado',
  ALERTA_ENCERRADO: 'Alerta encerrado',
  ADVERTENCIA: 'Advertência',
  SUSPENSAO: 'Suspensão',
  REMOCAO: 'Remoção',
  ACESSO_NEGADO: 'Acesso negado',
};

const estado = {
  token: lerToken(),
  sessao: null, // { predio, morador }
  push: { habilitado: false, chavePublica: null },
  inscricaoSincronizada: false,
  timer: null,
  atualizar: null,
  alertasConhecidos: null, // ids de alertas ativos já vistos; null = ainda não carregou
  disparo: null,
};

const app = document.getElementById('app');
const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const enc = encodeURIComponent;

/* ---------- utilitários ---------- */

function esc(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function lerToken() {
  try { return localStorage.getItem(CHAVE_TOKEN); } catch (e) { return null; }
}

function salvarToken(token) {
  estado.token = token;
  try {
    if (token) localStorage.setItem(CHAVE_TOKEN, token); else localStorage.removeItem(CHAVE_TOKEN);
  } catch (e) { /* modo privado: o token vale só nesta aba */ }
}

function tipo(chave) { return TIPOS[chave] || { rotulo: chave, icone: '🚨' }; }
function primeiroNome(nome) { return String(nome || '').trim().split(/\s+/)[0]; }
function dataCurta(iso) { return new Date(iso).toLocaleDateString('pt-BR'); }
function dataHora(iso) {
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
function haQuanto(iso) {
  const segundos = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (segundos < 60) return 'agora';
  const minutos = Math.round(segundos / 60);
  if (minutos < 60) return `há ${minutos} min`;
  const horas = Math.round(minutos / 60);
  if (horas < 24) return `há ${horas} h`;
  return `há ${Math.round(horas / 24)} d`;
}
function plural(n, singular, pluralTexto) { return `${n} ${n === 1 ? singular : pluralTexto}`; }
function ehSindico() { return estado.sessao && estado.sessao.morador.papel === 'SINDICO'; }

async function api(caminho, { metodo = 'GET', corpo } = {}) {
  const cabecalhos = { Accept: 'application/json' };
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
  if (estado.token) cabecalhos['X-Morador-Token'] = estado.token;
  let resposta;
  try {
    resposta = await fetch(caminho, { method: metodo, headers: cabecalhos, body: corpo === undefined ? undefined : JSON.stringify(corpo) });
  } catch (e) {
    throw new Error('Sem conexão com o servidor. Verifique a internet e tente de novo.');
  }
  if (resposta.status === 204) return null;
  const dados = await resposta.json().catch(() => null);
  if (!resposta.ok) {
    if (resposta.status === 401 && estado.token) encerrarSessao();
    const erro = new Error(mensagemDeErro(dados, resposta.status));
    erro.status = resposta.status;
    throw erro;
  }
  return dados;
}

function mensagemDeErro(problema, status) {
  if (problema && problema.erros) return Object.values(problema.erros).join(' · ');
  return (problema && (problema.detail || problema.title)) || `Erro ${status}`;
}

let temporizadorAviso;
function avisar(mensagem, tipoAviso = '', aoClicar) {
  const el = $('#aviso');
  el.textContent = mensagem;
  el.className = 'aviso ' + tipoAviso;
  el.hidden = false;
  el.onclick = () => { el.hidden = true; if (aoClicar) aoClicar(); };
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => { el.hidden = true; }, tipoAviso === 'alerta' ? 12000 : 4500);
}

function atualizarACada(ms, funcao) {
  pararAtualizacao();
  estado.atualizar = funcao;
  estado.timer = setInterval(() => { if (!document.hidden) funcao().catch(() => {}); }, ms);
}

function pararAtualizacao() {
  clearInterval(estado.timer);
  estado.timer = null;
  estado.atualizar = null;
}

async function recarregar() {
  if (estado.atualizar) await estado.atualizar().catch((e) => avisar(e.message, 'erro'));
}

/* ---------- sessão e navegação ---------- */

async function carregarSessao() {
  estado.sessao = await api('api/moradores/eu');
  return estado.sessao;
}

function entrarComToken(token, sessao) {
  salvarToken(token);
  estado.sessao = sessao || null;
  estado.alertasConhecidos = null;
  estado.inscricaoSincronizada = false;
  location.hash = '#/';
}

function encerrarSessao() {
  salvarToken(null);
  estado.sessao = null;
  estado.alertasConhecidos = null;
  pararAtualizacao();
  if (location.hash !== '#/entrar') location.hash = '#/entrar';
}

function rotaAtual() {
  const partes = location.hash.replace(/^#\/?/, '').split('/');
  return { nome: partes[0] || 'inicio', id: partes[1] ? decodeURIComponent(partes[1]) : null };
}

async function navegar() {
  pararAtualizacao();
  const rota = rotaAtual();
  if (!estado.token && rota.nome !== 'entrar') { location.replace('#/entrar'); return; }
  if (estado.token && rota.nome === 'entrar') { location.replace('#/'); return; }
  try {
    if (estado.token && !estado.sessao) await carregarSessao();
    if (!estado.token && rota.nome !== 'entrar') return;
    atualizarAbas(rota);
    const telas = { entrar: telaEntrar, inicio: telaInicio, alerta: telaAlerta, painel: telaPainel, sindico: telaSindico };
    await (telas[rota.nome] || telaInicio)(rota.id);
    window.scrollTo(0, 0);
  } catch (e) {
    if (estado.token) mostrarErro(e);
  }
}

function atualizarAbas(rota) {
  const abas = $('#abas');
  abas.hidden = !estado.sessao;
  $('#aba-sindico').hidden = !ehSindico();
  abas.querySelectorAll('a').forEach((a) => {
    a.classList.toggle('atual', a.dataset.rota === (rota.nome === 'sindico' ? 'sindico' : 'inicio'));
  });
}

function mostrarErro(e) {
  app.innerHTML = `
    <section class="cartao">
      <h2>${e.status === 403 ? 'Acesso negado' : 'Não foi possível carregar'}</h2>
      <p class="sub">${esc(e.message)}</p>
      <a class="botao primario" href="#/">Voltar ao início</a>
    </section>`;
}

/* ---------- tela: entrar ---------- */

function camposMorador() {
  return `
    <label class="campo"><span>Seu nome</span>
      <input type="text" name="nomeMorador" required maxlength="80" autocomplete="name"></label>
    <div class="dupla">
      <label class="campo"><span>Apartamento</span>
        <input type="text" name="apartamento" required maxlength="10" placeholder="302"></label>
      <label class="campo"><span>Telefone</span>
        <input type="tel" name="telefone" required maxlength="20" autocomplete="tel" placeholder="(11) 98888-0000"></label>
    </div>
    <label class="check"><input type="checkbox" name="temNecessidade">
      <span>Tenho uma necessidade especial (mobilidade, saúde, idade) e quero ser priorizado(a) nos alertas</span></label>
    <div class="consentimento" data-necessidade hidden>
      <label class="campo"><span>Qual?</span>
        <textarea name="necessidadeEspecial" maxlength="200" placeholder="Ex.: cadeirante, preciso de ajuda para descer as escadas"></textarea></label>
      <label class="check"><input type="checkbox" name="consentimento">
        <span>Autorizo mostrar essa informação no painel dos alertas do meu prédio para que os vizinhos priorizem minha ajuda (LGPD, termo v1.0).</span></label>
    </div>`;
}

function dadosMorador(form) {
  const f = new FormData(form);
  const temNecessidade = f.get('temNecessidade') === 'on';
  const necessidade = temNecessidade ? String(f.get('necessidadeEspecial') || '').trim() : '';
  return {
    nome: String(f.get('nomeMorador') || '').trim(),
    apartamento: String(f.get('apartamento') || '').trim(),
    telefone: String(f.get('telefone') || '').trim(),
    necessidadeEspecial: necessidade || null,
    consentimentoNecessidadeEspecial: necessidade ? f.get('consentimento') === 'on' : null,
  };
}

async function telaEntrar() {
  app.innerHTML = `
    <section class="cartao boas-vindas">
      <h1>Emergência no prédio? Um toque avisa todos os vizinhos.</h1>
      <p class="sub">Cada morador responde se está seguro ou se precisa de ajuda. Quem tem necessidade especial aparece primeiro no painel.</p>
    </section>
    <div class="segmentado" role="tablist" aria-label="Como entrar">
      <button type="button" role="tab" aria-selected="true" data-aba="form-convite">Tenho um código</button>
      <button type="button" role="tab" aria-selected="false" data-aba="form-criar">Criar prédio</button>
    </div>
    <form id="form-convite" class="cartao form" novalidate>
      <label class="campo"><span>Código de convite</span>
        <input type="text" class="codigo-input" name="codigoConvite" required maxlength="12" autocomplete="off" autocapitalize="characters" placeholder="ABC123">
        <small>Peça ao síndico do seu prédio.</small></label>
      ${camposMorador()}
      <button class="botao primario grande bloco" type="submit">Entrar no prédio</button>
    </form>
    <form id="form-criar" class="cartao form" novalidate hidden>
      <label class="campo"><span>Nome do prédio</span>
        <input type="text" name="nomePredio" required maxlength="120" placeholder="Residencial Jardim das Acácias"></label>
      <label class="campo"><span>Endereço</span>
        <input type="text" name="endereco" required maxlength="200" placeholder="Rua das Acácias, 120"></label>
      <p class="faixa info">Você será o(a) síndico(a): recebe o código de convite e encerra os alertas.</p>
      ${camposMorador()}
      <button class="botao primario grande bloco" type="submit">Criar prédio</button>
    </form>
    <section id="demo" class="cartao" hidden>
      <h2>Demonstração</h2>
      <p class="sub">Entre no prédio de exemplo sem cadastro.</p>
      <div class="contas-demo" id="contas-demo"></div>
    </section>`;

  app.querySelectorAll('[data-aba]').forEach((aba) => aba.addEventListener('click', () => {
    app.querySelectorAll('[data-aba]').forEach((outra) => {
      const selecionada = outra === aba;
      outra.setAttribute('aria-selected', String(selecionada));
      $('#' + outra.dataset.aba).hidden = !selecionada;
    });
  }));
  app.querySelectorAll('form').forEach((form) => {
    const marcador = form.querySelector('[name=temNecessidade]');
    marcador.addEventListener('change', () => { form.querySelector('[data-necessidade]').hidden = !marcador.checked; });
  });

  $('#form-convite').addEventListener('submit', (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    enviarAcesso(form, () => api('api/predios/entrar', {
      metodo: 'POST',
      corpo: { codigoConvite: String(new FormData(form).get('codigoConvite') || '').trim(), morador: dadosMorador(form) },
    }));
  });
  $('#form-criar').addEventListener('submit', (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const f = new FormData(form);
    enviarAcesso(form, () => api('api/predios', {
      metodo: 'POST',
      corpo: { nome: String(f.get('nomePredio') || '').trim(), endereco: String(f.get('endereco') || '').trim(), sindico: dadosMorador(form) },
    }));
  });

  try {
    const contas = await api('api/demo/contas');
    $('#contas-demo').innerHTML = contas.map((c, i) => `
      <button type="button" class="botao secundario bloco conta-demo" data-conta="${i}">
        <span>${esc(c.nome)} · apto ${esc(c.apartamento)}</span><small>${esc(c.descricao)}</small>
      </button>`).join('');
    app.querySelectorAll('[data-conta]').forEach((botao) => botao.addEventListener('click', () => {
      entrarComToken(contas[Number(botao.dataset.conta)].token);
    }));
    $('#demo').hidden = false;
  } catch (e) { /* sem seed: sem contas de demonstração */ }
}

async function enviarAcesso(form, chamada) {
  const dados = dadosMorador(form);
  if (dados.necessidadeEspecial && !dados.consentimentoNecessidadeEspecial) {
    avisar('Para registrar a necessidade especial, marque a autorização (LGPD).', 'erro');
    return;
  }
  const botao = form.querySelector('[type=submit]');
  botao.disabled = true;
  try {
    const acesso = await chamada();
    entrarComToken(acesso.token, { predio: acesso.predio, morador: acesso.morador });
    if (acesso.morador.papel === 'SINDICO' && acesso.predio.codigoConvite) {
      avisar(`Prédio criado! Código de convite: ${acesso.predio.codigoConvite}`);
    } else {
      avisar(`Bem-vindo(a) ao ${acesso.predio.nome || 'prédio'}!`);
    }
  } catch (e) {
    avisar(e.message, 'erro');
  } finally {
    botao.disabled = false;
  }
}

/* ---------- tela: início e disparo ---------- */

function faixaStatus(morador, suspenso) {
  if (suspenso) {
    return `<p class="faixa alerta"><strong>Botão de alerta suspenso até ${dataCurta(morador.suspensoAte)}</strong>
      <span>Por alertas falsos de má-fé. Você continua recebendo os alertas do prédio e pode responder se está seguro.</span></p>`;
  }
  if (morador.status === 'ADVERTIDO' || morador.qtdAdvertencias > 0) {
    return `<p class="faixa atencao"><strong>${plural(morador.qtdAdvertencias, 'advertência', 'advertências')} por alerta falso de má-fé</strong>
      <span>Na 2ª, o botão de alerta fica suspenso por 30 dias; na 3ª, você sai do prédio.</span></p>`;
  }
  return '';
}

async function telaInicio() {
  await carregarSessao();
  const { morador, predio } = estado.sessao;
  if (morador.status === 'REMOVIDO') {
    app.innerHTML = `
      <section class="cartao">
        <h1>Você foi removido deste prédio</h1>
        <p class="sub">Após 3 alertas falsos de má-fé, o acesso aos alertas do ${esc(predio.nome)} foi encerrado. Fale com o síndico.</p>
        <button type="button" class="botao primario" id="btn-sair-removido">Sair</button>
      </section>`;
    $('#btn-sair-removido').addEventListener('click', sair);
    return;
  }
  const suspenso = morador.status === 'SUSPENSO' && (!morador.suspensoAte || new Date(morador.suspensoAte) > new Date());
  app.innerHTML = `
    <section class="saudacao">
      <p class="sub">${esc(predio.nome)}</p>
      <h1>Olá, ${esc(primeiroNome(morador.nome))}</h1>
      <p class="sub">Apto ${esc(morador.apartamento)}${morador.papel === 'SINDICO' ? ' · Síndico(a)' : ''}</p>
    </section>
    ${faixaStatus(morador, suspenso)}
    <section class="emergencia">
      <button id="btn-emergencia" class="botao-emergencia" type="button" ${suspenso ? 'disabled' : ''}>
        <span class="be-icone" aria-hidden="true">🚨</span>
        <span class="be-texto">EMERGÊNCIA</span>
        <small>${suspenso ? 'Botão suspenso até ' + dataCurta(morador.suspensoAte) : 'Toque para avisar o prédio'}</small>
      </button>
    </section>
    <section id="disparo" class="cartao folha" hidden></section>
    <section id="cartao-push" class="cartao" hidden></section>
    <h2 class="secao">Alertas ativos</h2>
    <div id="ativos" class="lista-alertas"><p class="vazio">Carregando…</p></div>
    <details class="cartao"><summary>Alertas encerrados</summary><div id="encerrados" class="lista-alertas"></div></details>`;

  $('#btn-emergencia').addEventListener('click', abrirEscolhaDeTipo);
  renderizarCartaoPush();
  await carregarAlertasDoPredio();
  atualizarACada(10000, carregarAlertasDoPredio);
}

function itemAlerta(alerta, destino = 'alerta') {
  const t = tipo(alerta.tipo);
  const ativo = alerta.status === 'ATIVO';
  const confirmacoes = alerta.totalConfirmacoes ? ` · ${plural(alerta.totalConfirmacoes, 'confirmação', 'confirmações')}` : '';
  const selo = ativo
    ? '<span class="chip ativo">ATIVO</span>'
    : `<span class="chip">${esc(alerta.encerramento ? MOTIVOS_CURTOS[alerta.encerramento.motivo] : 'Encerrado')}</span>`;
  return `
    <a class="item-alerta ${ativo ? 'ativo' : ''}" href="#/${destino}/${enc(alerta.id)}">
      <span class="ia-icone" aria-hidden="true">${t.icone}</span>
      <div><h3>${esc(t.rotulo)} · Apto ${esc(alerta.autor.apartamento)}</h3>
        <p>${esc(alerta.autor.nome)} · ${haQuanto(alerta.dataHora)}${confirmacoes}</p></div>
      ${selo}
    </a>`;
}

async function carregarAlertasDoPredio() {
  const alertas = await api(`api/predios/${enc(estado.sessao.predio.id)}/alertas`);
  const ativos = alertas.filter((a) => a.status === 'ATIVO');
  const encerrados = alertas.filter((a) => a.status !== 'ATIVO').slice(0, 10);
  const lista = $('#ativos');
  if (lista) {
    lista.innerHTML = ativos.length ? ativos.map((a) => itemAlerta(a)).join('')
      : '<p class="vazio">Nenhum alerta ativo. Tudo tranquilo por aqui. ✅</p>';
    $('#encerrados').innerHTML = encerrados.length ? encerrados.map((a) => itemAlerta(a)).join('')
      : '<p class="vazio">Nenhum alerta encerrado.</p>';
  }
  detectarNovos(ativos);
}

/** Com o app aberto, avisa de alertas novos mesmo sem push (vibra e mostra um aviso clicável). */
function detectarNovos(ativos) {
  const eu = estado.sessao && estado.sessao.morador;
  if (!eu) return;
  if (estado.alertasConhecidos) {
    const novo = ativos.find((a) => !estado.alertasConhecidos.has(a.id) && a.autor.moradorId !== eu.id);
    if (novo) {
      const t = tipo(novo.tipo);
      if (navigator.vibrate) navigator.vibrate([400, 150, 400, 150, 800]);
      avisar(`🚨 ${t.rotulo} no apto ${novo.autor.apartamento}. Toque para responder.`, 'alerta',
        () => { location.hash = '#/alerta/' + enc(novo.id); });
    }
  }
  estado.alertasConhecidos = new Set(ativos.map((a) => a.id));
}

async function vigiar() {
  if (!estado.sessao || document.hidden || rotaAtual().nome === 'inicio') return;
  try {
    const alertas = await api(`api/predios/${enc(estado.sessao.predio.id)}/alertas?status=ATIVO`);
    detectarNovos(alertas);
  } catch (e) { /* tenta de novo no próximo ciclo */ }
}

function abrirEscolhaDeTipo() {
  estado.disparo = { tipo: null, fotoUrl: null };
  const folha = $('#disparo');
  folha.hidden = false;
  folha.innerHTML = `
    <h2>Qual é a emergência?</h2>
    <div class="tipos">
      ${Object.entries(TIPOS).map(([chave, t]) => `
        <button type="button" class="tipo" data-tipo="${chave}">
          <span class="tipo-icone" aria-hidden="true">${t.icone}</span>${esc(t.rotulo)}
        </button>`).join('')}
    </div>
    <button type="button" class="botao secundario bloco" data-cancelar>Cancelar</button>`;
  folha.querySelectorAll('[data-tipo]').forEach((b) => b.addEventListener('click', () => abrirDetalhes(b.dataset.tipo)));
  folha.querySelector('[data-cancelar]').addEventListener('click', fecharDisparo);
  folha.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function abrirDetalhes(chave) {
  estado.disparo.tipo = chave;
  const t = TIPOS[chave];
  const folha = $('#disparo');
  folha.innerHTML = `
    <div class="tipo-escolhido"><span aria-hidden="true">${t.icone}</span><span>${esc(t.rotulo)}</span></div>
    <label class="campo"><span>O que está acontecendo? <small>(opcional)</small></span>
      <textarea id="d-descricao" maxlength="500" placeholder="${esc(t.exemplo)}"></textarea></label>
    <label class="botao secundario bloco foto-botao"><span id="d-foto-rotulo">📷 Tirar ou escolher foto (opcional)</span>
      <input id="d-foto" type="file" accept="image/*" capture="environment" aria-label="Foto da emergência (opcional)"></label>
    <img id="d-previa" class="foto-previa" alt="Prévia da foto" hidden>
    <div class="acoes">
      <button type="button" id="d-disparar" class="botao primario grande bloco">🚨 Disparar alerta agora</button>
      <button type="button" class="botao secundario bloco" data-voltar>Trocar o tipo</button>
      <button type="button" class="botao secundario bloco" data-cancelar>Cancelar</button>
    </div>
    <p class="sub">O alerta chega na hora para todos os moradores. Alerta falso de má-fé gera advertência.</p>`;
  $('#d-foto').addEventListener('change', async (evento) => {
    const arquivo = evento.target.files[0];
    if (!arquivo) return;
    try {
      estado.disparo.fotoUrl = await reduzirFoto(arquivo);
      const previa = $('#d-previa');
      previa.src = estado.disparo.fotoUrl;
      previa.hidden = false;
      $('#d-foto-rotulo').textContent = '📷 Trocar a foto';
    } catch (e) {
      estado.disparo.fotoUrl = null;
      avisar(e.message, 'erro');
    }
  });
  $('#d-disparar').addEventListener('click', (evento) => enviarAlerta(evento.currentTarget));
  folha.querySelector('[data-voltar]').addEventListener('click', abrirEscolhaDeTipo);
  folha.querySelector('[data-cancelar]').addEventListener('click', fecharDisparo);
}

function fecharDisparo() {
  estado.disparo = null;
  const folha = $('#disparo');
  folha.hidden = true;
  folha.innerHTML = '';
}

async function enviarAlerta(botao) {
  botao.disabled = true;
  botao.textContent = 'Enviando…';
  try {
    const descricao = $('#d-descricao').value.trim();
    const alerta = await api(`api/predios/${enc(estado.sessao.predio.id)}/alertas`, {
      metodo: 'POST',
      corpo: { tipo: estado.disparo.tipo, descricao: descricao || null, fotoUrl: estado.disparo.fotoUrl },
    });
    estado.disparo = null;
    avisar('Alerta enviado para todo o prédio.');
    location.hash = '#/alerta/' + enc(alerta.id);
  } catch (e) {
    avisar(e.message, 'erro');
    botao.disabled = false;
    botao.textContent = '🚨 Disparar alerta agora';
    if (e.status === 403) telaInicio().catch(() => {});
  }
}

/** Reduz a foto no próprio celular (lado maior de 1024 px, JPEG) antes de enviar. */
function reduzirFoto(arquivo, lado = 1024, qualidade = 0.72) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(arquivo);
    const imagem = new Image();
    imagem.onload = () => {
      const escala = Math.min(1, lado / Math.max(imagem.width, imagem.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(imagem.width * escala);
      canvas.height = Math.round(imagem.height * escala);
      canvas.getContext('2d').drawImage(imagem, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      resolve(canvas.toDataURL('image/jpeg', qualidade));
    };
    imagem.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Não foi possível ler essa foto.')); };
    imagem.src = url;
  });
}

/* ---------- tela: alerta ---------- */

function blocoIa(c) {
  if (!c || c.status === 'PENDENTE') {
    return `<div class="ia pendente"><strong>🤖 Triagem por IA em andamento…</strong>
      <span>O alerta já foi enviado: a análise não atrasa o aviso.</span></div>`;
  }
  if (c.status === 'INDISPONIVEL') {
    return `<div class="ia indisponivel"><strong>🤖 Triagem por IA indisponível</strong>
      <span>O alerta segue normalmente.</span></div>`;
  }
  const confianca = Math.round((c.confianca || 0) * 100);
  return `<div class="ia concluida">
      <strong>🤖 IA: ${esc(tipo(c.tipoProvavel).rotulo)} provável · gravidade ${esc(GRAVIDADES[c.gravidade] || c.gravidade)} · ${confianca}% de confiança</strong>
      ${c.justificativa ? `<span>${esc(c.justificativa)}</span>` : ''}
      <small>Sugestão automática (${esc(c.modelo)}). Quem decide é o síndico.</small>
    </div>`;
}

function blocoEncerramento(e) {
  if (!e) return '';
  return `<div class="faixa info">
      <strong>Alerta encerrado: ${esc(MOTIVOS[e.motivo][0])}</strong>
      ${e.observacao ? `<span>${esc(e.observacao)}</span>` : ''}
      <span>Por ${esc(e.porNome)} em ${dataHora(e.dataHora)}</span>
      ${e.penalidadeAplicada ? `<span>${esc(PENALIDADES[e.penalidadeAplicada])}</span>` : ''}
    </div>`;
}

function minhaResposta(painel, moradorId) {
  const ajuda = painel.precisamDeAjuda.find((e) => e.moradorId === moradorId);
  if (ajuda) return 'PRECISA_AJUDA';
  return painel.seguros.find((e) => e.moradorId === moradorId) ? 'SEGURO' : null;
}

function blocoTotais(totais) {
  return `
    <div class="total ajuda"><strong>${totais.precisamDeAjuda}</strong><span>precisam de ajuda</span></div>
    <div class="total sem"><strong>${totais.semResposta}</strong><span>sem resposta</span></div>
    <div class="total seguro"><strong>${totais.seguros}</strong><span>seguros</span></div>`;
}

async function telaAlerta(id) {
  const [alerta, painel] = await Promise.all([api(`api/alertas/${enc(id)}`), api(`api/alertas/${enc(id)}/painel`)]);
  app.innerHTML = `
    <a class="link-voltar" href="#/">← Início</a>
    <section id="a-topo" class="cartao alerta-topo"></section>
    ${alerta.descricao ? `<p class="cartao descricao">${esc(alerta.descricao)}</p>` : ''}
    ${alerta.fotoUrl ? `<img class="foto" src="${esc(alerta.fotoUrl)}" alt="Foto enviada por quem disparou o alerta">` : ''}
    <div id="a-ia"></div>
    <section id="a-acao"></section>
    <section id="a-totais" class="totais" aria-label="Respostas dos moradores"></section>
    <a class="botao secundario bloco" href="#/painel/${enc(id)}">📋 Ver quem respondeu</a>
    <div id="a-confirmar"></div>`;
  preencherAlerta(alerta, painel);
  atualizarACada(5000, async () => {
    const [a, p] = await Promise.all([api(`api/alertas/${enc(id)}`), api(`api/alertas/${enc(id)}/painel`)]);
    preencherAlerta(a, p);
  });
}

function preencherAlerta(alerta, painel) {
  if (!$('#a-topo')) return;
  const eu = estado.sessao.morador;
  const t = tipo(alerta.tipo);
  const ativo = alerta.status === 'ATIVO';
  const topo = $('#a-topo');
  topo.classList.toggle('ativo', ativo);
  topo.innerHTML = `
    <span class="grande-icone" aria-hidden="true">${t.icone}</span>
    <div>
      <span class="chip ${ativo ? 'ativo' : ''}">${ativo ? 'ATIVO' : 'ENCERRADO'}</span>
      <h1>${esc(t.rotulo)}</h1>
      <p>Apto ${esc(alerta.autor.apartamento)} · ${esc(alerta.autor.nome)}</p>
      <p class="sub">${dataHora(alerta.dataHora)} · ${haQuanto(alerta.dataHora)}</p>
    </div>`;
  $('#a-ia').innerHTML = blocoIa(alerta.classificacao);

  const minha = minhaResposta(painel, eu.id);
  const classe = (situacao) => (minha === situacao ? 'escolhido' : minha ? 'apagado' : '');
  $('#a-acao').innerHTML = ativo ? `
    <div class="checkin">
      <p class="minha-resposta">${minha === 'SEGURO' ? '✅ Você avisou que está seguro.'
        : minha === 'PRECISA_AJUDA' ? '🆘 Você pediu ajuda. Os vizinhos e o síndico estão vendo.' : 'Você está bem?'}</p>
      <button type="button" class="botao seguro ${classe('SEGURO')}" data-situacao="SEGURO">✅ Estou seguro</button>
      <button type="button" class="botao ajuda ${classe('PRECISA_AJUDA')}" data-situacao="PRECISA_AJUDA">🆘 Preciso de ajuda</button>
    </div>` : blocoEncerramento(alerta.encerramento);
  $('#a-acao').querySelectorAll('[data-situacao]').forEach((botao) => botao.addEventListener('click', () =>
    responder(alerta.id, botao.dataset.situacao)));

  $('#a-totais').innerHTML = blocoTotais(painel.totais);

  const confirmacoes = alerta.confirmacoes || [];
  const jaConfirmei = confirmacoes.some((c) => c.moradorId === eu.id);
  const podeConfirmar = ativo && alerta.autor.moradorId !== eu.id && !jaConfirmei;
  $('#a-confirmar').innerHTML = podeConfirmar
    ? `<button type="button" class="botao secundario bloco" id="btn-confirmar">👍 Confirmo que é real${confirmacoes.length ? ` (${confirmacoes.length})` : ''}</button>`
    : confirmacoes.length ? `<p class="sub" style="text-align:center">👍 ${plural(confirmacoes.length, 'vizinho confirmou', 'vizinhos confirmaram')} o alerta</p>` : '';
  const botaoConfirmar = $('#btn-confirmar');
  if (botaoConfirmar) botaoConfirmar.addEventListener('click', () => confirmar(alerta.id));
}

async function responder(alertaId, situacao) {
  try {
    await api(`api/alertas/${enc(alertaId)}/checkin`, { metodo: 'PUT', corpo: { situacao } });
    avisar(situacao === 'SEGURO' ? 'Resposta enviada: você está seguro.' : 'Pedido de ajuda enviado ao prédio.');
    await recarregar();
  } catch (e) {
    avisar(e.message, 'erro');
  }
}

async function confirmar(alertaId) {
  try {
    await api(`api/alertas/${enc(alertaId)}/confirmacoes`, { metodo: 'POST' });
    avisar('Obrigado! Sua confirmação ajuda o síndico a agir.');
    await recarregar();
  } catch (e) {
    avisar(e.message, 'erro');
  }
}

/* ---------- tela: painel do alerta ---------- */

function pessoa(entrada) {
  const telefone = entrada.telefone ? entrada.telefone.replace(/[^\d+]/g, '') : '';
  return `
    <div class="pessoa ${entrada.vulneravel ? 'vulneravel' : ''}">
      <span class="apto">${esc(entrada.apartamento)}</span>
      <div>
        <div class="nome">${esc(entrada.nome)}</div>
        ${entrada.respondidoEm ? `<div class="detalhe">respondeu ${haQuanto(entrada.respondidoEm)}</div>` : ''}
        ${entrada.vulneravel ? `<span class="necessidade">Prioridade · ${esc(entrada.necessidadeEspecial)}</span>` : ''}
      </div>
      ${telefone ? `<a class="ligar" href="tel:${esc(telefone)}" aria-label="Ligar para ${esc(entrada.nome)}">📞</a>` : '<span></span>'}
    </div>`;
}

function grupo(titulo, classe, entradas, vazio) {
  return `
    <section class="cartao grupo">
      <h2>${titulo} <span class="chip ${classe}">${entradas.length}</span></h2>
      ${entradas.length ? entradas.map(pessoa).join('') : `<p class="sub">${vazio}</p>`}
    </section>`;
}

async function telaPainel(id) {
  const painel = await api(`api/alertas/${enc(id)}/painel`);
  app.innerHTML = `
    <a class="link-voltar" href="#/alerta/${enc(id)}">← Voltar ao alerta</a>
    <section class="cartao" id="p-titulo"></section>
    <section id="p-totais" class="totais"></section>
    <div id="p-listas" style="display:grid;gap:16px"></div>
    ${ehSindico() ? `
    <form id="form-encerrar" class="cartao form" hidden>
      <h2>Encerrar alerta</h2>
      <div class="motivos" role="radiogroup" aria-label="Motivo do encerramento">
        ${Object.entries(MOTIVOS).map(([chave, [rotulo, descricao]]) => `
          <label class="motivo"><input type="radio" name="motivo" value="${chave}" ${chave === 'REAL' ? 'checked' : ''}>
            <span><strong>${rotulo}</strong><small>${descricao}</small></span></label>`).join('')}
      </div>
      <label class="campo"><span>Observação <small>(opcional)</small></span>
        <textarea name="observacao" maxlength="300" placeholder="Ex.: bombeiros controlaram o foco, sem feridos"></textarea></label>
      <button class="botao primario bloco" type="submit">Encerrar alerta</button>
    </form>` : ''}`;
  preencherPainel(painel);
  const form = $('#form-encerrar');
  if (form) form.addEventListener('submit', (evento) => { evento.preventDefault(); encerrar(id, form); });
  atualizarACada(5000, async () => preencherPainel(await api(`api/alertas/${enc(id)}/painel`)));
}

function preencherPainel(painel) {
  if (!$('#p-titulo')) return;
  const a = painel.alerta;
  const t = tipo(a.tipo);
  const ativo = a.status === 'ATIVO';
  $('#p-titulo').innerHTML = `
    <h1>${t.icone} ${esc(t.rotulo)} · Apto ${esc(a.autor.apartamento)}</h1>
    <p class="sub">${esc(a.autor.nome)} · ${dataHora(a.dataHora)} · ${plural(painel.totais.moradores, 'morador', 'moradores')} no prédio</p>
    ${ativo ? '<span class="chip ativo" style="justify-self:start">ATIVO</span>' : blocoEncerramento(a.encerramento)}`;
  $('#p-totais').innerHTML = blocoTotais(painel.totais);
  $('#p-listas').innerHTML =
    grupo('🆘 Precisam de ajuda', 'perigo', painel.precisamDeAjuda, 'Ninguém pediu ajuda.')
    + grupo('⏳ Sem resposta', 'atencao', painel.semResposta, 'Todos responderam.')
    + grupo('✅ Seguros', 'ok', painel.seguros, 'Ninguém respondeu ainda.');
  const form = $('#form-encerrar');
  if (form) form.hidden = !ativo;
}

async function encerrar(alertaId, form) {
  const f = new FormData(form);
  const motivo = f.get('motivo');
  if (motivo === 'FALSO_MA_FE'
    && !confirm('Encerrar como falso alarme de má-fé? Quem disparou recebe uma advertência (2ª suspende o botão por 30 dias; 3ª remove do prédio).')) {
    return;
  }
  const botao = form.querySelector('[type=submit]');
  botao.disabled = true;
  try {
    const observacao = String(f.get('observacao') || '').trim();
    const alerta = await api(`api/alertas/${enc(alertaId)}/encerramento`, {
      metodo: 'POST', corpo: { motivo, observacao: observacao || null },
    });
    const penalidade = alerta.encerramento && alerta.encerramento.penalidadeAplicada;
    avisar('Alerta encerrado.' + (penalidade ? ' ' + PENALIDADES[penalidade] : ''));
    await recarregar();
  } catch (e) {
    avisar(e.message, 'erro');
  } finally {
    botao.disabled = false;
  }
}

/* ---------- tela: área do síndico ---------- */

function linhaMorador(m) {
  const [rotulo, classe] = STATUS_MORADOR[m.status] || [m.status, ''];
  const detalhes = [m.papel === 'SINDICO' ? 'síndico(a)' : null,
    m.qtdAdvertencias ? plural(m.qtdAdvertencias, 'advertência', 'advertências') : null,
    m.suspensoAte ? 'suspenso até ' + dataCurta(m.suspensoAte) : null].filter(Boolean).join(' · ');
  return `
    <div class="morador-linha">
      <span class="apto">${esc(m.apartamento)}</span>
      <div>
        <div class="nome">${esc(m.nome)}</div>
        ${detalhes ? `<div class="detalhe">${esc(detalhes)}</div>` : ''}
        ${m.necessidadeEspecial ? `<span class="necessidade">Prioridade · ${esc(m.necessidadeEspecial.descricao)}</span>` : ''}
      </div>
      <span class="chip ${classe}">${esc(rotulo)}</span>
    </div>`;
}

function linhaEvento(e) {
  return `
    <div class="evento">
      <strong>${esc(EVENTOS[e.tipo] || e.tipo)}</strong>
      <span>${esc(e.descricao)}${e.moradorNome ? ' · ' + esc(e.moradorNome) : ''}</span>
      <span class="quando">${dataHora(e.dataHora)}</span>
    </div>`;
}

async function telaSindico() {
  if (!ehSindico()) { location.replace('#/'); return; }
  const base = `api/predios/${enc(estado.sessao.predio.id)}`;
  const [predio, moradores, alertas, eventos] = await Promise.all(
    [api(base), api(base + '/moradores'), api(base + '/alertas'), api(base + '/auditoria')]);
  app.innerHTML = `
    <section class="saudacao">
      <p class="sub">Área do síndico</p>
      <h1>${esc(predio.nome)}</h1>
      <p class="sub">${esc(predio.endereco)}</p>
    </section>
    <section class="cartao convite">
      <p class="sub">Código de convite</p>
      <div class="codigo">${esc(predio.codigoConvite)}</div>
      <button type="button" class="botao primario" id="btn-convite">Compartilhar convite</button>
      <p class="sub">Os vizinhos abrem ${esc(location.origin)} e entram com esse código.</p>
    </section>
    <section class="cartao"><h2>Moradores (${moradores.length})</h2>${moradores.map(linhaMorador).join('')}</section>
    <h2 class="secao">Alertas do prédio</h2>
    <div class="lista-alertas">${alertas.length ? alertas.map((a) => itemAlerta(a, 'painel')).join('') : '<p class="vazio">Nenhum alerta ainda.</p>'}</div>
    <details class="cartao"><summary>Auditoria (${eventos.length})</summary>${eventos.slice(0, 50).map(linhaEvento).join('')}</details>`;
  $('#btn-convite').addEventListener('click', async () => {
    const texto = `Entre no AlertaPrédio do ${predio.nome}: ${location.origin}/ (código de convite ${predio.codigoConvite})`;
    try {
      if (navigator.share) await navigator.share({ title: 'AlertaPrédio', text: texto });
      else { await navigator.clipboard.writeText(texto); avisar('Convite copiado.'); }
    } catch (e) { /* compartilhamento cancelado */ }
  });
}

/* ---------- notificações (Web Push) ---------- */

function suportaPush() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

function base64UrlParaBytes(base64) {
  const preenchimento = '='.repeat((4 - (base64.length % 4)) % 4);
  const binario = atob((base64 + preenchimento).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(binario, (c) => c.charCodeAt(0));
}

async function renderizarCartaoPush() {
  const cartao = $('#cartao-push');
  if (!cartao) return;
  let html;
  if (!suportaPush()) {
    html = `<p class="faixa info">Este navegador não recebe notificações. Deixe o AlertaPrédio aberto
      (no iPhone, instale o app pela opção "Adicionar à Tela de Início").</p>`;
  } else if (!estado.push.habilitado) {
    html = '<p class="faixa info">As notificações push não estão configuradas neste ambiente. Com o app aberto, os alertas aparecem sozinhos.</p>';
  } else if (Notification.permission === 'denied') {
    html = '<p class="faixa atencao">As notificações estão bloqueadas no navegador. Libere nas configurações do site para ser avisado com o app fechado.</p>';
  } else {
    const registro = await navigator.serviceWorker.ready;
    const inscricao = await registro.pushManager.getSubscription();
    if (inscricao && Notification.permission === 'granted') {
      if (!estado.inscricaoSincronizada) {
        // garante que este aparelho está ligado ao morador atual (troca de conta no mesmo celular)
        await api('api/push/inscricoes', { metodo: 'POST', corpo: inscricao.toJSON() }).catch(() => {});
        estado.inscricaoSincronizada = true;
      }
      html = '<p class="faixa ok">🔔 Notificações ativas neste aparelho.</p>';
    } else {
      html = `<strong>Receba os alertas mesmo com o app fechado</strong>
        <button type="button" class="botao primario bloco" id="btn-push">🔔 Ativar notificações</button>`;
    }
  }
  if (!$('#cartao-push')) return;
  cartao.innerHTML = html;
  cartao.hidden = false;
  const botao = $('#btn-push');
  if (botao) botao.addEventListener('click', ativarPush);
}

async function ativarPush() {
  try {
    const permissao = await Notification.requestPermission();
    if (permissao !== 'granted') {
      avisar('Sem permissão, os alertas só aparecem com o app aberto.', 'erro');
    } else {
      const registro = await navigator.serviceWorker.ready;
      const inscricao = await registro.pushManager.subscribe({
        userVisibleOnly: true, applicationServerKey: base64UrlParaBytes(estado.push.chavePublica),
      });
      await api('api/push/inscricoes', { metodo: 'POST', corpo: inscricao.toJSON() });
      estado.inscricaoSincronizada = true;
      avisar('Notificações ativadas neste aparelho.');
    }
  } catch (e) {
    avisar('Não foi possível ativar as notificações: ' + e.message, 'erro');
  }
  renderizarCartaoPush();
}

async function sair() {
  if (!confirm('Sair desta conta neste aparelho? Você deixa de receber os alertas aqui.')) return;
  try {
    if (suportaPush()) {
      const registro = await navigator.serviceWorker.getRegistration();
      const inscricao = registro && await registro.pushManager.getSubscription();
      if (inscricao) {
        await api('api/push/inscricoes?endpoint=' + enc(inscricao.endpoint), { metodo: 'DELETE' }).catch(() => {});
        await inscricao.unsubscribe();
      }
    }
  } catch (e) { /* sai mesmo assim */ }
  encerrarSessao();
}

/* ---------- inicialização ---------- */

async function carregarInfo() {
  const selo = $('#selo');
  try {
    const info = await api('api/info');
    const ambiente = (info.ambiente || 'local').toLowerCase();
    selo.textContent = ambiente;
    selo.className = 'selo ' + (ambiente === 'staging' || ambiente === 'production' ? ambiente : 'local');
    document.title = 'AlertaPrédio · ' + ambiente;
    const versao = $('#versao');
    versao.textContent = (info.versao || 'dev').substring(0, 7);
    versao.title = info.versao || '';
  } catch (e) {
    selo.textContent = 'offline';
  }
}

async function carregarPush() {
  try {
    estado.push = await api('api/push/chave-publica');
  } catch (e) {
    estado.push = { habilitado: false, chavePublica: null };
  }
}

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
  navigator.serviceWorker.addEventListener('message', (evento) => {
    const dados = evento.data || {};
    if (dados.tipo === 'abrir' && dados.url) {
      const destino = new URL(dados.url, location.href);
      if (destino.origin === location.origin) location.hash = destino.hash || '#/';
    } else if (dados.tipo === 'push') {
      recarregar();
    }
  });
}

window.addEventListener('hashchange', navegar);
document.addEventListener('visibilitychange', () => { if (!document.hidden) recarregar(); });
$('#btn-sair').addEventListener('click', sair);
setInterval(vigiar, 15000);

carregarInfo();
carregarPush().then(navegar);
