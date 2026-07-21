package tv.kaya.turksat;

final class Channel {
    final int number;
    final String name;
    final String playbackUrl;
    final String pageUrl;

    Channel(int number, String name, String playbackUrl, String pageUrl) {
        this.number = number;
        this.name = name;
        this.playbackUrl = playbackUrl;
        this.pageUrl = pageUrl;
    }

    boolean isDirectStream() {
        String value = playbackUrl.toLowerCase(java.util.Locale.ROOT);
        return value.contains(".m3u8")
                || value.contains(".mpd")
                || value.contains(".mp4")
                || value.contains("format=m3u8")
                || value.contains("format=mpd");
    }
}
