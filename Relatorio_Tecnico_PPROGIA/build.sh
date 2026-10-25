#!/usr/bin/env bash
#
# build.sh - Compila o relatorio tecnico (PPROGIA) para PDF.
#
# Compativel com Linux e macOS. Escrito para bash 3.2 (versao que a Apple
# distribui), sem depender de utilitarios exclusivos do GNU coreutils.
#
# Uso:   ./build.sh [opcoes]
#        ./build.sh --help
#
# Codigos de saida:
#   0  sucesso
#   1  erro de compilacao LaTeX
#   2  dependencias em falta
#   3  erro de utilizacao (opcao invalida, ficheiros do projeto ausentes)

set -u

# ---------------------------------------------------------------------------
# Localizacao do script (resolve symlinks sem 'readlink -f', ausente no macOS)
# ---------------------------------------------------------------------------
_self=$0
while [ -L "$_self" ]; do
    _link=$(ls -ld -- "$_self" | sed -e 's/^.*-> //')
    case $_link in
        /*) _self=$_link ;;
        *)  _self=$(dirname -- "$_self")/$_link ;;
    esac
done
SCRIPT_DIR=$(cd -- "$(dirname -- "$_self")" && pwd -P)
unset _self _link

MAIN=relatorio
TEXFILE="$MAIN.tex"
OUTDIR=build          # PDF e ficheiros auxiliares
PDFFILE="$OUTDIR/$MAIN.pdf"
LOGFILE="$OUTDIR/$MAIN.log"
BIBFILE=referencias.bib
FIGDIR=figuras
SVGDIR=figuras/svg
LOGODIR=logos

# ---------------------------------------------------------------------------
# Opcoes
# ---------------------------------------------------------------------------
DO_CLEAN=0
DO_DISTCLEAN=0
DO_FIGURES=0
DO_BIB=1
SHOW_WARNINGS=0
VERBOSE=0
QUIET=0
ENGINE=auto
MAX_PASSES=5

# ---------------------------------------------------------------------------
# Cores (apenas em terminal interativo)
# ---------------------------------------------------------------------------
if [ -t 1 ] && [ "${TERM:-dumb}" != dumb ] && [ -z "${NO_COLOR:-}" ]; then
    C_RESET=$(printf '\033[0m')
    C_BOLD=$(printf '\033[1m')
    C_DIM=$(printf '\033[2m')
    C_RED=$(printf '\033[31m')
    C_GREEN=$(printf '\033[32m')
    C_YELLOW=$(printf '\033[33m')
    C_BLUE=$(printf '\033[34m')
else
    C_RESET=''; C_BOLD=''; C_DIM=''
    C_RED=''; C_GREEN=''; C_YELLOW=''; C_BLUE=''
fi

say()  { [ "$QUIET" -eq 1 ] || printf '%s\n' "$*"; }
step() { [ "$QUIET" -eq 1 ] || printf '%s==>%s %s\n' "$C_BLUE$C_BOLD" "$C_RESET" "$*"; }
ok()   { [ "$QUIET" -eq 1 ] || printf '%s  ok%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%s aviso%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
err()  { printf '%s erro%s %s\n' "$C_RED$C_BOLD" "$C_RESET" "$*" >&2; }
dbg()  { [ "$VERBOSE" -eq 1 ] && printf '%s      %s%s\n' "$C_DIM" "$*" "$C_RESET" >&2; return 0; }

usage() {
    cat <<'USAGE'
build.sh - compila o relatorio tecnico para PDF

USO
    ./build.sh [opcoes]

OPCOES
    -f, --figures       Regenera os PNG das figuras a partir dos SVG originais
                        antes de compilar (requer inkscape, rsvg-convert ou
                        cairosvg). Por omissao, so sao gerados os que faltarem.
    -c, --clean         Remove os ficheiros auxiliares de build/ e termina.
    -C, --distclean     Remove a pasta build/ (auxiliares e PDF) e termina.
    -w, --warnings      Mostra os avisos do LaTeX (overfull boxes, referencias
                        por resolver, etc.).
    -q, --quiet         Apenas erros.
    -v, --verbose       Mostra os comandos executados e a saida completa.
        --no-bib        Nao executa o bibtex (compilacao mais rapida; a
                        bibliografia fica desatualizada).
        --engine=NOME   latexmk | pdflatex | auto  (por omissao: auto)
    -h, --help          Mostra esta ajuda.

EXEMPLOS
    ./build.sh                 compilacao normal
    ./build.sh -f              regenera figuras e compila
    ./build.sh -w              compila e lista os avisos do LaTeX
    ./build.sh --clean         limpa os ficheiros auxiliares

O script pode ser invocado a partir de qualquer diretoria; muda sempre para a
pasta onde reside antes de compilar. O PDF e os ficheiros auxiliares ficam
em build/.
USAGE
}

# ---------------------------------------------------------------------------
# Processamento de argumentos
# ---------------------------------------------------------------------------
while [ $# -gt 0 ]; do
    case $1 in
        -f|--figures)   DO_FIGURES=1 ;;
        -c|--clean)     DO_CLEAN=1 ;;
        -C|--distclean) DO_DISTCLEAN=1 ;;
        -w|--warnings)  SHOW_WARNINGS=1 ;;
        -q|--quiet)     QUIET=1 ;;
        -v|--verbose)   VERBOSE=1 ;;
        --no-bib)       DO_BIB=0 ;;
        --engine=*)     ENGINE=${1#--engine=} ;;
        --engine)       shift; ENGINE=${1:-auto} ;;
        -h|--help)      usage; exit 0 ;;
        --)             shift; break ;;
        -*)
            err "opcao desconhecida: $1"
            say "Use './build.sh --help' para ver as opcoes disponiveis."
            exit 3 ;;
        *)
            err "argumento inesperado: $1"
            exit 3 ;;
    esac
    shift
done

case $ENGINE in
    auto|latexmk|pdflatex) ;;
    *) err "motor invalido: '$ENGINE' (use latexmk, pdflatex ou auto)"; exit 3 ;;
esac

cd "$SCRIPT_DIR" || { err "nao foi possivel entrar em $SCRIPT_DIR"; exit 3; }

# ---------------------------------------------------------------------------
# Limpeza
# ---------------------------------------------------------------------------
AUX_EXT="aux log out toc lof lot bbl blg fls fdb_latexmk synctex.gz run.xml bcf nav snm vrb"

clean_aux() {
    for ext in $AUX_EXT; do
        rm -f "$OUTDIR/$MAIN.$ext"
    done
    rm -rf "$OUTDIR/_minted-$MAIN"
}

if [ "$DO_DISTCLEAN" -eq 1 ]; then
    step "A remover auxiliares e PDF"
    rm -rf "$OUTDIR"
    ok "limpeza completa"
    exit 0
fi

if [ "$DO_CLEAN" -eq 1 ]; then
    step "A remover ficheiros auxiliares"
    clean_aux
    ok "auxiliares removidos (PDF mantido)"
    exit 0
fi

# ---------------------------------------------------------------------------
# PATH: o MacTeX e o TeX Live nem sempre estao no PATH de shells nao-login
# ---------------------------------------------------------------------------
add_to_path() {
    [ -d "$1" ] || return 0
    case ":$PATH:" in
        *":$1:"*) ;;
        *) PATH="$PATH:$1"; dbg "PATH += $1" ;;
    esac
}

add_to_path /Library/TeX/texbin
add_to_path /usr/texbin
add_to_path /opt/homebrew/bin
add_to_path /usr/local/bin
add_to_path "$HOME/.TinyTeX/bin/universal-darwin"
add_to_path "$HOME/bin"
for d in /usr/local/texlive/*/bin/*; do
    add_to_path "$d"
done
for d in "$HOME"/.TinyTeX/bin/*; do
    add_to_path "$d"
done
export PATH

have() { command -v "$1" >/dev/null 2>&1; }

# ---------------------------------------------------------------------------
# Verificacao do projeto
# ---------------------------------------------------------------------------
if [ ! -f "$TEXFILE" ]; then
    err "nao encontrei '$TEXFILE' em $SCRIPT_DIR"
    err "o script deve residir na pasta do relatorio"
    exit 3
fi

missing_sources=''
for f in metadados.tex preambulo.tex capa.tex; do
    [ -f "$f" ] || missing_sources="$missing_sources $f"
done
if [ -n "$missing_sources" ]; then
    err "ficheiros do projeto em falta:$missing_sources"
    exit 3
fi

# ---------------------------------------------------------------------------
# Dependencias LaTeX
# ---------------------------------------------------------------------------
detect_os() {
    case $(uname -s) in
        Darwin) echo macos ;;
        Linux)  echo linux ;;
        *)      echo outro ;;
    esac
}
OS=$(detect_os)

install_hint() {
    case $OS in
        macos)
            cat <<'HINT'
  Instale o MacTeX (distribuicao completa, ~4 GB):
      brew install --cask mactex
  ou a versao minima e os pacotes necessarios:
      brew install --cask basictex
      sudo tlmgr update --self
      sudo tlmgr install latexmk babel-portuges titlesec enumitem csquotes \
                         multirow tabularx caption microtype collection-fontsrecommended
  Depois abra um novo terminal (o MacTeX acrescenta /Library/TeX/texbin ao PATH).
HINT
            ;;
        linux)
            cat <<'HINT'
  Debian / Ubuntu:
      sudo apt install texlive-latex-base texlive-latex-recommended \
                       texlive-latex-extra texlive-lang-portuguese latexmk
  Fedora:
      sudo dnf install texlive-scheme-medium texlive-babel-portuges latexmk
  Arch:
      sudo pacman -S texlive-basic texlive-latex texlive-latexrecommended \
                     texlive-latexextra texlive-langportuguese
HINT
            ;;
        *)
            echo "  Instale uma distribuicao TeX Live com pdflatex e bibtex."
            ;;
    esac
}

if ! have pdflatex; then
    err "pdflatex nao encontrado no PATH"
    say ""
    install_hint
    exit 2
fi

if [ "$DO_BIB" -eq 1 ] && ! have bibtex; then
    warn "bibtex nao encontrado; a bibliografia nao sera gerada"
    DO_BIB=0
fi

# Pacotes LaTeX usados pelo preambulo
REQUIRED_STY="geometry.sty graphicx.sty booktabs.sty longtable.sty array.sty
tabularx.sty multirow.sty amsmath.sty amssymb.sty enumitem.sty xcolor.sty
colortbl.sty fancyhdr.sty titlesec.sty caption.sty float.sty listings.sty
csquotes.sty hyperref.sty microtype.sty lmodern.sty babel.sty
tcolorbox.sty cleveref.sty icomma.sty"

if have kpsewhich; then
    missing_sty=''
    for sty in $REQUIRED_STY; do
        kpsewhich "$sty" >/dev/null 2>&1 || missing_sty="$missing_sty $sty"
    done
    # o babel portugues precisa do ficheiro de lingua
    if ! kpsewhich portuges.ldf >/dev/null 2>&1; then
        missing_sty="$missing_sty portuges.ldf"
    fi
    if [ -n "$missing_sty" ]; then
        err "pacotes LaTeX em falta:"
        for sty in $missing_sty; do
            say "    - $sty"
        done
        say ""
        install_hint
        exit 2
    fi
    dbg "todos os pacotes LaTeX necessarios estao disponiveis"
else
    dbg "kpsewhich indisponivel; verificacao de pacotes ignorada"
fi

# ---------------------------------------------------------------------------
# Figuras
# ---------------------------------------------------------------------------
# O Inkscape precisa de um HOME onde possa criar o seu perfil; se nao o
# conseguir, escreve um aviso, NAO gera ficheiro e ainda assim termina com
# codigo 0. Por isso usamos um HOME temporario e validamos sempre a saida.
INK_HOME=''
ink_home() {
    if [ -z "$INK_HOME" ]; then
        INK_HOME=${TMPDIR:-/tmp}/build-inkscape-$$
        mkdir -p "$INK_HOME" 2>/dev/null || INK_HOME=$HOME
    fi
    printf '%s' "$INK_HOME"
}

svg_to_pdf() {
    # $1 = svg de entrada, $2 = pdf de saida
    src=$1; dst=$2
    rm -f "$dst"
    if have inkscape; then
        ink_major=$(inkscape --version 2>/dev/null \
            | sed -n 's/^Inkscape \([0-9]*\).*/\1/p' | head -1)
        [ -n "$ink_major" ] || ink_major=1
        if [ "$ink_major" -ge 1 ]; then
            HOME=$(ink_home) QT_QPA_PLATFORM=offscreen DISPLAY= inkscape "$src" \
                --export-area-drawing --export-margin=6 \
                --export-type=pdf --export-filename="$dst" >/dev/null 2>&1
        else
            HOME=$(ink_home) QT_QPA_PLATFORM=offscreen DISPLAY= inkscape "$src" \
                --export-area-drawing --export-pdf="$dst" >/dev/null 2>&1
        fi
        [ -s "$dst" ] && return 0
        rm -f "$dst"
    fi
    if have rsvg-convert; then
        rsvg-convert -f pdf -o "$dst" "$src" >/dev/null 2>&1 && [ -s "$dst" ] && return 0
    fi
    if have cairosvg; then
        cairosvg "$src" -o "$dst" >/dev/null 2>&1 && [ -s "$dst" ] && return 0
    fi
    if have python3 && python3 -c 'import cairosvg' >/dev/null 2>&1; then
        python3 -c 'import cairosvg,sys; cairosvg.svg2pdf(url=sys.argv[1], write_to=sys.argv[2])' \
            "$src" "$dst" >/dev/null 2>&1 && [ -s "$dst" ] && return 0
    fi
    rm -f "$dst"
    return 1
}

