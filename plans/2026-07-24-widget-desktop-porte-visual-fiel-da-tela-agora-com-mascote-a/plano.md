# Widget desktop: porte visual fiel da tela "Agora" (com mascote animado completo)

> **Criado:** 2026-07-24 12:13:31 · **Atualizado:** 2026-07-24 18:07:22

## Context

Depois de testar o `.exe` da v2, o usuário viu que o **widget flutuante principal** ficou parecido com o mini-player do Spotify (pequeno, card simples com um círculo laranja de mascote) — mas não é isso que ele queria como resultado final. Ele mostrou capturas de tela do **app Android** (tela "Agora": logo pixelada "CLAUDE CODE", dois cards grandes 5H/SEMANA lado a lado com mascote animado em cada um, medidor segmentado, contagem grande) e deixou claro: *"o visual do widget principal tem de ser igual à primeira tela do app, a tela do agora, deve ser idêntica"*. Ele topa que o resto (Tendência/Ritmo/Configurações/Sobrevivência) continue só no painel acessado pela bandeja — não precisa de paginação por swipe dentro do widget.

Ele também confirmou quando perguntado: quer a **animação completa do mascote** (respiração, olhar, piscar, pulo, suor — motor de mola) nesta mesma leva, não uma versão estática simplificada.

Este plano substitui o conteúdo do widget (`src/renderer/index.html`/`styles.css`/`renderer.js`) por um porte fiel da tela "Agora" do Android, incluindo o motor de comportamento do mascote. O painel (`panel.html`) e o restante da arquitetura Electron (main, preload, stores, overlay, tray) **não mudam** — só o conteúdo/tamanho da janela do widget.

## Especificação exata extraída do Android (fonte da verdade)

- **Canvas lógico**: 480×320 (`StickDimens.CanvasWidth/CanvasHeight`) — é a resolução em que toda a tela "Agora" foi desenhada. `MonitorScaffold.kt` escala esse canvas inteiro (`graphicsLayer scaleX/scaleY`) para caber na tela disponível, mantendo a proporção — vamos replicar exatamente essa técnica via `transform: scale()` num wrapper, em vez de reescrever o layout como responsivo.
- **Header** (`StickHeader.kt`, `HeaderHeight=40dp`): mascote 42dp + logo "wordmark" + texto de status + botão refresh (56×40, ícone) + botão settings (78×40, ícone) + barra fina de progresso (`RefreshBarHeight=3dp`) por baixo.
- **Logo "wordmark"**: `app/src/main/res/drawable/wordmark_claudecode.xml` é um `VectorDrawable` viewBox `0 0 56 24`, um único `<path>` com `fillColor="#D97757"` e `fillType="evenOdd"`. A sintaxe do `pathData` é idêntica à de um `d=""` de SVG — dá pra copiar literalmente para um `<svg viewBox="0 0 56 24"><path fill="#D97757" fill-rule="evenodd" d="...">` sem converter nada.
- **Fonte**: Montserrat variável, arquivo real em `app/src/main/res/font/montserrat_variable.ttf` — vamos copiar esse `.ttf` para o projeto desktop e usar via `@font-face` (fidelidade tipográfica exata, não um fallback do sistema). Tamanhos exatos em `StickTypography.kt`: displayPercent 48/Bold, displayCountdown 40/SemiBold, heading 16/SemiBold, label 14/Medium, caption 12/Medium.
- **Cards "Agora"** (`TileAgora.kt`, `UsageCardStick`): dois cards lado a lado (`Row`, `spacedBy(8dp)`, cada um `weight(1f)`), altura 210dp, fundo `Surface`, `CardRadius=18dp`, `CardPadding=14dp`. Cada card: título ("5 HORAS"/"SEMANA", `label` style, `Muted`), linha com % grande (`displayPercent`, cor por `usageColor(pct)`) + mascote mini 56dp à direita, medidor segmentado, "RESETA EM • dia/hora" (`caption`, `Faint`) + contagem regressiva grande (`displayCountdown`, `Text`).
- **Medidor segmentado** (`SegmentMeter.kt` + `StickDimens`): 18 segmentos (`SegmentCount`), cada um 8×16dp (`SegmentWidth`×`SegmentHeight`), raio 2dp, passo 11dp entre centros, cor acesa = `usageColor(pct)`, apagados = `Track` a 63% de opacidade. `litSegments(pct,count) = round(pct/100*count)`.
- **Status chip** (`StatusChip.kt` + `pctColorDiscrete`): pílula com fundo `lerp(color, Bg, 0.76)`, texto `caption` na cor cheia. Rótulo/cor: `< 70% → "OK"/Ok`, `< 90% → "ATENCAO"/Warn`, `>= 90% → "BLOQUEADO"/Bad` (ou `"BLOQUEADO"/Bad` fixo se `isApiBlocked`).
- **Cores derivadas** (`StickVisuals.kt`): `usageColor(pct)` = lerp contínuo Ok→Warn (0-50%) → Warn→Bad (50-100%) — usado no texto % e nos segmentos acesos. Precisam de um `lerpHex(colorA, colorB, t)` em JS.
- **Mascote** (`Mascot.kt` + `MascotBehavior.kt`, já lidos por completo): silhueta = `OuterContour` (28 pontos, viewBox 24×24, banda de arte y=[3.5, 22.5]) com dois retângulos de olho (`EyeLeft`/`EyeRight`) recortados por `fillType=evenOdd`. Sombra elíptica no chão. Sombreado "flat" (tira mais clara no topo, mais escura numa lateral, ambas cor sólida, clipadas ao path do corpo). Olho contínuo = pupila (retângulo arredondado, `pupilScale`/`lookX`/`lookY`) + pálpebra (retângulo arredondado, `eyelidFrac`). O motor `MascotBehaviorState` (springs amortecidas, 6 âncoras de traços por vitalidade 100/75/50/30/10/0, fila de 9 ações com pesos, piscar como canal reflexo separado, pool de 6 partículas suor/faísca) é **puro e sem dependência de Compose/Canvas** — só o passo final (`drawBehaviorFrame`) usa a API de desenho, que no desktop vira Canvas 2D (`fill('evenodd')`, `translate/rotate/scale`, `clip()`, `roundRect`).

