# Claude Counter Desktop — widget flutuante Electron

> **Criado:** 2026-07-24 09:33:33 · **Atualizado:** 2026-07-24 10:46:18

## Context

Hoje o Claude Counter existe como **app Android** (Kotlin/Compose) e **extensão de navegador** (JS), duas implementações independentes do mesmo tracking de limites do Claude.ai (janela de 5h e semanal). O usuário quer uma **terceira encarnação**: um programa para Windows que seja um *widget flutuante* — uma janelinha pequena, sempre por cima das outras janelas e móvel, no estilo do mini-player flutuante do Spotify — replicando o visual e o comportamento do app.

Restrição técnica central que define a arquitetura: a Anthropic bloqueia (`403`) o endpoint `/usage` para clientes HTTP cujo fingerprint TLS/HTTP não seja de um browser real. Por isso o Android usa um `WebView` oculto em `claude.ai` (`WebViewUsageFetcher.kt`) e a extensão usa o `fetch` da própria página (`bridge.js`). **Qualquer versão desktop precisa do mesmo motor Chromium real** — o que torna o **Electron** a escolha natural (Chromium + Node), além de permitir reaproveitar o visual que já existe em forma web (`clawd-v3-preview.html`, HTML/CSS autocontido com cores 1:1 de `StickColors.kt` e o mascote 1:1 de `Mascot.kt`).

Decisões confirmadas com o usuário: **Tecnologia** Electron; **Local** pasta irmã `C:\git\Claude-Counter-Desktop` como repositório git próprio; **Escopo v1** widget mínimo flutuante (card Agora + contadores de reset + mascote + always-on-top arrastável — trend/heatmap/overlays/settings ficam para depois); **Espelhamento** via um `SPEC.md` canônico + regra no `CLAUDE.md` de cada projeto. Resultado pretendido: um `.exe` flutuando no canto da tela, sempre visível, mostrando o uso do Claude em tempo real, mantido em sincronia comportamental com o app Android via um spec compartilhado.

## Mapeamento Android → Desktop (o que será portado)

| Android (Kotlin) | Desktop (Electron/TS) |
|---|---|
| `WebViewUsageFetcher` (WebView oculto em claude.ai) | `BrowserWindow` oculto (`show:false`) em claude.ai + `webContents.executeJavaScript` do mesmo `fetch('/api/organizations/{orgId}/usage')` |
| `LoginWebViewActivity` (login e captura de cookie/org) | `BrowserWindow` de login visível, `session` particionada persistente (cookies salvos entre execuções) |
| `ClaudeApiService.parseUsageResponse` (parse de `five_hour`/`seven_day`) | função TS equivalente, mesma lógica de `utilization`/`used`/`limit`/`resets_at` |
| `SessionManager` (StateFlow, thresholds 25/50/70/100, revival ≥10pts, ACTIVE_LINGER 3min) | store TS (event emitter) com a mesma máquina de estados |
| `UsagePollingService` (cadência adaptativa idle/active) | loop `setTimeout` no main process com a mesma cadência |
| `PollingSettings` / `DisplaySettings` (SharedPreferences) | `electron-store` (JSON em `userData`) |
| UI Compose (`TileAgora`, `SegmentMeter`, `Mascot`) | HTML/CSS baseado em `clawd-v3-preview.html` |
| Notificação persistente | tray icon + `Notification` do Electron (fora do escopo v1 mínimo; opcional) |

### Fase 0 — Scaffold do repositório desktop

