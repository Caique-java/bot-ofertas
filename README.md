# Bot de ofertas — Mercado Livre, Amazon e Telegram

Serviço Java 25 / Spring Boot 3.5.16 / Maven / PostgreSQL para consultar fontes autorizadas, avaliar ofertas e preparar ou publicar mensagens no Telegram. **Inicia em dry-run, com ambas as fontes desabilitadas.** Não existem produtos fictícios nem mensagens periódicas de “bot online” no fluxo de produção.

Leia [DIAGNOSTICO.md](DIAGNOSTICO.md), [docs/INTEGRACOES.md](docs/INTEGRACOES.md) e [docs/VALIDACAO.md](docs/VALIDACAO.md) antes de habilitar publicações. Integrações reais dependem de credenciais, permissões e quotas da sua conta. A coleta pode ficar sem ofertas; funcionamento contínuo não garante disponibilidade absoluta ou descoberta de todo o catálogo.

**Projeto no GitHub:** o workflow `Verify` testa com JDK 25 e PostgreSQL 17 e, quando passa, disponibiliza o JAR em `bot-ofertas-jar`. O GitHub guarda e verifica o projeto; a execução contínua do bot precisa de um computador ou servidor ligado. Veja [como trabalhar com o repositório](docs/GITHUB.md) e [o que está pronto e o que depende das suas contas](docs/PROXIMOS_PASSOS.md).

## Segurança da configuração

Este repositório público começou com um histórico limpo e não contém credenciais reais. Tokens e senhas são fornecidos por variáveis de ambiente ou por arquivos locais ignorados pelo Git. O workflow usa somente uma senha descartável no banco temporário de testes e não acessa Telegram nem marketplaces reais.

O endpoint público `/api/teste/oferta` foi removido, pois permitia a qualquer cliente encaminhar conteúdo ao canal. O GET `/api/preview` existe somente em dry-run e apenas lê ofertas já coletadas.

## Executar com Docker Compose

Requisitos: Docker com Compose v2 e acesso à internet para baixar imagens/dependências. As imagens usam JDK/JRE 25 e PostgreSQL 17. Os componentes não exigem serviço pago; a máquina e eventuais custos de hospedagem ficam por sua conta. O contêiner não é implantado automaticamente por este projeto.

1. Extraia o pacote ou clone o repositório e entre na pasta do projeto.
2. Copie `.env.example` para `.env` e `config/monitoramento.example.yml` para `config/monitoramento.yml`.
3. Defina uma senha nova em `DB_PASSWORD`. Não compartilhe o `.env`.
4. Configure IDs reais e preços-alvo em `config/monitoramento.yml`. IDs de exemplos comentados são apenas instruções; substitua-os.
5. Preencha as credenciais de uma fonte autorizada e habilite somente essa fonte. Mantenha `BOT_DRY_RUN=true`.
6. Inicie:

```bash
docker compose up -d --build
docker compose logs --tail=100 -f bot
```

A porta de administração fica publicada apenas no computador local:

- `http://localhost:8080/actuator/health`: saúde do banco e da coleta. Fontes habilitadas sem coleta válida aparecem como indisponíveis.
- `http://localhost:8080/api/preview`: JSON com HTML da mensagem e URL de compra, apenas em dry-run. A fila ainda revalida a fonte antes de marcar PREVIEW.

A primeira coleta ocorre após alguns segundos. Sem credenciais ou produtos configurados, não haverá mensagem. `ML_ENABLED=false` e `AMAZON_ENABLED=false` desligam consultas das respectivas fontes. Os exemplos não inventam promoções para “mostrar funcionamento”.

Para ver detalhes de estado localmente, adicione `HEALTH_DETAILS=always` ao `.env` e recrie o serviço. Não exponha essa porta publicamente. As métricas `bot.offers` e `bot.publications` são registradas pelo Micrometer; a exposição HTTP de `/actuator/metrics` não está habilitada por padrão.

## Configurar produtos e critérios

