#include <chewing.h>
#include <stdio.h>
#include <string.h>

static void cands(ChewingContext *ctx, const char *label) {
    int r = chewing_cand_open(ctx);
    printf("    [%s] open=%d total=%d :", label, r, chewing_cand_TotalChoice(ctx));
    chewing_cand_Enumerate(ctx);
    int n = 0;
    while (chewing_cand_hasNext(ctx) && n < 14) { printf(" %s", chewing_cand_String_static(ctx)); n++; }
    printf("\n");
    chewing_cand_close(ctx);
}

static ChewingContext *mk(const char *path, int rearward) {
    ChewingContext *ctx = chewing_new2(path, NULL, NULL, NULL);
    chewing_config_set_int(ctx, "chewing.conversion_engine", 2);
    chewing_set_KBType(ctx, chewing_KBStr2Num("KB_DEFAULT"));
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_maxChiSymbolLen(ctx, 39);
    chewing_set_phraseChoiceRearward(ctx, rearward);
    chewing_set_candPerPage(ctx, 10);
    return ctx;
}

static void run(const char *path, int rearward, const char *keys, const char *desc) {
    ChewingContext *ctx = mk(path, rearward);
    printf("== rearward=%d keys='%s'  %s\n", rearward, keys, desc);
    for (const char *k = keys; *k; k++) chewing_handle_Default(ctx, *k);
    printf("    real : buffer='%s' pending='%s'\n", chewing_buffer_String_static(ctx), chewing_bopomofo_String_static(ctx));
    // shadow trick: a dummy initial forces the pending syllable into the buffer
    chewing_handle_Default(ctx, 'z');
    printf("    +z   : buffer='%s' pending='%s' cursor=%d len=%d\n", chewing_buffer_String_static(ctx), chewing_bopomofo_String_static(ctx),
           chewing_cursor_Current(ctx), chewing_buffer_Len(ctx));
    cands(ctx, "cands");
    chewing_delete(ctx);
}

int main(int argc, char **argv) {
    const char *path = argc > 1 ? argv[1] : ".";
    const char *cases[][2] = {
        {"sc", "ni hao  (ㄋㄏ)"},
        {"wj", "tai wan (ㄊㄨ)"},
        {"vv", "xie xie (ㄒㄒ)"},
        {"rw", "jin tian (ㄐㄊ)"},
        {"jga", "wei shen me (ㄨㄕㄇ)"},
        {"scs", "ㄋㄏㄕ"},
        {"su3c", "你 + ㄏ"},
        {"ja", "wo men (ㄨㄇ)"},
        {"s", "ㄋ only"},
    };
    for (int rw = 1; rw >= 0; rw--)
        for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++)
            run(path, rw, cases[i][0], cases[i][1]);
    return 0;
}
