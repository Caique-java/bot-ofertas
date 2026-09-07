# Bot de ofertas: resumo para vídeo e entrevista

Registro atualizado em 07/09/2026. Versão de trabalho 1.0.0, em integração e testes.

## O que é
Um backend Java para monitorar produtos em fontes autorizadas, avaliar se atendem a critérios de oferta e preparar ou publicar mensagens no Telegram. A meta é operação contínua; o serviço só deve publicar quando houver uma oferta elegível.

O código possui conectores Mercado Livre e Amazon. O Mercado Livre consulta IDs de anúncios configurados; a Amazon aceita ASINs e buscas limitadas por palavras-chave. Não existe descoberta garantida de todas as promoções nem varredura de todo o catálogo. As integrações reais ainda dependem das contas e permissões.

## Como foi criado
Evolução de um projeto Java existente, com apoio de IA para revisão, implementação, testes e documentação. O desenvolvimento usa IntelliJ e Maven; Git registra as versões. Na sessão, a configuração local e os testes manuais foram conduzidos passo a passo pelo proprietário.

A estrutura separa clients (APIs externas), services (regras e publicação), repository (SQL), scheduler (tarefas periódicas), model (dados), controller (consulta de prévias) e config (parâmetros).

## Tecnologias e função
| Tecnologia | Para que serve neste projeto |
|---|---|
| Java 25 | Linguagem e execução do backend. |
| Spring Boot 3.5.16 | Inicialização, organização dos componentes e servidor HTTP. |
| Maven | Dependências, compilação, testes e empacotamento em JAR. |
| PostgreSQL + JDBC | Persistência relacional acessada por SQL. Esta versão usa JDBC, não JPA/Hibernate. |
| Flyway | Migrações SQL versionadas para criar/evoluir as tabelas. |
| APIs HTTP + JSON | Comunicação com marketplaces e Telegram. |
| Actuator e logs | Saúde da aplicação e diagnóstico. |
| Git/GitHub | Versionamento, revisão pública do código e testes automáticos no CI. |
| Docker Compose | Execução reproduzível da aplicação e do PostgreSQL, com volume persistente; hospedagem ainda pendente. |

## Fluxo que o código implementa
1. O agendador consulta os produtos configurados nas fontes habilitadas.
2. Os conectores normalizam os dados. As regras conferem preço-alvo, referências permitidas, estoque, validade e condições.
3. As ofertas aprovadas entram na fila persistente, com prioridade, prazo e identificação para reduzir duplicidades.
4. O trabalhador reserva uma oferta e consulta novamente a fonte antes de publicar. Em dry-run gera PREVIEW; em envio real autorizado chama o Telegram e só registra SENT após uma confirmação válida.

Se uma resposta de envio se perder, o registro pode ficar UNCERTAIN: a aplicação evita repetir automaticamente algo que talvez já tenha chegado. Não há garantia de entrega exatamente uma vez.

## Banco de dados
No ambiente nativo de testes: PostgreSQL 18.4, banco `bot_ofertas_v2`. Houve aviso de compatibilidade do Flyway com PostgreSQL 18; mesmo assim, a migração V1 e a inicialização funcionaram. O ambiente Docker foi validado separadamente com PostgreSQL 17.11, versão alinhada ao pacote e ao CI.

| Tabela | Papel |
|---|---|
| offer_queue | Ofertas, estados, prioridade, expiração e identificação da mensagem. |
| publication_attempt | Tentativas de publicação e seus resultados. |
| source_state | Agenda, sucesso e falhas das fontes. |
| channel_state | Controle do intervalo de envio por canal. |
| price_observation | Histórico de preços somente quando o armazenamento estiver autorizado; Amazon não alimenta histórico longitudinal. |
| bot_guard | Coordenação transacional de operações da fila. |
| flyway_schema_history | Controle das migrações aplicadas pelo Flyway. |

Preços usam BigDecimal no Java e numeric no SQL para manter precisão decimal. Chaves, índices e transações ajudam a manter os registros consistentes. A fila está no PostgreSQL; não usamos RabbitMQ ou Kafka.