```yaml
bot:
  filters:
    min-price: 20
    max-price: 3000
    min-discount-percent: 10
    allow-store-reference: false
    allowed-sellers: []
    blocked-sellers: []
    excluded-categories: []
    excluded-terms: [usado, recondicionado]
  mercado-livre:
    products:
      - id: MLB_ID_REAL
        variant: "ID_EXATO_SE_HOUVER_VARIACOES"
        seller: "ID_DO_VENDEDOR"
        target-price: 199.90
  amazon:
    products: []
    keywords: []
    browse-node-id: ""
```

Não copie `MLB_ID_REAL` como produto: é um marcador a substituir. ASINs têm 10 caracteres alfanuméricos. O ID de vendedor é o identificador da API, não o nome de exibição. As listas globais de vendedores aplicam-se às duas fontes; use os IDs correspondentes.

Uma oferta pode ser aprovada por preço-alvo atingido, queda observada comparável ou percentual sobre referência da loja **somente quando `allow-store-reference=true`**. Referência da loja fica identificada na mensagem e nunca é apresentada como queda historicamente comprovada. Sem um desses critérios, o produto é rejeitado. Nas buscas Amazon por palavras-chave, configure a aceitação explícita da referência da loja se desejar esse critério; a Amazon não alimenta histórico longitudinal.

Histórico Mercado Livre é opcional (`ML_RETAIN_HISTORY=true`) e depende de autorização para armazenar dados. Usa o menor preço observado no período de 30 dias, para o mesmo produto, variante, vendedor, moeda e condições. O preço da coleta atual não vira retroativamente a referência anterior. O histórico da versão antiga não é importado: sua identidade dependia de URLs incompletas e a data não confirmava uma publicação.

`repost-after` controla o intervalo mínimo para republicar preço inalterado; `repost-drop-percent` permite antecipar uma republicação por queda relevante. Cada registro guarda a razão da aprovação e da republicação. Cupons e condições fazem parte da identidade; mudanças de condição não são anunciadas automaticamente como “melhoria”. Não há regra de republicação por retorno ao estoque nesta versão.

## Habilitar Telegram após conferir as prévias

1. Crie/gerencie seu bot com o **BotFather** e gere um token novo.
2. Adicione o bot como administrador do seu canal com permissão para publicar mensagens.
3. Preencha `TELEGRAM_TOKEN` e o ID numérico `TELEGRAM_CHAT_ID` do canal (formato `-100...`). Esta versão não aceita nomes `@canal` como identidade de fila.
4. Confirme que a publicação dos dados e links nesse canal é autorizada pelo programa da fonte. Configure `ML_PUBLICATION_AUTHORIZED=true` e/ou `AMAZON_PUBLICATION_AUTHORIZED=true` somente para as fontes autorizadas. Veja as limitações específicas da Amazon na documentação.
5. Defina `BOT_DRY_RUN=false` e execute `docker compose up -d --force-recreate bot`.

A aplicação verifica `getMe`, `getChat` e `getChatMember` antes da publicação e repete a validação periodicamente. O teste manual posterior via PowerShell foi confirmado pelo usuário; veja docs/VALIDACAO.md. O fluxo Java completo com fontes reais ainda precisa ser validado. Os registros PREVIEW não são consumidos por uma execução live; a nova coleta cria candidatos live.

Para pausar apenas publicações, defina `BOT_PAUSED=true` e recrie o bot. A coleta continua, e ofertas vencidas serão descartadas. Para interromper tudo, use `docker compose stop bot`. Não há comandos administrativos públicos ou endpoint de injeção de ofertas.

## Fila, falhas e reinício

A fila é persistida no PostgreSQL. As transações são curtas; chamadas externas ficam fora dos bloqueios do banco.

