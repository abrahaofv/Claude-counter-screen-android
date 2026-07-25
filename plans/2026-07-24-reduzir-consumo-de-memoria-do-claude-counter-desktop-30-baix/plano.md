# Reduzir consumo de memória do Claude Counter Desktop (~30%, baixo risco)

> **Criado:** 2026-07-24 20:43:42 · **Atualizado:** 2026-07-24 20:43:42

## Context

O usuário pediu uma medição da memória total do app e, ao ver ~330MB de working set somados
entre os processos, perguntou o que seria necessário para cortar isso pela metade. Uma
investigação dos processos (`Get-CimInstance Win32_Process` com `CommandLine`) mostrou que a
instância rodando é composta por: processo principal, processo de GPU dedicado (~64MB),
utilitário de rede (~27,6MB), utilitário de áudio (~26,9MB), e três renderers — o widget, o
overlay de limiar, e a janela oculta que carrega `claude.ai` de verdade para burlar a
detecção de bot da Anthropic (a mais pesada, ~79MB, porque carrega o SPA React completo do
claude.ai só para emprestar seu `fetch()`/cookies).

Cortar pela metade (~165MB) só é alcançável mexendo nesse maior consumidor — reciclando a
janela oculta quando o app está ocioso —, o que recarregaria o claude.ai a cada ciclo de poll
ocioso (custo extra de rede/CPU) e, em teoria, tornaria o padrão de acesso mais "parecido com
script" para a detecção de bot (área que o próprio projeto já trata como frágil — ver
`WebViewUsageFetcher.kt`/`usageFetcher.ts`, que deliberadamente mantêm essa janela **sempre
viva**, tanto no Android quanto no desktop, exatamente para evitar esse padrão de
recarregamentos repetidos). O usuário optou por ficar só com os cortes de baixo risco (~30%,
sem tocar nesse comportamento fundamental), deixando a opção mais agressiva de fora por
enquanto.

**Não incluído neste plano** (fora do escopo escolhido pelo usuário): reciclar a janela
oculta do `usageFetcher.ts` durante ociosidade — é o que fecharia a distância até 50%, mas
muda o comportamento de rede/anti-bot documentado como intencional, então fica de fora.

### Fase 1 — Cortes de memória de baixo risco (GPU, áudio, overlay lazy)

- [ ] 1.1 `src/main/main.ts`: adicionar `app.disableHardwareAcceleration()` no topo do
  arquivo, antes de `app.whenReady().then(...)` — remove o processo de GPU dedicado por
  inteiro (~50-64MB). As janelas do app são pequenas e usam só Canvas 2D (nunca WebGL/CSS 3D
  pesado), então a compositação por software não deve custar CPU perceptível.
- [ ] 1.2 `src/main/main.ts`: adicionar `app.commandLine.appendSwitch('disable-features', 'AudioServiceOutOfProcess')`
  antes de `app.whenReady()` — o app não usa nenhuma API de áudio em lugar nenhum, então esse
  processo (~27MB) é puro overhead do Chromium; a chamada dobra essa responsabilidade de
  volta pro processo principal sem custo real.
- [ ] 1.3 `src/main/overlayWindow.ts`: mover a criação da `BrowserWindow` (hoje no
  construtor de `OverlayWindow`) para dentro de um método privado `ensureWindow()`, chamado
  no início de `show()`. `dismiss()` e `reposition()` passam a checar se a janela existe
  antes de operar nela (ela pode nunca ter sido criada). `getAnchorBounds` continua injetado
  no construtor sem mudança. Overlay só aparece em eventos raros (cruzar 25/50/70/100% ou
  reviver) — manter esse processo inteiro fora da memória na maioria das sessões, ao custo
  de uma carga de página quase instantânea (`overlay.html` é estático e pequeno) só na
  primeira vez que um limiar é cruzado.
- [ ] 1.4 Build (`npm run build`) e smoke test (`npm start`) — ver seção Verificação abaixo.

## Verificação

1. `npm run build` — checar que compila sem erros de TypeScript.
2. `npm start` (modo dev, sem precisar gerar `.exe` de novo) — abrir o app, aguardar alguns
   segundos, e comparar a lista de processos (`Get-CimInstance Win32_Process -Filter
   "Name='Claude Counter Desktop.exe'" | Select ProcessId,CommandLine`): confirmar que **não
   há** processo `--type=gpu-process` nem `AudioService`, e que só há **dois** renderers
   (widget + fetcher oculto) até o primeiro limiar ser cruzado.
3. Somar o `WorkingSet64` de todos os processos (mesmo comando `Get-Process` usado antes) e
   comparar com a baseline de ~330MB — esperar uma redução na faixa de ~90-100MB.
4. Forçar visualmente um cruzamento de limiar (ou usar a `MascotEngine`/estado de teste já
   existente) para confirmar que o overlay ainda aparece corretamente na primeira vez
   (criação lazy funcionando) e em usos subsequentes (reaproveitando a janela já criada).
5. Confirmar visualmente que o widget continua renderizando normalmente (mascotes animados,
   sem estatelamento/flicker) com a aceleração de hardware desativada — abrir o app e
   observar por ~30s.

## Riscos / notas

- **Sem mudança de contrato de comportamento** (threshold/cadência/parse continuam iguais) —
  `SPEC.md` não precisa de entrada de changelog, é só otimização de processo, invisível ao
  usuário/ao Android.