- [x] 0.1 Criar `C:\git\Claude-Counter-Desktop\` e `git init`.
- [x] 0.2 Estrutura mínima: `package.json` (electron, electron-builder, electron-store, typescript); `src/main/` (processo main: janelas, polling, store); `src/preload/` (ponte segura `contextBridge` main↔renderer); `src/renderer/` (UI do widget: `index.html` + `styles.css` + `renderer.js`); `src/core/` (lógica portável e testável: parse, máquina de estados, formatação); `tsconfig.json`, `.gitignore` (node_modules, dist, out). Também: `scripts/copy-static.js` (copia os assets estáticos do renderer para `dist/` no build, já que `tsc` só compila `.ts`).
- [x] 0.3 Registrar o projeto no `C:\git\PORTS.md` como **projeto sem porta** (v1 carrega o renderer via `file://`, sem dev server). Se mais tarde adotar Vite, pegar `8085` (próxima porta dev livre) e fixar em `vite.config.ts`.

### Fase 1 — Autenticação e fetch com bypass de bot

Espelha `WebViewUsageFetcher.kt` e `LoginWebViewActivity.kt`.

- [x] 1.1 Criar uma `session` particionada **persistente** (`persist:claude`) para os cookies de login sobreviverem entre execuções. (`src/main/usageFetcher.ts`, `src/main/loginWindow.ts`)
- [x] 1.2 Janela de login (visível, sob demanda): carrega `https://claude.ai/login`, usuário loga normalmente. Detecta login concluído lendo o cookie `lastActiveOrg` a cada `did-finish-load` (mesma lógica de `LoginWebViewActivity.kt`). (`src/main/loginWindow.ts`)
- [x] 1.3 Janela oculta (`show:false`) fixada em `https://claude.ai/`, na mesma `session`. Fetch de uso via `webContents.executeJavaScript`, com o mesmo `fetch(url, { credentials:'include' })` do bridge da extensão. Trata erro/403 como `isApiBlocked`, com timeout de 25s (`Promise.race`). (`src/main/usageFetcher.ts`)
- [x] 1.4 Exportar `fetchUsage(orgId): Promise<{json, error}>` para o loop de polling.

### Fase 2 — Núcleo de dados portado (`src/core/`)

Portar de `ClaudeApiService.kt` e `SessionManager.kt`.

- [x] 2.1 `parseUsage.ts`: parse de `five_hour`/`seven_day` → `{ utilization, resetsAt }`, com o mesmo fallback `used/limit` e detecção de `403`.
- [x] 2.2 `sessionStore.ts`: estado único `{ orgId, sessionUtilization, sessionResetsAt, weeklyUtilization, weeklyResetsAt, lastFetchTime, pollMode, isApiBlocked, lastError }`; persistência via adaptador `PersistentStore` (implementado com `electron-store` em `src/main/store.ts`). Máquina de estados idêntica: `pollMode` IDLE/ACTIVE (vira ACTIVE quando a utilização **sobe**; volta a IDLE após `ACTIVE_LINGER_MS` = 3 min); limiares de "momento" 25/50/70/100 na subida; **revival** quando cai ≥10 pontos. Emite eventos `change`/`moment` (EventEmitter) em vez de StateFlow.
- [x] 2.3 `pollLoop.ts`: cadência adaptativa (idle lento / active rápido) via `setTimeout`, espelhando `UsagePollingService.kt` (incluindo `rescheduleNow()` ao detectar transição para ACTIVE). Defaults: idle 2min, active 15s (`DEFAULT_POLLING_CONFIG`).
- [x] 2.4 `format.ts`: countdown `Xd Yh` / `Hh Mm` / `Mm`, espelhando `formatCountdown` do Android.
- [x] 2.5 (nota) Trend/heatmap/projeção (`UsageAnalytics.kt`) ficam de fora da v1, mas a assinatura de estado já deixa espaço para adicioná-los depois.

### Fase 3 — Widget flutuante (UI)

Base visual: extrair CSS/geometria de `clawd-v3-preview.html`; comportamento de tile de `TileAgora.kt` e do medidor de `SegmentMeter.kt`.