svg_to_png() {
    # $1 = svg de entrada, $2 = png de saida (2592 px de largura, fundo branco).
    # As figuras do relatorio sao rasterizadas; os logotipos continuam em PDF.
    src=$1; dst=$2
    rm -f "$dst"
    if have inkscape; then
        HOME=$(ink_home) QT_QPA_PLATFORM=offscreen DISPLAY= inkscape "$src" \
            --export-type=png --export-width=2592 \
            --export-background=white --export-background-opacity=1 \
            --export-filename="$dst" >/dev/null 2>&1
        [ -s "$dst" ] && return 0
        rm -f "$dst"
    fi
    if have rsvg-convert; then
        rsvg-convert -f png -w 2592 -b white -o "$dst" "$src" >/dev/null 2>&1 && [ -s "$dst" ] && return 0
    fi
    if have cairosvg; then
        cairosvg "$src" --output-width 2592 -o "$dst" >/dev/null 2>&1 && [ -s "$dst" ] && return 0
    fi
    rm -f "$dst"
    return 1
}

cleanup() {
    [ -n "$INK_HOME" ] && [ "$INK_HOME" != "$HOME" ] && rm -rf "$INK_HOME"
    return 0
}
trap cleanup EXIT INT TERM

build_figures() {
    force=$1
    [ -d "$SVGDIR" ] || { dbg "sem pasta de SVG ($SVGDIR); figuras ignoradas"; return 0; }
    mkdir -p "$FIGDIR"

    todo=''
    for svg in "$SVGDIR"/*.svg; do
        [ -e "$svg" ] || continue
        base=$(basename "$svg" .svg)
        if [ "$force" -eq 1 ] || [ ! -f "$FIGDIR/$base.png" ]; then
            todo="$todo $svg"
        fi
    done

    [ -n "$todo" ] || return 0

    if ! have inkscape && ! have rsvg-convert && ! have cairosvg; then
        if [ "$force" -eq 1 ]; then
            err "nenhum conversor de SVG disponivel (inkscape, rsvg-convert ou cairosvg)"
            case $OS in
                macos) say "    brew install inkscape   # ou:  brew install librsvg" ;;
                linux) say "    sudo apt install inkscape   # ou:  sudo apt install librsvg2-bin" ;;
            esac
            return 1
        fi
        warn "faltam PNG de figuras e nao ha conversor de SVG instalado"
        return 1
    fi

    step "A converter figuras SVG para PNG"
    failed=0
    for svg in $todo; do
        base=$(basename "$svg" .svg)
        if svg_to_png "$svg" "$FIGDIR/$base.png"; then
            ok "$base.png"
        else
            err "falhou a conversao de $base.svg"
            failed=1
        fi
    done
    return $failed
}

# Os logotipos em SVG precisam de ser convertidos para PDF (o pdflatex nao
# le SVG). Os que ja estao em JPEG/PNG sao usados directamente.
build_logos() {
    force=$1
    [ -d "$LOGODIR" ] || return 0
    todo=''
    for svg in "$LOGODIR"/*.svg; do
        [ -e "$svg" ] || continue
        base=$(basename "$svg" .svg)
        if [ "$force" -eq 1 ] || [ ! -f "$LOGODIR/$base.pdf" ]; then
            todo="$todo $svg"
        fi
    done
    [ -n "$todo" ] || return 0

    step "A converter logotipos SVG para PDF"
    failed=0
    for svg in $todo; do
        base=$(basename "$svg" .svg)
        if svg_to_pdf "$svg" "$LOGODIR/$base.pdf"; then
            ok "$base.pdf"
        else
            err "falhou a conversao de $base.svg"
            failed=1
        fi
    done
    return $failed
}

if [ "$DO_FIGURES" -eq 1 ]; then
    build_logos 1 || exit 1
    build_figures 1 || exit 1
else
    build_logos 0 || true
    build_figures 0 || true
fi

# Avisa sobre figuras referenciadas mas ausentes
missing_fig=''
for fig in $(sed -n 's/.*\\includegraphics\(\[[^]]*\]\)\{0,1\}{\([^}]*\)}.*/\2/p' \
             "$TEXFILE" capa.tex seccoes/*.tex 2>/dev/null | sort -u); do
    [ -f "$fig" ] || [ -f "$fig.pdf" ] || missing_fig="$missing_fig $fig"
done
if [ -n "$missing_fig" ]; then
    warn "figuras referenciadas mas ausentes:$missing_fig"
    warn "execute './build.sh --figures' para as gerar"
fi

# ---------------------------------------------------------------------------
# Aviso sobre placeholders da capa
# ---------------------------------------------------------------------------
if grep -q 'Nome Completo do Aluno\|Nome do Docente\|SUBSTITUIR\|TODO\|preencher' metadados.tex 2>/dev/null; then
    warn "a capa ainda contem placeholders; edite 'metadados.tex'"
fi

# ---------------------------------------------------------------------------
# Compilacao
# ---------------------------------------------------------------------------
log_errors() {
    [ -f "$LOGFILE" ] || return 0
    awk '
        /^!/            { show = 12 }
        /^l\.[0-9]+/    { if (show < 3) show = 3 }
        show > 0        { print "    " $0; show-- }
    ' "$LOGFILE" | head -60
}

log_warnings() {
    [ -f "$LOGFILE" ] || return 0
    grep -E 'LaTeX Warning|Overfull|Underfull|Citation .* undefined|Reference .* undefined' \
        "$LOGFILE" 2>/dev/null | sed 's/^/    /' | head -50
}

run_pdflatex() {
    label=$1
    dbg "pdflatex ($label)"
    if [ "$VERBOSE" -eq 1 ]; then
        pdflatex -interaction=nonstopmode -file-line-error -output-directory="$OUTDIR" "$TEXFILE"
    else
        pdflatex -interaction=nonstopmode -file-line-error -output-directory="$OUTDIR" "$TEXFILE" >/dev/null 2>&1
    fi
    return $?
}

needs_rerun() {
    [ -f "$LOGFILE" ] || return 1
    grep -q 'Rerun to get\|Rerun LaTeX\|There were undefined references\|Label(s) may have changed' \
        "$LOGFILE" 2>/dev/null
}

compile_with_latexmk() {
    step "A compilar com latexmk"
    lm_opts="-pdf -interaction=nonstopmode -file-line-error -outdir=$OUTDIR"
    [ "$DO_BIB" -eq 0 ] && lm_opts="$lm_opts -nobibtex"
    if [ "$VERBOSE" -eq 1 ]; then
        # shellcheck disable=SC2086
        latexmk $lm_opts "$TEXFILE"
    else
        # shellcheck disable=SC2086
        latexmk $lm_opts -silent "$TEXFILE" >/dev/null 2>&1
    fi
}

compile_with_pdflatex() {
    step "A compilar com pdflatex"

    run_pdflatex "passagem 1"
    rc=$?
    if [ $rc -ne 0 ] && [ ! -f "$PDFFILE" ]; then
        return $rc
    fi

    if [ "$DO_BIB" -eq 1 ] && [ -f "$BIBFILE" ] && grep -q '\\citation' "$OUTDIR/$MAIN.aux" 2>/dev/null; then
        dbg "bibtex"
        if [ "$VERBOSE" -eq 1 ]; then
            bibtex "$OUTDIR/$MAIN" || warn "bibtex terminou com erros (ver $OUTDIR/$MAIN.blg)"
        else
            bibtex "$OUTDIR/$MAIN" >/dev/null 2>&1 || warn "bibtex terminou com erros (ver $OUTDIR/$MAIN.blg)"
        fi
    fi

    pass=2
    while [ $pass -le $MAX_PASSES ]; do
        run_pdflatex "passagem $pass"
        rc=$?
        [ $rc -ne 0 ] && return $rc
        needs_rerun || break
        pass=$((pass + 1))
    done

    return 0
}

use_latexmk=0
case $ENGINE in
    latexmk)
        if have latexmk; then
            use_latexmk=1
        else
            err "latexmk pedido mas nao encontrado no PATH"
            exit 2
        fi ;;
    pdflatex) use_latexmk=0 ;;
    auto)     have latexmk && use_latexmk=1 ;;
