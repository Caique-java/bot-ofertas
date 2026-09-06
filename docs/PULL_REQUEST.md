# Coleta de ofertas com fila persistente e validação antes de publicar

A versão anterior criava ofertas fictícias, enviava mensagens periódicas de status e registrava envio antes da confirmação do Telegram. Esta atualização consulta fontes configuradas, avalia preço/condições e revalida candidatos em uma fila PostgreSQL antes de preparar ou enviar mensagens.

Inclui conectores Mercado Livre e Amazon Creators API, deduplicação por produto/variante/vendedor/condições, expiração, limites por canal, tratamento de resultado incerto, configuração por variáveis de ambiente, migração Flyway, indicadores de saúde, Docker Compose e documentação. O início padrão é dry-run, com fontes desabilitadas. Os componentes demonstrativos e o endpoint público de injeção de ofertas foram removidos.

O workflow Verify executa Maven com JDK 25/PostgreSQL 17 e disponibiliza o JAR somente após sucesso. Não exige credenciais de lojas ou Telegram.

Validação disponível: 34 testes passaram no ambiente Java 25/ECJ/PGlite descrito em docs/VALIDACAO.md. A validação nativa do workflow e o uso com contas reais ainda precisam ser confirmados. A versão não inclui renovação OAuth do Mercado Livre, geração de links de afiliado ML nem atualização/remoção de posts Amazon antigos. Os segredos do commit original foram retirados dos arquivos atuais, mas precisam ser revogados porque permanecem no histórico.