## Decisões de porte

- **Tamanho da janela do widget**: passa de 300×180 para **480×320** (o canvas lógico exato), com um wrapper que escala tudo via CSS `transform: scale()` proporcional ao tamanho real da janela — mesma técnica do `MonitorScaffold.kt`. Isso elimina qualquer necessidade de reinventar breakpoints/responsividade: a autoria continua em pixels fixos 480×320, igual ao Android.
- **Continua flutuante/arrastável/sempre-no-topo/sem barra de tarefas** — só o conteúdo e o tamanho padrão mudam, não o comportamento de janela já validado na v1.
- **Motor do mascote**: porte completo e fiel de `MascotBehaviorState` para uma classe JS (`mascotEngine.js`), populando um "frame" por `requestAnimationFrame`, e um desenhista Canvas 2D (`mascotDraw.js`) que replica `drawBehaviorFrame`/`drawContinuousEye`/`drawShadow`/`drawFlatFaceShading`. Três instâncias independentes: header (42px), card 5h (56px), card semana (56px) — cada uma com seu próprio estado de mola (igual a três `remember { MascotBehaviorState() }` no Android).
- **Mapeamento de "mood"**: no widget ao vivo, o desktop usa sempre o caminho contínuo (`mood=Ok`, dirigido por `pct`), exceto quando `isApiBlocked`, que mapeia para o visual estático `Error` (recolor cinza `MascotKo`, olhos em X) já existente em `Mascot.kt`. *(Atualização Fase 15: os demais moods — `Limited`/`Unavailable`/`NeverProbed` — e a galeria de animações completa acabaram portados também, ver Fase 15 abaixo; só não se aplicam ao ciclo normal de polling do widget, que não tem esses probes de rede.)*
- **Cores/tipografia/logo**: 1:1, arquivos copiados/portados diretamente das fontes acima (nenhum valor "aproximado").

### Fase 10 — Janela e escala do widget

