# Trabalhar com este repositório no GitHub

O repositório público `Caique-java/bot-ofertas` usa um histórico limpo. Credenciais reais não fazem parte do código: tokens e senhas devem permanecer somente no ambiente local ou no serviço escolhido para implantação.

## Clonar no Windows

Com Git instalado, abra o PowerShell e execute:

```powershell
Set-Location 'C:\Users\KAIKE\Downloads'
git clone https://github.com/Caique-java/bot-ofertas.git
Set-Location '.\bot-ofertas'
```

Para configuração local, use `.env.example` e `config/monitoramento.example.yml` apenas como modelos. Os arquivos com valores reais são ignorados pelo Git. O IntelliJ também pode fornecer as variáveis na configuração de execução.

## Rotina segura para alterações

Antes de cada commit, confira exatamente o que mudou:

```powershell
git status --short
git diff
git add -- caminho-do-arquivo
git diff --cached
git commit -m "Descreva a alteracao realizada"
git push
```

Para uma mudança maior, crie uma branch e abra um pull request:

```powershell
git switch -c nome-da-alteracao
git push -u origin nome-da-alteracao
```

Não use `git add .` sem revisar. Nunca envie `.env`, configurações locais, dumps do banco, logs, arquivos da IDE ou credenciais. Se algum segredo for publicado, revogue-o imediatamente; apenas apagar o arquivo ou o commit não torna o valor seguro novamente.

## GitHub Actions

O workflow `Verify` é executado em pushes e pull requests. Ele usa JDK 25, PostgreSQL 17 temporário e credenciais descartáveis de teste para executar:

```bash
bash mvnw -B verify
```

Esse processo não consulta marketplaces, não acessa o canal e não envia mensagens ao Telegram. Quando os testes passam, o workflow disponibiliza temporariamente:

- `bot-ofertas-jar`: aplicação empacotada;
- `test-reports`: relatórios dos testes automatizados.

Para baixar um artefato, abra **Actions**, escolha uma execução verde e localize **Artifacts**. Veja as [instruções oficiais do GitHub](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

## O que o GitHub não faz neste projeto

O GitHub guarda o código, executa testes e gera o JAR. O workflow termina após essas tarefas e não mantém o bot funcionando 24 horas. A operação contínua ainda exige uma máquina ou serviço de hospedagem, PostgreSQL persistente, internet, monitoramento e credenciais válidas.
