# Passo a passo do deploy: GitHub, GHCR, Render e Atlas

Este guia leva o AlertaPrédio do repositório local até os ambientes de **staging** e **produção**.
Siga na ordem: cada passo depende do anterior. Nada aqui exige Docker na sua máquina: a imagem é
construída pelo GitHub Actions.

**Pré-requisito: MongoDB Atlas.** Um cluster free (AWS São Paulo) recebe dois databases,
`alerta_staging` e `alerta_prod`. As duas connection strings são configuradas **apenas nas variáveis de
ambiente do Render, nunca no repositório**. O acesso `0.0.0.0/0` fica liberado porque o Render free não tem IP fixo.

**Pré-requisito: chaves.**

- **Gemini:** crie uma API key em <https://aistudio.google.com/apikey>. Sem ela o app funciona, mas a triagem por IA fica "indisponível".
- **VAPID (Web Push):** gere **um par por ambiente** com `java scripts/GerarChavesVapid.java` e guarde as duas linhas. Não troque o par depois do deploy: os celulares já inscritos param de receber.

---

## 1. Criar o repositório e dar push

O repositório precisa ser **público**: no plano free do GitHub, os *Required reviewers* de um
environment só funcionam em repositórios públicos.

1. Em <https://github.com/new>, crie `alerta-predio`, **Public**, **sem** README, .gitignore ou licença.
2. Na pasta do projeto (a raiz `AlertaPredio/`, onde está o `pom.xml`):

   ```bash
   git init -b main
   git add .
   git commit -m "AlertaPrédio: primeira versão"
   git remote add origin https://github.com/Matheus-Costa2004/alerta-predio.git
   git push -u origin main
   ```

## 2. Primeira execução do pipeline (os deploys vão falhar, e isso é esperado)

O push na `main` dispara o workflow **CI/CD** (aba *Actions*):

| Job | Resultado esperado agora |
|---|---|
| Build e testes unitários | ✅ |
| Testes de integração (MongoDB) | ✅ |
| Imagem Docker → GHCR | ✅ publica `ghcr.io/matheus-costa2004/alerta-predio:<sha>` e `:latest` |
| Deploy em staging | ❌ `Secret ausente`: o Render ainda não existe |
| Deploy em produção | não roda (depende de staging) |

A falha do staging é a mensagem clara do próprio pipeline:
`O Deploy Hook do ambiente staging não está configurado`.

## 3. Tornar público o package no GHCR

O Render (plano free) baixa a imagem sem credenciais, então o package precisa ser público.

1. No GitHub, abra seu perfil → aba **Packages** → `alerta-predio`.
2. **Package settings** → *Danger Zone* → **Change visibility** → **Public** → confirme digitando o nome.
3. Ainda em *Package settings*, confira se o repositório `alerta-predio` aparece em
   **Manage Actions access** com papel *Write* (é o padrão quando o próprio workflow cria o package).

## 4. Criar os 2 serviços no Render

Repita para cada ambiente em <https://dashboard.render.com> → **New** → **Web Service** →
**Existing Image**:

| Campo | Staging | Produção |
|---|---|---|
| Image URL | `ghcr.io/matheus-costa2004/alerta-predio:latest` | `ghcr.io/matheus-costa2004/alerta-predio:latest` |
| Name | `alerta-predio-staging` | `alerta-predio-prod` |
| Region | Virginia (US East) | Virginia (US East) |
| Instance Type | **Free** | **Free** |

**Environment Variables** de cada serviço:

| Variável | Staging | Produção |
|---|---|---|
| `MONGODB_URI` | connection string terminada em `/alerta_staging?...` | connection string terminada em `/alerta_prod?...` |
| `APP_ENV` | `staging` | `production` |
| `SPRING_PROFILES_ACTIVE` | `staging` | `prod` |
| `SEED_ENABLED` | `true` | `true` |
| `GEMINI_API_KEY` | sua chave do Google AI Studio | sua chave do Google AI Studio |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | par gerado para staging | par gerado para produção |
| `VAPID_SUBJECT` | `mailto:<e-mail de contato da equipe>` | idem |

Os profiles `staging` e `prod` já usam `IA_PROVEDOR=gemini`; só crie essa variável se quiser forçar
`fake` (por exemplo, para economizar cota durante testes). O modelo padrão é `gemini-3.5-flash-lite`;
para trocar, defina `GEMINI_MODELO`.

Em **Advanced** → **Health Check Path**, coloque `/actuator/health`. Clique em **Deploy Web Service**.

> **Não** crie `APP_VERSION` nem `PORT` no Render. `APP_VERSION` vem gravada na imagem (o SHA do
> commit) e é o que o smoke test confere; `PORT` o próprio Render injeta.

