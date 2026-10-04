package dev.afkmod.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** The content of {@code config/afkmod-stats.json}. */
final class StatsData {
	int version = 1;
	LifetimeStats lifetime = new LifetimeStats();
	/** Newest first, at most {@link AfkStats#MAX_RECENT}. */
	List<SessionRecord> recent = new ArrayList<>();
	/** The running session as of the last periodic save; turned into an INTERRUPTED session if found on load. */
	SessionRecord inProgress;

	/** Repairs fields that a hand-edited or partly written file can leave null. */
	void sanitize() {
		if (lifetime == null) lifetime = new LifetimeStats();
		if (lifetime.endReasons == null) lifetime.endReasons = new LinkedHashMap<>();
		if (recent == null) recent = new ArrayList<>();
		recent.removeIf(Objects::isNull);
		recent.removeIf(r -> r.test);
		while (recent.size() > AfkStats.MAX_RECENT) recent.remove(recent.size() - 1);
		if (inProgress != null && inProgress.test) inProgress = null;
	}
}
