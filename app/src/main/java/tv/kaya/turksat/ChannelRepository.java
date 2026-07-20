package tv.kaya.turksat;

import android.content.Context;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelRepository {
    static final String CATALOG_URL = "https://www.canlitv.diy/tr";

    private static final String BASE_URL = "https://www.canlitv.diy";
    private static final String PLAYER_URL = BASE_URL + "/player/index.php?id=%s&mobile=1";
    private static final String PREFS = "tv_settings";
    private static final String CACHE_KEY = "channel_cache_v3";
    private static final int MINIMUM_COMPLETE_CATALOG_SIZE = 250;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/138.0.0.0 Safari/537.36";

    private static final Pattern CHANNEL_BLOCK = Pattern.compile(
            "<li\\s+class=['\\\"]ft_(\\d+)\\s+tv[^'\\\"]*['\\\"][^>]*>(.*?)</li>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\s+([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ADAPTIVE_STREAM_URL = Pattern.compile(
            "https?://[^\\s\\\"'<>\\\\]+?\\.(?:m3u8|mpd)(?:\\?[^\\s\\\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_URL = Pattern.compile(
            "<iframe[^>]+src=['\\\"]([^'\\\"]+)['\\\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANY_HTTPS_URL = Pattern.compile(
            "https://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> PREFERRED_STREAMS = createPreferredStreams();

    private ChannelRepository() {
    }

    static List<Channel> load(Context context) {
        try {
            List<Channel> online = downloadCatalog();
            if (online.size() >= MINIMUM_COMPLETE_CATALOG_SIZE) {
                saveCache(context, online);
                return online;
            }
        } catch (Exception ignored) {
            // The last complete catalogue is a better TV experience than a partial list.
        }

        List<Channel> cached = loadCache(context);
        if (cached.size() >= MINIMUM_COMPLETE_CATALOG_SIZE) {
            return cached;
        }
        return new ArrayList<>();
    }

    static String resolvePlaybackUrl(Channel channel) {
        if (channel.isDirectStream()) {
            return channel.playbackUrl;
        }
        try {
            String playerHtml = downloadText(channel.playbackUrl, channel.pageUrl);
            String normalizedHtml = playerHtml
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
                    .replace("&amp;", "&");
            Matcher matcher = ADAPTIVE_STREAM_URL.matcher(normalizedHtml);
            if (matcher.find()) {
                return matcher.group();
            }

            Matcher iframeMatcher = IFRAME_URL.matcher(normalizedHtml);
            while (iframeMatcher.find()) {
                String iframeUrl = iframeMatcher.group(1);
                if (iframeUrl.startsWith("//")) {
                    iframeUrl = "https:" + iframeUrl;
                } else if (iframeUrl.startsWith("/")) {
                    iframeUrl = BASE_URL + iframeUrl;
                }
                if (iframeUrl.startsWith("https://") && !isTrackingUrl(iframeUrl)) {
                    return withAutoplay(iframeUrl);
                }
            }

            Matcher urlMatcher = ANY_HTTPS_URL.matcher(normalizedHtml);
            while (urlMatcher.find()) {
                String candidate = urlMatcher.group()
                        .replaceAll("[),;]+$", "");
                if (!isTrackingUrl(candidate)
                        && !candidate.contains("canlitv.diy")
                        && !candidate.contains("canlitv.tel")) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            // Keep the channel's own source player when direct stream resolution fails.
        }
        return channel.playbackUrl;
    }

    private static boolean isTrackingUrl(String url) {
        return url.contains("google")
                || url.contains("doubleclick")
                || url.contains("cloudflare")
                || url.contains("w3.org");
    }

    private static String withAutoplay(String url) {
        if (url.contains("youtube.com/embed/") && !url.contains("autoplay=")) {
            return url + (url.contains("?") ? "&" : "?") + "autoplay=1";
        }
        return url;
    }

    private static List<Channel> downloadCatalog() throws Exception {
        String html = downloadText(CATALOG_URL);
        ArrayList<Channel> channels = new ArrayList<>();
        Matcher blockMatcher = CHANNEL_BLOCK.matcher(html);

        while (blockMatcher.find()) {
            String playerId = blockMatcher.group(1);
            Matcher anchorMatcher = ANCHOR.matcher(blockMatcher.group(2));
            while (anchorMatcher.find()) {
                String attributes = anchorMatcher.group(1);
                String title = attribute(attributes, "title");
                if (title == null || !title.toLowerCase(Locale.forLanguageTag("tr-TR"))
                        .endsWith(" canlı izle")) {
                    continue;
                }

                String href = attribute(attributes, "href");
                if (href == null || href.isEmpty()) {
                    break;
                }

                String name = decodeHtml(title.substring(0, title.length() - " canlı izle".length()).trim());
                String pageUrl = absoluteUrl(href);
                String preferredStream = PREFERRED_STREAMS.get(normalize(name));
                String playbackUrl = preferredStream != null
                        ? preferredStream
                        : String.format(Locale.ROOT, PLAYER_URL, playerId);
                channels.add(new Channel(channels.size() + 1, name, playbackUrl, pageUrl));
                break;
            }
        }
        return channels;
    }

    private static String downloadText(String source) throws Exception {
        return downloadText(source, null);
    }

    private static String downloadText(String source, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
        if (referer != null && referer.startsWith("https://")) {
            connection.setRequestProperty("Referer", referer);
        }

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
                return content.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String attribute(String attributes, String name) {
        Pattern pattern = Pattern.compile(
                "(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*(['\\\"])(.*?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(attributes);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static String decodeHtml(String value) {
        return Html.fromHtml(value).toString().trim();
    }

    private static String absoluteUrl(String href) {
        if (href.startsWith("https://")) {
            return href;
        }
        return BASE_URL + (href.startsWith("/") ? href : "/" + href);
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.forLanguageTag("tr-TR"))
                .replace("İ", "I")
                .replace("Ş", "S")
                .replace("Ğ", "G")
                .replace("Ü", "U")
                .replace("Ö", "O")
                .replace("Ç", "C")
                .replaceAll("[^A-Z0-9]", "");
    }

    private static void saveCache(Context context, List<Channel> channels) {
        try {
            JSONArray array = new JSONArray();
            for (Channel channel : channels) {
                JSONObject item = new JSONObject();
                item.put("number", channel.number);
                item.put("name", channel.name);
                item.put("playbackUrl", channel.playbackUrl);
                item.put("pageUrl", channel.pageUrl);
                array.put(item);
            }
            context.getSharedPreferences(PREFS, 0)
                    .edit()
                    .putString(CACHE_KEY, array.toString())
                    .apply();
        } catch (Exception ignored) {
            // A cache write must never interrupt playback.
        }
    }

    private static List<Channel> loadCache(Context context) {
        ArrayList<Channel> channels = new ArrayList<>();
        try {
            String raw = context.getSharedPreferences(PREFS, 0).getString(CACHE_KEY, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String playbackUrl = item.optString("playbackUrl", "");
                String pageUrl = item.optString("pageUrl", playbackUrl);
                if (playbackUrl.startsWith("https://") && pageUrl.startsWith("https://")) {
                    channels.add(new Channel(
                            item.getInt("number"),
                            item.getString("name"),
                            playbackUrl,
                            pageUrl));
                }
            }
        } catch (Exception ignored) {
            channels.clear();
        }
        return channels;
    }

    private static Map<String, String> createPreferredStreams() {
        HashMap<String, String> streams = new HashMap<>();
        streams.put(normalize("TRT 1"), "https://tv-trt1.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT 2"), "https://tv-trt2.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Haber"), "https://tv-trthaber.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Spor"), "https://tv-trtspor1.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Belgesel"), "https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Çocuk"), "https://tv-trtcocuk.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Müzik"), "https://tv-trtmuzik.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Türk"), "https://tv-trtturk.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Avaz"), "https://tv-trtavaz.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("CNBC-e"), "https://hnpsechtsc.turknet.ercdn.net/xpnvudnlsv/cnbc-e/cnbc-e.m3u8");
        streams.put(normalize("Yol Tv"), "https://live.yoltv.com/hls/stream.m3u8");
        return streams;
    }

}
