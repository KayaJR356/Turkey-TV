package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class ChannelRepositoryTest {
    @Test
    public void parsesChannelsRegardlessOfClassOrderAndNameSource() {
        String html = "<ul>"
                + "<li class='featured tv ft_101'><a href='/tr/trt-1' "
                + "title='TRT 1 canlı izle'>TRT</a></li>"
                + "<li data-kind='live' class='ft_202 extra tv'><a href='show-tv'>"
                + "<span>Show TV</span></a><a data-kat='spor'></a></li>"
                + "<li class='tv ft_303'><a href='/tr/star-tv'><img alt='Star TV'></a></li>"
                + "</ul>";

        List<Channel> channels = ChannelRepository.parseCatalogHtml(html);

        assertEquals(3, channels.size());
        assertEquals(1, channels.get(0).number);
        assertEquals("TRT 1", channels.get(0).name);
        assertEquals("Show TV", channels.get(1).name);
        assertEquals("Star TV", channels.get(2).name);
        assertEquals("Spor", channels.get(1).category());
        assertEquals("https://www.canlitv.diy/show-tv", channels.get(1).pageUrl);
        assertTrue(channels.get(0).isDirectStream());
        assertEquals("https://www.canlitv.diy/player/index.php?id=202&mobile=1",
                channels.get(1).playbackUrl);
    }

    @Test
    public void ignoresDuplicatePlayersAndUntrustedLinks() {
        String html = "<li class='tv ft_1'><a href='/tr/one' title='Bir canlı izle'></a></li>"
                + "<li class='ft_1 tv'><a href='/tr/duplicate' title='Kopya canlı izle'></a></li>"
                + "<li class='tv ft_2'><a href='https://example.com/two' "
                + "title='Dış canlı izle'></a></li>";

        List<Channel> channels = ChannelRepository.parseCatalogHtml(html);

        assertEquals(1, channels.size());
        assertEquals("Bir", channels.get(0).name);
    }

    @Test
    public void extractsDynamicPlayerTargetInsteadOfAdvertisementScripts() {
        String source = "https://www.canlitv.diy/player/index.php?id=4&mobile=1";
        String html = "<script src='https://imasdk.googleapis.com/js/sdkloader/ima3.js'></script>"
                + "<script>const videoSrc='https://www.canlitv.diy/player/html5video.php?"
                + "url=https://cdn.example.test/live/show.m3u8';"
                + "function showGame(){game.innerHTML='<iframe src=\"' + videoSrc + '\" />';}"
                + "</script>";

        assertEquals("https://www.canlitv.diy/player/html5video.php?"
                        + "url=https://cdn.example.test/live/show.m3u8",
                ChannelRepository.extractWebPlayerUrl(source, html));
    }

    @Test
    public void extractsTrustedExternalIframeAndRejectsUnknownHost() {
        String source = "https://www.canlitv.diy/player/index.php?id=11222&mobile=1";

        assertEquals("https://www.youtube.com/embed/example",
                ChannelRepository.extractWebPlayerUrl(source,
                        "<iframe src='https://www.youtube.com/embed/example'></iframe>"));
        assertNull(ChannelRepository.extractWebPlayerUrl(source,
                "<iframe src='https://example.com/advertisement'></iframe>"));
    }
}
