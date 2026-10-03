/**
 * Adapted from ViTune (https://github.com/bartoostveen/ViTune)
 * Original Author: Bart Oostveen
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 *
 * Adapted for Zyfen Music's canonical track identity and streaming architecture.
 */
package com.zyfen.music.data.source

import com.zyfen.music.data.media.Song

/**
 * Strict Canonical Track Identity Validator.
 * Enforces 1:1 parity between selected track and playback track.
 * Disallows fuzzy substitutions, covers, and remixes.
 */
object TrackMatcher {

    /**
     * Checks if two tracks have identical canonical identity.
     */
    fun isExactMatch(a: TrackIdentity, b: TrackIdentity): Boolean {
        if (a.sourceProvider == b.sourceProvider && a.sourceTrackId == b.sourceTrackId && a.sourceTrackId.isNotBlank()) {
            return true
        }
        return a.id == b.id
    }

    /**
     * Checks if a Song matches the canonical track identity.
     */
    fun matchesSong(identity: TrackIdentity, song: Song): Boolean {
        val songIdentity = TrackIdentity.fromSong(song)
        return isExactMatch(identity, songIdentity)
    }
}