- [x] 10.1 `widgetWindow.ts`: `CANVAS_WIDTH/CANVAS_HEIGHT = 480/320` exportados, tamanho padrão da `BrowserWindow` agora 480×320; `minWidth/minHeight` = 2/3 disso (320×213); demais flags (`frame:false`, `transparent:true`, `alwaysOnTop`, `skipTaskbar`, arrasto) inalteradas.
- [x] 10.2 `index.html`/`styles.css`: `#stageOuter` (100vw/100vh) contendo `#stage` (480px×320px fixo, `transform-origin: 0 0`); `renderer.js` calcula `scale = min(innerWidth/480, innerHeight/320)` a cada `resize` e aplica `stage.style.transform`.

### Fase 11 — Layout estático fiel (header + cards + chip)

- [x] 11.1 `styles.css`: variáveis CSS com os valores exatos (`--seg-w/h/radius/step/count`, `--header-h`, `--card-radius` etc.); `@font-face` `MontserratVariable` apontando para `assets/fonts/montserrat_variable.ttf` (copiado do Android) — **verificado carregando** via Playwright (`document.fonts` → status `loaded`).
- [x] 11.2 Header: mascote 42px + `<svg viewBox="0 0 56 24">` com o `pathData` colado literalmente do `wordmark_claudecode.xml` (funciona sem conversão — sintaxe idêntica a `d=""` de SVG) + status text + botões refresh (↻) / settings (⚙) + barra fina de progresso.
- [x] 11.3 Dois cards lado a lado — título, % grande (`usageColor`), mascote mini 56px, medidor de 18 segmentos, "RESETA EM • dia hora" + contagem grande.
- [x] 11.4 Status chip: `pctColorDiscrete`/`isApiBlocked` → OK/ATENCAO/BLOQUEADO com fundo tintado.
- [x] 11.5 `src/renderer/stickVisuals.js`: `usageColorRgb`/`pctColorDiscreteRgb`/`chipBackground`/`litSegments`, operando em arrays `[r,g,b]` (não hex) para poder alimentar direto o `lerpRgb` também usado pelo motor do mascote (Fase 13).

### Fase 12 — Mascote: desenho estático (silhueta, olhos, sombra)

- [x] 12.1 `src/renderer/mascotDraw.js`: `drawMascotFrame(ctx, widthPx, frame, particles)` — `Path2D` do corpo + 2 retângulos de olho, `ctx.fill(path,'evenodd')`; `translate(tremor,postura)` → `translate(pivô pés)/rotate/scale(squashX=1/√squashY, squashY)/translate(-pivô)` (mesma composição de transforms do Compose); sombreado flat via `ctx.clip(path,'evenodd')` + 2 `fillRect`; olho contínuo (pupila + pálpebra via `roundRectPath`); partículas suor(azul)/faísca(âmbar). Também `drawMascotError(ctx, widthPx)` — visual estático cinza + olhos em X para `isApiBlocked`.
- [x] 12.2 Constantes geométricas copiadas literalmente (`OUTER_CONTOUR` 28 pontos, `EYE_LEFT`/`EYE_RIGHT`, `ARTWORK_TOP=3.5`, `ARTWORK_HEIGHT=19`, `FOOT_Y=20`).
- [x] 12.3 Validado visualmente via Playwright (servidor HTTP local temporário + `window.claudeCounter` stub + `window.render()` direto) — silhueta/proporção batendo com a captura da Fase 5.

### Fase 13 — Mascote: motor de comportamento (springs, ações, piscar, partículas)

- [x] 13.1 `src/renderer/mascotEngine.js`: classe `Spring` (sub-passos de 0.016s, igual ao Kotlin) + `ANCHORS` (6 pontos de vitalidade 100/75/50/30/10/0, tints pré-calculados via `lerpRgb(accent,warn/bad,fração)` reaproveitando `stickVisuals.js`) + `traitsFor(vitality)` (interpolação linear entre âncoras adjacentes, incluindo os pesos de ação).
- [x] 13.2 Fila de 9 ações com durações/curvas fiéis (`LOOK/LOOK_UP/TILT/JUMP/STRETCH/YAWN/WIPE/SURPRISE/SETTLE`) + piscar como canal reflexo separado + pool fixo de 6 partículas (suor/faísca) com spawn/step/expiração/`_clearSweat` no meio do `WIPE`.
- [x] 13.3 `MascotEngine.step(dt)` produz o mesmo objeto `frame` do Kotlin — consumido por `drawMascotFrame`.
- [x] 13.4 `renderer.js`: três instâncias (`headerEngine`/`sessionEngine`/`weeklyEngine`) tickando via `requestAnimationFrame` com `setVitalityTarget(100-pct)` atualizado a cada `usage:update`; `isApiBlocked` desvia para `drawMascotError` nos 3 canvases em vez de rodar o motor. **Verificado**: capturas em sequência (0s e 2s) mostram os mascotes em poses diferentes — motor rodando de verdade, não parado.

