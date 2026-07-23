# Fase 7 — Lápide/Fantasma & Contador de Sobrevivência

Criado: 2026-07-23
Atualizado: 2026-07-23

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

- [ ] 7.1.1 Adicionar um novo composable `GraveScene(width: Dp, modifier: Modifier = Modifier)` — provavelmente em [Mascot.kt](../app/src/main/java/com/example/claudecounter/ui/brand/Mascot.kt) ou um arquivo novo `ui/brand/GraveScene.kt`, já que estruturalmente é diferente do `Mascot()` (dois elementos — lápide estática + fantasma animado — não um único `Path` com stages). Não é um `MascotStage` a mais: é a substituição visual específica de `MascotStage.KO`.
- [ ] 7.1.2 Portar a geometria exata do preview (`clawd-fase7-preview.html`, função `graveSvg()`):
  - Lápide: path com topo arredondado (dois arcos) + base reta, cor `StickColors.MascotKo` (`#6A6A74`) — mesma cor já usada no corpo do mascote morto, pra manter a associação visual.
  - Texto gravado "RIP" centralizado, fonte mono, baixa opacidade preta (~0.34), simulando entalhe.
  - Sombra/monte de terra: elipse escura semitransparente na base.
  - Fantasma: silhueta clássica (topo arredondado + base ondulada de 5 pontas), `fill = StickColors.Accent` a ~60% de opacidade, dois olhos retangulares escuros. Flutua com bob vertical (~3.2s, ease-in-out) + pulso sutil de opacidade — em Compose, usar `rememberInfiniteTransition` como o `Mascot()` já faz (ver `bobPhase`/`shakePhase` em Mascot.kt para o padrão).
