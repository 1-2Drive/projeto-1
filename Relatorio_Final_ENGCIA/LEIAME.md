# Relatório Final — ENGCIA

Fontes LaTeX do relatório final do projeto *Sistema Pericial de Apoio à Decisão em Veículos
Autónomos* (implementação em Drools). O conteúdo é igual ao do Word final (`Relatorio_ENGCIA.docx`): mesmo texto, numeração seguida de figuras e tabelas e mesmos capítulos.

## Compilar

```bash
tectonic relatorio.tex --outdir build     # Windows (recomendado; descarrega os pacotes sozinho)
./build.sh                                # Linux / macOS com TeX Live (pdflatex)
```

O PDF fica em `build/relatorio.pdf`. Funciona com XeTeX/Tectonic e com pdfLaTeX.

No IntelliJ (TeXiFy): *Add Tectonic SDK from disk…* e escolher o compilador **Tectonic** na
configuração de execução.

## Estrutura

```
relatorio.tex        documento principal (ordem dos capítulos)
metadados.tex        dados da capa (título, autores, docentes, data)
preambulo.tex        pacotes, cores, estilos de tabelas e listagens
capa.tex             página de rosto
seccoes/             um ficheiro por capítulo, mais bibliografia e anexos
figuras/             figuras; figuras/fluxograma/ tem os fluxogramas em PDF vetorial
logos/               logotipos do ISEP e do MEIA
```

## Notas

- As Figuras 1 e 2 (desenhos do Word) estão refeitas em TikZ, dentro de `introducao.tex` e `aquisicao.tex`.
- Os fluxogramas são gerados a partir de Mermaid. As fontes e o script estão em
  `DOCUMENTOS/fluxograma/fontes/` (`flow.mjs` + `render.mjs`).
- A bibliografia está escrita à mão em `seccoes/bibliografia.tex`, com a numeração do relatório.
  As citações usam `\cite{chave}`.
