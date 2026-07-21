package tv.kaya.turksat;

import java.util.Locale;

final class Channel {
    final int number;
    final String name;
    final String playbackUrl;
    final String pageUrl;
    final String sourceCategory;

    Channel(int number, String name, String playbackUrl, String pageUrl) {
        this(number, name, playbackUrl, pageUrl, "");
    }

    Channel(int number, String name, String playbackUrl, String pageUrl, String sourceCategory) {
        this.number = number;
        this.name = name;
        this.playbackUrl = playbackUrl;
        this.pageUrl = pageUrl;
        this.sourceCategory = sourceCategory == null ? "" : sourceCategory.trim();
    }

    boolean isDirectStream() {
        String value = playbackUrl.toLowerCase(Locale.ROOT);
        return value.contains(".m3u8")
                || value.contains(".mpd")
                || value.contains(".mp4")
                || value.contains("format=m3u8")
                || value.contains("format=mpd");
    }

    String key() {
        return normalize(name);
    }

    String category() {
        String catalogCategory = normalize(sourceCategory);
        if ("HABER".equals(catalogCategory)) {
            return "Haber";
        }
        if ("SPOR".equals(catalogCategory)) {
            return "Spor";
        }
        if ("COCUK".equals(catalogCategory)) {
            return "Çocuk";
        }
        if ("BELGESEL".equals(catalogCategory)) {
            return "Belgesel";
        }
        if ("YEREL".equals(catalogCategory)) {
            return "Yerel";
        }
        if ("DINI".equals(catalogCategory)) {
            return "Dini";
        }
        String value = key();
        if (containsAny(value, "HABER", "NEWS", "CNN", "NTV", "HABERTURK", "A HABER",
                "BLOOMBERG", "ULKE", "TGRT")) {
            return "Haber";
        }
        if (containsAny(value, "SPOR", "SPORT", "NBA", "FUTBOL", "BEIN")) {
            return "Spor";
        }
        if (containsAny(value, "COCUK", "CARTOON", "DISNEY", "MINIKA", "NICKELODEON",
                "BABY", "PIJAMASKELILER", "KRAL SAKIR")) {
            return "Çocuk";
        }
        if (containsAny(value, "BELGESEL", "DISCOVERY", "NATIONAL GEOGRAPHIC", "VIASAT",
                "ANIMAL PLANET", "HISTORY")) {
            return "Belgesel";
        }
        if (containsAny(value, "MUZIK", "MUSIC", "KRAL POP", "DREAM TURK", "POWER",
                "NUMBER1", "NR1")) {
            return "Müzik";
        }
        if (containsAny(value, "ANKARA", "ISTANBUL", "IZMIR", "BURSA", "ADANA", "KONYA",
                "KAYSERI", "GAZIANTEP", "ERZURUM", "TRABZON", "SAMSUN", "DIYARBAKIR",
                "ANTALYA", "MERSIN", "URFA", "ELAZIG", "MALATYA", "KOCAELI", "TEKIRDAG",
                "DENIZLI", "BALIKESIR", "CANAKKALE")) {
            return "Yerel";
        }
        return "Ulusal";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String value) {
        return value.toUpperCase(Locale.forLanguageTag("tr-TR"))
                .replace("İ", "I")
                .replace("Ş", "S")
                .replace("Ğ", "G")
                .replace("Ü", "U")
                .replace("Ö", "O")
                .replace("Ç", "C")
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
