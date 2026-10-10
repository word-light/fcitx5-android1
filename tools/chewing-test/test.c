// Emulates the SogaKey chewing glue (replay into a shadow context, dummy 'z',
// live candidates) to see what happens around Space used as the first tone.
#include <chewing.h>
#include <stdio.h>
#include <string.h>

static const char *path;
static ChewingContext *mk(void) {
    ChewingContext *ctx = chewing_new2(path, NULL, NULL, NULL);
    chewing_config_set_int(ctx, "chewing.conversion_engine", 2);
    chewing_config_set_int(ctx, "chewing.sort_candidates_by_frequency", 1);
    chewing_set_KBType(ctx, chewing_KBStr2Num("KB_DEFAULT"));
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_maxChiSymbolLen(ctx, 39);
    chewing_set_phraseChoiceRearward(ctx, 1);
    chewing_set_spaceAsSelection(ctx, 1);
    return ctx;
}
static void feed(ChewingContext *c, char k) {
    if (k == ' ') chewing_handle_Space(c); else chewing_handle_Default(c, (unsigned char)k);
}
static void replay(ChewingContext *s, const char *keys, int n) {
    chewing_cand_close(s);
    chewing_clean_preedit_buf(s);
    chewing_clean_bopomofo_buf(s);
    chewing_Reset(s);
    for (int i = 0; i < n; i++) feed(s, keys[i]);
}
static void state(const char *tag, ChewingContext *c) {
    printf("  %s buffer='%s' bopomofo='%s' bufLen=%d bopoCheck=%d commitCheck=%d\n", tag,
           chewing_buffer_String_static(c), chewing_bopomofo_String_static(c),
           chewing_buffer_Len(c), chewing_bopomofo_Check(c), chewing_commit_Check(c));
}
static void run(const char *keys) {
    int n = (int)strlen(keys);
    ChewingContext *real = mk(), *s = mk();
    printf("== keys='%s'\n", keys);
    for (int i = 0; i < n; i++) {
        feed(real, keys[i]);
        printf(" after key %d ('%c'):", i, keys[i]);
        state("real", real);
        if (chewing_commit_Check(real)) { printf("   COMMITTED '%s'\n", chewing_commit_String_static(real)); chewing_ack(real); }
    }
    replay(s, keys, n);
    state("shadowReplay", s);
    printf("  replay matches real: %d\n",
           !strcmp(chewing_buffer_String_static(s), chewing_buffer_String_static(real)) &&
           !strcmp(chewing_bopomofo_String_static(s), chewing_bopomofo_String_static(real)));
    for (int j = 1; j <= n; j++) {
        replay(s, keys, j);
        chewing_handle_Default(s, 'z');
        printf("  prefix %d '%.*s': lens=%d buf='%s'", j, j, keys, chewing_buffer_Len(s), chewing_buffer_String_static(s));
        int r = chewing_cand_open(s);
        int total = chewing_cand_TotalChoice(s);
        printf(" cand_open=%d total=%d first:", r, total);
        chewing_cand_Enumerate(s);
        int k = 0;
        while (chewing_cand_hasNext(s) && k < 8) { printf(" %s", chewing_cand_String_static(s)); k++; }
        printf("\n");
        chewing_cand_close(s);
    }
    chewing_delete(real); chewing_delete(s);
}
int main(int argc, char **argv) {
    path = argc > 1 ? argv[1] : ".";
    const char *cases[] = {"su", "su ", "su3", "su ru", "su ru ", "su cl3", "g. ", "g.3 ", "5j ", "g. ru", NULL};
    for (int i = 0; cases[i]; i++) run(cases[i]);
    return 0;
}
