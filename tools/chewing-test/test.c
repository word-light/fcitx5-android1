#include <chewing.h>
#include <stdio.h>
#include <string.h>

static void find(ChewingContext *ctx, const char *target, const char *label) {
    int r = chewing_cand_open(ctx);
    int total = chewing_cand_TotalChoice(ctx);
    int idx = -1, n = 0;
    chewing_cand_Enumerate(ctx);
    printf("    [%s] open=%d total=%d first:", label, r, total);
    while (chewing_cand_hasNext(ctx)) {
        const char *s = chewing_cand_String_static(ctx);
        if (n < 8) printf(" %s", s);
        if (idx < 0 && strcmp(s, target) == 0) idx = n;
        n++;
    }
    printf("  | '%s' at index %d\n", target, idx);
    chewing_cand_close(ctx);
}

static void run(const char *path, int sort, const char *keys, const char *target) {
    ChewingContext *ctx = chewing_new2(path, NULL, NULL, NULL);
    chewing_config_set_int(ctx, "chewing.conversion_engine", 2);
    int rc = chewing_config_set_int(ctx, "chewing.sort_candidates_by_frequency", sort);
    chewing_set_KBType(ctx, chewing_KBStr2Num("KB_DEFAULT"));
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_maxChiSymbolLen(ctx, 39);
    chewing_set_phraseChoiceRearward(ctx, 1);
    printf("== sort=%d(rc=%d) keys='%s' want '%s'\n", sort, rc, keys, target);
    for (const char *k = keys; *k; k++) chewing_handle_Default(ctx, *k);
    chewing_handle_Default(ctx, 'z');
    printf("    buffer='%s'\n", chewing_buffer_String_static(ctx));
    find(ctx, target, "cands");
    chewing_delete(ctx);
}

int main(int argc, char **argv) {
    const char *path = argc > 1 ? argv[1] : ".";
    const char *cases[][2] = {
        {"sc", "你好"}, {"vv", "謝謝"}, {"rw", "今天"}, {"ja", "我們"}, {"jga", "為什麼"},
        {"s", "你"}, {"sj", "你"}, {"wv", "他"}, {"ba", "的"},
    };
    for (int sort = 0; sort <= 1; sort++)
        for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++)
            run(path, sort, cases[i][0], cases[i][1]);
    return 0;
}