- `PENDING`: aguardando prioridade, prazo ou nova tentativa.
- `CHECKING`: reservado por um trabalhador para revalidar preço e disponibilidade.
- `SENDING`: tentativa registrada imediatamente antes do envio.
- `SENT`: resposta válida com `message_id`, persistida junto do resultado da tentativa.
- `PREVIEW`: validado em dry-run, sem envio.
- `FAILED` / `EXPIRED`: erro definitivo/limite de tentativas ou dados vencidos.
- `UNCERTAIN`: o envio pode ter sido recebido; não há reenvio automático.

Se o processo cair durante CHECKING, a reserva expira e pode ser retomada com um novo identificador. Se cair durante SENDING, o registro fica UNCERTAIN. Uma resposta perdida do Telegram também fica UNCERTAIN. **Não existe garantia de entrega exatamente uma vez.** A política conservadora evita duplicação automática, mas pode deixar uma oferta sem publicar.

Para uma ocorrência UNCERTAIN, confira o canal e o `job_id` no banco. Após confirmar que NÃO houve envio, um operador pode mudar esse registro para FAILED usando uma sessão administrativa de banco; o sistema respeitará o intervalo de republicação. Se houve envio, registre o `message_id` confirmado e SENT. Não exclua registros incertos às cegas.

A fila tem limite padrão de 500 candidatos ativos. Prioriza descontos maiores e desempata por antiguidade. O limite padrão por canal é 20 **tentativas** por hora e 30 segundos entre mensagens. Retentativas contam nesse limite. Um 429 aplica `retry_after` ao canal inteiro. Somente uma rejeição definitiva de imagem permite fallback para texto; um timeout de imagem não dispara um segundo envio.

A implementação verifica concorrência na reserva da fila e usa identificadores de reserva para bloquear trabalhadores antigos. **Implantação suportada: uma instância do serviço.** O dimensionamento horizontal dos coletores e das quotas de conta ainda exige coordenação de liderança. Não use `--scale bot=2`. Reinícios da instância preservam a fila e a deduplicação.

## Retenção e limites

- Dados de anúncios Amazon na fila (inclusive preços e links) são limpos depois de 23 horas, com rotina de limpeza a cada 10 minutos; não são mantidos como histórico de preços. Imagens Amazon não são baixadas nem reenviadas como foto.
- Metadados operacionais e histórico autorizado do Mercado Livre têm retenção de 30 dias. Registros UNCERTAIN preservam a identidade de deduplicação até intervenção.
- Logs possuem rotação; não registram tokens, respostas de autenticação nem texto integral das ofertas Amazon.
- A limpeza do banco local **não remove mensagens já publicadas no Telegram**. Confirme a autorização para manter os dados no canal; a aplicação não implementa atualização/remoção automática de mensagens Amazon antigas. Se a autorização exigir isso, mantenha Amazon em dry-run até adaptar essa parte.
- Os conectores atuais não verificam cupons, Pix, parcelamento, reputação de vendedor ou frete por CEP. Esses campos não são inventados. O modelo e os filtros tratam condições explícitas, mas só devem ser preenchidos por uma fonte que realmente as comprove.

## Executar no IntelliJ / Windows

Use JDK 25 como Project SDK e Maven Runner JRE. Não é necessário instalar Maven separado, pois há `mvnw.cmd`.

1. Inicie PostgreSQL 17 e crie um banco vazio e um usuário exclusivo para o bot.
2. Defina `DB_URL`, `DB_USER`, `DB_PASSWORD` e demais variáveis nas configurações de execução do IntelliJ.
3. Copie o arquivo de monitoramento e mantenha o diretório de trabalho na raiz do projeto.
4. Execute `com.ofertas.bot.BotOfertasApplication`.

```powershell
.\mvnw.cmd -B verify
.\mvnw.cmd spring-boot:run
```

O `.env` é lido pelo Docker Compose; o Spring executado diretamente não o carrega automaticamente. Configure as variáveis no IntelliJ ou no terminal. Linux/macOS: `bash mvnw -B verify` e `bash mvnw spring-boot:run`.

