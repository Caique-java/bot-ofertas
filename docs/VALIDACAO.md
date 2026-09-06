# Resultados reais da validação

Executada em 04/09/2026 UTC. **34 testes, zero falhas, zero erros, zero testes ignorados. Maven verify concluiu com BUILD SUCCESS e gerou o JAR executável.**

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| BotOfertasApplicationTests | 1 | 0 | 0 | 0 |
| ConnectorTest | 2 | 0 | 0 | 0 |
| OfferRulesTest | 8 | 0 | 0 | 0 |
| PipelineTest | 2 | 0 | 0 | 0 |
| QueueIntegrationTest | 12 | 0 | 0 | 0 |
| SourceHttpTest | 2 | 0 | 0 | 0 |
| TelegramTest | 7 | 0 | 0 | 0 |

## Ambiente efetivamente utilizado

- Runtime Temurin Java 25.0.2.
- Compilação de código e testes para Java 25 com Eclipse ECJ 3.46.0, via um POM temporário de validação. O ambiente disponibilizava runtime sem javac; a configuração de compilador alternativo não foi incorporada ao POM de produção.
- Spring Boot 3.5.16, Maven Wrapper do projeto e dependências reais do Maven Central.
- PGlite, motor PostgreSQL 18.3 em WebAssembly, com conexão pelo driver JDBC PostgreSQL real, via servidor TCP local.
- Nos 34 testes finais, pool de uma conexão para compatibilidade com o servidor PGlite. O SQL da migração V1 foi executado antes da suíte e o inicializador Flyway foi desativado somente nesse processo de validação.
- Em uma execução separada, o Flyway real validou e aplicou V1 e o contexto Spring iniciou corretamente contra PGlite, usando bloqueio de sessão (`spring.flyway.postgresql.transactional-lock=false`) por limitação do adaptador de teste.

**Limite daquela evidência inicial:** a concorrência entre threads passou com serialização pelo pool de uma conexão. Os testes posteriores no PostgreSQL nativo do Windows e no GitHub Actions, registrados abaixo, ampliaram essa validação.

O POM de produção permanece com compilação Java 25 pelo compilador padrão do JDK. Não é necessário instalar PGlite ou ECJ para usar o projeto no seu computador; são adaptações exclusivas do ambiente de validação.

## Comportamentos exercitados

- Cálculo conservador de desconto, preço-alvo e distinção entre referência da loja e queda observada.
- Estoque, expiração, condições de cupom e consistência de parcelamento.
- Escape HTML e validação de destino/tag de afiliado.
- Parsing de variantes Mercado Livre e ofertas Amazon Creators API OffersV2.
- Respostas Telegram 429/500/403, timeout incerto, fallback restrito de imagem, dry-run e permissão de canal.
- Deduplicação persistente após recriação do repositório, republicação por queda real, reserva concorrente, fencing de trabalhadores antigos, recuperação de leases e quarentena de envio interrompido.
- Confirmação e message_id persistidos depois do retorno do Telegram, limite de retentativas e cooldown por canal.
- Expiração de candidatos e limpeza de cache Amazon.
- Fluxo completo de leitura de fixture de contrato pela coleta, avaliação por preço-alvo, fila, nova consulta de revalidação e PREVIEW, sem interação com Telegram.
- Isolamento de falha entre Amazon e Mercado Livre.

## Verificações não realizadas

- Autenticação ou coleta com contas reais Amazon/Mercado Livre.
- Coleta e revalidação com credenciais e ofertas reais do Mercado Livre/Amazon; o pipeline Java controlado até o canal real foi validado posteriormente.
- Renovação OAuth Mercado Livre, que continua pendente de implementação/homologação.
- Compose/Docker e limites sob carga em uma máquina de produção.
- Concorrência em PostgreSQL 17 nativo com múltiplas conexões.
- Implantação Docker e operação contínua em uma máquina de produção.

## Como repetir no ambiente definitivo

