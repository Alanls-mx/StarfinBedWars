# Homologação conjunta — Fases 6.1 a 7.1

## Fase 7.3: laboratório preparado, execução manual pendente

Em 2026-11-04 foi preparado um laboratório isolado com proxy, lobby, dois BedWars e MySQL 8. Veja [ambiente](HOMOLOGATION_ENVIRONMENT.md), [roteiro A–H e matrizes](HOMOLOGATION_PHASE_7_3.md), [resultados](HOMOLOGATION_RESULTS.md) e [bloqueadores](HOMOLOGATION_BUGS.md). Os testes automáticos locais passaram, porém **os 25 cenários H7.2-01 a H7.2-25 abaixo permanecem individualmente `NOT_RUN`**. Torneios só podem começar após criar arenas reais do grupo `tournament` e reunir clientes suficientes. Para cada execução, use [modelo de registro](homologation/phase-7-3/TEST_RECORD_TEMPLATE.md), manifesto das JARs, captura e IDs de jogo/reserva.

| ID | Roteiro original | Estado |
| --- | --- | --- |
| H7.2-01 | item 1 | NOT_RUN |
| H7.2-02 | item 2 | NOT_RUN |
| H7.2-03 | item 3 | NOT_RUN |
| H7.2-04 | item 4 | NOT_RUN |
| H7.2-05 | item 5 | NOT_RUN |
| H7.2-06 | item 6 | NOT_RUN |
| H7.2-07 | item 7 | NOT_RUN |
| H7.2-08 | item 8 | NOT_RUN |
| H7.2-09 | item 9 | NOT_RUN |
| H7.2-10 | item 10 | NOT_RUN |
| H7.2-11 | item 11 | NOT_RUN |
| H7.2-12 | item 12 | NOT_RUN |
| H7.2-13 | item 13 | NOT_RUN |
| H7.2-14 | item 14 | NOT_RUN |
| H7.2-15 | item 15 | NOT_RUN |
| H7.2-16 | item 16 | NOT_RUN |
| H7.2-17 | item 17 | NOT_RUN |
| H7.2-18 | item 18 | NOT_RUN |
| H7.2-19 | item 19 | NOT_RUN |
| H7.2-20 | item 20 | NOT_RUN |
| H7.2-21 | item 21 | NOT_RUN |
| H7.2-22 | item 22 | NOT_RUN |
| H7.2-23 | item 23 | NOT_RUN |
| H7.2-24 | item 24 | NOT_RUN |
| H7.2-25 | item 25 | NOT_RUN |

Esta lista é para uma sessão posterior com Minecraft 1.8.8, proxy, StarfinSocial, StarfinMinigames, StarfinBedWars e MySQL de **teste**. Nenhum item abaixo foi executado nesta implementação. Registrar data, JAR/commit, arena, jogadores, evidência visual, logs e estado do banco para cada caso. Não ativar torneios em produção antes de concluir a sessão.

## Registro obrigatório por cenário 01–25

Para cada item numerado, abrir um registro com: ID `H7.2-01` a `H7.2-25`, data, operador, versões e SHA-256 das JARs, commits, configuração sem segredos, dependências, comandos executados, estado inicial e final da arena/torneio, resultado esperado e observado, captura ou vídeo, trecho de log e consulta SQL pertinente. Marcar `PASS`, `FAIL`, `BLOCKED` ou `NOT_RUN`. Os 25 itens abaixo permanecem `NOT_RUN` até teste com clientes reais. Repetir comandos idempotentes onde o item solicitar; preservar os IDs de partida, jogo, confronto, série e torneio nas evidências.

## Separação da Fase 7.2

- **Automatizados:** suítes JDBC de estatísticas, ranqueadas, reservas e torneios, com resultados e XML em `VALIDATION_PHASE_7_2.md`; elas não validam renderização, transporte BungeeCord nem menus Minecraft.
- **Manuais:** itens 01–20 e 24–25 abaixo, em cliente Minecraft 1.8.8, proxy e backends de homologação.
- **Falhas dirigidas:** itens 18 e 21–23, apenas em ambiente isolado, com journal e logs preservados.
- **Desempenho:** medir latência de reserva/admissão e tempo de recuperação, com carga e hardware registrados; ainda `NOT_RUN`.

