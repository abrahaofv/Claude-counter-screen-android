# Fase 7 — Lápide/Fantasma & Contador de Sobrevivência

Criado: 2026-07-23
Atualizado: 2026-07-23 (implementado)

**Decisões tomadas** (defaults recomendados no documento, sem objeção do usuário): lápide/fantasma **substitui** o KO cinza-com-X (não é opcional); contador de sobrevivência **global** (uma única janela morrendo — 5h ou semana — zera o contador); recorde **permanente**.

## Context

Esta é a continuação do plano "Clawd Tamagochi" (Fases 1-6, já implementadas — ver histórico da conversa e `README.md`). Na Fase 7, adiada de propósito, o usuário aprovou visualmente as duas mecânicas restantes depois de ver um preview interativo (`clawd-fase7-preview.html`, na raiz do projeto — abrir no navegador para reconferir cores, geometria e animação antes de implementar):

- **Lápide/fantasma**: aos 100%, em vez do mascote cinza caído com X nos olhos (comportamento atual — `MascotStage.KO` em [Mascot.kt](../app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt)), mostrar uma cena — lápide de pixel art + fantasma coral translúcido flutuando acima — até a janela resetar.
- **Contador de sobrevivência**: faixa no rodapé da aba Clawd tipo `CLAWD VIVO HA 4d 6h · RECORDE 11d`, zerando quando uma janela bate 100% e continuando a contar durante o "descanso" (não zera de novo até a próxima morte).

O usuário curtiu o preview e quer implementar, mas **não agora** — este documento existe para retomar o trabalho depois sem precisar re-explorar o código do zero.

**Decisões ainda em aberto** (o preview lista as mesmas 3 perguntas — resolver antes ou no início da implementação):

1. A lápide/fantasma **substitui** o KO cinza-com-X atual, ou fica opcional (ex.: um switch extra em Configurações)?
2. O contador de sobrevivência é **um contador global** (reseta na morte de qualquer uma das duas janelas) ou **um contador por janela** (5h e semana cada um com o seu)?
3. O recorde é **permanente** (guardado para sempre) ou só **"melhor da semana"** (reseta periodicamente)?

Recomendação default caso o usuário não tenha preferência quando isto for retomado: substituir (não deixar as duas versões coexistindo como opção — mais uma chave de config para manter), contador **global** (mais simples, e é o que o preview mostra), recorde **permanente**.

---

## Fase 7.1 — Cena de lápide/fantasma (`GraveScene`)

- [x] 7.1.1 Novo composable `GraveScene(width: Dp, modifier: Modifier = Modifier)` em `ui/brand/GraveScene.kt` (arquivo próprio, como o plano antecipava — dois elementos desenhados num só `Canvas`, não um `MascotStage` a mais).
- [x] 7.1.2 Geometria portada 1:1 do `graveSvg()` do preview: lápide com dois `Path.arcTo` (r=6, centros em (16,17) e (24,17)) + base reta, `StickColors.MascotKo`; texto "RIP" via `nativeCanvas`/`android.graphics.Paint` (monospace, alpha 0.34); elipse de sombra `Color.Black alpha 0.32`; fantasma com `quadraticTo` (topo arredondado + base de 5 pontas), `StickColors.Accent` a 60%, dois olhos `drawRoundRect` pretos, bob (`sin(phase/2)`, 3.2s) + pulso de opacidade via `rememberInfiniteTransition`.
- [x] 7.1.3 Pergunta 1 resolvida como **substituir** (default recomendado) — troca direto onde `MascotStage.KO` é renderizado, sem switch novo.
- [x] 7.1.4 Decidido manter o mascote cinza-com-X simples no mini-card (`TileAgora`/`StickHeader`, 56dp) — `GraveScene` só entra na aba Clawd (96dp) e no `MomentOverlay` (176dp), onde há espaço pra ler as duas peças.
- [x] 7.1.5 `TileClawd.kt` (`ClawdCard`) e `MomentOverlay.kt` atualizados para renderizar `GraveScene` em vez de `Mascot` quando `stage == MascotStage.KO`. Borda vermelha pulsante do overlay mantida (ainda é um sinal de "crítico" independente da cena). `TileAgora.kt`/`StickHeader.kt` não tocados, por causa da 7.1.4.