Crie um banco vazio exclusivo para testes e configure `TEST_DB_URL`, `TEST_DB_USER` e `TEST_DB_PASSWORD`. Use JDK 25 e execute:

```powershell
.\mvnw.cmd -B verify
```

Ou, em Linux/macOS:

```bash
bash mvnw -B verify
```

Os testes limpam suas tabelas nesse banco. As variáveis TEST_DB_* são separadas das variáveis de produção. No CI fornecido, o banco é criado como serviço isolado pelo workflow. Para a validação real dos marketplaces, mantenha dry-run ativo até confirmar as permissões e examinar as prévias.

## Preparação inicial para o GitHub — 04/09/2026

O código Java, a migração e os testes Java não foram alterados nessa etapa. Foram conferidos a estrutura do workflow, o isolamento das fontes no CI, o nome do JAR e a ausência do formato de token Telegram nos arquivos preparados.

Os auxiliares usados somente para transportar a atualização até o projeto local foram removidos depois da criação do repositório limpo. O workflow foi executado e aprovado posteriormente, conforme o registro ao final deste documento.

## Validação manual posterior no Windows - encerramento de 04/09/2026

Evidência: logs e saídas de comando enviados pelo proprietário nesta conversa.

- IntelliJ IDEA 2025.3.2, JDK 25.0.2 e PostgreSQL 18.4 nativo: aplicação iniciada com conexão JDBC/Hikari.
- Banco novo bot_ofertas_v2, usuário de aplicação com diferenciação de maiúsculas; Flyway aplicou V1 e depois confirmou o schema atualizado.
- Endpoint /actuator/health retornou UP. Mercado Livre e Amazon continuavam desabilitados e BOT_DRY_RUN=true.
- Mantido como pendência o aviso de compatibilidade entre Flyway 11.7.2 e PostgreSQL 18.4. A execução bem-sucedida não elimina a necessidade de homologação.
- O verificador PowerShell inicial falhou sem resposta HTTP. TCP/HTTPS funcionaram por IPv4; a versão 2 usando curl/IPv4 confirmou token, canal privado e permissão de publicação.
- O usuário executou um teste manual de envio. A API Telegram confirmou a mensagem 36 no canal privado. Não houve envio durante a validação local dos scripts pelo assistente.

**Alcance:** o envio confirmado foi feito pelo script PowerShell auxiliar; não atravessou a fila, o revalidador e o worker Java. A coleta real Mercado Livre/Amazon, a publicação completa pelo Java, os testes automatizados no Windows e a operação contínua ainda estão pendentes.

O pacote de encerramento inclui esses auxiliares em uma pasta de apoio local separada do código publicável. O código Java não mudou nesta etapa, portanto os 34 testes anteriores não foram repetidos para essas mudanças de documentação. Foram conferidos os relatórios existentes, o conteúdo do pacote e seus hashes.

## Teste manual do pipeline Java no canal privado

`LiveTelegramPipelineIT` valida o caminho Java -> PostgreSQL -> fila -> revalidação -> Telegram e registra o `message_id`. Ele não é executado pelo build normal, não cria endpoint HTTP e não depende de Mercado Livre ou Amazon. A oferta é identificada claramente como teste e o botão aponta apenas para a página inicial do Mercado Livre.

Proteções do teste:

- exige `RUN_LIVE_TELEGRAM_PIPELINE_TEST=CONFIRMO_UM_ENVIO_NO_CANAL_PRIVADO`;
- confere a mesma confirmação novamente dentro do método, mesmo se o IntelliJ desativar condições do JUnit;
- usa `BOT_DRY_RUN=false` somente dentro do contexto do teste;
- desabilita o agendador e recusa executar se houver qualquer trabalho live ativo no canal;
- usa identidade fixa: uma repetição dentro do intervalo de republicação confirma a deduplicação sem enviar outra mensagem;
- não contém token, senha ou ID de canal no código.

