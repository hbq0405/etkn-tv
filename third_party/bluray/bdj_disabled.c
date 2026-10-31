/* ETKN plays a selected title; it does not execute disc Java applications. */
#include "libbluray/bdj/bdj.h"
#include <stdlib.h>
BDJAVA *bdj_open(const char *path, struct bluray *bd, const char *id, BDJ_CONFIG *storage) { return NULL; }
void bdj_close(BDJAVA *bdjava) {}
int bdj_process_event(BDJAVA *bdjava, unsigned event, unsigned param) { return 0; }
int bdj_jvm_available(BDJ_CONFIG *storage) { return BDJ_CHECK_NO_JVM; }
void bdj_config_cleanup(BDJ_CONFIG *storage) {
    free(storage->persistent_root); free(storage->cache_root);
    free(storage->java_home); free(storage->classpath[0]); free(storage->classpath[1]);
}
