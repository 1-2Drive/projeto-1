#
# build.ps1 - Compila o relatorio tecnico (PPROGIA) para PDF.
#
# Versao Windows do build.sh, para Windows PowerShell 5.1 ou PowerShell 7.
# Faz o mesmo que o build.sh, com opcoes ao estilo do PowerShell. O ficheiro
# e so ASCII: o Windows PowerShell 5.1 le os .ps1 sem BOM como ANSI.
#
# Uso:   .\build.ps1 [opcoes]
#        .\build.ps1 -Help
#
# Codigos de saida:
#   0  sucesso
#   1  erro de compilacao LaTeX
#   2  dependencias em falta
#   3  erro de utilizacao (opcao invalida, ficheiros do projeto ausentes)

param(
    [Alias('f')][switch]$Figures,
    [Alias('c')][switch]$Clean,
    [switch]$DistClean,
    [Alias('w')][switch]$Warnings,
    [Alias('q')][switch]$Quiet,
    [Alias('v')][switch]$Verbose,
    [switch]$NoBib,
    [ValidateSet('auto', 'latexmk', 'pdflatex')][string]$Engine = 'auto',
    [Alias('h')][switch]$Help
)

Set-StrictMode -Version 2.0
# Nao usar 'Stop': no PowerShell 5.1, redirecionar o stderr de um programa
# externo transforma cada linha num erro, que com 'Stop' aborta o script.
$ErrorActionPreference = 'Continue'

$Main = 'relatorio'
$TexFile = "$Main.tex"
$OutDir = 'build'          # PDF e ficheiros auxiliares
$PdfFile = "$OutDir\$Main.pdf"
$LogFile = "$OutDir\$Main.log"
$BibFile = 'referencias.bib'
$FigDir = 'figuras'
$SvgDir = 'figuras\svg'
$LogoDir = 'logos'
$MaxPasses = 5
$DoBib = -not $NoBib

# ---------------------------------------------------------------------------
# Mensagens
# ---------------------------------------------------------------------------
$UseColor = -not $env:NO_COLOR

function Write-Tagged([string]$Tag, [ConsoleColor]$Color, [string]$Text) {
    if ($UseColor) { Write-Host $Tag -ForegroundColor $Color -NoNewline }
    else { Write-Host $Tag -NoNewline }
    Write-Host " $Text"
}

function Write-Dim([string]$Text) {
    if ($UseColor) { Write-Host $Text -ForegroundColor DarkGray }
    else { Write-Host $Text }
}

function Say([string]$Text = '') { if (-not $Quiet) { Write-Host $Text } }
function Step([string]$Text) { if (-not $Quiet) { Write-Tagged '==>' Cyan $Text } }
function Ok([string]$Text) { if (-not $Quiet) { Write-Tagged '  ok' Green $Text } }
function Warn([string]$Text) { Write-Tagged ' aviso' Yellow $Text }
function Err([string]$Text) { Write-Tagged ' erro' Red $Text }
function Dbg([string]$Text) { if ($Verbose) { Write-Dim "      $Text" } }

function Show-Usage {
    Write-Host @'
build.ps1 - compila o relatorio tecnico para PDF

USO
    .\build.ps1 [opcoes]

OPCOES
    -Figures, -f        Regenera os PDF das figuras a partir dos SVG originais
                        antes de compilar (requer inkscape, rsvg-convert ou
                        cairosvg). Por omissao, so sao gerados os que faltarem.
    -Clean, -c          Remove os ficheiros auxiliares de build\ e termina.
    -DistClean          Remove a pasta build\ (auxiliares e PDF) e termina.
                        (No build.sh e -C, mas o PowerShell nao distingue
                        maiusculas de minusculas: aqui -C e o mesmo que -c.)
    -Warnings, -w       Mostra os avisos do LaTeX (overfull boxes, referencias
                        por resolver, etc.).
    -Quiet, -q          Apenas erros.
    -Verbose, -v        Mostra os comandos executados e a saida completa.
    -NoBib              Nao executa o bibtex (compilacao mais rapida; a
                        bibliografia fica desatualizada).
    -Engine NOME        latexmk | pdflatex | auto  (por omissao: auto)
    -Help, -h           Mostra esta ajuda.

EXEMPLOS
    .\build.ps1                compilacao normal
    .\build.ps1 -f             regenera figuras e compila
    .\build.ps1 -w             compila e lista os avisos do LaTeX
    .\build.ps1 -Clean         limpa os ficheiros auxiliares

O script pode ser invocado a partir de qualquer pasta; compila sempre na
pasta onde reside. O PDF e os ficheiros auxiliares ficam em build\.

Se a politica de execucao do PowerShell bloquear o script:
    powershell -ExecutionPolicy Bypass -File .\build.ps1
'@
}