## Fase 7.2 — Persistência de sobrevivência (`SurvivalStatsRepository`)

- [x] 7.2.1 Criado `app/src/main/java/com/example/claudecounter/data/SurvivalStatsRepository.kt`, mesmo padrão singleton + `SharedPreferences` de `PollingSettings`/`DisplaySettings`.
- [x] 7.2.2 Pergunta 2 resolvida como **global**: `SurvivalState(aliveSinceMs, recordMs)` único, sem distinção por `UsageWindow`.
- [x] 7.2.3 Pergunta 3 resolvida como **permanente** — `recordMs` nunca é arquivado/resetado.
- [x] 7.2.4 `recordDeath(now: Long)` implementado: compara `now - aliveSinceMs` contra `recordMs`, persiste os dois valores em `SharedPreferences` e atualiza o `StateFlow`.
- [x] 7.2.5 `formatSurvivalDuration(ms)` adicionada em `util/Formatting.kt`, portando `fmtDuration()` do preview (`Xd Yh` / `Xh Ymin` / `Xmin`).

## Fase 7.3 — Ligar a morte ao contador

- [x] 7.3.1 `SessionManager.detectMomentCrossing` agora chama `SurvivalStatsRepository.getInstance(appContext).recordDeath(...)` no mesmo `if (top == 100)` que dispara `NotificationHelper.notifyClawdDown` — só na transição, mesma proteção contra spam.
- [x] 7.3.2 N/A — contador é global (pergunta 2), não há duas chamadas por janela a coordenar.

## Fase 7.4 — UI do contador

- [x] 7.4.1 `SurvivalBanner` (composable privado novo) adicionado em `TileClawd.kt`, abaixo dos dois `ClawdCard`, mesmo `Column`. Estilo do preview: `Surface` + borda `Border`/`Accent`, texto mono, elapsed em `StickColors.Text` (bold), recorde em `StickColors.Accent`.
- [x] 7.4.2 Estado coletado via `SurvivalStatsRepository.state` (`collectAsStateWithLifecycle`); o texto usa o `now: Long` que `TileClawd` já recebia (tick de 1s de `MonitorRoot.kt`), sem timer novo.
- [x] 7.4.3 Flash de recorde implementado com `Animatable` — borda anima de `StickColors.Border` para `StickColors.Accent` e volta (900ms) quando `recordMs` sobe.
- [x] 7.4.4 N/A — contador global (pergunta 2), uma faixa única embaixo, como no preview.

## Fase 7.5 — Configurações (se aplicável)

- [x] 7.5.1 N/A — pergunta 1 resolvida como "substituir", não "opcional"; nenhum switch novo em `SettingsScreen.kt`.
- [x] 7.5.2 N/A pela mesma razão — o contador já é condicional ao switch existente "Mascote Clawd" por estar dentro da aba Clawd.

## Verificação

1. [x] **Build:** `./gradlew assembleDebug` — `BUILD SUCCESSFUL`.
2. [ ] **Cena de morte:** instrumentar temporariamente `SessionManager.updateUsage` injetando 95 → 100 para forçar `MascotStage.KO`/`GraveScene` e conferir a legibilidade em cada tamanho (mini-card, aba Clawd, overlay). **Pendente** — não há emulador/dispositivo conectado neste ambiente (`adb devices` vazio); precisa ser feito manualmente.
3. [ ] **Contador:** forçar duas "mortes" seguidas com tempo de sobrevivência diferente entre elas pra confirmar que o recorde só atualiza quando supera o anterior, e que o texto formata corretamente nas três faixas (minutos / horas / dias). **Pendente**, mesmo motivo.
4. [ ] **Persistência:** matar o processo do app (não só a activity) e reabrir — `aliveSinceMs`/`recordMs` devem sobreviver. **Pendente**, mesmo motivo.
5. [ ] Revisitar o preview (`clawd-fase7-preview.html`) lado a lado com a implementação real pra conferir fidelidade de cor/proporção. **Pendente**, mesmo motivo.