## Preparação

- Conferir backup, `stats.yml`, o mesmo banco no proxy e no backend, e `tournaments.enabled: true` nos dois plugins de teste.
- Criar arenas exclusivas no grupo `tournament`, com dois times e vagas conforme a modalidade; manter ao menos uma arena casual e uma ranqueada para regressão.
- Ter clientes Minecraft 1.8.8 suficientes para duas equipes, um espectador e um árbitro; usar uma Party do StarfinSocial para modalidade Dupla.
- Preparar torneios descartáveis MD1 e MD3, e um terceiro MD5 para série longa. As datas e grupos precisam coincidir entre proxy e backend.

## Roteiro

1. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Criar torneio no wizard; revisar todos os campos e confirmar. Reiniciar o backend e conferir que o rascunho persiste.
2. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Alterar regras de modo, datas, tamanho, mínimo/máximo, série, check-in, espectadores e títulos; rejeitar configuração inválida antes da publicação.
3. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Abrir inscrições e registrar equipe solo; verificar limite de equipes e jogador já inscrito.
4. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Registrar equipe Dupla pela Party; confirmar o segundo integrante; mudar/desfazer Party e verificar que o elenco oficial permanece.
5. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Fechar inscrições; verificar bloqueio de novas entradas e retirada.
6. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Gerar chave com número não potência de dois; inspecionar BYEs, seeds e emparelhamentos em menu e MySQL.
7. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Programar confronto, realizar check-in de todos e iniciar o campeonato; jogo não pode abrir antes da hora nem com elenco incompleto.
8. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Iniciar confronto MD1 com duas equipes distintas; jogadores recebem apenas seus times e inventário de partida.
9. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Iniciar série MD3; conferir placar após cada jogo, replay em empate e vitória após duas vitórias legítimas.
10. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Repetir série MD5 até três vitórias; conferir que não há jogo extra após o resultado decisivo.
11. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Transferir a Party inteira pela reserva em lote, inclusive quando jogadores já estão no backend BedWars; impedir jogador externo e time incorreto.
12. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Assistir como espectador público, privado e árbitro; conferir limite, isolamento, inventário e retorno ao lobby.
13. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Concluir jogo por vitória real; verificar journal, linha do jogo, série e ausência de atualização casual/MMR.
14. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Avançar vencedor da primeira rodada sem trocar elencos nem cores das equipes erroneamente.
15. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Concluir semifinais com jogos simultâneos e arenas distintas; conferir que uma arena não é alocada duas vezes.
16. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Concluir final; confirmar campeão e finalista somente após o último resultado legítimo.
17. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Finalizar campeonato e resgatar títulos; repetir os comandos e garantir que não há prêmio duplicado.
18. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Reiniciar backend/proxy durante inscrição, alocação e jogo; conferir retomada, journal pendente e estado `REVIEW` quando o resultado é incerto.
19. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Consultar menus de lista, equipe, chave paginada, partidas, histórico e prêmios depois de reiniciar; não retirar ícones dos menus.
20. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Rodar fila casual e ranqueada nas arenas próprias durante um torneio; conferir TAB, scoreboard, estatísticas, MMR, inventário e retorno ao lobby sem regressão.

## Falhas dirigidas

21. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Derrubar MySQL antes de um resultado, restaurar e confirmar aplicação única do journal.
22. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Desconectar um jogador antes da transferência; não atribuir walkover automático.
23. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Repetir reserva e resultado; garantir idempotência e revisão para dados conflitantes.
24. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Substituir integrante autorizado fora de série ativa; exigir confirmação do substituto. Recusar substituição durante jogo ou série em andamento.
25. **NÃO EXECUTADO — HOMOLOGAÇÃO MANUAL PENDENTE** — Pausar torneio, reiniciar servidor e retomar; nenhum jogo novo deve começar durante a pausa.
