# Relatório Prévio de Aquisição de Conhecimento — ENGCIA

Fontes LaTeX do relatório prévio de aquisição de conhecimento do projeto
*Sistema Pericial de Apoio à Decisão em Veículos Autónomos* (implementação
prevista em Drools).

## Antes de compilar

Os dados da capa estão **apenas** em `metadados.tex`. Confirmar:

| Macro | Verificar |
|---|---|
| `\uc`, `\ucsigla` | designação oficial da UC |
| `\anoletivo`, `\semestre` | ano letivo e semestre |
| `\listaautores`, `\autoresmeta` | elementos do grupo |
| `\listadocentes` | docente(s) de ENGCIA |
| `\datarelatorio` | data de entrega |

O `build.sh` avisa enquanto existirem `TODO` em `metadados.tex`.

## Compilar

```bash
./build.sh          # PDF (opções: -w avisos, -c limpar, -h ajuda)
```

## Estrutura

```
relatorio.tex        documento principal
metadados.tex        dados da capa
preambulo.tex        pacotes, cores, estilos, listagens Drools, macros
capa.tex             página de rosto
referencias.bib      bibliografia (só aparecem as entradas citadas)
seccoes/             um ficheiro por capítulo (a ordem é a dos \input em relatorio.tex)
figuras/             figuras em PDF; SVG em figuras/svg/ são convertidos pelo build.sh
logos/               logotipos do ISEP e do MEIA
build/               PDF, capa (relatorio_so_capa.pdf) e auxiliares (gerado; pode ser apagado)
```

## Macros e ambientes próprios

| Comando | Uso |
|---|---|
| `\preencher{texto}` | informação em falta (realçada a laranja) |
| `\begin{regranl}{R_CHUVA_01} ... \end{regranl}` | regra em linguagem natural (SE/ENTÃO) |
| `\begin{lstlisting}[caption=...,label=lst:...]` | código Drools (linguagem por omissão) |
| `\facto{...}`, `\regra{...}` | nomes de factos/regras no texto |
| `\cref{...}` | referências cruzadas («Figura 2», «Secção 3.1», «Listagem 1») |
| `$1,5$` | vírgula decimal em modo matemático (pacote `icomma`) |

