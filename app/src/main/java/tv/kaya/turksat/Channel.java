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
        return playbackUrl.contains(".m3u8") || playbackUrl.contains(".mpd");
    }
}
