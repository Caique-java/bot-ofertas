[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Repositorio)
$ErrorActionPreference = 'Stop'
$baseEsperada = '82fea41d024d1b22c942840a230ecfd069b29e7c'
$destino = (Resolve-Path -LiteralPath $Repositorio).Path
if (-not (Test-Path -LiteralPath (Join-Path $destino '.git'))) {
    throw 'Informe a raiz do seu clone Git do bot-ofertas.'
}
$estado = @(& git -C $destino status --porcelain)
if ($LASTEXITCODE -ne 0 -or $estado.Count -gt 0) {
    throw 'O clone deve estar limpo. Salve suas alterações em uma branch antes de aplicar.'
}
$commitAtual = (& git -C $destino rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $commitAtual -ne $baseEsperada) {
    throw 'O clone possui outra versão. Não aplicarei arquivos sobre mudanças desconhecidas. Use uma branch a partir do commit-base informado no DIAGNOSTICO.md.'
}
$manifesto = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'arquivos-atualizacao.json') -Raw | ConvertFrom-Json
foreach ($arquivo in $manifesto.files) {
    if ($arquivo.path -match '(^[/\\]|\.\.|:)') { throw 'Caminho inválido no pacote.' }
    $origem = Join-Path $PSScriptRoot $arquivo.path
    if ((Get-FileHash -LiteralPath $origem -Algorithm SHA256).Hash.ToLowerInvariant() -ne $arquivo.sha256) {
        throw "Arquivo alterado ou corrompido: $($arquivo.path)"
    }
}
$branch = 'codex/ofertas-confiaveis-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
& git -C $destino switch -c $branch
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível criar a branch.' }
foreach ($arquivo in $manifesto.files) {
    $caminho = Join-Path $destino $arquivo.path
    New-Item -ItemType Directory -Force -Path (Split-Path $caminho -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $arquivo.path) -Destination $caminho -Force
}
$removidos = @(
    'src/main/java/com/ofertas/bot/BotScheduler.java',
    'src/main/java/com/ofertas/bot/controller/TestController.java',
    'src/main/java/com/ofertas/bot/event/OfertaAprovadaEvent.java',
    'src/main/java/com/ofertas/bot/listener/TelegramNotificationListener.java',
    'src/main/java/com/ofertas/bot/model/PromocaoHistorico.java',
    'src/main/java/com/ofertas/bot/repository/PromocaoHistoricoRepository.java',
    'src/main/java/com/ofertas/bot/util/UrlNormalizer.java'
)
foreach ($arquivo in $removidos) {
    $caminho = Join-Path $destino $arquivo
    if (Test-Path -LiteralPath $caminho) { Remove-Item -LiteralPath $caminho }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'arquivos-atualizacao.json') -Destination (Join-Path $destino 'arquivos-atualizacao.json') -Force
Write-Host "Alterações aplicadas na branch $branch."
Write-Host 'Revise git diff --stat e execute os testes com TEST_DB_URL de um banco exclusivo para testes.'
Write-Host 'Depois: git add .; git commit; git push -u origin NOME_DA_BRANCH'
Write-Host 'O script não fez commit, push, merge nem publicação no Telegram.'
