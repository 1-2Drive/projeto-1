# Relatório Técnico — PPROGIA

Fontes LaTeX do relatório técnico do projeto *Sistema Pericial de Apoio à
Condução* (motor de inferência, base de conhecimento, metaconhecimento e
explicação em Prolog). A formatação segue a do `../Relatorio_Previo_ENGCIA/`.

## Antes de compilar

Os dados da capa estão **apenas** em `metadados.tex`. Confirmar:

| Macro | Verificar |
|---|---|
| `\uc`, `\ucsigla` | designação oficial da UC |
| `\anoletivo`, `\semestre` | ano letivo e semestre |
| `\listaautores`, `\autoresmeta` | elementos do grupo |
| `\listadocentes` | docente(s) da UC |
| `\datarelatorio` | data de entrega |

O `build.sh` avisa enquanto existirem `\preencher` em `metadados.tex`.

## Compilar

```bash
./build.sh          # PDF em build/relatorio.pdf (opções: -w avisos, -c limpar, -h ajuda)
```

## Estrutura

```
relatorio.tex        documento principal (a ordem dos capítulos é a dos \input)
metadados.tex        dados da capa
preambulo.tex        pacotes, cores, estilos, listagens Prolog/Drools/JSON, macros
capa.tex             página de rosto
referencias.bib      bibliografia (\nocite{*}: aparecem todas as entradas)
seccoes/             um ficheiro por capítulo
figuras/             figuras (PNG extraídas da versão Word)
logos/               logotipos do ISEP e do MEIA
build/               PDF e auxiliares (gerado)
Other/               rascunhos e versões anteriores (não compilados)
Teachers_Engine/     motor da UC, para a comparação (não compilado)
```

## Macros próprias

| Comando | Uso |
|---|---|
| `\cod{wm_support/3}` | identificadores de código no texto; dispensa escapar `_` e quebra a linha em `_ / .`; só ASCII; não usar em legendas (aí, `\texttt{...\_...}`) |
| `\preencher{texto}` | informação em falta (realçada a laranja) |
| `\begin{lstlisting}[caption=...,label=lst:...]` | código Prolog (por omissão); `language=Drools` ou `language=JSON` para os outros |
| `\cref{...}` | referências cruzadas («Figura 2.1», «Secção 5.5», «Listagem 4.1») |