### Fase 14 — Cabo solto: refresh manual e status "atualizado há Xs"

- [x] 14.1 `PollLoop.triggerNow()` (novo método público) — poll imediato fora do ciclo agendado, reagenda depois. `main.ts` escuta `ipcMain.on('usage:refresh', ...)`; botão ⚙ dispara `widget:openPanel` → `openPanelWindow(pushPanelState)`.
- [x] 14.2 `WidgetUsageState` (novo tipo em `preload.ts`, estende `UsageState` com `pollIntervalMs`) — `main.ts` inclui `pollLoop.currentIntervalMs()` (método tornado público) a cada `usage:update`; `renderer.js` recalcula "atualizado há Xs" e a fração da barra a cada segundo via `setInterval`.

### Fase 15 — Painel: paridade de Configurações + galeria de animações

Depois de testar a v3 do widget, o usuário reparou que o painel desktop (aba "Configurações",
acessada pela bandeja) não tinha todas as opções do app Android — faltavam os toggles de aparência
e, principalmente, a "Ver animações do Clawd" (galeria) que existe no Android desde a Fase 7
(`MascotGalleryScreen.kt`, `MascotBehavior.kt` com `forceAction`/`forceBlink`, `GraveScene.kt` —
lápide/fantasma no stage KO). Esta fase leva essas peças para o desktop, respeitando a doutrina de
espelhamento do `CLAUDE.md`.

- [x] 15.1 `src/main/displaySettingsStore.ts` (novo): mirror de `DisplaySettings.kt` — apenas
  `showMascots`/`showAgoraMascots` (os toggles `showClawdTab`/`openClawdFirst` do Android não se
  aplicam: o widget desktop não tem uma "aba dedicada do Clawd"/pager). Persistido via
  `electron-store` (`display-settings`), padrão `true`/`true`.
- [x] 15.2 `main.ts`: instancia o store, inclui `displayConfig` no payload `usage:update` do widget
  e no `PanelData` do painel; novo canal `panel:setDisplayConfig`; `displaySettingsStore.on('change', pushState)`.
- [x] 15.3 `preload.ts`/`panelPreload.ts`: `WidgetUsageState`/`PanelData` ganham `displayConfig`;
  bridge do painel ganha `setDisplayConfig`.
- [x] 15.4 `renderer.js` (widget): mascote do header escondido se `!showMascots`; mascotes dos cards
  escondidos se `!(showMascots && showAgoraMascots)` — via nova classe `.hidden` genérica em
  `styles.css` (a regra antiga só cobria `.login-link.hidden`, corrigido para reaproveitar em
  qualquer elemento).
- [x] 15.5 `panel.html`/`panel.css`/`panel.js`: seção "Aparência" no topo da aba Configurações (dois
  checkboxes com copy igual ao Android).
- [x] 15.6 `mascotEngine.js`: `forceAction(action)`/`forceBlink()` — porta 1:1 de
  `MascotBehaviorState.forceAction`/`forceBlink` (Android), para a galeria disparar
  microexpressões sob demanda sem esperar a fila.
- [x] 15.7 `mascotDraw.js`: generaliza o antigo `drawMascotError` num `drawStaticMood(ctx, widthPx, mood)`
  cobrindo `error`/`unavailable`/`neverProbed` (olho sonolento + alpha reduzido para NeverProbed,
  igual ao `drawStaticMood`/`drawSleepyEye` do `Mascot.kt`); `drawMascotError` vira um wrapper fino.
  Adiciona `drawGraveScene`/`drawSpirits` — porte do `GraveScene.kt` (tumba com dois arcos
  `ctx.arc`, fantasma via `quadraticCurveTo`, 5 partículas de espírito com fase escalonada).
