# Claude Counter Desktop v2 — ícone, bandeja, overlay, trend, heatmap, config, sobrevivência

> **Criado:** 2026-07-24 11:01:16 · **Atualizado:** 2026-07-24 11:50:54

## Context

A v1 do widget flutuante desktop (`C:\git\Claude-Counter-Desktop`) está funcionando: o usuário testou o `.exe` gerado, logou de verdade em claude.ai e confirmou que o card de uso 5h/7 dias, o mascote simplificado e o comportamento always-on-top/arrastável funcionam. Esse trabalho está registrado e fechado em `plans/2026-07-24-claude-counter-desktop-widget-flutuante-electron/plano.md` (Status Final).

Este plano cobre a "v2": os itens que a v1 deixou explicitamente de fora, agora priorizados pelo usuário — **todos os 7 itens pendentes**, mais um novo pedido: **ícone real do `.exe` com o mascote Clawd** (hoje ele usa o ícone padrão do Electron, ver `npm run dist` anterior: *"default Electron icon is used"*).

Decisão de UX central: o widget flutuante da v1 é **propositalmente pequeno** (300×180, estilo mini-player do Spotify) — não é o lugar certo para enfiar gráfico de tendência, heatmap de 24 barras e uma tela de configurações. A analogia mais fiel ao app Android (que tem um `MonitorPager` com múltiplos tiles) é: **manter o widget minúsculo como está**, e abrir os recursos maiores numa **janela secundária normal** (com moldura, redimensionável, na barra de tarefas) acessível pelo ícone de bandeja — não um segundo widget flutuante.

## Descobertas da exploração

- `MomentOverlay.kt` — overlay full-screen (mobile) com mascote caindo, % contando, mensagem por limiar (25/50/70/100, revival), auto-dismiss em 4600ms, anel vermelho pulsante em 100%/KO. No desktop, full-screen é exagero — vira uma janela pequena tipo "toast" perto do widget, mesmo texto/lógica, mesmo auto-dismiss.
- `TileTrend.kt` + `UsageAnalytics.kt` (já lido na v1) — linha de uso desenhada em `Canvas`, quebrada em segmentos por `splitOnGaps` (gaps tratados como "serviço parado", não reset), projeção linear tracejada até `projectExhaustionMs`, toggle 5h/7d via `PeriodPill`.
- `TileHeat.kt` — 24 barras verticais, altura proporcional a `hourlyBurnProfile`, seletor de período (Hoje/7d/30d/Tudo), barra da hora atual destacada.
- `UsageHistoryRepository.kt` — grava um `UsageSample{timestampMs, sessionPct, weeklyPct}` a cada poll bem-sucedido, arquivo JSON plano (não SharedPreferences — cresce demais), retenção de 31 dias.
- `SurvivalStatsRepository.kt` — `{aliveSinceMs, recordMs}`; `recordDeath(now)` é chamado no mesmo ponto onde o Android dispara a notificação de "Clawd morreu" (cruzamento de 100% pela primeira vez, não repetido enquanto fica pinado em 100%).
- `PollingSettings.kt` (já lido na v1) — toggle adaptativo + intervalos idle/active com opções fixas (`IDLE_OPTIONS_MS`, `ACTIVE_OPTIONS_MS`).
- **Ícone**: `clawd-v3-preview.html` monta o mascote via JS em tempo de execução (não é markup estático) — `#hero-slot` recebe o nó depois que o script roda, e há um botão `#btn-pause` para congelar a animação num frame limpo. Dá para carregar o arquivo local no Playwright, esperar o JS montar, pausar, e tirar um screenshot só daquele nó.
- O core do desktop (`src/core/sessionStore.ts`) **já emite** o evento `moment` (limiares + revival) desde a v1 — só falta algo consumir esse evento (hoje ele é encaminhado ao renderer via `preload.ts`/`onMoment`, mas o `renderer.js` do widget não faz nada com ele ainda).

### Fase 5 — Ícone do aplicativo (Clawd)

- [x] 5.1 Playwright abriu `clawd-v3-preview.html` (servido via um HTTP estático efêmero, já que o Playwright bloqueia `file://`), esperou o mascote montar em `#hero-slot`, clicou em `#btn-pause`, e capturou só aquele elemento (392×270, fundo `#1A1A20` = `StickColors.Surface`).
- [x] 5.2 `scripts/make-icon.js` (novas devDeps `jimp` + `png-to-ico`): centraliza o mascote num canvas 512×512 preenchido com a mesma cor de fundo da captura (elimina a costura visível que aparecia com `Bg` puro), grava `assets/icon.png` (runtime) e `build/icon.ico` multi-resolução (16/24/32/48/64/128/256).
- [x] 5.3 `package.json` → `build.win.icon: "build/icon.ico"`; `widgetWindow.ts` exporta `ICON_PATH` (`assets/icon.png`, resolvido relativo a `dist/main/` tanto em dev quanto empacotado) e passa `icon: ICON_PATH` na `BrowserWindow`.
- [x] 5.4 `npm run dist` não mostrou mais o aviso "default Electron icon is used"; ícone extraído do `.exe` gerado (`System.Drawing.Icon` via PowerShell) confirmado visualmente = o mascote Clawd.

