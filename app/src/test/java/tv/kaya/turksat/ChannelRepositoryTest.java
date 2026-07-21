package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
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
                + "<span>Show TV</span></a></li>"
                + "<li class='tv ft_303'><a href='/tr/star-tv'><img alt='Star TV'></a></li>"
                + "</ul>";

        List<Channel> channels = ChannelRepository.parseCatalogHtml(html);

        assertEquals(3, channels.size());
        assertEquals(1, channels.get(0).number);
        assertEquals("TRT 1", channels.get(0).name);
        assertEquals("Show TV", channels.get(1).name);
        assertEquals("Star TV", channels.get(2).name);
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
}