esac

mkdir -p "$OUTDIR" || { err "nao foi possivel criar $OUTDIR/"; exit 1; }

start_time=$(date +%s 2>/dev/null || echo 0)

if [ $use_latexmk -eq 1 ]; then
    compile_with_latexmk
    status=$?
else
    compile_with_pdflatex
    status=$?
fi

end_time=$(date +%s 2>/dev/null || echo 0)

# ---------------------------------------------------------------------------
# Resultado
# ---------------------------------------------------------------------------
if [ $status -ne 0 ] || [ ! -f "$PDFFILE" ]; then
    err "a compilacao falhou"
    say ""
    say "${C_BOLD}Erros encontrados em $LOGFILE:${C_RESET}"
    log_errors
    say ""
    say "Para ver a saida completa:  ./build.sh --verbose"
    exit 1
fi

pages=$(sed -n 's/.*Output written on .*(\([0-9][0-9]*\) page.*/\1/p' "$LOGFILE" 2>/dev/null | tail -1)
[ -n "$pages" ] || pages='?'

bytes=$(wc -c < "$PDFFILE" 2>/dev/null | tr -d ' ')
size=$(awk -v b="${bytes:-0}" 'BEGIN{
    if (b >= 1048576) printf "%.1f MB", b/1048576;
    else if (b >= 1024) printf "%.0f KB", b/1024;
    else printf "%d B", b
}')

