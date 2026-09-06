# Fontes oficiais e limites das integrações

Consulta documental: 03/09/2026. URLs abaixo são as referências verificadas; dados reais de conta não foram usados.

## Amazon Brasil

A [Creators API](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/introduction) sucede a PA-API 5. A [página de descontinuação](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/paapiv5-deprecation) informa que chamadas antigas são recusadas. O projeto usa o [protocolo HTTP documentado](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/get-started/using-curl): credenciais LwA versão 3.1, OAuth client_credentials em `https://api.amazon.com/auth/o2/token`, escopo `creatorsapi::default` e token Bearer no host `https://creatorsapi.amazon`.

`GetItems` aceita até 10 IDs; o conector divide os ASINs em lotes. `SearchItems` usa palavras-chave e browse node opcional, com apenas a primeira página de 10 resultados por consulta; não varre todo o catálogo. Campos retornados dependem da conta/região. [GetItems](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/api-reference/operations/get-items), [SearchItems](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/api-reference/operations/search-items), [OffersV2](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/api-reference/resources/offersV2).

Requisitos publicados incluem inscrição no programa do marketplace, acesso à API, credenciais e elegibilidade comercial. A documentação menciona 10 vendas qualificadas nos últimos 30 dias; confirme os requisitos aplicáveis na sua conta. Tokens são renovados em memória antes de expirar; não são gravados na fila. Credenciais anteriores 2.x/PA-API não são compatíveis com este adaptador.

Somente ofertas com Buy Box, vendedor identificado, condição nova e preço explícito são consideradas. O ASIN filho identifica a variante; o preço de um ASIN pai não substitui o de uma cor/tamanho específico. Estoque desconhecido, assinaturas recorrentes e acesso antecipado são excluídos. Prime exclusivo é rotulado e bloqueado por padrão. `savingBasis` é referência da loja, não histórico. Links vêm de `detailPageURL` e precisam conter exatamente a tag configurada para a Amazon Brasil.

As [boas práticas](https://affiliate-program.amazon.com/creatorsapi/docs/en-us/concepts/best-programming-practices) exigem controle de TPS. O projeto inicia com dois segundos entre requisições, sem prometer que essa é a quota contratada da sua conta. Erros de quota adiam a fonte. Ajuste o intervalo à sua alocação real.

O [contrato brasileiro](https://associados.amazon.com.br/help/operating/agreement) e as [políticas](https://associados.amazon.com.br/help/operating/policies/) regem destino, divulgação e cache. Imagens não são armazenadas; conteúdo de produto é cache temporário e excluído localmente antes de 24h. Não há histórico longitudinal Amazon. Mensagens incluem identificação de associado, carimbo de consulta e avisos de preço/conteúdo. A compatibilidade do canal com sua autorização, inclusive permanência de mensagens, precisa ser confirmada antes de ativar envio. `AMAZON_PUBLICATION_AUTHORIZED` é um registro da decisão do operador, não uma autorização concedida pelo software.

## Mercado Livre Brasil

Fonte implementada: recurso oficial `GET https://api.mercadolibre.com/items/{id}`, com Bearer token, para IDs MLB configurados e autorizados. A consulta às páginas [itens e buscas](https://developers.mercadolivre.com.br/pt_br/itens-e-buscas) e [documentação equivalente](https://developers.mercadolibre.com.ar/es_ar/items-y-busquedas) foi tentada, mas devolveu HTTP 403 no ambiente de pesquisa; não se declara que a documentação completa atual ou a permissão da conta foram validadas. A [criação de aplicação](https://developers.mercadolivre.com.br/pt_br/crie-uma-aplicacao-no-mercado-livre) é o ponto inicial para configurar acesso oficial.

Não é usado um endpoint de promoção de vendedor como se fosse um catálogo universal de ofertas. Não há scraping, contorno de CAPTCHA ou API de afiliados inventada. A pesquisa global por palavra-chave/categoria não foi habilitada para ML sem confirmação de acesso; use lista explícita de IDs. `keywords` ML deve permanecer vazia.

O adaptador considera price, original_price (quando houver no item/variante exatos), currency_id, status, available_quantity, seller_id, permalink e imagem HTTPS. Para item com variações, exige variant explícita e lê preço/estoque da própria variação. Ausência de campos indispensáveis descarta o item. Não considera frete universal nem classifica o preço como Pix.

`ML_ACCESS_TOKEN` precisa ser válido e ter acesso aos itens. **Renovação automática de refresh_token não está implementada nesta entrega**: quando o token expirar, a fonte sinaliza falha e não publica preço antigo. Atualize o segredo e recrie o serviço; para operação autônoma prolongada, a próxima etapa é homologar OAuth e persistir refresh tokens em um armazenamento de segredos, conforme as permissões da aplicação. Isso não impede a fonte Amazon de continuar.

Quotas e retenção dependem da aplicação e dos termos; não foi confirmada uma quota universal. O limite local é configurável e as respostas 429 são respeitadas. Histórico fica desligado até `ML_RETAIN_HISTORY=true` para uma conta que permita esse armazenamento. Vendedores podem ser permitidos/bloqueados por ID; reputação não é consultada. Links ML permanecem links oficiais comuns: **não geram comissão automaticamente**. Integre um link de afiliado emitido por mecanismo autorizado após homologação.

## Telegram

[Bot API oficial](https://core.telegram.org/bots/api): `getMe`, `getChat`, `getChatMember`, `sendPhoto` e `sendMessage`. O bot precisa ser administrador do canal com permissão de publicação. Usa POST com corpo JSON e HTML escapado, botão de compra e `message_id` confirmado. Conteúdo externo nunca define tags HTML ou destino da API.

A resposta de falha define `retry_after` quando aplicável. O software aplica o prazo à fila do canal e diferencia rejeições permanentes, falhas transitórias e resultado incerto. Há limites locais de volume; não se promete que eles substituam as quotas do Telegram. Um fallback para texto só ocorre em erros definitivos específicos da imagem. Não há envio a canal de produção nos testes.

## O que ainda requer validação real

1. Conta Amazon Brasil elegível, credenciais LwA 3.1 e permissões para o canal.
2. Acesso ML aos IDs configurados e confirmação do contrato de resposta atual; renovação OAuth para operação prolongada.
3. Links de afiliado ML emitidos por fonte autorizada, se forem desejados.
4. Permissões do bot, IDs corretos e mensagem de teste autorizada pelo operador.
5. Quotas reais, necessidade de expiração/atualização de posts Amazon e termos de armazenamento.
6. Aprovação de qualquer hospedagem contratada; nenhum recurso público/pago foi provisionado.