- [x] 15.8 Nova aba "Galeria" no painel (`panel.html`): preview (canvas 160px) + seletores de
  Estágio (7: RESTED..REVIVING), Status de conexão (5 moods) e Microexpressões (PISCAR + 9 ações)
  — mesmos rótulos em português do `MascotGalleryScreen.kt`. `panel.js` mantém uma
  `MascotEngine` própria da galeria, tickando via `requestAnimationFrame` independente do resto do
  painel; `KO` sempre renderiza a cena de lápide (prioridade sobre o mood, igual ao Android).
- [x] 15.9 Colisão de nome corrigida: `mascotView.js` (usado por `overlay.html`) e `stickVisuals.js`
  (usado pelo widget) declaravam `const STICK` cada um com formato diferente — inofensivo
  enquanto nunca eram carregados juntos, mas `panel.html` agora carrega ambos (`mascotView.js` para
  `formatCountdown` da aba Tendência, `stickVisuals.js` para o motor do mascote da Galeria). Renomeado
  o `STICK` interno de `mascotView.js` para `OVERLAY_STICK` (único consumidor é o próprio arquivo).
- [x] 15.10 `SPEC.md` atualizado (seção 6, mascote) documentando moods estáticos, cena de lápide,
  galeria e os toggles de aparência — doutrina de espelhamento do `CLAUDE.md` cumprida.

## Verificação (manual, end-to-end)

1. `npm run build && npm start` (ou `npm run dist` + abrir o `.exe`): o widget abre em 480×320, visualmente equivalente à tela "Agora" do Android — logo, dois cards, medidor segmentado, chip de status.
2. Redimensionar a janela do widget (arrastando a borda): o conteúdo inteiro escala proporcionalmente (texto, cards, mascotes), sem cortar nem distorcer — igual ao comportamento do `MonitorScaffold` ao rotacionar/redimensionar no Android.
3. Os três mascotes (header + 2 cards) respiram, olham para os lados, piscam, e ocasionalmente pulam/espreguiçam/bocejam de forma independente uns dos outros — comparar informalmente contra o app Android rodando lado a lado.
4. Puxar a vitalidade para baixo (uso alto): mascotes suam (partículas azuis), postura cai, tremor aparece; puxar para 100% aciona o overlay + o card mostra o mascote na configuração mais "cansada" que o motor produzir a essa vitalidade.
5. Clicar no botão de refresh do header dispara um poll imediato e a barra/"atualizado há Xs" reflete isso.
6. `isApiBlocked` (simular deslogando): os mascotes viram cinza com olhos em X (visual estático "Error"), sem quebrar a animação dos outros elementos.
7. Abrir o painel pela bandeja → aba "Configurações": seção "Aparência" no topo com os toggles "Mascote Clawd"/"Mascotes nos cards"; desligar "Mascote Clawd" some com os 3 mascotes do widget (header + cards); religar e desligar só "Mascotes nos cards" mantém o mascote do header mas some os dos cards.
8. Aba "Galeria": trocar Estágio para "MORTO" mostra a lápide com fantasma flutuante e partículas subindo; trocar Status de conexão para "ERRO" mostra o corpo cinza com olhos em X; para "INDISPONIVEL"/"NUNCA SONDADO" mostra olho sonolento (o segundo com transparência); clicar nas microexpressões (PISCAR + 9 ações) dispara a pose imediatamente no preview.

## Riscos / notas

- **Maior esforço desta leva**: o motor de comportamento (Fase 13) é o item de maior risco/tempo — é uma porta linha-a-linha de ~550 linhas de Kotlin (springs, 6 âncoras, fila de 9 ações, partículas) para JS/Canvas 2D. Fazer a Fase 12 (desenho estático) primeiro e validar visualmente antes de acoplar o motor reduz o risco de depurar duas coisas novas ao mesmo tempo.
- **Fonte Montserrat**: usar o `.ttf` real via `@font-face` custa alguns KB no pacote final, mas é a única forma de ficar "idêntico" tipograficamente — nada de fallback do sistema.
- **A janela ficou bem maior que a v1** (480×320 vs 300×180) — isso é uma mudança real de forma do produto (deixa de ser um "mini-player" e vira uma "telinha de monitor" flutuante), mas é exatamente o que foi pedido ("deve ser idêntica" à tela Agora).
- O painel (Tendência/Ritmo/Configurações/Sobrevivência) **não é tocado neste plano** — continua com o visual simples atual; se depois de ver o widget novo o usuário quiser o mesmo tratamento visual lá, é um próximo incremento separado.

