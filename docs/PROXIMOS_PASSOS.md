# O que está pronto e o que falta

## Entregue no código

- Coleta Mercado Livre por IDs configurados e Amazon por ASINs/palavras-chave, sujeita ao acesso da conta.
- Avaliação por preço-alvo e referências identificadas, validação de estoque, condições e validade dos dados.
- Fila PostgreSQL, deduplicação persistente, revalidação, limites de envio e tratamento de resultado incerto.
- Telegram com escape HTML, checagem de permissões e confirmação da resposta; dry-run como padrão.
- Configuração externa, Docker Compose, migração, documentação e 34 testes aprovados no ambiente de validação e repetidos com sucesso no Windows do proprietário.
- Repositório GitHub recriado com histórico limpo; workflow de testes/geração de JAR executado com sucesso na branch `main`.

## Trabalho de desenvolvimento que pode ser feito a seguir

| Item | O que posso implementar | Dependência para concluir e validar |
|---|---|---|
| Renovação do token Mercado Livre | Renovação, persistência segura do refresh token e testes de expiração/falha | Credenciais de aplicação, fluxo OAuth e permissões reais confirmados; configuração dos segredos feita por você |
| Links de afiliado Mercado Livre | Uso de links emitidos por mecanismo autorizado, preservação do identificador e testes | Acesso ao programa e ao mecanismo oficial disponível na sua conta; comissão depende do programa |
| Cupons, Pix, parcelamento e reputação | Leitura e validação dos campos que a API fornecer | Respostas/documentação oficiais disponíveis para essa conta; campos ausentes não podem ser inventados |
| Frete | Consulta e apresentação das condições retornadas | Fonte com suporte autorizado e CEP de referência; o valor não será universal |
| Posts Amazon antigos | Rotina configurável para editar/retirar mensagens e testes isolados | Definição das regras aplicáveis à sua autorização, permissões do bot e autorização para alterar mensagens reais |
| Validação e implantação | Corrigir falhas do CI, preparar configuração e auxiliar a operação | Ambiente acessível, banco de teste e infraestrutura escolhida; envio real só depois de autorizado |

Essas funções futuras **não estão implementadas neste pacote**. Testes com respostas simuladas validam o código, mas não substituem a aprovação das contas nem a verificação das APIs reais.

## Ações que precisam acontecer nas suas contas

1. Manter o token Telegram atual e as senhas somente nas configurações locais/segredos do ambiente. Não cole segredos em issues, commits ou mensagens.
2. Obter/configurar as credenciais e permissões Amazon/Mercado Livre no ambiente de execução.
3. Escolher produtos, vendedores, preços-alvo e o canal; adicionar o bot como administrador.
4. Manter o workflow `Verify` obrigatório nas mudanças futuras e revisar a visibilidade do repositório somente depois das conferências de segurança.
5. Conferir as prévias em dry-run e validar as condições das ofertas.
6. Autorizar a publicação real quando tudo estiver conferido; manter o computador/servidor ligado.

O GitHub pode armazenar o código, executar testes e disponibilizar o JAR. O workflow fornecido termina após o build; ele não mantém o bot em execução durante as 24 horas.

## Ponto de retomada confirmado - 04/09/2026

O proprietário já configurou IntelliJ e PostgreSQL, renovou o token Telegram, adicionou o bot ao canal privado e validou um envio manual pela API. A próxima ação é criar a aplicação no portal de desenvolvedores do Mercado Livre. Ele possui cadastro/ID de afiliado, mas ainda não criou essa aplicação nem concluiu OAuth.

Manter dry-run e fontes desabilitadas até a configuração das credenciais e dos produtos. O canal permanece privado durante os testes. Consulte [RESUMO_ENTREVISTA.md](RESUMO_ENTREVISTA.md) para a apresentação do projeto e [VALIDACAO.md](VALIDACAO.md) para o alcance dos testes.

## Estado atualizado - 06/09/2026

- Pipeline controlado Java -> PostgreSQL -> fila -> revalidação -> Telegram aprovado no canal privado com `message_id=37`.
- Registro da fila e da tentativa confirmado como `SENT`, com uma única tentativa; deduplicação aprovada.
- Trava interna adicionada ao teste manual e confirmação temporária removida da configuração do IntelliJ.
- Banco isolado `bot_ofertas_test` e usuário `bot_test` criados; suíte Maven aprovada no Windows usando esse banco.
- Token atual não foi incorporado aos arquivos do projeto.
- Repositório antigo excluído, novo histórico limpo publicado em `main` e workflow `Verify` aprovado no GitHub Actions.

Próximas frentes independentes do Mercado Livre: preparar backup/monitoramento e escolher a infraestrutura para operação 24 horas. As fontes reais permanecem desabilitadas até suas credenciais e autorizações estarem disponíveis.