Desligar, suspender ou perder a conexão do computador interrompe o monitoramento. Para operação contínua, a máquina precisa permanecer ligada. Compose reinicia contêineres em falhas, mas não resolve falta de energia, rede ou permissões expiradas.

## Testes

A suíte tem testes de regras, parsing dos conectores, Telegram simulado, concorrência, migrações e persistência em PostgreSQL real. Os testes de banco exigem um **banco exclusivo para testes**: fazem TRUNCATE das tabelas da aplicação. Usam `TEST_DB_URL`, `TEST_DB_USER` e `TEST_DB_PASSWORD`, separados das variáveis de produção. Nunca aponte essas variáveis para produção.

```bash
# Com TEST_DB_URL, TEST_DB_USER e TEST_DB_PASSWORD apontando para um banco de testes vazio:
bash mvnw -B verify
```

O workflow `.github/workflows/verify.yml` prepara Java 25 e PostgreSQL 17 e executa a suíte sem tokens. Não utiliza canal real. Veja [docs/VALIDACAO.md](docs/VALIDACAO.md) para os resultados efetivamente obtidos nesta revisão.

## Backup, atualização e recuperação

Backup consistente no próprio contêiner, sem expor a senha na linha de comando:

```bash
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/bot.dump'
docker compose cp db:/tmp/bot.dump ./bot.dump
```

Guarde o arquivo em local protegido. A cópia pode conter cache recente Amazon; retenha esse conteúdo somente nos limites autorizados e não restaure cache antigo para publicação. Para backups duradouros, exclua o conteúdo transitório com `--exclude-table-data=offer_queue --exclude-table-data=publication_attempt`; essa opção perde a fila/deduplicação daquele backup e exige conferência do canal ao restaurar. Não restaure uma fila antiga diretamente com envio habilitado.

Restaure em **outro banco vazio**, com bot parado e dry-run ativado:

```bash
docker compose cp ./bot.dump db:/tmp/bot.dump
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner /tmp/bot.dump'
```

O comando pressupõe que `POSTGRES_DB` é o banco vazio de recuperação, não o banco que já está em uso. Faça uma restauração de teste e confira registros incertos antes de reativar envio.

Antes de atualizar: pause o bot, faça backup e registre o commit/imagem anterior. Atualize os arquivos e execute `docker compose up -d --build`. O Flyway aplica migrações versionadas. Para adotar um banco antigo que já contém `promocao_historico`, após backup use `DB_BASELINE=true` **uma vez**; baseline versão 0 permite executar V1 sem alterar a tabela antiga. Remova essa opção depois. Um banco novo não precisa dela.

Para rollback: volte à imagem anterior somente se ela for compatível com o esquema atual. Caso contrário, restaure o backup em outro banco e valide em dry-run. Não use `docker compose down -v` como atualização: isso apaga o volume do banco.

## Versionamento no GitHub

O [guia do GitHub](docs/GITHUB.md) mostra como clonar o projeto, manter configurações privadas fora dos commits, revisar mudanças e executar o workflow. Para alterações futuras, crie uma branch, confira o diff, faça commit e abra um pull request.

## Estado validado em 06/09/2026

O pipeline controlado completo do Java foi aprovado no canal privado: oferta de teste -> PostgreSQL -> fila -> revalidação -> Telegram, com `message_id=37` e deduplicação confirmada. A suíte de 34 testes também passou no Windows usando um banco exclusivo de testes.

O repositório atual foi criado com histórico limpo, passou duas vezes no GitHub Actions e foi tornado público após a conferência de segurança. As integrações reais de marketplace e a operação 24 horas continuam pendentes; `BOT_DRY_RUN=true`, `ML_ENABLED=false` e `AMAZON_ENABLED=false` permanecem como configuração segura inicial.

Para apresentar o trabalho em vídeo ou entrevista, consulte [o resumo do projeto](docs/RESUMO_ENTREVISTA.md). Os detalhes de evidência e limites estão em [VALIDACAO.md](docs/VALIDACAO.md).
