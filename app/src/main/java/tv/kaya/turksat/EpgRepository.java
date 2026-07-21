package tv.kaya.turksat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParserFactory;

final class EpgRepository {
    static final String SOURCE_URL = "https://iptv-epg.org/files/epg-tr.xml.gz";
    private static final String CACHE_KEY = "epg_cache_v1";
    private static final String CACHE_TIME_KEY = "epg_cache_time_v1";
    private static final long CACHE_TTL_MS = 6L * 60L * 60L * 1_000L;
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PROGRAMS_PER_CHANNEL = 12;

    interface Callback {
        void onGuide(GuideData guide, boolean fresh);
    }

    static final class GuideData {
        private final Map<String, List<EpgProgram>> byChannel;

        GuideData(Map<String, List<EpgProgram>> byChannel) {
            this.byChannel = byChannel;
        }

        EpgProgram current(String channelName, long now) {
            for (EpgProgram program : programs(channelName)) {
                if (program.startTimeMs <= now && program.endTimeMs > now) {
                    return program;
                }
            }
            return null;
        }

        EpgProgram next(String channelName, long now) {
            for (EpgProgram program : programs(channelName)) {
                if (program.startTimeMs > now) {
                    return program;
                }
            }
            return null;
        }

        int channelCount() {
            return byChannel.size();
        }

        List<EpgProgram> upcoming(String channelName, long now, int limit) {
            ArrayList<EpgProgram> result = new ArrayList<>();
            for (EpgProgram program : programs(channelName)) {
                if (program.endTimeMs > now) {
                    result.add(program);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        }

        private List<EpgProgram> programs(String channelName) {
            String wanted = guideKey(channelName);
            List<EpgProgram> exact = byChannel.get(wanted);
            if (exact != null) {
                return exact;
            }
            String compact = compactGuideKey(wanted);
            for (Map.Entry<String, List<EpgProgram>> entry : byChannel.entrySet()) {
                String candidate = compactGuideKey(entry.getKey());
                if (candidate.equals(compact)
                        || (candidate.length() >= 4 && compact.length() >= 4
                        && (candidate.startsWith(compact) || compact.startsWith(candidate)))) {
                    return entry.getValue();
                }
            }
            return Collections.emptyList();
        }

        private static String compactGuideKey(String value) {
            String compact = value.replace(" ", "");
            if (compact.endsWith("TV") && compact.length() > 4) {
                return compact.substring(0, compact.length() - 2);
            }
            return compact;
        }
    }

    private EpgRepository() {
    }

    static void load(Context context, boolean forceRefresh, Callback callback) {
        Context appContext = context.getApplicationContext();
        GuideData cached = readCache(appContext);
        if (cached.channelCount() > 0) {
            callback.onGuide(cached, false);
        }
        long cacheTime = appContext.getSharedPreferences(ChannelUserData.PREFS, 0)
                .getLong(CACHE_TIME_KEY, 0L);
        if (!forceRefresh && cached.channelCount() > 0
                && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
            return;
        }
        new Thread(() -> {
            try {
                GuideData fresh = downloadGuide();
                if (fresh.channelCount() > 0) {
                    saveCache(appContext, fresh);
                    new Handler(Looper.getMainLooper()).post(() -> callback.onGuide(fresh, true));
                }
            } catch (Exception ignored) {
                // Cached data remains available when the public EPG source is temporarily offline.
            }
        }, "epg-refresh").start();
    }

    static GuideData parseXml(InputStream input, long now) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        trySetFeature(reader, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false);
        GuideHandler handler = new GuideHandler(now);
        reader.setContentHandler(handler);
        reader.parse(new InputSource(input));
        return handler.result();
    }

