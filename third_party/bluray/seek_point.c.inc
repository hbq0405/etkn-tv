/* Appended to pinned libbluray's bluray.c by Build-BlurayNative.py.
 * A Media3 SeekMap query must never seek, read or call back into Java.
 * nav_time_search uses the already loaded playlist/CLPI index only.
 */
int64_t etkn_bd_seek_point(BLURAY *bd, uint64_t tick)
{
    uint32_t clip_pkt = 0, out_pkt = 0;
    int64_t result = -1;
    bd_mutex_lock(&bd->mutex);
    if (bd->title && bd->title->duration) {
        uint64_t time = tick / 2;
        if (time >= bd->title->duration) time = bd->title->duration - 1;
        if (nav_time_search(bd->title, (uint32_t)time, &clip_pkt, &out_pkt))
            result = (int64_t)out_pkt * 192;
    }
    bd_mutex_unlock(&bd->mutex);
    return result;
}