## Registro de execução

- 2026-07-24 12:31 — Todas as fases (10–14) implementadas. Build limpo em cada etapa (`npm run build`). Validação visual feita **sem precisar abrir o Electron**: servidor HTTP local efêmero servindo `dist/renderer/` + Playwright injetando um `window.claudeCounter` de mentira via `window.render(fakeState)` direto (já que o bridge real só existe dentro do Electron) — capturas confirmaram: layout idêntico à referência (logo, dois cards, medidor, chip), fonte Montserrat carregada (`document.fonts` → `loaded`), motor do mascote rodando de verdade (poses diferentes entre duas capturas com 2s de intervalo), e o estado `isApiBlocked` (mascotes cinza + olhos em X, chip BLOQUEADO, botão de login aparecendo) funcionando.
- 2026-07-24 12:31 — Antes do build final (`npm run dist`), havia 8 processos antigos do widget ainda rodando (o usuário tinha aberto manualmente após a sugestão da leva anterior) — pedi confirmação antes de encerrar (`Stop-Process -Force`), autorizado, segui. `npm run dist` rodado sozinho, sem build concorrente (lição da leva anterior), completou sem erro.
- **Não verificado nesta sessão**: abrir o `.exe` de verdade, redimensionar a janela ao vivo (conferir o `transform: scale()`), comparar lado a lado com o app Android rodando, e testar os botões de refresh/configurações dentro do Electron real (o preview via Playwright não tem o bridge `window.claudeCounter` de verdade, só um teste de layout/animação) — isso fica para o usuário.
- 2026-07-24 18:07 — Fase 15 (paridade de Configurações + galeria) implementada. Corrigido também: janela do widget não estava de fato sempre-no-topo (Windows ordena janelas "topmost" por quem se reafirmou por último — apps como Teams/VSCode conseguiam passar por cima); `widgetWindow.ts` agora reforça `setAlwaysOnTop`/`moveTop()` a cada 2s. Botão de configurações (⚙) removido do header do widget — o painel já é acessível pela bandeja, ficava redundante. Validado visualmente via Playwright (servidor HTTP local + `dist/renderer/panel.html`): aba Galeria renderizando corretamente o mascote normal (RESTED/Ok), a cena de lápide (stage KO) com tumba/fantasma/RIP, e o mood ERRO (corpo cinza + olhos em X); aba Configurações mostrando a nova seção Aparência. `npm run build` limpo (sem erros de TypeScript); 8 processos antigos do widget precisaram ser encerrados (autorização pedida e concedida) antes de gerar o `.exe` final.
- **Não verificado nesta sessão (Fase 15)**: abrir o `.exe` de verdade e confirmar que os toggles Aparência realmente escondem/mostram os mascotes do widget ao vivo (só testado via Playwright fora do Electron, sem o bridge IPC real), e que o widget agora permanece de fato acima de janelas como VSCode/Teams por período prolongado.

## Status Final

Todas as 5 fases (10–14) da v3 concluídas: o widget agora é um porte visual fiel da tela "Agora" do Android — 480×320, logo vetorial, fonte Montserrat real, dois cards com medidor segmentado e mascote animado (motor de springs/ações/partículas completo, não simplificado), chip de status, e barra/texto de "atualizado há Xs" com refresh manual. A Fase 15 fechou uma lacuna de paridade encontrada pelo usuário após testar a v3 (settings incompletos, sem galeria de animações) e corrigiu o always-on-top do widget. Validado visualmente via Playwright (fora do Electron); falta o teste real do usuário abrindo o `.exe` (`release/Claude Counter Desktop 0.1.0.exe`, já gerado com as Fases 10–15).
