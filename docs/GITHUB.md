# Colocar esta versão no GitHub

Esta é a versão completa do projeto para o repositório `Caique-java/bot-ofertas`. O ZIP contém código-fonte, Maven Wrapper, exemplos de configuração, Docker, testes e o workflow `Verify`. Não envie o ZIP como um único arquivo dentro do repositório: aplique o conteúdo para que o GitHub reconheça o projeto.

O código mantém dry-run habilitado por padrão. O workflow usa banco temporário de teste e fontes desabilitadas; não precisa dos seus tokens e não envia ofertas.

## Atualizar seu repositório no Windows

Pré-requisitos: Git instalado e acesso autenticado ao seu GitHub. Você pode fazer o clone e o envio pelo GitHub Desktop, usando a pasta local do repositório nos passos abaixo.

1. Extraia `bot-ofertas-github.zip` em uma pasta separada do seu clone. A pasta extraída chama-se `bot-ofertas` e contém `pom.xml`.
2. Abra o PowerShell na pasta onde deseja guardar seu clone e execute, se ainda não o tiver:

```powershell
git clone https://github.com/Caique-java/bot-ofertas.git
```

3. No PowerShell, entre na pasta **extraída do ZIP** e aplique a atualização ao clone. Troque o caminho abaixo pelo caminho real:

```powershell
.\aplicar-atualizacao.ps1 -Repositorio 'C:\Projetos\bot-ofertas'
```

O script exige um clone limpo no commit original `82fea41d024d1b22c942840a230ecfd069b29e7c`. Ele cria uma branch `codex/ofertas-confiaveis-...`, copia os arquivos conferidos e remove os sete componentes antigos. Se houver mudanças próprias, ele para antes de modificar o clone; preserve essas mudanças e integre a atualização manualmente. Não use comandos de descarte para forçar a aplicação.

4. Entre na pasta do **clone atualizado**, confira o resumo e grave/envie a branch:

```powershell
Set-Location 'C:\Projetos\bot-ofertas'
git status --short
git diff --stat
git add .
git diff --cached --stat
git commit -m "Implementa coleta de ofertas e fila persistente"
git push -u origin HEAD
```

Os scripts não fazem commit nem push sozinhos. Confira que só os arquivos esperados estão sendo incluídos. Os segredos devem ficar no seu ambiente de execução; os exemplos são vazios. As remoções de segredos no diff não revogam os valores que já ficaram no histórico.

## Linux/macOS

É necessário Python 3 apenas para aplicar o ZIP; o bot continua sendo Java. Execute da pasta extraída, apontando para um clone limpo do commit original:

```bash
python3 aplicar-atualizacao.py --repositorio /caminho/do/clone/bot-ofertas
cd /caminho/do/clone/bot-ofertas
git status --short
git diff --stat
git add .
git diff --cached --stat
git commit -m "Implementa coleta de ofertas e fila persistente"
git push -u origin HEAD
```

## Abrir a revisão e conferir os testes

1. Abra [seu repositório](https://github.com/Caique-java/bot-ofertas) e crie um pull request da branch enviada para `main`.
2. Use o texto de [PULL_REQUEST.md](PULL_REQUEST.md) como descrição.
3. Na aba **Actions**, abra a execução **Verify** da sua branch e confira o resultado. O workflow roda a cada push em branches e em pull requests.
4. Se passar, revise a alteração e faça o merge quando estiver satisfeito. Uma execução com falha não valida o pacote; abra o passo com erro para investigar.

Se já usa GitHub CLI autenticado, você pode abrir um rascunho pelo terminal do clone:

```bash
gh pr create --draft --base main --title "Coleta de ofertas com fila persistente" --body-file docs/PULL_REQUEST.md
```

O workflow instala JDK 25, inicia PostgreSQL 17 e executa `bash mvnw -B verify` com o POM padrão. Depois de o workflow existir na branch padrão, ele também permite execução manual por **Actions → Verify → Run workflow**.

## Baixar e executar o JAR

Em uma execução **bem-sucedida** do workflow, abra a seção **Artifacts** e baixe `bot-ofertas-jar`. Extraia o arquivo `bot-ofertas-1.0.0.jar`. Também são disponibilizados relatórios em `test-reports`; os artefatos têm retenção de sete dias nesta configuração. É preciso estar conectado ao GitHub e ter acesso ao repositório para baixar os artefatos. [Instruções oficiais](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

Para usar o JAR, instale Java 25, disponibilize PostgreSQL e configure as variáveis de ambiente de acordo com o README. Execute no diretório onde está sua pasta `config`:

```bash
java -jar bot-ofertas-1.0.0.jar
```

Spring executado diretamente não carrega `.env` automaticamente. Configure as variáveis no terminal/serviço ou use o Docker Compose, que lê esse arquivo. O JAR sozinho não inclui o banco nem as suas credenciais.

O GitHub hospeda o código e executa o workflow de teste/build. Para monitoramento contínuo, mantenha o bot em um computador ou servidor ligado, conforme o [README](../README.md). Este projeto não usa GitHub Pages para executar Java nem agenda Actions como serviço permanente.

Referência do workflow: [build e testes Java com Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven).
