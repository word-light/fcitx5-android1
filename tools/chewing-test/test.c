#include <chewing.h>
#include <stdio.h>
#include <string.h>

static void dump(ChewingContext *ctx, const char *label) {
    printf("    [%s] buffer='%s' zhuyin='%s' commit=%d", label,
           chewing_buffer_String_static(ctx), chewing_bopomofo_String_static(ctx),
           chewing_commit_Check(ctx));
    if (chewing_commit_Check(ctx)) printf(" committed='%s'", chewing_commit_String_static(ctx));
    printf("\n");
}

static void cands(ChewingContext *ctx) {
    int r = chewing_cand_open(ctx);
    printf("    [cands] open=%d total=%d :", r, chewing_cand_TotalChoice(ctx));
    chewing_cand_Enumerate(ctx);
    int n = 0;
    while (chewing_cand_hasNext(ctx) && n < 12) { printf(" %s", chewing_cand_String_static(ctx)); n++; }
    printf("\n");
    chewing_cand_close(ctx);
}

static void run(const char *path, int engine, const char *keys, const char *desc) {
    ChewingContext *ctx = chewing_new2(path, NULL, NULL, NULL);
    int rc = chewing_config_set_int(ctx, "chewing.conversion_engine", engine);
    chewing_set_KBType(ctx, chewing_KBStr2Num("KB_DEFAULT"));
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_maxChiSymbolLen(ctx, 39);
    printf("== engine=%d (set_int rc=%d, get=%d) keys='%s'  %s\n", engine, rc,
           chewing_config_get_int(ctx, "chewing.conversion_engine"), keys, desc);
    for (const char *k = keys; *k; k++) {
        chewing_handle_Default(ctx, *k);
        char lab[8] = {*k, 0};
        dump(ctx, lab);
    }
    cands(ctx);
    chewing_delete(ctx);
}

int main(int argc, char **argv) {
    const char *path = argc > 1 ? argv[1] : ".";
    const char *cases[][2] = {
        {"s", "N only"},
        {"sc", "N then H  (want: 你好)"},
        {"suc", "NI then H"},
        {"sucl", "NI then HAO(no tone)"},
        {"su3cl3", "full ni3 hao3"},
        {"wv", "T then X"},
        {"5j", "ZH then U"},
    };
    for (int e = 1; e <= 2; e++)
        for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++)
            run(path, e, cases[i][0], cases[i][1]);
    return 0;
}