No IntelliJ, abra `src/test/java/com/ofertas/bot/LiveTelegramPipelineIT.java`, clique no triângulo ao lado da classe e escolha **Modify Run Configuration**. Copie para essa configuração as variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TELEGRAM_TOKEN` e `TELEGRAM_CHAT_ID` já usadas pelo aplicativo. Acrescente a variável de confirmação acima e execute somente `LiveTelegramPipelineIT`.

Sucesso esperado: o teste fica verde, o canal recebe uma única mensagem `[TESTE CONTROLADO]` e o banco registra `status=SENT` com `message_id`. Depois, remova a variável `RUN_LIVE_TELEGRAM_PIPELINE_TEST` da configuração do teste. Nunca publique essa variável como segredo ou configuração permanente.

## Resultado do teste manual do pipeline - 06/09/2026

Execução confirmada pelo proprietário em Windows, IntelliJ IDEA 2025.3.2, Java 25.0.2 e PostgreSQL 18.4:

- Hikari abriu a conexão com `bot_ofertas_v2`;
- Flyway validou a migração V1 e confirmou o schema atualizado;
- o worker Java revalidou a oferta controlada e chamou o Telegram;
- o Telegram confirmou a publicação e o banco registrou `message_id=37`;
- a asserção de deduplicação passou e o processo terminou com `exit code 0`.

O comando gerado pelo IntelliJ continha uma opção que desativava condições `@Enabled...` do JUnit. A confirmação estava corretamente configurada nesta execução, portanto isso não causou envio indevido. Como reforço permanente, o teste passou a conferir a frase de confirmação também dentro do método. A variável deve ser removida depois do teste e o teste não deve ser repetido.

**Alcance atualizado:** o caminho controlado Java -> banco -> fila -> revalidação -> Telegram foi homologado no canal privado. Ainda falta homologar a coleta e revalidação usando credenciais e dados reais das fontes.

## Suíte Maven no Windows - 06/09/2026

O proprietário criou o banco isolado `bot_ofertas_test`, pertencente ao usuário restrito `bot_test`, confirmou a conexão e executou `mvnw.cmd -B verify` com Java 25.0.2 e PostgreSQL 18.4. O comando terminou com sucesso e os 34 testes automatizados passaram.

As variáveis `TEST_DB_*` apontaram somente para o banco de testes; `bot_ofertas_v2` não foi usado pela suíte. A senha foi lida de forma oculta e removida do ambiente do PowerShell ao final. `LiveTelegramPipelineIT` não faz parte da seleção normal do Surefire, portanto essa execução não enviou outra mensagem ao Telegram.

Com isso, ficaram validados no Windows nativo: compilação, contexto Spring, migração Flyway, regras de oferta, conectores simulados, concorrência da fila em PostgreSQL, tratamento do Telegram simulado e empacotamento Maven. Permanecem fora desse alcance as APIs reais dos marketplaces, carga de produção, Docker e operação contínua.

## Publicação limpa e GitHub Actions - 06/09/2026

O proprietário excluiu o repositório remoto antigo e criou `Caique-java/bot-ofertas` novamente como repositório privado vazio. A versão consolidada foi inicializada localmente com um único commit raiz (`3d9fb52`), após uma conferência que informou 57 arquivos preparados, zero arquivos privados incluídos e zero tokens Telegram encontrados.

O push da branch `main` foi concluído. O workflow `Verify` associado ao commit `3d9fb52` terminou com sucesso em aproximadamente 1 minuto e 15 segundos. Assim, o POM padrão, JDK 25, PostgreSQL 17, os 34 testes e a criação dos artefatos foram exercitados no GitHub Actions sem credenciais de Telegram ou marketplaces.

O repositório foi mantido privado durante a validação, recebeu depois o commit documental `ea3f4e8` e teve uma segunda execução verde do workflow. Após a conferência de segurança, foi tornado público. O aplicativo não foi implantado pelo Actions; o workflow termina depois de testar e empacotar.
