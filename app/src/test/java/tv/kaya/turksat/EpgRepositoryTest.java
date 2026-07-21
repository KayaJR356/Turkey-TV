package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public final class EpgRepositoryTest {
    @Test
    public void parsesCurrentAndUpcomingProgramsAndMatchesHdSuffix() throws Exception {
        long now = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                .parse("20260721213000 +0300").getTime();
        String xml = "<?xml version='1.0' encoding='UTF-8'?><tv>"
                + "<channel id='TRT1.tr'><display-name>TR - TRT 1 HD</display-name></channel>"
                + "<programme channel='TRT1.tr' start='20260721210000 +0300' "
                + "stop='20260721220000 +0300'><title>Akşam Haberleri</title>"
                + "<desc>Günün gelişmeleri.</desc></programme>"
                + "<programme channel='TRT1.tr' start='20260721220000 +0300' "
                + "stop='20260721230000 +0300'><title>Yeni Dizi</title></programme>"
                + "</tv>";

        EpgRepository.GuideData guide = EpgRepository.parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), now);

        EpgProgram current = guide.current("TRT 1", now);
        assertNotNull(current);
        assertEquals("Akşam Haberleri", current.title);
        assertEquals("Yeni Dizi", guide.next("TRT 1", now).title);
        List<EpgProgram> upcoming = guide.upcoming("TRT 1", now, 8);
        assertEquals(2, upcoming.size());
        assertEquals(50, current.progressPercent(now));
    }

    @Test
    public void matchesGenericTvSuffix() throws Exception {
        long now = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                .parse("20260721213000 +0300").getTime();
        String xml = "<tv><channel id='now'><display-name>TR - NOW</display-name></channel>"
                + "<programme channel='now' start='20260721210000 +0300' "
                + "stop='20260721220000 +0300'><title>Ana Haber</title></programme></tv>";

        EpgRepository.GuideData guide = EpgRepository.parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), now);

        assertEquals("Ana Haber", guide.current("NOW TV", now).title);
    }
}