Anote as URLs públicas (ex.: `https://alerta-predio-staging.onrender.com`). O primeiro deploy sobe a
imagem `:latest` e o seed cria o prédio de demonstração (código `ACACIA`).

## 5. Copiar os Deploy Hooks

Em cada serviço: **Settings** → **Deploy Hook** → copie a URL (`https://api.render.com/deploy/srv-...?key=...`).

Trate essa URL como senha: quem a tiver consegue disparar deploys.

## 6. Environments, secrets e variables no GitHub

No repositório: **Settings** → **Environments** → **New environment**.

**`staging`**
- *Environment secrets* → `RENDER_DEPLOY_HOOK_STAGING` = hook do `alerta-predio-staging`
- *Environment variables* → `STAGING_URL` = URL pública do staging (sem `/` no final)

**`production`**
- Marque **Required reviewers** e adicione você e o Lucas. Deixe *Prevent self-review*
  desmarcado para conseguir aprovar o próprio deploy.
- *Environment secrets* → `RENDER_DEPLOY_HOOK_PROD` = hook do `alerta-predio-prod`
- *Environment variables* → `PROD_URL` = URL pública da produção (sem `/` no final)

## 7. Rodar o pipeline de novo e aprovar a produção

O job de imagem só roda em `push` na `main`, então use uma destas formas:

- Na aba **Actions**, abra a execução do passo 2 → **Re-run all jobs**; ou
- faça um novo push: `git commit --allow-empty -m "Dispara pipeline" && git push`.

O que acontece:

1. `Deploy em staging` chama o hook com `imgURL=ghcr.io/matheus-costa2004/alerta-predio:<sha>` e consulta
   `STAGING_URL/api/info` a cada 15 s (até 12 min, por causa do cold start do plano free) até
   `versao` ser o SHA do commit. Depois confere `/actuator/health` = `UP`.
2. `Deploy em produção` fica **Waiting**. Clique em **Review deployments** → marque `production`
   → **Approve and deploy**.
3. O mesmo deploy + smoke test roda contra `PROD_URL`.

## 8. Conferir o app publicado

1. Abra a URL de staging no celular: o selo **STAGING** (âmbar) e a versão do commit aparecem no topo e no rodapé.
2. Entre como **Carla (síndica)** num aparelho e como **Ana** ou **João** em outro (seção *Demonstração*).
3. Em cada aparelho, toque em **Ativar notificações**. No iPhone, primeiro instale o app: *Compartilhar → Adicionar à Tela de Início*.
4. Dispare um alerta com foto: os outros aparelhos recebem o push, e em alguns segundos aparece o selo da IA (ou "indisponível", se a chave não estiver configurada).

## 9. Validar o docker-compose (local ou no Codespace)

O pipeline não usa o `docker-compose.yml`; ele é o ambiente local. Se o Docker não sobe na sua
máquina, valide-o num **GitHub Codespace**, que já vem com Docker e Compose v2:

1. No repositório: **Code** → **Codespaces** → **Create codespace on main**.
2. No terminal do Codespace:

   ```bash
   cp .env.example .env            # troque as senhas no .env (e cole as chaves, se quiser push/IA)
   docker compose up -d --build
   docker compose ps               # alerta-api e alerta-mongo "healthy"
   ```

3. Aba **Ports** → porta `8080` → ícone de globo abre o PWA
   (`https://<nome-do-codespace>-8080.app.github.dev/`).
4. Ao terminar: `docker compose down` e **Stop codespace**, para não gastar a cota gratuita.

---

### Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Render: `unauthorized` ao baixar a imagem | Package do GHCR ainda privado (passo 3) |
| Smoke test esgota as 48 tentativas | Deploy falhou no Render: veja *Logs* do serviço (geralmente `MONGODB_URI` errada) |
| `/actuator/health` com `mongo: DOWN` | Usuário/senha da string ou acesso de rede no Atlas |
| Produção não pede aprovação | Repositório privado ou *Required reviewers* não marcado |
| Selo da IA sempre "indisponível" | `GEMINI_API_KEY` ausente ou inválida; a mensagem exata aparece no alerta (`classificacao.erro`) e no log |
| Botão "Ativar notificações" não aparece | `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY` ausentes ou inválidas (veja o log de inicialização) |
| Push não chega no iPhone | O app precisa estar instalado na tela de início (iOS 16.4+) |
| Docker Desktop não inicia na máquina (hipervisor/virtualização) | Rode com `./mvnw spring-boot:run` + MongoDB local, ou use o Codespace do passo 9 |
