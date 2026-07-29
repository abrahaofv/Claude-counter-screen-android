# Corrigir bug do always-on-top que impede ocultar o widget

> **Criado:** 2026-07-29 09:28:02 · **Atualizado:** 2026-07-29 09:29:18

## Context

O usuário reportou dois bugs no `.exe` atual do Claude Counter Desktop: (1) ocultar o widget
pela bandeja às vezes não funciona — a janela reaparece; (2) a contagem de uso do widget
parou de atualizar. Ele atribuiu isso à "otimização de memória" da leva anterior, mas essa
otimização (desativar GPU/áudio dedicados, overlay lazy) **nunca chegou a ser codificada** —
só existe como plano em markdown (`plans/2026-07-24-reduzir-consumo-de-memoria-.../plano.md`),
sem nenhum commit ou build correspondente. Não há nada dessa mudança para reverter.

O `.exe` que o usuário está rodando contém apenas a leva anterior a essa ("Fase 15": galeria
de animações, toggles de aparência, remoção do botão ⚙, e uma correção de always-on-top).
Essa última correção é a suspeita real: `widgetWindow.ts` armou um `setInterval` de 2s que
chama `win.moveTop()`/`win.setAlwaysOnTop(true, 'screen-saver')` incondicionalmente, sem
checar se a janela está oculta — isso briga diretamente com `tray.ts`'s `widget.hide()`
(clique na bandeja), e a instabilidade de mostrar/ocultar que resulta disso é uma explicação
plausível também para a contagem "congelada" (o Chromium pode tratar a janela como ocluída
e conter a renderização enquanto esse cabo de guerra visibilidade/oclusão continua).

O usuário optou pelo reparo cirúrgico: corrigir só o timer do always-on-top, sem reverter a
galeria/toggles de aparência da Fase 15.

### Fase 1 — Corrigir o timer do always-on-top

- [x] 1.1 `src/main/widgetWindow.ts`: o callback do `setInterval` de 2s passa a checar
  `win.isVisible()` antes de agir — só chama `setAlwaysOnTop`/`moveTop()` quando a janela
  está realmente visível. Quando oculta (`tray.ts`'s `widget.hide()`), o timer não faz nada,
  deixando o `hide()` valer de fato. Ao mostrar de novo (`widget.show()`/clique na bandeja),
  o próprio `win.on('focus', ...)` já existente reafirma o topmost, e o timer retoma a partir
  do próximo tick — não precisa de lógica adicional de "retomar". `npm run build` limpo.

## Verificação

1. `npm run build` — compila sem erros de TypeScript.
2. `npm start` — abrir o widget, clicar no ícone da bandeja para ocultar: confirmar que ele
   permanece oculto por mais de 2s (passando do intervalo do timer) em vez de reaparecer.
3. Mostrar de novo pela bandeja: confirmar que volta a ficar por cima de outras janelas
   (VSCode/Teams) normalmente, preservando o comportamento original da correção.
4. Deixar o widget visível e observar por alguns minutos (ou forçar `usage:refresh`) para
   confirmar que a contagem de uso (%, segmentos, "atualizado há Xs") continua atualizando
   normalmente — já que essa era a segunda queixa do usuário.
5. Caso o passo 4 mostre que a contagem trava por um motivo independente do always-on-top,
   reportar ao usuário como um bug separado em vez de assumir que a mesma correção resolve.

## Riscos / notas

- Único arquivo alterado: `src/main/widgetWindow.ts`, dentro do bloco do `alwaysOnTopTimer`.
- Não reverte nem toca na galeria de animações/toggles de aparência da Fase 15.

## Registro de execução

- 2026-07-29 09:29 — Fase 1 implementada: `win.isVisible()` guarda o corpo do
  `alwaysOnTopTimer`. `npm run build` limpo, `.exe` reconstruído
  (`release/Claude Counter Desktop 0.1.0.exe`) sem precisar encerrar processos (nenhuma
  instância estava rodando no momento do build).
- **Não verificado nesta sessão**: os passos 2–5 da Verificação (ocultar/mostrar pela
  bandeja e confirmar que a contagem volta a atualizar) exigem o usuário testando o `.exe`
  de verdade — ainda não confirmado se a correção também resolveu a queixa nº2 (contagem
  travada) ou se essa é uma causa independente.

## Status Final

Corrigido o timer do always-on-top para respeitar `isVisible()`. A causa raiz do bug de
"não oculta" está corrigida com alta confiança (o `moveTop()`/`setAlwaysOnTop()`
incondicional era o único candidato óbvio brigando com `widget.hide()`). O efeito sobre a
contagem travada é uma hipótese razoável mas não comprovada — falta o usuário confirmar
após testar o novo `.exe`.