### Fase 6 — Ícone de bandeja (Tray)

- [x] 6.1 `src/main/tray.ts`: `Tray` com `ICON_PATH` redimensionado para 16×16, tooltip inicial "Claude Counter Desktop".
- [x] 6.2 Menu (botão direito): "Mostrar/Ocultar widget" (rótulo dinâmico conforme `widget.isVisible()`), "Abrir painel...", "Entrar no claude.ai...", separador, "Sair". Clique simples (esquerdo) alterna mostrar/ocultar direto. Item "Abrir painel" foi ligado quando a Fase 9 ficou pronta (não ficou como botão morto).
- [x] 6.3 `updateTrayTooltip(tray, state)` chamado a cada `pushState()` em `main.ts` (mesmo listener `sessionStore.on('change', ...)` que já atualizava o widget) — tooltip mostra `% da sessão`, ou "aguardando login"/"API restrita" conforme o estado.

### Fase 7 — Overlay de limiar animado

- [x] 7.1 `src/main/overlayWindow.ts`: classe `OverlayWindow` (340×140, frameless, transparente, `alwaysOnTop`, `focusable:false`), reposiciona relativa aos bounds atuais do widget (`getAnchorBounds()`, segue mesmo se o widget foi arrastado), abaixo do widget por padrão ou acima se não couber na tela. `show(event)` reagenda o auto-dismiss (4600ms); `dismiss()` esconde e cancela o timer.
- [x] 7.2 `src/renderer/overlay.html` + `overlay.css` + `overlay.js`: mensagem por limiar/REVIVAL igual a `MomentOverlay.kt`, reaproveita `applyMascotState`/`formatCountdown` extraídos para `mascotView.js` (compartilhado agora com o widget — `renderer.js` também foi refatorado para usar esse módulo, eliminando a duplicação que existia antes). Animação de entrada via CSS (`overlay-in` keyframe), clique em qualquer lugar do card dispara `dismissOverlay()`.
- [x] 7.3 `main.ts`: `sessionStore.on('moment', event => overlay.show(event))` — substitui o encaminhamento morto que só mandava pro widget (que nunca consumia `onMoment`).

### Fase 8 — Histórico local + núcleo de analytics

- [x] 8.1 `src/main/historyStore.ts`: classe `HistoryStore` — grava `{timestampMs, sessionPct, weeklyPct}` em `app.getPath('userData')/usage-history.json`, retenção de 31 dias por corte de data a cada `recordSample`.
- [x] 8.2 `src/core/usageAnalytics.ts`: porta pura de `samplesSince`, `splitOnGaps`, `projectExhaustionMs`, `hourlyBurnProfile`. **Nota de arquitetura**: como o renderer não tem bundler nesta v1, essas mesmas funções foram duplicadas em `src/renderer/analyticsView.js` (JS puro) para o painel poder recomputar Tendência/Ritmo instantaneamente ao trocar de aba, sem round-trip de IPC — os dois arquivos precisam ser mantidos em sincronia (comentário cruzado nos dois).
- [x] 8.3 `PollLoop` ganhou um 4º parâmetro opcional `onSample(sessionPct, weeklyPct, now)`, chamado logo após `store.updateUsage()` ter sucesso; `main.ts` passa `historyStore.recordSample`.

### Fase 9 — Painel (janela secundária com abas)

- [x] 9.1 `src/main/panelWindow.ts`: `openPanelWindow(onReady)` — janela normal (720×560, moldura, redimensionável, barra de tarefas), singleton (reabrir foca a existente em vez de duplicar). `onReady` resolve a mesma corrida que o widget já tratava (chamado direto se a janela já estava carregada, ou em `did-finish-load` se acabou de ser criada). Aberta pelo tray ("Abrir painel..."). Preload dedicado `src/preload/panelPreload.ts` (`window.claudeCounterPanel`).
- [x] 9.2 Aba **Tendência** (`panel.html`/`panel.js`): `<canvas>` com gridlines 25/50/75, linha por segmento (`splitOnGaps`), projeção tracejada até `projectExhaustionMs` (ou até o reset da janela), ponto final destacado, rótulos de eixo (hora para 5h, dia da semana abreviado para 7d), pills 5h/7d, texto de veredito com as 3 mesmas variações do Android.
- [x] 9.3 Aba **Ritmo**: 24 barras (`hourlyBurnProfile`), pills Hoje/7d/30d/Tudo, barra da hora atual destacada, legenda de horas (0/6/12/18/23h).
- [x] 9.4 Aba **Configurações**: `src/main/pollingSettingsStore.ts` (electron-store dedicado, partição `polling-settings`) com `IDLE_OPTIONS_MS`/`ACTIVE_OPTIONS_MS`/`AGGRESSIVE_THRESHOLD_MS` idênticos ao Android; toggle adaptativo + 2 selects; `PollLoop` agora lê `pollingSettingsStore.current` em vez do `DEFAULT_POLLING_CONFIG` fixo, e um `pollingSettingsStore.on('change', () => pollLoop.rescheduleNow())` aplica a nova cadência na hora (mesmo padrão do `UsagePollingService.kt`).
- [x] 9.5 Aba **Sobrevivência**: `src/main/survivalStore.ts` (electron-store dedicado, partição `survival`); `main.ts` chama `survivalStore.recordDeath(now)` dentro do handler de `moment` quando `kind===THRESHOLD && threshold===100`. UI mostra "Clawd vivo há Xd Yh" (atualiza a cada 60s) + "Recorde: Zd Wh".

