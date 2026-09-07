# Coleta de ofertas com fila persistente e validação antes de publicar

A versão anterior criava ofertas fictícias, enviava mensagens periódicas de status e registrava envio antes da confirmação do Telegram. Esta atualização consulta fontes configuradas, avalia preço/condições e revalida candidatos em uma fila PostgreSQL antes de preparar ou enviar mensagens.

Inclui conectores Mercado Livre e Amazon Creators API, deduplicação por produto/variante/vendedor/condições, expiração, limites por canal, tratamento de resultado incerto, configuração por variáveis de ambiente, migração Flyway, indicadores de saúde, Docker Compose e documentação. O início padrão é dry-run, com fontes desabilitadas. Os componentes demonstrativos e o endpoint público de injeção de ofertas foram removidos.

O workflow Verify executa Maven com JDK 25/PostgreSQL 17 e disponibiliza o JAR somente após sucesso. Não exige credenciais de lojas ou Telegram.

Validação disponível: 34 testes passaram no ambiente Java 25/ECJ/PGlite, no Windows com PostgreSQL nativo e no GitHub Actions com JDK 25/PostgreSQL 17. O pipeline Java controlado também publicou no canal privado e persistiu a confirmação. No Docker Compose local, a aplicação ficou saudável, a migração V1 foi aplicada e foram aprovados o reinício, a persistência do volume e a restauração de backup em banco isolado. O uso com contas reais dos marketplaces ainda precisa ser confirmado. A versão não inclui renovação OAuth do Mercado Livre, geração de links de afiliado ML nem atualização/remoção de posts Amazon antigos. O repositório antigo foi excluído após a revogação das credenciais expostas, e esta versão começou em um histórico limpo.
