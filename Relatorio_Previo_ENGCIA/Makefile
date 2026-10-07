# Compilação do relatório.
# Alternativa simples ao ./build.sh, que é mais portável e diagnostica
# dependências em falta. Requer pdflatex e bibtex.
# O PDF e os ficheiros auxiliares ficam em $(OUT)/. Se o pdftk estiver
# instalado, gera também $(OUT)/$(MAIN)_so_capa.pdf (apenas a capa).

MAIN = relatorio
OUT  = build
LATEX = pdflatex -interaction=nonstopmode -output-directory=$(OUT)

.PHONY: all clean distclean figuras

all: $(OUT)/$(MAIN).pdf $(OUT)/$(MAIN)_so_capa.pdf

$(OUT)/$(MAIN).pdf: $(MAIN).tex metadados.tex preambulo.tex capa.tex \
             $(wildcard seccoes/*.tex) referencias.bib
	@mkdir -p $(OUT)
	$(LATEX) $(MAIN).tex
	bibtex $(OUT)/$(MAIN) || true
	$(LATEX) $(MAIN).tex
	$(LATEX) $(MAIN).tex

# PDF só com a capa (1.ª página). Sem pdftk, ou se falhar, apenas avisa.
$(OUT)/$(MAIN)_so_capa.pdf: $(OUT)/$(MAIN).pdf
	@if command -v pdftk >/dev/null 2>&1; then \
	  pdftk $< cat 1 output $@ || { rm -f $@; echo "aviso: o pdftk falhou; $@ não foi gerado" >&2; }; \
	else \
	  echo "aviso: pdftk não encontrado; $@ não foi gerado" >&2; \
	fi

# Regenera os PDF das figuras a partir dos SVG originais (requer inkscape)
figuras:
	@mkdir -p figuras
	@for f in figuras/svg/*.svg; do \
	  b=$$(basename $$f .svg); \
	  inkscape $$f --export-area-drawing --export-margin=6 \
	    --export-type=pdf --export-filename=figuras/$$b.pdf; \
	done

clean:
	cd $(OUT) 2>/dev/null && rm -f $(MAIN).aux $(MAIN).log $(MAIN).out \
	      $(MAIN).toc $(MAIN).lof $(MAIN).lot $(MAIN).bbl $(MAIN).blg \
	      $(MAIN).fls $(MAIN).fdb_latexmk $(MAIN).synctex.gz || true

distclean:
	rm -rf $(OUT)