- [x] 3.1 `BrowserWindow` do widget: `frame:false`, `transparent:true`, `alwaysOnTop:true` (nível `floating`), `resizable:true`, 300×180 inicial, `skipTaskbar:true`. (`src/main/widgetWindow.ts`)
- [x] 3.2 Sempre visível em todos os desktops virtuais: `setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })` + reforço de `setAlwaysOnTop` no evento `focus`.
- [x] 3.3 Arrastável: `-webkit-app-region: drag` na titlebar/linha do mascote; botões e barras com `no-drag` (`styles.css`).
- [x] 3.4 Renderer mostra barras de uso 5h/7 dias com %, countdown até reset, e um mascote simplificado (cor/olhos mudam por faixa de utilização, viram cinza "KO" perto de 100%). **Simplificação de v1**: não porta a engine completa de springs/partículas de `MascotBehavior.kt` — só os 4 estados de tinta/vitalidade, documentado no código como próximo incremento. Dark-only, cores 1:1 de `StickColors.kt`.
- [x] 3.5 Ponte de dados: `preload.ts` expõe `onUsageUpdate(cb)` e `onMoment(cb)` via `contextBridge`; `main.ts` envia o estado a cada `change`/`moment` via `webContents.send`.
- [x] 3.6 Menu de contexto (clique direito no widget): abrir login, sair. **Desvio do plano**: implementado como `context-menu` do próprio `webContents` em vez de um ícone de bandeja (`Tray`) dedicado — mais simples para v1; bandeja fica como incremento futuro se fizer falta.

### Fase 4 — Doutrina de espelhamento (app ↔ programa)

- [x] 4.1 Criar `SPEC.md` **canônico** em `Claude-Counter-Desktop/SPEC.md`. Contrato compartilhado: motivo do bypass 403 (§1), endpoint/forma do JSON e regras de parse/fallback (§2), captura de `orgId` via cookie (§3), máquina de estados — cadência adaptativa, limiares 25/50/70/100, revival ≥10pts, `ACTIVE_LINGER` 3min, timeout 25s (§4), paleta 1:1 (§5), mascote (§6), persistência (§7) + changelog.
- [x] 4.2 Seção **"Espelhamento (app ↔ desktop)"** adicionada ao `CLAUDE.md` do Android (aponta para `Claude-Counter-Desktop/SPEC.md`) e ao `CLAUDE.md` novo do desktop (aponta de volta para o Android e para o `SPEC.md` local).
- [x] 4.3 `README.md` do desktop criado: propósito, motivo do Electron, build/instalação, escopo da v1, créditos (ignitedvisions + benevid/stick).

## Verificação (manual, end-to-end)

1. `cd C:\git\Claude-Counter-Desktop && npm install && npm start` — o widget sobe.
2. Primeira execução abre a janela de login; logar em claude.ai; confirmar que o `orgId` é capturado e os cookies persistem (fechar e reabrir sem precisar relogar).
3. Confirmar que o card mostra uso 5h e 7 dias com % e countdown coerentes com o app Android / a página `/usage`.
4. Widget fica **por cima** de outras janelas (testar sobre navegador/editor em tela cheia janelada), é **arrastável** e reaparece em todos os desktops virtuais.
5. Gerar atividade no Claude e observar o polling acelerar (ACTIVE) e desacelerar (IDLE) após ~3 min — mesmo comportamento do Android.
6. Simular bloqueio (ex.: deslogar) → o widget deve exibir estado "API restrita" em vez de quebrar.
7. `npm run build` (electron-builder) gera um `.exe` instalável/portátil que roda sem toolchain de dev.
8. Conferir que `SPEC.md` existe, e que ambos os `CLAUDE.md` têm a seção de espelhamento apontando um para o outro.

## Status Final

Todas as fases de implementação (0–4) estão concluídas: o projeto `C:\git\Claude-Counter-Desktop`
existe, compila sem erros (`npm install && npm run build`), e contém o widget flutuante mínimo
(card de uso 5h/7 dias, mascote simplificado, sempre-no-topo, arrastável), o núcleo de dados
portado do Android, o bypass do bloqueio 403 via Chromium oculto, o fluxo de login, e a doutrina de
espelhamento (`SPEC.md` + `CLAUDE.md` dos dois lados + `README.md`).