elapsed=''
if [ "$start_time" -gt 0 ] && [ "$end_time" -ge "$start_time" ]; then
    elapsed=" em $((end_time - start_time))s"
fi

say ""
ok "${C_BOLD}$PDFFILE${C_RESET} -- $pages paginas, $size$elapsed"

if [ "$SHOW_WARNINGS" -eq 1 ]; then
    w=$(log_warnings)
    if [ -n "$w" ]; then
        say ""
        say "${C_BOLD}Avisos do LaTeX:${C_RESET}"
        printf '%s\n' "$w"
    else
        say "Sem avisos."
    fi
else
    nw=$(grep -c 'LaTeX Warning\|Overfull\|Underfull' "$LOGFILE" 2>/dev/null | tr -d ' ')
    [ -n "$nw" ] || nw=0
    if [ "$nw" -gt 0 ]; then
        say "     ${C_DIM}$nw avisos do LaTeX (use -w para os listar)${C_RESET}"
    fi
fi

# Referencias/citacoes por resolver sao silenciosas mas relevantes
if grep -q 'Citation .* undefined' "$LOGFILE" 2>/dev/null; then
    warn "existem citacoes por resolver; verifique o $BIBFILE"
fi
if grep -q 'Reference .* undefined' "$LOGFILE" 2>/dev/null; then
    warn "existem referencias cruzadas por resolver"
fi

exit 0