## Verificação (manual, end-to-end)

1. `npm run dist` gera o `.exe`; o ícone do arquivo/atalho mostra o Clawd (não o ícone padrão do Electron).
2. Um ícone aparece na bandeja do Windows; tooltip mostra o % atual; menu abre/fecha o widget e o painel.
3. Forçar (ou esperar) o uso cruzar 25/50/70/100% → um toast/overlay aparece perto do widget com a mensagem certa, some sozinho em ~4,6s.
4. Abrir o painel: aba Tendência mostra uma linha crescendo com o tempo (depois de alguns polls) e projeção tracejada; aba Ritmo mostra barras crescendo nas horas de uso; aba Configurações muda o intervalo de polling e o widget reage (cadência muda de verdade); aba Sobrevivência mostra o contador subindo e reseta ao cruzar 100%.
5. Fechar e reabrir o app: histórico de uso, configurações de polling e recorde de sobrevivência persistem (não voltam a zero).

## Status Final

Todas as 5 fases (5–9) implementadas e compilando/empacotando sem erro. Novidades desta v2:
ícone real do Clawd no `.exe` e na janela; ícone de bandeja com mostrar/ocultar + abrir painel;
overlay "toast" ancorado ao widget para limiares 25/50/70/100%/revival; histórico local de uso
persistido em disco; e um painel secundário (janela normal, redimensionável) com 4 abas —
Tendência (gráfico + projeção), Ritmo (heatmap 24h), Configurações (cadência de polling) e
Sobrevivência (streak + recorde).

**Não verificado nesta sessão** (deixado rodando para o usuário testar com conta real):
login de verdade, cruzar limiares de fato para ver o overlay, deixar o histórico acumular pontos
suficientes pro gráfico de Tendência ficar visível, mudar a cadência pelo painel e confirmar que o
widget reage, e persistência entre reinícios do app.

## Riscos / notas

- **Extração do ícone via Playwright** depende do JS do `clawd-v3-preview.html` continuar montando o mascote em `#hero-slot`/`#btn-pause` do jeito atual — se o arquivo mudar nessas IDs, o script de captura (5.1) quebra silenciosamente (precisa de uma checagem/erro claro se o elemento não existir).
- **Painel como janela separada** é uma mudança de forma em relação ao pedido original de "só um widget" — mas enfiar trend+heatmap+config+sobrevivência dentro de 300×180 pioraria a experiência que a v1 acabou de validar; manter o widget minúsculo intacto e abrir o resto à parte preserva o que já funciona.
- **`SPEC.md`** (contrato compartilhado com o Android) deve ganhar uma entrada no changelog para os novos comportamentos portados nesta v2 (histórico local, sobrevivência, overlay) — não repetido aqui item a item porque é o mesmo padrão já estabelecido na v1 (Fase 4).
- Dado o volume desta v2 (5 fases, várias janelas novas), a implementação deve seguir em checkpoints com pausa para o usuário conferir consumo de tokens, como já combinado na v1.

## Registro de execução

- 2026-07-24 11:3x — Fases 5 e 6 concluídas. Dois incidentes no meio do caminho: (1) rodei `npm run build` em foreground enquanto um `npm run dist` de background ainda estava de pé — os dois escrevem em `dist/`, então o de background ficou preso por ~17min sem nunca terminar; matei a tarefa (`TaskStop`) e refiz o `npm run dist` sozinho, sem nada concorrente, dessa vez completou normalmente. Lição: não rodar `npm run build`/`npm run dist` em paralelo no mesmo projeto. (2) O `.exe` anterior (do teste da v1) ainda estava aberto (6 processos `Claude Counter Desktop*.exe`, incluindo o próprio arquivo em `release/`), travando a sobrescrita — pedi confirmação ao usuário antes de encerrar os processos (`Stop-Process -Force`), ele autorizou, segui.
- 2026-07-24 11:3x — Ícone verificado via extração do próprio `.exe` (`[System.Drawing.Icon]::ExtractAssociatedIcon` + salvar PNG), não só pela ausência do aviso do electron-builder — confirmação visual de verdade.
- 2026-07-24 11:50 — Fases 7, 8 e 9 implementadas em sequência. `npm run build` limpo depois de cada fase. Build final (`npm run dist`) rodado sozinho, em foreground, sem nada concorrente (lição do incidente anterior) — completou sem erro, gerou `release/Claude Counter Desktop 0.1.0.exe`. Smoke test: lancei o `.exe` via PowerShell, esperou 4s, processo `Claude Counter Desktop 0.1.0` seguia de pé (sem crash imediato) — deixei rodando para o usuário testar de verdade (login real, abrir o painel, etc.), já que isso depende de conta real do Claude.ai.
