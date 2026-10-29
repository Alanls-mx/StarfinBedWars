# Fase 6.2: admissão competitiva no BedWars

O proxy StarfinMinigames coordena filas e reservas; o BedWars permanece a autoridade da partida e do rating. Consulte o documento [do StarfinMinigames](../StarfinMinigames/RANKED_PHASE_6_2.md) para comandos, filas e algoritmo. O StarfinSocial não precisou mudar: a interface existente fornece liderança, revisão e membros da Party.

## Contrato de admissão

`sbw_ranked_policy` publica contexto e rating inicial com o heartbeat da instância. O proxy lê esses dados e só reserva uma arena habilitada, livre e do grupo permitido. As reservas `sm_network_reservations` ganharam `ranked_allocation_id` e `competitive_context`, preservando as reservas casuais antigas. O BedWars consulta `sm_ranked_allocations` e membros no MySQL antes de admitir cada UUID, verifica reserva, servidor, instância, arena, modalidade, contexto, equipe, elenco e validade. Cada chegada é marcada; só depois do elenco completo o StarfinMinigames Spigot invoca `RankedApi.authorize(GameMatch, Plugin)`.

Uma arena do grupo ranqueado recusa reserva casual e não inicia contagem antes dessa autorização. A autorização exige pelo menos duas equipes, elenco inteiro conectado, prova de alocação e o emissor `StarfinMinigames` listado em `ranked.yml`. Sem todas as chegadas até a validade da alocação, a formação é cancelada sem resultado competitivo. Depois da abertura, o ReconnectService mantém equipe e dados da partida. Kits e habilidades continuam desabilitados no competitivo pelos padrões de `ranked.yml`.

## Resultados e penalidades

O snapshot final usa o mesmo motor ELO/MMR da Fase 6.1. O journal local `ranked-pending/*.properties` agora tem um executor dedicado à escrita com `force(true)` antes de publicar o arquivo por rename atômico quando o sistema de arquivos permite. O worker MySQL processa esses arquivos depois, com chave `match_id` idempotente. O desligamento controlado espera as escritas pendentes antes de fechar o repositório. Uma queda abrupta entre o evento final e a gravação pelo executor ainda pode perder esse resultado; não há garantia de recuperação de uma partida ativa após crash. Não apague o diretório pendente antes da confirmação no banco.

`sbw_ranked_abandons` registra cada abandono definitivo por partida/jogador. `sbw_ranked_suspensions` guarda suspensão temporal por jogador/contexto quando há, por padrão, três abandonos em 24 horas, durante 30 minutos. O registro e o rating são confirmados na mesma transação; repetir o mesmo match não soma punição. O proxy consulta a suspensão antes da entrada. Cancelamento de fila ou falha pré-partida não gera abandono. As configurações estão em `ranked.yml/penalties.suspensions`.

## Configuração e migração

As chaves novas do arquivo são `authorized-plugins: [StarfinMinigames]`, `arenas.allowed-groups: [ranked]` e `penalties.suspensions.*`. `enabled` permanece `false` por padrão. Instalações com `ranked.yml` antigo precisam incluir essas chaves manualmente; o plugin não sobrescreve o arquivo do operador. `stats.yml` e `network.yml` devem apontar ao mesmo MySQL e publicar o mesmo `server-id` usado no proxy. Inicie StarfinCore e StarfinMinigames Spigot antes do BedWars.

Foram testados `mvn -B clean verify`, validação de admissão e processamento idempotente em schema MariaDB descartável, além de regressão casual. **PENDENTE DE HOMOLOGAÇÃO MANUAL FINAL:** partida real Solo/Dupla, rotação de mundos, reconexão dentro e fora do prazo, abandono, falha do destino e queda de MySQL. A ativação nos servidores fica para essa etapa.
