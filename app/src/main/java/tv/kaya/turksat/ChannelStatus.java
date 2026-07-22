package tv.kaya.turksat;

enum ChannelStatus {
    CHECKING("◌", "Denetleniyor"),
    NATIVE("●", "Yerel"),
    WEB("◆", "Alternatif yayın"),
    UNAVAILABLE("○", "Geçici sorun");

    final String symbol;
    final String label;

    ChannelStatus(String symbol, String label) {
        this.symbol = symbol;
        this.label = label;
    }
}
