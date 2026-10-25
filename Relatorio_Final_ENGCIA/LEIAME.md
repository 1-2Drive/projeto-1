# Relatório Final — ENGCIA

Fontes LaTeX do relatório *Sistema Pericial de Apoio à Decisão em Veículos Autónomos*, convertidas
do Word `Relatorio_ENGCIA.docx` com formatação uniforme. O PDF compilado é `relatorio.pdf`.

## Compilar

Na pasta do projeto:

```
tectonic relatorio.tex
```

ou, com TeX Live / MiKTeX: `xelatex relatorio.tex` (duas vezes, para o índice e as referências).
É preciso XeLaTeX (ou Tectonic), por causa dos tipos de letra.

## Tipos de letra

Iguais aos do Word: **Book Antiqua** (texto), **Georgia** (títulos) e **Aptos** (legendas).
A Book Antiqua e a Georgia vêm com o Windows/Office. A Aptos é uma fonte da Microsoft e
**não está no repositório** (não pode ser distribuída): para compilar, criar a pasta `fontes/`
com os ficheiros `Aptos-Regular.ttf`, `Aptos-Bold.ttf`, `Aptos-Italic.ttf` e
`Aptos-BoldItalic.ttf` (vêm com o Microsoft 365 ou podem ser descarregados da Microsoft).
O PDF já leva todos os tipos de letra embutidos.

## Estrutura

```
relatorio.tex     documento principal (ordem dos capítulos)
metadados.tex     dados da capa: título, autores, docentes, data
preambulo.tex     pacotes, tipos de letra, cores, títulos, legendas e tabelas
capa.tex          página de rosto
seccoes/          um ficheiro por capítulo, bibliografia e anexos
figuras/          imagens das figuras (nome = número + título)
logos/            logótipos do ISEP e do MEIA
fontes/           fonte Aptos (legendas) — não incluída no repositório
```

## Convenções

- Figuras e tabelas são numeradas automaticamente; no texto usa-se `Figura~\ref{fig:N}`,
  `Tabela~\ref{tab:N}` e `Secção~\ref{sec:X.Y}`, que se atualizam sozinhas.
- Tabelas: ambiente `tabela` (cabeçalho azul e linhas alternadas, como no Word).
- Figuras mais largas do que a página ficam numa página rodada (`landscape`).
- As Figuras 1 e 2 e a caixa da estrutura das regras estão desenhadas em TikZ.
