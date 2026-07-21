package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ChannelTest {
    @Test
    public void infersCommonTurkishTvCategories() {
        assertEquals("Haber", channel("TRT Haber").category());
        assertEquals("Spor", channel("TRT Spor Yıldız").category());
        assertEquals("Çocuk", channel("TRT Çocuk").category());
        assertEquals("Belgesel", channel("TRT Belgesel").category());
        assertEquals("Müzik", channel("Dream Türk").category());
        assertEquals("Yerel", channel("Konya TV").category());
        assertEquals("Dini", new Channel(1, "Kanal İsmi", "https://example.com/live.m3u8",
                "https://www.canlitv.diy/tr/test", "dini").category());
        assertEquals("Ulusal", channel("Show TV").category());
    }

    private static Channel channel(String name) {
        return new Channel(1, name, "https://example.com/live.m3u8",
                "https://www.canlitv.diy/tr/test");
    }
}