- [ ] 7.1.3 Decidir a resolução da pergunta 1 (substituir vs. opcional) e implementar de acordo. Se "substituir": trocar direto onde `MascotStage.KO` é renderizado.
- [ ] 7.1.4 **Atenção ao tamanho**: o mini-mascote em `TileAgora` (`UsageCardStick`, ~56dp — ver [TileAgora.kt:138](../app/src/main/java/com/example/claudecounter/ui/tiles/TileAgora.kt#L138)) provavelmente é pequeno demais para a cena de duas peças (lápide + fantasma) ficar legível. Avaliar no preview/emulador se compensa manter o mascote cinza-com-X simples só nesse tamanho mini, e usar `GraveScene` apenas na aba Clawd (`TileClawd.kt`, mascote ~96-160dp) e no `MomentOverlay` (176dp).
- [ ] 7.1.5 Atualizar os call sites que hoje decidem `MascotStage.KO` para mascote: `TileClawd.kt` (`ClawdCard`), `MomentOverlay.kt` (mensagem/borda vermelha pulsante — conferir se a borda ainda faz sentido com a cena nova ou se fica redundante), e opcionalmente `TileAgora.kt`/`StickHeader.kt` conforme decisão da 7.1.4.

## Fase 7.2 — Persistência de sobrevivência (`SurvivalStatsRepository`)

- [ ] 7.2.1 Criar `app/src/main/java/com/example/claudecounter/data/SurvivalStatsRepository.kt`, seguindo o padrão de singleton + `SharedPreferences` já usado em [PollingSettings.kt](../app/src/main/java/com/example/claudecounter/data/PollingSettings.kt) e [DisplaySettings.kt](../app/src/main/java/com/example/claudecounter/data/DisplaySettings.kt) (prefs simples, sem criptografia — não guarda credencial).
- [ ] 7.2.2 Resolver a pergunta 2 (global vs. por janela) e desenhar o estado de acordo:
  - Se **global**: `aliveSinceMs: Long` (default = primeira instalação/primeiro uso) + `recordMs: Long`.
  - Se **por janela**: duplicar os dois campos para `SESSION` e `WEEKLY` (`UsageWindow` já existe em [SessionManager.kt](../app/src/main/java/com/example/claudecounter/SessionManager.kt)).
- [ ] 7.2.3 Resolver a pergunta 3 (recorde permanente vs. semanal). Se semanal, definir o dia/hora do corte (ex.: mesma âncora do reset semanal da Anthropic) e quando o valor é arquivado/exibido.
- [ ] 7.2.4 Função `recordDeath(now: Long)`: compara `now - aliveSinceMs` contra `recordMs` (atualiza se for maior), depois reseta `aliveSinceMs = now`. Persistir os dois valores.
- [ ] 7.2.5 Função de leitura pura para formatar duração (`Xd Yh` / `Xh Ymin` / `Xmin`) — o preview já tem a lógica de referência em `fmtDuration()` dentro de `clawd-fase7-preview.html`; portar para Kotlin, possivelmente em `util/Formatting.kt`.

## Fase 7.3 — Ligar a morte ao contador

- [ ] 7.3.1 Em `SessionManager.detectMomentCrossing` ([SessionManager.kt](../app/src/main/java/com/example/claudecounter/SessionManager.kt)) — o mesmo ponto que já dispara `NotificationHelper.notifyClawdDown` quando `top == 100` — chamar `SurvivalStatsRepository.getInstance(appContext).recordDeath(now)`. Mesma garantia de "só na transição" que já protege a notificação contra spam a cada poll.
- [ ] 7.3.2 Se a resposta da pergunta 2 for "por janela", cuidado: duas mortes na mesma leitura (sessão e semana baterem 100% juntas) devem gerar duas chamadas independentes, uma por janela.

## Fase 7.4 — UI do contador

- [ ] 7.4.1 Faixa (`SurvivalBanner` composable novo, ou inline em `TileClawd.kt`) no rodapé da aba Clawd — abaixo dos dois `ClawdCard`, dentro do mesmo `Column` de [TileClawd.kt](../app/src/main/java/com/example/claudecounter/ui/tiles/TileClawd.kt). Estilo de referência no preview (`.survival-banner`): card `Surface` + borda `Border`, texto mono, número em `StickColors.Text` (bold), recorde em `StickColors.Accent`.
- [ ] 7.4.2 Coletar o estado de `SurvivalStatsRepository` como `StateFlow`/recomposição periódica (o texto muda com o tempo mesmo sem novo poll — reaproveitar o `LaunchedEffect` de tick de 1s que já existe em [MonitorRoot.kt](../app/src/main/java/com/example/claudecounter/ui/MonitorRoot.kt), não precisa de um novo timer).
- [ ] 7.4.3 Pequena animação de destaque ("flash") quando um novo recorde é batido — no preview isso é o `record-flash` (box-shadow pulsando na cor accent); portar como uma animação Compose de curta duração disparada quando `recordMs` muda para um valor maior.
- [ ] 7.4.4 Se a contagem for "por janela" (pergunta 2), decidir layout: duas faixas menores (uma por card) em vez de uma faixa única embaixo.

## Fase 7.5 — Configurações (se aplicável)

- [ ] 7.5.1 Se a pergunta 1 ficou "opcional" (não substituir), adicionar o switch correspondente em `SettingsScreen.kt` — seguir o padrão já existente da seção "Aparencia" ([SettingsScreen.kt](../app/src/main/java/com/example/claudecounter/ui/SettingsScreen.kt), `AppearanceSection`).
- [ ] 7.5.2 Avaliar se o contador de sobrevivência deveria ter switch próprio ou simplesmente seguir o switch já existente "Mascote Clawd" (`DisplaySettings.showMascots`) — recomendação: seguir o existente, já que o contador só aparece dentro da aba Clawd, que já é condicional a esse switch (ver `MonitorRoot.kt`, `pages` dinâmico).

## Verificação

1. **Build:** `./gradlew assembleDebug` a partir de `c:\git\Claude-Counter-Android`.
2. **Cena de morte:** instrumentar temporariamente `SessionManager.updateUsage` (como já foi feito para testar Fases 1-6) injetando 95 → 100 para forçar `MascotStage.KO`/`GraveScene` e conferir a legibilidade em cada tamanho (mini-card, aba Clawd, overlay).
3. **Contador:** forçar duas "mortes" seguidas com tempo de sobrevivência diferente entre elas pra confirmar que o recorde só atualiza quando supera o anterior, e que o texto formata corretamente nas três faixas (minutos / horas / dias).
4. **Persistência:** matar o processo do app (não só a activity) e reabrir — `aliveSinceMs`/`recordMs` devem sobreviver, já que estão em `SharedPreferences`, não em memória.
5. Revisitar o preview (`clawd-fase7-preview.html`) lado a lado com a implementação real pra conferir fidelidade de cor/proporção antes de considerar a fase concluída.