# ---------------------------------------------------------------------------
# Processamento de argumentos
# ---------------------------------------------------------------------------
# Os parametros declarados acima sao tratados pelo PowerShell; o que sobra
# fica em $args.
foreach ($a in $args) {
    if ("$a" -like '-*') {
        Err "opcao desconhecida: $a"
        Say "Use '.\build.ps1 -Help' para ver as opcoes disponiveis."
    } else {
        Err "argumento inesperado: $a"
    }
    exit 3
}

if ($Help) { Show-Usage; exit 0 }

# A pasta e o PATH do terminal que chamou o script sao repostos no fim.
$OldPath = $env:PATH
Push-Location -LiteralPath $PSScriptRoot
try {

# ---------------------------------------------------------------------------
# Limpeza
# ---------------------------------------------------------------------------
$AuxExt = 'aux', 'log', 'out', 'toc', 'lof', 'lot', 'bbl', 'blg', 'fls',
          'fdb_latexmk', 'synctex.gz', 'run.xml', 'bcf', 'nav', 'snm', 'vrb'

function Remove-Aux {
    foreach ($ext in $AuxExt) {
        Remove-Item -LiteralPath "$OutDir\$Main.$ext" -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath "$OutDir\_minted-$Main" -Recurse -Force -ErrorAction SilentlyContinue
}

if ($DistClean) {
    Step 'A remover auxiliares e PDF'
    Remove-Item -LiteralPath $OutDir -Recurse -Force -ErrorAction SilentlyContinue
    Ok 'limpeza completa'
    exit 0
}

if ($Clean) {
    Step 'A remover ficheiros auxiliares'
    Remove-Aux
    Ok 'auxiliares removidos (PDF mantido)'
    exit 0
}

# ---------------------------------------------------------------------------
# PATH: um terminal aberto antes de instalar o MiKTeX ou o TeX Live ainda nao
# tem as pastas deles no PATH
# ---------------------------------------------------------------------------
function Add-ToPath([string]$Dir) {
    if (-not (Test-Path -LiteralPath $Dir -PathType Container)) { return }
    if (($env:PATH -split ';') -notcontains $Dir) {
        $env:PATH = "$env:PATH;$Dir"
        Dbg "PATH += $Dir"
    }
}

Add-ToPath "$env:LOCALAPPDATA\Programs\MiKTeX\miktex\bin\x64"
Add-ToPath "$env:ProgramFiles\MiKTeX\miktex\bin\x64"
# TeX Live: C:\texlive\<ano>\bin\windows (win32/win64 nas versoes antigas)
foreach ($d in @(Get-ChildItem -Path 'C:\texlive\*\bin\*' -Directory -ErrorAction SilentlyContinue |
                 Sort-Object FullName -Descending)) {
    Add-ToPath $d.FullName
}
foreach ($d in @(Get-ChildItem -Path "$env:APPDATA\TinyTeX\bin\*" -Directory -ErrorAction SilentlyContinue)) {
    Add-ToPath $d.FullName
}
Add-ToPath "$env:ProgramFiles\Inkscape\bin"

function Have([string]$Name) {
    [bool](Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue)
}

# Corre um programa externo e devolve o codigo de saida. Sem -Verbose, a
# saida e descartada.
function Invoke-Native([string]$Exe, [string[]]$ArgList) {
    if ($Verbose) { & $Exe @ArgList | Out-Host }
    else { & $Exe @ArgList *> $null }
    return $LASTEXITCODE
}

# ---------------------------------------------------------------------------
# Verificacao do projeto
# ---------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $TexFile -PathType Leaf)) {
    Err "nao encontrei '$TexFile' em $PSScriptRoot"
    Err 'o script deve residir na pasta do relatorio'
    exit 3
}

$missingSources = @('metadados.tex', 'preambulo.tex', 'capa.tex' |
                    Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
if ($missingSources.Count -gt 0) {
    Err "ficheiros do projeto em falta: $($missingSources -join ' ')"
    exit 3
}

# ---------------------------------------------------------------------------
# Dependencias LaTeX
# ---------------------------------------------------------------------------
function Show-InstallHint {
    Write-Host @'
  Instale o MiKTeX:
      winget install MiKTeX.MiKTeX
  e, na MiKTeX Console (Settings), ponha a instalacao de pacotes em falta
  ("install missing packages on-the-fly") em "Always".
  Em alternativa, o TeX Live: https://tug.org/texlive/windows.html
'@
}

if (-not (Have 'pdflatex')) {
    Err 'pdflatex nao encontrado no PATH'
    Say
    Show-InstallHint
    exit 2
}

if ($DoBib -and -not (Have 'bibtex')) {
    Warn 'bibtex nao encontrado; a bibliografia nao sera gerada'
    $DoBib = $false
}

# Pacotes LaTeX usados pelo preambulo
$RequiredSty = @(
    'geometry.sty', 'graphicx.sty', 'booktabs.sty', 'longtable.sty', 'array.sty',
    'tabularx.sty', 'multirow.sty', 'amsmath.sty', 'amssymb.sty', 'enumitem.sty', 'xcolor.sty',
    'colortbl.sty', 'fancyhdr.sty', 'titlesec.sty', 'caption.sty', 'float.sty', 'listings.sty',
    'csquotes.sty', 'hyperref.sty', 'microtype.sty', 'lmodern.sty', 'babel.sty',
    'tcolorbox.sty', 'cleveref.sty', 'icomma.sty',
    'portuges.ldf'   # o babel portugues precisa do ficheiro de lingua
)

$IsMiKTeX = Have 'initexmf'

if ($IsMiKTeX) {
    # No MiKTeX, o kpsewhich tenta instalar o que nao encontra (e pode abrir a
    # janela de instalacao); os pacotes em falta sao instalados ao compilar.
    Dbg 'MiKTeX: verificacao de pacotes ignorada (sao instalados ao compilar)'
} elseif (Have 'kpsewhich') {
    $missingSty = @($RequiredSty | Where-Object { -not (& kpsewhich $_ 2>$null) })
    if ($missingSty.Count -gt 0) {
        Err 'pacotes LaTeX em falta:'
        foreach ($sty in $missingSty) { Say "    - $sty" }
        Say
        Show-InstallHint
        exit 2
    }
    Dbg 'todos os pacotes LaTeX necessarios estao disponiveis'
} else {
    Dbg 'kpsewhich indisponivel; verificacao de pacotes ignorada'
}

# ---------------------------------------------------------------------------
# Figuras
# ---------------------------------------------------------------------------
function Test-NonEmptyFile([string]$Path) {
    (Test-Path -LiteralPath $Path -PathType Leaf) -and (Get-Item -LiteralPath $Path).Length -gt 0
}

function Convert-SvgToPdf([string]$Src, [string]$Dst) {
    Remove-Item -LiteralPath $Dst -Force -ErrorAction SilentlyContinue
    if (Have 'inkscape') {
        $inkMajor = 1
        if ("$(& inkscape --version 2>$null | Select-Object -First 1)" -match '^Inkscape (\d+)') {
            $inkMajor = [int]$Matches[1]
        }
        if ($inkMajor -ge 1) {
            & inkscape $Src --export-area-drawing --export-margin=6 `
                --export-type=pdf "--export-filename=$Dst" *> $null
        } else {
            & inkscape $Src --export-area-drawing "--export-pdf=$Dst" *> $null
        }
        if (Test-NonEmptyFile $Dst) { return $true }
        Remove-Item -LiteralPath $Dst -Force -ErrorAction SilentlyContinue
    }
    if (Have 'rsvg-convert') {
        & rsvg-convert -f pdf -o $Dst $Src *> $null
        if ($LASTEXITCODE -eq 0 -and (Test-NonEmptyFile $Dst)) { return $true }
    }
    if (Have 'cairosvg') {
        & cairosvg $Src -o $Dst *> $null
        if ($LASTEXITCODE -eq 0 -and (Test-NonEmptyFile $Dst)) { return $true }
    }
    if (Have 'python') {
        & python -c 'import cairosvg' *> $null
        if ($LASTEXITCODE -eq 0) {
            & python -c 'import cairosvg,sys; cairosvg.svg2pdf(url=sys.argv[1], write_to=sys.argv[2])' `
                $Src $Dst *> $null
            if ($LASTEXITCODE -eq 0 -and (Test-NonEmptyFile $Dst)) { return $true }
        }
    }
    Remove-Item -LiteralPath $Dst -Force -ErrorAction SilentlyContinue
    return $false
}

# Converte os SVG de $SrcDir para PDF em $DstDir (o pdflatex nao le SVG). Sem
# $Force, so converte os que ainda nao tem PDF. Os logotipos em JPEG/PNG sao
# usados directamente.
function Convert-SvgFolder([string]$SrcDir, [string]$DstDir, [bool]$Force, [string]$What) {
    if (-not (Test-Path -LiteralPath $SrcDir -PathType Container)) {
        Dbg "sem pasta de SVG ($SrcDir); $What ignorados"
        return $true
    }
    $todo = @(Get-ChildItem -LiteralPath $SrcDir -Filter '*.svg' -File | Where-Object {
        $Force -or -not (Test-Path -LiteralPath (Join-Path $DstDir "$($_.BaseName).pdf"))
    })
    if ($todo.Count -eq 0) { return $true }

    if (-not ((Have 'inkscape') -or (Have 'rsvg-convert') -or (Have 'cairosvg'))) {
        if ($Force) {
            Err 'nenhum conversor de SVG disponivel (inkscape, rsvg-convert ou cairosvg)'
            Say '    winget install Inkscape.Inkscape'
        } else {
            Warn "faltam PDF de $What e nao ha conversor de SVG instalado"
        }
        return $false
    }

    New-Item -ItemType Directory -Force -Path $DstDir | Out-Null
    Step "A converter $What SVG para PDF"
    $allOk = $true
    foreach ($svg in $todo) {
        if (Convert-SvgToPdf $svg.FullName (Join-Path $DstDir "$($svg.BaseName).pdf")) {
            Ok "$($svg.BaseName).pdf"
        } else {
            Err "falhou a conversao de $($svg.Name)"
            $allOk = $false
        }
    }
    return $allOk
}

if ($Figures) {
    if (-not (Convert-SvgFolder $LogoDir $LogoDir $true 'logotipos')) { exit 1 }
    if (-not (Convert-SvgFolder $SvgDir $FigDir $true 'figuras')) { exit 1 }
} else {
    Convert-SvgFolder $LogoDir $LogoDir $false 'logotipos' | Out-Null
    Convert-SvgFolder $SvgDir $FigDir $false 'figuras' | Out-Null
}

# Avisa sobre figuras referenciadas mas ausentes
$figRefs = @(Select-String -Path $TexFile, 'capa.tex', 'seccoes\*.tex' -AllMatches -CaseSensitive `
                 -Pattern '\\includegraphics(\[[^\]]*\])?\{([^}]*)\}' -ErrorAction SilentlyContinue |
             ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[2].Value } | Sort-Object -Unique)
$missingFig = @($figRefs | Where-Object {
    -not (Test-Path -LiteralPath $_ -PathType Leaf) -and -not (Test-Path -LiteralPath "$_.pdf" -PathType Leaf)
})
if ($missingFig.Count -gt 0) {
    Warn "figuras referenciadas mas ausentes: $($missingFig -join ' ')"
    Warn "execute '.\build.ps1 -Figures' para as gerar"
}

# ---------------------------------------------------------------------------
# Aviso sobre placeholders da capa
# ---------------------------------------------------------------------------
if (Select-String -LiteralPath 'metadados.tex' -CaseSensitive -Quiet -ErrorAction SilentlyContinue `
        -Pattern 'Nome Completo do Aluno|Nome do Docente|SUBSTITUIR|TODO|preencher') {
    Warn "a capa ainda contem placeholders; edite 'metadados.tex'"
}

# ---------------------------------------------------------------------------
# Compilacao
# ---------------------------------------------------------------------------
function Get-LogLines {
    if (Test-Path -LiteralPath $LogFile -PathType Leaf) {
        Get-Content -LiteralPath $LogFile -Encoding UTF8
    }
}

# Com -file-line-error, os erros aparecem como 'ficheiro:linha: mensagem' e
# nao com o '!' habitual; procuram-se as duas formas.
function Show-LogErrors {
    $show = 0
    $printed = 0
    foreach ($line in @(Get-LogLines)) {
        if ($line -cmatch '^!|^\S.*?:\d+: ') { $show = 12 }
        if ($line -cmatch '^l\.\d+' -and $show -lt 3) { $show = 3 }
        if ($show -gt 0) {
            Write-Host "    $line"
            $show--
            $printed++
            if ($printed -ge 60) { break }
        }
    }
}

function Invoke-Pdflatex([string]$Label) {
    Dbg "pdflatex ($Label)"
    Invoke-Native 'pdflatex' @('-interaction=nonstopmode', '-file-line-error',
                               "-output-directory=$OutDir", $TexFile)
}

function Test-NeedsRerun {
    $hits = @(Get-LogLines) -cmatch 'Rerun to get|Rerun LaTeX|There were undefined references|Label\(s\) may have changed'
    @($hits).Count -gt 0
}

# O MiKTeX traz o latexmk, mas este precisa de Perl, que o Windows nao tem;
# por isso so se usa o latexmk se ele de facto correr.
function Test-Latexmk {
    if (-not (Have 'latexmk')) { return $false }
    & latexmk -v *> $null
    return $LASTEXITCODE -eq 0
}

function Invoke-LatexmkBuild {
    Step 'A compilar com latexmk'
    $lmArgs = @('-pdf', '-interaction=nonstopmode', '-file-line-error', "-outdir=$OutDir")
    if (-not $DoBib) { $lmArgs += '-nobibtex' }
    if (-not $Verbose) { $lmArgs += '-silent' }
    Invoke-Native 'latexmk' ($lmArgs + $TexFile)
}

function Invoke-PdflatexBuild {
    Step 'A compilar com pdflatex'

    $rc = Invoke-Pdflatex 'passagem 1'
    if ($rc -ne 0 -and -not (Test-Path -LiteralPath $PdfFile -PathType Leaf)) {
        return $rc
    }

    if ($DoBib -and (Test-Path -LiteralPath $BibFile -PathType Leaf) -and
        (Select-String -LiteralPath "$OutDir\$Main.aux" -Pattern '\citation' -SimpleMatch -CaseSensitive `
             -Quiet -ErrorAction SilentlyContinue)) {
        Dbg 'bibtex'
        $brc = Invoke-Native 'bibtex' @("$OutDir/$Main")
        if ($brc -ne 0) { Warn "bibtex terminou com erros (ver $OutDir\$Main.blg)" }
    }

    for ($pass = 2; $pass -le $MaxPasses; $pass++) {
        $rc = Invoke-Pdflatex "passagem $pass"
        if ($rc -ne 0) { return $rc }
        if (-not (Test-NeedsRerun)) { break }
    }

    return 0
}

$useLatexmk = $false
switch ($Engine) {
    'latexmk' {
        if (Test-Latexmk) {
            $useLatexmk = $true
        } else {
            Err 'latexmk pedido mas nao encontrado no PATH (ou sem Perl, no caso do MiKTeX)'
            exit 2
        }
    }
    'auto' { $useLatexmk = Test-Latexmk }
}

try {
    New-Item -ItemType Directory -Force -Path $OutDir -ErrorAction Stop | Out-Null
} catch {
    Err "nao foi possivel criar $OutDir\"
    exit 1
}

$timer = [Diagnostics.Stopwatch]::StartNew()

if ($useLatexmk) {
    $status = Invoke-LatexmkBuild
} else {
    $status = Invoke-PdflatexBuild
}

$timer.Stop()

# ---------------------------------------------------------------------------
# Resultado
# ---------------------------------------------------------------------------
if ($status -ne 0 -or -not (Test-Path -LiteralPath $PdfFile -PathType Leaf)) {
    Err 'a compilacao falhou'
    Say
    Say "Erros encontrados em ${LogFile}:"
    Show-LogErrors
    Say
    if ($IsMiKTeX -and @(@(Get-LogLines) -cmatch "File ``.*' not found").Count -gt 0) {
        Say 'O MiKTeX nao instalou um pacote em falta. Na MiKTeX Console (Settings),'
        Say 'ponha "install missing packages on-the-fly" em "Always" e compile de novo.'
        Say
    }
    Say 'Para ver a saida completa:  .\build.ps1 -Verbose'
    exit 1
}

$logLines = @(Get-LogLines)

# O TeX parte as linhas do log aos 79 caracteres e o MiKTeX escreve o caminho
# absoluto do PDF; juntando as linhas, a mensagem fica inteira outra vez.
$pages = '?'
if ((-join $logLines) -cmatch 'Output written on .*\((\d+) page') { $pages = $Matches[1] }

$bytes = (Get-Item -LiteralPath $PdfFile).Length
if ($bytes -ge 1MB) { $size = '{0:0.0} MB' -f ($bytes / 1MB) }
elseif ($bytes -ge 1KB) { $size = '{0:0} KB' -f ($bytes / 1KB) }
else { $size = "$bytes B" }

$elapsed = ' em {0}s' -f [int][Math]::Floor($timer.Elapsed.TotalSeconds)

Say
Ok "$PdfFile -- $pages paginas, $size$elapsed"

if ($Warnings) {
    $w = @($logLines -cmatch 'LaTeX Warning|Overfull|Underfull|Citation .* undefined|Reference .* undefined' |
           Select-Object -First 50)
    if ($w.Count -gt 0) {
        Say
        Say 'Avisos do LaTeX:'
        foreach ($line in $w) { Write-Host "    $line" }
    } else {
        Say 'Sem avisos.'
    }
} else {
    $nw = @($logLines -cmatch 'LaTeX Warning|Overfull|Underfull').Count
    if ($nw -gt 0 -and -not $Quiet) {
        Write-Dim "     $nw avisos do LaTeX (use -w para os listar)"
    }
}

# Referencias/citacoes por resolver sao silenciosas mas relevantes
if (@($logLines -cmatch 'Citation .* undefined').Count -gt 0) {
    Warn "existem citacoes por resolver; verifique o $BibFile"
}
if (@($logLines -cmatch 'Reference .* undefined').Count -gt 0) {
    Warn 'existem referencias cruzadas por resolver'
}

exit 0

} finally {
    Pop-Location
    $env:PATH = $OldPath
}
