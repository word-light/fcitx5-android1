#include <chewing.h>
#include <stdio.h>
#include <string.h>

static void dump(ChewingContext *ctx, const char *label) {
    printf("  [%s] buffer='%s' zhuyin='%s' commit=%d",
           label, chewing_buffer_String_static(ctx), chewing_bopomofo_String_static(ctx),
           chewing_commit_Check(ctx));
    if (chewing_commit_Check(ctx)) printf(" committed='%s'", chewing_commit_String_static(ctx));
    printf(" totalChoice=%d\n", chewing_cand_TotalChoice(ctx));
}

static void cands(ChewingContext *ctx, const char *label) {
    int r = chewing_cand_open(ctx);
    printf("  [%s] cand_open=%d total=%d :", label, r, chewing_cand_TotalChoice(ctx));
    chewing_cand_Enumerate(ctx);
    int n = 0;
    while (chewing_cand_hasNext(ctx) && n < 12) { printf(" %s", chewing_cand_String_static(ctx)); n++; }
    printf("\n");
    chewing_cand_close(ctx);
}

static void run(int engine, const char *keys, const char *desc) {
    ChewingContext *ctx = chewing_new();
    chewing_config_set_int(ctx, "chewing.conversion_engine", engine);
    chewing_set_KBType(ctx, chewing_KBStr2Num("KB_DEFAULT"));
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_phraseChoiceRearward(ctx, 1);
    chewing_set_spaceAsSelection(ctx, 1);
    chewing_set_maxChiSymbolLen(ctx, 39);
    printf("== engine=%d keys='%s' (%s)\n", engine, keys, desc);
    for (const char *k = keys; *k; k++) {
        if (*k == '_') chewing_handle_Space(ctx);
        else if (*k == '>') chewing_handle_Down(ctx);
        else chewing_handle_Default(ctx, *k);
    }
    dump(ctx, "after keys");
    cands(ctx, "cand_open");
    chewing_handle_Enter(ctx);
    dump(ctx, "after enter");
    chewing_delete(ctx);
}

int main(void) {
    const char *cases[][2] = {
        {"su3cl3", "ni3 hao3 full"},
        {"sc", "N H initials only"},
        {"sc_", "N H + space"},
        {"suc", "NI H"},
        {"sucl", "NI HAO no tone"},
        {"sucl_", "NI HAO + space"},
        {"wjo", "T UEI partial"},
        {"wjovm", "T UEI X ? partial (taiwan)"},
        {"wv", "T X (taiwan initials)"},
        {"wv_", "T X + space"},
        {"5j/", "zh u eng partial"},
        {"sc>", "N H + down"},
    };
    for (int e = 1; e <= 2; e++)
        for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++) run(e, cases[i][0], cases[i][1]);
    return 0;
}
