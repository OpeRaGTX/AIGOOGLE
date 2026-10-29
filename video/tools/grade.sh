# grade.sh — sourced via tools/env.sh. grade <contrast> <saturate> <brightness> prints ffmpeg filters equal to
# the CSS filter "contrast() saturate() brightness()" (sRGB, in that order), so Remotion doesn't filter every frame.
grade() {
  awk -v c="$1" -v s="$2" -v b="$3" 'BEGIN {
    k = sprintf("(val-127.5)*%g+127.5", c)
    printf "format=gbrp,lutrgb=r=%s:g=%s:b=%s,", k, k, k
    printf "colorchannelmixer=rr=%g:rg=%g:rb=%g:gr=%g:gg=%g:gb=%g:br=%g:bg=%g:bb=%g,", \
      0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s, \
      0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s, \
      0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s
    k = sprintf("val*%g", b)
    printf "lutrgb=r=%s:g=%s:b=%s", k, k, k
  }'
}
