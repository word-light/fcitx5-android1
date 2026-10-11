// Emulates the SogaKey chewing glue (replay into a shadow context, dummy 'z',
// live candidates) to see what happens around Space used as the first tone.
#include <chewing.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

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

static const char *KEYS="1qaz2wsxedcrfv5tgbyhnujm8ik,9ol.0p;/-";
static const char *ZY[]={"ㄅ","ㄆ","ㄇ","ㄈ","ㄉ","ㄊ","ㄋ","ㄌ","ㄍ","ㄎ","ㄏ","ㄐ","ㄑ","ㄒ","ㄓ","ㄔ","ㄕ","ㄖ","ㄗ","ㄘ","ㄙ","ㄧ","ㄨ","ㄩ","ㄚ","ㄛ","ㄜ","ㄝ","ㄞ","ㄟ","ㄠ","ㄡ","ㄢ","ㄣ","ㄤ","ㄥ","ㄦ"};
static void exact_for(const char *typed) {
    int n = (int)strlen(typed);
    ChewingContext *s = mk();
    replay(s, typed, n);
    int len = chewing_get_phoneSeqLen(s);
    unsigned short *seq = chewing_get_phoneSeq(s);
    printf("== exact for '%s' phoneSeqLen=%d\n", typed, len);
    if (len < 1) return;
    char buf[64] = {0};
    chewing_phone_to_bopomofo(seq[len - 1], buf, sizeof buf);
    printf("  last syllable bopomofo='%s'\n", buf);
    char keys[32] = {0}; int kn = 0;
    const char *p = buf;
    while (*p) {
        int matched = 0;
        for (int z = 0; z < 37; z++) {
            size_t l = strlen(ZY[z]);
            if (!strncmp(p, ZY[z], l)) { keys[kn++] = KEYS[z]; p += l; matched = 1; break; }
        }
        if (!matched) { printf("  unmapped at '%s'\n", p); break; }
    }
    keys[kn++] = ' ';
    printf("  rebuilt keys='%s'\n", keys);
    ChewingContext *e = mk();
    chewing_config_set_int(e, "chewing.conversion_engine", 1);
    for (int i = 0; i < kn; i++) feed(e, keys[i]);
    state("exact", e);
    int r = chewing_cand_open(e);
    printf("  cand_open=%d total=%d:", r, chewing_cand_TotalChoice(e));
    chewing_cand_Enumerate(e);
    int k = 0, found = 0;
    while (chewing_cand_hasNext(e)) { const char *c = chewing_cand_String_static(e); if (k < 30) printf(" %s", c); if (!strcmp(c, "肌")) found = 1; k++; }
    printf("\n  contains 肌: %d\n", found);
    chewing_delete(e); chewing_delete(s);
}

// Where does a word land when listing phrases for typed keys (like buildLiveList)?
static void where(const char *keys, const char *target, int fuzzy) {
    int n = (int)strlen(keys);
    ChewingContext *s = mk();
    chewing_config_set_int(s, "chewing.conversion_engine", fuzzy ? 2 : 1);
    replay(s, keys, n);
    if (fuzzy) chewing_handle_Default(s, 'z');
    printf("== where keys='%s' target='%s' fuzzy=%d buffer='%s' len=%d\n", keys, target, fuzzy,
           chewing_buffer_String_static(s), chewing_buffer_Len(s));
    int r = chewing_cand_open(s);
    int total = chewing_cand_TotalChoice(s);
    chewing_cand_Enumerate(s);
    int idx = -1, k = 0, wl = chewing_buffer_Len(s);
    int same = 0, sameIdx = -1;
    const char *first[6]; int fc = 0; char firstbuf[6][32];
    while (chewing_cand_hasNext(s)) {
        const char *c = chewing_cand_String_static(s);
        if (idx < 0 && !strcmp(c, target)) idx = k;
        if (fc < 6) { strncpy(firstbuf[fc], c, 31); firstbuf[fc][31] = 0; fc++; }
        k++;
    }
    printf("  cand_open=%d total=%d enumerated=%d index_of_target=%d first:", r, total, k, idx);
    for (int i = 0; i < fc; i++) printf(" %s", firstbuf[i]);
    printf("\n");
    (void)wl; (void)same; (void)sameIdx; (void)first;
    chewing_cand_close(s);
    chewing_delete(s);
}

// coverage: read word\tkeys\tfreq lines; is each word offered by the exact engine?
static void coverage(const char *file, int limit) {
    FILE *fp = fopen(file, "r");
    if (!fp) { printf("no words file\n"); return; }
    char line[512];
    int total = 0, found = 0, shown = 0;
    ChewingContext *e = mk();
    chewing_config_set_int(e, "chewing.conversion_engine", 1);
    while (fgets(line, sizeof line, fp) && total < limit) {
        char *w = strtok(line, "\t"), *k = strtok(NULL, "\t"), *f = strtok(NULL, "\t\n");
        if (!w || !k) continue;
        total++;
        chewing_cand_close(e); chewing_clean_preedit_buf(e); chewing_clean_bopomofo_buf(e); chewing_Reset(e);
        for (const char *c = k; *c; c++) feed(e, *c);
        int ok = !strcmp(chewing_buffer_String_static(e), w);
        if (!ok && chewing_cand_open(e) == 0) {
            chewing_cand_Enumerate(e);
            while (chewing_cand_hasNext(e)) { if (!strcmp(chewing_cand_String_static(e), w)) { ok = 1; break; } }
            chewing_cand_close(e);
        }
        if (ok) found++; else if (shown < 400) { printf("MISSING %s %s %s\n", w, k, f ? f : ""); shown++; }
    }
    printf("COVERAGE %d/%d\n", found, total);
    chewing_delete(e);
}
int main(int argc, char **argv) {
    path = argc > 1 ? argv[1] : ".";
    if (argc > 2) { coverage(argv[2], argc > 3 ? atoi(argv[3]) : 5000); return 0; }
    const char *cases[] = {"su", "su ", "su3", "su ru", "su ru ", "su cl3", "g. ", "g.3 ", "5j ", "g. ru", NULL};
    for (int i = 0; cases[i]; i++) run(cases[i]);
    where("u654", "一致", 0);
    where("u654", "一致", 1);
    where("u 54", "一致", 1);
    where("u 5", "一致", 1);
    where("u6 5", "一致", 1);
    where("u65", "一致", 1);
    where("u454", "一致", 0);
    where("u5", "一致", 1);
    where("u5", "一直", 1);
    where("u54", "一致", 1);
    where("u45", "一致", 1);
    where("u4", "一", 1);
    where("g.r", "手機", 1);
    exact_for("su ru ");
    exact_for("ru ");
    exact_for("ru");
    exact_for("a3 ru ");
    return 0;
}
