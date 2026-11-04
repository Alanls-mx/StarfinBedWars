# Registro de defeitos e bloqueadores — Fase 7.3

O registro separa falhas de preparação do laboratório de defeitos comprovados do produto. Não há defeito novo de gameplay comprovado sem cliente nesta fase. Prioridades: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.

| ID | Gravidade | Sistema | Passos e resultado observado | Causa | Correção / estado | Testes afetados |
| --- | --- | --- | --- | --- | --- | --- |
| H73-LAB-01 | MEDIUM | Proxy de laboratório | Iniciar BungeeCord com Java 8: processo encerra com `UnsupportedClassVersionError`, bytecode 55. | A JAR Bungee 26.1 disponível exige Java 11+. | Gerador/Compose usam Java 17 apenas no proxy; `PASS` no status ping. Backends permanecem Java 8. | A-06, A-07 |
| H73-LAB-02 | MEDIUM | MySQL de laboratório | Com `sslMode=DISABLED`, o usuário MySQL 8 `caching_sha2_password` recusa autenticação sem conexão segura; Social/Minigames mostram catálogo indisponível. | Transporte incompatível com política do usuário de teste. | `PREFERRED` no proxy/Login/Social; `REQUIRED` no BedWars. Migrações e catálogo confirmados. | A-02, A-07, A-08, A-09 |
| H73-LAB-03 | LOW | EssentialsX no laboratório | Consulta de versão externa falha com `UnknownHostException`. | Rede backend é interna por segurança. | Mantido como aviso; Vault e Essentials Economy carregaram. | A-01, compras manuais pendentes |
| H73-BLOCK-01 | HIGH | Arenas de teste | Só existe arena real Solo `1` em `casual`; faltam Dupla/Trio/Quarteto, `ranked` e `tournament`. | Configuração de mundos reais exige montagem no Minecraft. | `BLOCKED` para D/E/F; criar arenas próprias pelo assistente e validar capacidade antes de habilitar filas. | Ranqueadas, temporadas, 25 torneios |
| H73-BLOCK-02 | MEDIUM | StarfinCore no laboratório | Log mostra autorização remota pendente. | Serviço de licenciamento não confirmou esta instância isolada. | Registrar no teste visual; não contornar licença nem tratar recursos dependentes como aprovados. | Funcionalidades licenciadas, se aplicáveis |

Formato para novas falhas: ID; gravidade; sistema; ambiente/JAR/SHA; passos; esperado; observado; evidência; causa; correção; testes afetados; commit; estado; nova execução. Falhas de corrupção, cobrança duplicada, acesso indevido, resultado falso ou perda de journal devem bloquear homologação até correção e reexecução.
