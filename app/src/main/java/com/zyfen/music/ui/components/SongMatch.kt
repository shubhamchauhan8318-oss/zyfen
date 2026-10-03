package com.zyfen.music.ui.components

import com.zyfen.music.data.media.Song

/**
 * Matches imported Spotify tracks against songs that already exist on the device,
 * so imported playlists play inside ZYFEN MUSIC (never downloads Spotify audio).
 *
 * SongIndex precomputes normalized titles + a token->positions map once, so
 * lookups stay O(cheap candidates) even on large libraries (no per-frame jank).
 */
object SongMatch {

    fun norm(s: String): String {
        var x = s.lowercase()
        x = x.substringBefore(" | ")
        x = Regex("""\([^)]*\)|\[[^\]]*\]""").replace(x, " ")
        x = x.replace(Regex("""[^a-z0-9]+"""), " ")
        return x.split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }

    fun build(songs: List<Song>, index: SongIndex): Pair<Map<String, Song>, List<Song?>> {
        val matches = songs.asSequence()
            .filter { !it.isLocal }
            .mapNotNull { sp -> index.find(sp)?.let { sp.id to it } }
            .toMap()
        val aligned = songs.map { if (it.isLocal) it else matches[it.id] }
        return matches to aligned
    }
}

class SongIndex(private val locals: List<Song>) {

    private val titleNorms: Array<String>
    private val artistNorms: Array<String>
    private val albumNorms: Array<String>
    private val byTitle = HashMap<String, Int>(locals.size * 2)
    private val byToken = HashMap<String, MutableList<Int>>(locals.size * 4)

    init {
        titleNorms = Array(locals.size) { SongMatch.norm(locals[it].title) }
        artistNorms = Array(locals.size) { SongMatch.norm(locals[it].artist) }
        albumNorms = Array(locals.size) { SongMatch.norm(locals[it].album) }
        locals.indices.forEach { i ->
            val t = titleNorms[i]
            if (t.length < 3) return@forEach
            byTitle.putIfAbsent(t, i)
            t.split(' ').forEach { tok ->
                if (tok.length >= 3) byToken.getOrPut(tok) { ArrayList(4) }.add(i)
            }
        }
    }

    private fun artistOverlap(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return true // missing metadata — can't disprove
        val at = a.split(' ')
        val bt = b.split(' ')
        return at.any { it.length > 2 && b.contains(it) } || bt.any { it.length > 2 && a.contains(it) }
    }

    fun find(target: Song): Song? {
        if (target.isLocal) return target
        val tt = SongMatch.norm(target.title)
        if (tt.length < 3) return null
        val ta = SongMatch.norm(target.artist)
        val albumTarget = SongMatch.norm(target.album)

        byTitle[tt]?.let { i ->
            // exact normalized title: accept immediately unless artists clearly clash
            if (artistOverlap(ta, artistNorms[i])) return locals[i]
        }

        val tTokens = tt.split(' ')
        if (tTokens.isEmpty()) return null

        val candidates = LinkedHashSet<Int>()
        tTokens.sortedByDescending { it.length }.take(3).forEach { seed ->
            byToken[seed]?.forEach { i -> if (candidates.size < 150) candidates.add(i) }
        }
        if (candidates.isEmpty()) return null

        var strong: Song? = null  // title + artist verified
        var weak: Song? = null    // only when artist info is missing on one side

        for (i in candidates) {
            val lt = titleNorms[i]
            if (lt.length < 3) continue
            val cTokens = lt.split(' ')
            val common = cTokens.count { it in tTokens }
            val titleOk = lt == tt ||
                (minOf(lt.length, tt.length) >= 6 && (lt.contains(tt) || tt.contains(lt))) ||
                (common >= 2 && common * 2 >= minOf(tTokens.size, cTokens.size))
            if (!titleOk) continue

            val la = artistNorms[i]
            if (artistOverlap(ta, la)) {
                // album breaks ties between same-title/artist candidates (e.g. live vs studio)
                if (albumTarget.isNotEmpty() && albumNorms[i] == albumTarget) return locals[i]
                if (strong == null) strong = locals[i]
            } else if (weak == null && (ta.isEmpty() || la.isEmpty())) {
                weak = locals[i]
            }
        }
        return strong ?: weak
    }
}