    private static GuideData downloadGuide() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(SOURCE_URL).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(18_000);
        connection.setRequestProperty("User-Agent", "TurkiyeCanliTV/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", "application/gzip, application/xml;q=0.9");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("EPG HTTP " + connection.getResponseCode());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[32 * 1024];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IllegalStateException("EPG download is too large");
                    }
                    output.write(buffer, 0, read);
                }
            }
            try (InputStream gzip = new GZIPInputStream(
                    new ByteArrayInputStream(output.toByteArray()))) {
                return parseXml(gzip, System.currentTimeMillis());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void trySetFeature(XMLReader reader, String feature, boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (Exception ignored) {
            // Android parser implementations do not expose every Xerces feature.
        }
    }

    private static void saveCache(Context context, GuideData guide) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<EpgProgram>> entry : guide.byChannel.entrySet()) {
                JSONArray programs = new JSONArray();
                int count = 0;
                for (EpgProgram program : entry.getValue()) {
                    if (count++ >= MAX_PROGRAMS_PER_CHANNEL) {
                        break;
                    }
                    JSONObject item = new JSONObject();
                    item.put("start", program.startTimeMs);
                    item.put("end", program.endTimeMs);
                    item.put("title", program.title);
                    item.put("description", program.description);
                    programs.put(item);
                }
                root.put(entry.getKey(), programs);
            }
            context.getSharedPreferences(ChannelUserData.PREFS, 0).edit()
                    .putString(CACHE_KEY, root.toString())
                    .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // EPG cache failure must never interrupt playback.
        }
    }

    private static GuideData readCache(Context context) {
        LinkedHashMap<String, List<EpgProgram>> guide = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(context.getSharedPreferences(ChannelUserData.PREFS, 0)
                    .getString(CACHE_KEY, "{}"));
            JSONArray names = root.names();
            if (names == null) {
                return new GuideData(guide);
            }
            long cutoff = System.currentTimeMillis() - 2L * 60L * 60L * 1_000L;
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                JSONArray items = root.getJSONArray(name);
                ArrayList<EpgProgram> programs = new ArrayList<>();
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);
                    EpgProgram program = new EpgProgram(item.getLong("start"),
                            item.getLong("end"), item.optString("title"),
                            item.optString("description"));
                    if (program.endTimeMs >= cutoff) {
                        programs.add(program);
                    }
                }
                if (!programs.isEmpty()) {
                    guide.put(name, programs);
                }
            }
        } catch (Exception ignored) {
            guide.clear();
        }
        return new GuideData(guide);
    }

    private static String guideKey(String value) {
        return Channel.normalize(value)
                .replaceFirst("^(?:TR|TURKEY|TURKIYE) ", "")
                .replaceAll("(?:^| )(?:HD|FHD|UHD|4K)(?: |$)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static long parseXmlTvTime(String value) {
        if (value == null) {
            return 0L;
        }
        String trimmed = value.trim();
        String[] patterns = {"yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmss"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            if (!pattern.contains("Z")) {
                format.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));
            }
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(trimmed, position);
            if (date != null && position.getIndex() == trimmed.length()) {
                return date.getTime();
            }
        }
        return 0L;
    }

    private static final class GuideHandler extends DefaultHandler {
        private final long minimumTime;
        private final long maximumTime;
        private final Map<String, String> channelNames = new HashMap<>();
        private final Map<String, List<EpgProgram>> programsById = new HashMap<>();
        private final StringBuilder text = new StringBuilder();
        private String channelId;
        private String programmeChannel;
        private long programmeStart;
        private long programmeEnd;
        private String programmeTitle;
        private String programmeDescription;
        private String element;

        GuideHandler(long now) {
            minimumTime = now - 2L * 60L * 60L * 1_000L;
            maximumTime = now + 30L * 60L * 60L * 1_000L;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            element = qName;
            text.setLength(0);
            if ("channel".equals(qName)) {
                channelId = attributes.getValue("id");
            } else if ("programme".equals(qName)) {
                programmeChannel = attributes.getValue("channel");
                programmeStart = parseXmlTvTime(attributes.getValue("start"));
                programmeEnd = parseXmlTvTime(attributes.getValue("stop"));
                programmeTitle = "";
                programmeDescription = "";
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if ("display-name".equals(element) || "title".equals(element)
                    || "desc".equals(element)) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("display-name".equals(qName) && channelId != null
                    && !channelNames.containsKey(channelId)) {
                channelNames.put(channelId, text.toString().trim());
            } else if ("title".equals(qName) && programmeChannel != null) {
                programmeTitle = text.toString().trim();
            } else if ("desc".equals(qName) && programmeChannel != null) {
                programmeDescription = text.toString().trim();
            } else if ("programme".equals(qName)) {
                if (programmeChannel != null && programmeEnd >= minimumTime
                        && programmeStart <= maximumTime && programmeEnd > programmeStart) {
                    List<EpgProgram> programs = programsById.get(programmeChannel);
                    if (programs == null) {
                        programs = new ArrayList<>();
                        programsById.put(programmeChannel, programs);
                    }
                    programs.add(new EpgProgram(programmeStart, programmeEnd,
                            programmeTitle, programmeDescription));
                }
                programmeChannel = null;
            } else if ("channel".equals(qName)) {
                channelId = null;
            }
            element = null;
            text.setLength(0);
        }

        GuideData result() {
            LinkedHashMap<String, List<EpgProgram>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<EpgProgram>> entry : programsById.entrySet()) {
                String name = channelNames.get(entry.getKey());
                if (name == null || name.trim().isEmpty()) {
                    name = entry.getKey();
                }
                Collections.sort(entry.getValue(),
                        (left, right) -> Long.compare(left.startTimeMs, right.startTimeMs));
                result.put(guideKey(name), entry.getValue());
            }
            return new GuideData(result);
        }
    }
}