## Melhorias sobre a versão inicial
- Remoção de produtos fictícios e mensagens periódicas demonstrativas no fluxo de produção.
- Credenciais retiradas do código entregue; configuração por variáveis de ambiente.
- Remoção do endpoint público que permitia disparar conteúdo ao canal.
- Fila persistente, deduplicação por produto/variante/vendedor/condições e confirmação do envio.
- Revalidação de preço e estoque, tratamento de limites e falhas das APIs, prévias e testes.

## Estado demonstrado e pendências
- Concluído: execução no IntelliJ, conexão JDBC, migração V1 e endpoint de saúde com status UP.
- Concluído: token, canal privado e permissão de publicação verificados; Telegram confirmou a mensagem 36 enviada pelo script auxiliar.
- Concluído: pipeline controlado Java -> PostgreSQL -> fila -> revalidação -> Telegram, com `message_id=37`, uma tentativa e deduplicação aprovada.
- Testes: 34 testes automatizados aprovados no ambiente inicial, no Windows com PostgreSQL nativo e no GitHub Actions com JDK 25/PostgreSQL 17.
- Docker: imagem construída e serviços iniciados; saúde `UP`, Flyway V1, sete tabelas, reinício gracioso e persistência após recriar os contêineres.
- Recuperação: backup validado e restaurado em banco temporário com as sete tabelas e a versão 1 do Flyway.
- GitHub: repositório antigo excluído, histórico limpo publicado e três execuções do workflow `Verify` aprovadas. Isso valida CI, mas não é implantação 24h.
- Pendente: criação/autorização da aplicação Mercado Livre, coleta real de ambas as fontes, links de afiliado ML, renovação automática do token ML e operação 24h.

O ID de afiliado não substitui o token usado pelo conector. Os links ML atuais são links comuns: a integração de links com comissão ainda precisa ser concluída. Não anunciar cupons, Pix, parcelamento ou frete quando essas condições não vierem de uma fonte autorizada e verificada.

## Uma fala de aproximadamente um minuto
Estou evoluindo um bot de ofertas em Java com Spring Boot, com apoio de ferramentas de IA na revisão e implementação. O objetivo é consultar fontes autorizadas do Mercado Livre e da Amazon, avaliar preço e disponibilidade e publicar ofertas no Telegram. Usei PostgreSQL para persistir a fila e as tentativas de envio, e Flyway para versionar a estrutura do banco. O projeto tem modo de simulação, controle de duplicidade e tratamento de falhas. Validei no canal privado o fluxo completo do Java, desde a gravação no banco até a confirmação do Telegram. Os 34 testes passaram no Windows e também no GitHub Actions. Depois, validei a execução com Docker Compose, incluindo reinício, persistência do volume e restauração de backup. Agora faltam as autorizações reais dos marketplaces e a infraestrutura para funcionamento contínuo.

## Respostas curtas para perguntas comuns
- Por que banco? Para manter fila e resultados após reinícios e reduzir perda de trabalho.
- Por que consultar de novo? Preço e estoque podem mudar entre a descoberta e a publicação.
- O que é dry-run? Executar a validação e produzir prévias sem publicar pelo fluxo Java.
- Como evita duplicidade? Identidade estável da oferta, índice único para trabalhos ativos e regras de republicação; falhas ambíguas ficam em análise.
- Como funciona 24h? Um processo em uma máquina/servidor ligado, com internet, reinício e monitoramento, banco persistente e credenciais válidas. GitHub guarda/testa o código; este workflow não hospeda o bot continuamente.
- Já está pronto? A base, os testes e a recuperação do ambiente Docker local estão validados; APIs reais e operação contínua externa ainda estão pendentes.

Para entrevista, apresente o que você consegue explicar e demonstrar. O roteiro descreve um projeto em evolução e reconhece o apoio de IA, sem alegar operação em produção ou autoria integral sem auxílio.
