# Relatório Técnico (PPROGIA)

Fontes LaTeX do relatório técnico do projeto *Sistema Pericial de Apoio à
Condução* (motor de inferência, base de conhecimento, meta-conhecimento e
explicação em Prolog). A formatação segue a do `../Relatorio_Previo_ENGCIA/`, mas o
conteúdo e as regras são os desta UC: ver `AGENTS.md` (convenções do relatório,
em inglês) e `Avaliacao/` (o que o docente pede: cerca de 10 páginas).

## Antes de compilar

Os dados da capa estão **apenas** em `metadados.tex`. Confirmar:

| Macro | Verificar |
|---|---|
| `\uc`, `\ucsigla` | designação oficial da UC |
| `\anoletivo`, `\semestre` | ano letivo e semestre |
| `\listaautores`, `\autoresmeta` | elementos do grupo |
| `\listadocentes` | docente(s) da UC |
| `\datarelatorio` | data de entrega (25 de outubro de 2026) |

O `build.sh` avisa enquanto existirem `\preencher` em `metadados.tex`.

## Compilar

```bash
./build.sh          # PDF em build/relatorio.pdf
./build.sh -f       # regenera primeiro os PNG das figuras a partir dos SVG
./build.sh -w       # lista os avisos do LaTeX
./build.sh -h       # todas as opções (-c limpar, -C apagar build/, ...)
make                # alternativa com make
make figuras        # só regenera os PNG das figuras (requer inkscape)
./find-ai.sh        # procura travessões (—) e aspas angulares (« ») em seccoes/
```

No Windows, `.\build.ps1` (PowerShell) faz o mesmo que o `build.sh`
(`.\build.ps1 -Figures` regenera os PNG; `.\build.ps1 -Help` lista as opções).

## Estrutura

```
relatorio.tex        documento principal (a ordem dos capítulos é a dos \input)
metadados.tex        dados da capa
preambulo.tex        pacotes, cores, estilos, listagens Prolog/JSON, macros
capa.tex             página de rosto
referencias.bib      bibliografia (estilo unsrt: numerada pela ordem da primeira citação)
seccoes/             um ficheiro por capítulo
figuras/             figuras incluídas no relatório (PNG)
figuras/svg/         originais vetoriais dos dois diagramas (geram os PNG)
logos/               logotipos do ISEP e do MEIA
build/               PDF e auxiliares (gerado)
Avaliacao/           requisitos do docente e grelha de avaliação
Old/                 conteúdo retirado do relatório a pedido
Other/               rascunhos e versões anteriores (não compilados)
Teachers_Engine/     motor da UC, para a comparação (não compilado)
```

Capítulos (`seccoes/`, pela ordem de `relatorio.tex`): `resumo`, `perito`, `motor`,
`base_conhecimento`, `metaconhecimento`, `explicacao`, `arquitetura`,
`dificuldades`, `conclusao` e `anexos` (desativado).

## Configurações a saber

| Onde | O quê | Como reverter |
|---|---|---|
| `preambulo.tex`, bloco `COMPACTAÇÃO LIGEIRA` | letra de 10,5 pt e margens de 2,3 cm | os comentários do bloco explicam |
| `preambulo.tex`, bloco `CAPÍTULOS SEGUIDOS`; `\quebracapitulofalse` / `\quebracapitulotrue` em `relatorio.tex` | os capítulos 1 a 8 seguem sem mudar de página | apagar as duas linhas em `relatorio.tex` |
| `relatorio.tex`, bloco `RESUMO E ÍNDICE NA MESMA PÁGINA`; `resumo.tex`; `\l@chapter` em `preambulo.tex` | o Resumo e o Índice partilham uma página | repor o `\clearpage` em `resumo.tex` e usar só `\tableofcontents` |
| `relatorio.tex`, bloco `ANEXO` | o anexo está desativado | tirar o `% ` das duas linhas do bloco |
| `relatorio.tex`, bloco `LISTAS DE FIGURAS E DE TABELAS` | as listas de figuras e de tabelas estão desativadas (poupa uma página) | tirar o `% ` do início das seis linhas do bloco |
| `relatorio.tex`, `\nocite{...}` antes do Perito | as fontes do perito ficam em [1] a [4] | |

## Macros próprias

| Comando | Uso |
|---|---|
| `\cod{wm_support/3}` | identificadores de código no texto; dispensa escapar `_` e quebra a linha em `_ / .`; só ASCII; não usar em legendas (aí, `\texttt{...\_...}`) |
| `\preencher{texto}` | informação em falta (realçada a laranja) |
| `\begin{lstlisting}[caption=...,label=lst:...]` | código Prolog (por omissão, em grelha monoespaçada); `language=JSON` para os pedidos ao `server.pl` |
| `\cref{...}` | referências cruzadas ("Figura 2.1", "Secção 5.1", "Listagem 4.1") |
| `\enquote{...}` | aspas (“...”); não usar `«»` |
| `$\sim$120` | valores aproximados (um `~` sozinho é um espaço inquebrável) |