**Fora do escopo desta v1** (documentado em `CLAUDE.md`/`SPEC.md` do desktop, para incrementos
futuros): trend/projeção, heatmap por hora, overlay de limiar animado, contador de sobrevivência,
tela de configurações, ícone de bandeja dedicado (usa menu de contexto por enquanto), e a engine
completa de comportamento do mascote (springs/partículas — o desktop usa 4 estados simplificados).

**Não verificado nesta sessão** (depende de ação do usuário, ver "Registro de execução"): rodar
`npm start` de verdade, logar com uma conta real do Claude.ai, e observar o widget ao vivo
(always-on-top sobre outras janelas, arrasto, aceleração/desaceleração do polling). Nada foi
commitado em nenhum dos dois repositórios git.

## Riscos / notas

- **Captura do `orgId`:** depende do cookie `lastActiveOrg` / rotas internas do claude.ai — mesma dependência que a extensão já assume; validar cedo (Fase 1) por ser o ponto de maior incerteza.
- **`transparent:true` no Windows** pode ter arestas de repaint; se problemático, cair para janela opaca com cantos arredondados desenhados via CSS sobre fundo sólido.
- **Login persistente = credencial no disco** (partição de sessão do Electron em `userData`). O Android guarda o cookie criptografado; documentar no `SPEC.md`/README que a partição fica só na máquina do usuário.
- Este plano **não** cobre trend/heatmap/overlay de limiar/contador de sobrevivência — são o próximo incremento após a v1 mínima.

## Registro de execução

- 2026-07-24 10:32 — Fases 0–3 implementadas e compilando limpo (`npm install` + `npm run build` sem erros; `tsc` gera `dist/main`, `dist/preload`, `dist/core`, e `scripts/copy-static.js` copia os estáticos do renderer). `git init` feito no novo repo; nada commitado ainda (aguardando pedido explícito do usuário).
- 2026-07-24 10:32 — Pausa solicitada pelo usuário por orçamento de tokens semanais (~74%→85%). Faltam: Fase 4 completa (SPEC.md + seções de espelhamento nos dois CLAUDE.md + README do desktop) e o teste manual end-to-end de verdade (login real em claude.ai, `npm start`, observar always-on-top/drag/polling ao vivo) — isso depende de ação do usuário (login com conta real) e não foi executado nesta sessão.
- 2026-07-24 10:38 — Usuário autorizou continuar. Fase 4 concluída: `SPEC.md`, `CLAUDE.md` do desktop, seção de espelhamento no `CLAUDE.md` do Android, `README.md` do desktop. Rebuild após as mudanças (`npm run build`) segue limpo. **Nada commitado** em nenhum dos dois repos — segue sem commit até pedido explícito do usuário. **Não executado**: `npm start` de verdade / login real / observação ao vivo do widget (itens 1–6 da seção "Verificação" abaixo) — requer ação do usuário com conta real do Claude.ai.
- 2026-07-24 10:46 — Usuário pediu para gerar o `.exe` de teste (`npm run dist`). Bug corrigido antes de conseguir: `electron-builder` usa por padrão a mesma pasta `dist` que o `tsc` já usa como `outDir`, colidindo com o build TS — corrigido com `directories.output: "release"` no `package.json` (+ `.gitignore` atualizado). Também bateu no problema conhecido do `electron-builder` no Windows de falhar ao extrair symlinks do pacote `winCodeSign` (ferramentas de assinatura macOS, irrelevantes para um `.exe` portátil sem assinatura) — corrigido com `win.signAndEditExecutable: false` no `package.json`, sem precisar mexer em Modo de Desenvolvedor do Windows nem rodar como admin. `npm run dist` gerou `release/Claude Counter Desktop 0.1.0.exe` (~71MB, sem ícone customizado — usa o ícone padrão do Electron) com sucesso.
