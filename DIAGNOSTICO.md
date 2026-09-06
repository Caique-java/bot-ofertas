# Diagnóstico e decisões

Base legada inspecionada: snapshot local correspondente ao commit `82fea41d024d1b22c942840a230ecfd069b29e7c`. O repositório público atual começou posteriormente com histórico limpo.

| Gravidade | Evidência confirmada na versão original | Impacto e correção |
|---|---|---|
| Crítica | `application.yml` continha token Telegram e senha literais | Removidos e externalizados. As credenciais foram substituídas e o repositório público atual foi criado sem o histórico antigo. |
| Alta | `PromocaoScheduler` criava smartphone fictício e URL exemplo a cada minuto | Removida oferta demonstrativa; produção coleta apenas fontes habilitadas. |
| Alta | `BotScheduler` enviava “bot online” a cada cinco minutos | Removido para impedir spam e envios fora da fila. |
| Alta | `PromocaoService` gravava dataEnvio antes do listener AFTER_COMMIT | Uma falha podia impedir futuras tentativas sem mensagem entregue. Substituído por fila transacional e confirmação com message_id. |
| Alta | `TelegramNotificationService` ocultava falhas e fazia fallback em qualquer erro | Timeout podia duplicar; 429 não era respeitado. Agora há classificação, cooldown e quarentena de resultados incertos. |
| Alta | `/api/teste/oferta` não autenticava nem validava entrada e anunciava sucesso antecipado | Removido. Prévia somente GET em dry-run; sem injeção pública. |
| Alta | `UrlNormalizer` eliminava toda a query; identidade dependia de URL | Diferentes produtos por parâmetro podiam colidir. Nova chave inclui fonte, ID, variante, vendedor, moeda e condições. |
| Alta | `AffiliateLinkConverter` acrescentava `?pref=` e validava por contains | Link poderia ser inválido ou destino indevido. Não gera afiliado ML; preserva link oficial e valida host. Amazon verifica tag retornada pela API. |
| Média | AmazonClient, MercadoLivreClient, MarketplaceClient e AppConfig eram vazios | Implementados contratos, configuração e adaptadores; permissões reais continuam externas. |
| Média | `ddl-auto=update`, sem fila/migração/limites de rede | Migração Flyway, SQL com unicidade e reserva, limites e estado operacional. |
| Média | Teste único `contextLoads` dependia do banco/configuração de produção | Suíte dedicada com fontes/Telegram simulados e banco de testes separado. |

## Riscos e limites, sem confundir com defeitos comprovados

- O token Telegram atual, o canal privado e a permissão de publicação foram validados sem incorporar a credencial ao repositório. Credenciais e permissões reais dos marketplaces ainda não foram homologadas.
- Algumas páginas oficiais do Mercado Livre devolveram HTTP 403 à consulta de documentação. O adaptador por ID tem testes de contrato sobre fixtures; o acesso e o formato efetivo para a sua aplicação precisam ser homologados.
- A licença Amazon limita armazenamento; não se pode assumir autorização para arquivar preços ou fotos em canais indefinidamente. O conector fica desabilitado por padrão, com cache limitado e envio de fotos Amazon excluído. Publicação efetiva exige confirmar o uso autorizado e eventual expiração das mensagens.
- Um único serviço é o modo de implantação suportado. A fila protege reservas concorrentes, mas não se declara coordenação distribuída completa da coleta.
- Não foi demonstrado que a versão original compilava: a primeira tentativa foi interrompida pela resolução de dependências no ambiente.

## Por que trocar a persistência dos eventos por SQL

Preservados Java 25, Spring Boot, Maven, PostgreSQL e os papéis de coleta, serviço e envio. O modelo de eventos AFTER_COMMIT não tinha armazenamento do trabalho pendente e precisava mudar para cumprir recuperação. `spring-boot-starter-jdbc` substitui JPA nesse fluxo pequeno para tornar explícitas as reservas, a unicidade parcial e as transações curtas; não foram adicionados broker, microsserviços ou serviço pago. A tabela legada é preservada para consulta e não tratada como confirmação de entrega.

Arquivos obsoletos removidos: `BotScheduler`, `TestController`, `OfertaAprovadaEvent`, `TelegramNotificationListener`, `PromocaoHistorico`, `PromocaoHistoricoRepository` e `UrlNormalizer`.

A versão atual inclui: clientes independentes, `OfferEvaluator`, `QueueRepository`, `PublicationWorker`, `MessageFormatter`, validação de links, prévia, saúde/contadores, migração, Docker/Compose e testes. Integrações automáticas de cupons, Pix, reputação, renovação OAuth ML e remoção de mensagens Amazon antigas permanecem pendentes de implementação/homologação.
