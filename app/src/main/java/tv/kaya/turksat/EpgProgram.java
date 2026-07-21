package tv.kaya.turksat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class EpgProgram {
    final long startTimeMs;
    final long endTimeMs;
    final String title;
    final String description;

    EpgProgram(long startTimeMs, long endTimeMs, String title, String description) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.title = title == null || title.trim().isEmpty() ? "Program bilgisi yok" : title.trim();
        this.description = description == null ? "" : description.trim();
    }

    String timeRange() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR"));
        return format.format(new Date(startTimeMs)) + "–" + format.format(new Date(endTimeMs));
    }

    int progressPercent(long now) {
        long duration = endTimeMs - startTimeMs;
        if (duration <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, (now - startTimeMs) * 100L / duration));
    }
}
