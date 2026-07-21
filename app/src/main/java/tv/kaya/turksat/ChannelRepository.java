package tv.kaya.turksat;

import android.content.Context;
import android.util.Base64;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelRepository {
    static final String CATALOG_URL = "https://www.canlitv.diy/tr";

    private static final String BASE_URL = "https://www.canlitv.diy";
    private static final String PLAYER_URL = BASE_URL + "/player/index.php?id=%s&mobile=1";
    private static final String PREFS = "tv_settings";
    private static final String CACHE_KEY = "channel_cache_v3";
    private static final int MINIMUM_COMPLETE_CATALOG_SIZE = 250;
    private static final int MAX_RESOLVE_DEPTH = 3;
    private static final int MAX_DOCUMENT_CHARS = 1_250_000;
    private static final long MAX_RESOLVE_TIME_MS = 11_000L;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/138.0.0.0 Safari/537.36";

    private static final Pattern PLAYER_CLASS = Pattern.compile("^ft_(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_STREAM_URL = Pattern.compile(
            "https?://[^\\s\\\"'<>\\\\]+?(?:\\.(?:m3u8|mpd|mp4)|/manifest)"
                    + "(?:\\?[^\\s\\\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_URL = Pattern.compile(
            "<(?:iframe|video|source)[^>]+(?:src|data-src|data-lazy-src)\\s*=\\s*"
                    + "['\\\"]([^'\\\"]+)['\\\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENCODED_URL = Pattern.compile(
            "https?%3A%2F%2F[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE64_PAYLOAD = Pattern.compile(
            "(?:atob|Base64\\.decode)\\s*\\(\\s*['\\\"]([A-Za-z0-9+/=]{24,8192})['\\\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_URL = Pattern.compile(
            "['\\\"](https?://[^'\\\"<>]+)['\\\"]",
            Pattern.CASE_INSENSITIVE);
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
        return resolvePlaybackUrl(channel, Collections.emptySet());
    }

    static String resolvePlaybackUrl(Channel channel, Set<String> excludedStreams) {
        Set<String> excluded = sanitizeExcludedStreams(excludedStreams);
        if (channel.isDirectStream() && !excluded.contains(sanitizeUrl(channel.playbackUrl))
                && isStreamReachable(channel.playbackUrl, channel.pageUrl)) {
            return channel.playbackUrl;
        }

        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (!channel.isDirectStream()) {
            sources.add(channel.playbackUrl);
        }
        sources.add(channel.pageUrl);

        ExecutorService executor = Executors.newFixedThreadPool(sources.size());
        CompletionService<String> completion = new ExecutorCompletionService<>(executor);
        ArrayList<Future<String>> futures = new ArrayList<>();
        try {
            for (String source : sources) {
                futures.add(completion.submit(() -> {
                    try {
                        return resolveNativeStream(source, channel.pageUrl, 0,
                                new HashSet<>(), excluded);
                    } catch (Exception ignored) {
                        return null;
                    }
                }));
            }

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_RESOLVE_TIME_MS);
            for (int i = 0; i < sources.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                Future<String> completed = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (completed == null) {
                    break;
                }
                String resolved = completed.get();
                if (resolved != null) {
                    return resolved;
                }
            }
        } catch (Exception ignored) {
            // A failed source must not prevent the alternate player/page source from being tried.
        } finally {
            for (Future<String> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
        // Native playback is intentionally strict: never return a web page as media.
        return null;
    }

    private static String resolveNativeStream(
            String source,
            String referer,
            int depth,
            Set<String> visited,
            Set<String> excluded) throws Exception {
        if (source == null || depth > MAX_RESOLVE_DEPTH || !source.startsWith("https://")
                || isBrowserOnlySource(source) || !visited.add(source)) {
            return null;
        }
        if (isPlayableStream(source)) {
            String stream = sanitizeUrl(source);
            return excluded.contains(stream) || !isStreamReachable(stream, referer)
                    ? null : stream;
        }

        String normalizedHtml = normalizeDocument(downloadText(source, referer));
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        collectMediaUrls(normalizedHtml, candidates);

        Matcher encodedMatcher = ENCODED_URL.matcher(normalizedHtml);
        while (encodedMatcher.find()) {
            try {
                String decoded = URLDecoder.decode(encodedMatcher.group(), StandardCharsets.UTF_8.name());
                collectCandidate(decoded, candidates);
            } catch (Exception ignored) {
                // Ignore malformed percent-encoded values and continue scanning.
            }
        }

        Matcher base64Matcher = BASE64_PAYLOAD.matcher(normalizedHtml);
        while (base64Matcher.find()) {
            try {
                String decoded = new String(
                        Base64.decode(base64Matcher.group(1), Base64.DEFAULT),
                        StandardCharsets.UTF_8);
                collectMediaUrls(normalizeDocument(decoded), candidates);
            } catch (Exception ignored) {
                // Player pages often contain unrelated base64 assets.
            }
        }

        for (String candidate : candidates) {
            if (isPlayableStream(candidate) && !excluded.contains(sanitizeUrl(candidate))
                    && isStreamReachable(candidate, source)) {
                return candidate;
            }
        }

        Matcher embedMatcher = EMBED_URL.matcher(normalizedHtml);
        while (embedMatcher.find()) {
            String embedUrl = absoluteUrl(source, sanitizeUrl(embedMatcher.group(1)));
            String nestedStream = resolveNativeStream(
                    embedUrl, source, depth + 1, visited, excluded);
            if (nestedStream != null) {
                return nestedStream;
            }
        }

        Matcher quotedMatcher = QUOTED_URL.matcher(normalizedHtml);
        while (quotedMatcher.find()) {
            String candidate = sanitizeUrl(quotedMatcher.group(1));
            if (isLikelyPlayerPage(candidate)) {
                String nestedStream = resolveNativeStream(
                        candidate, source, depth + 1, visited, excluded);
                if (nestedStream != null) {
                    return nestedStream;
                }
            }
        }
        return null;
    }

    private static String absoluteUrl(String base, String candidate) {
        try {
            if (candidate.startsWith("//")) {
                return "https:" + candidate;
            }
            return new URL(new URL(base), candidate).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void collectMediaUrls(String source, Set<String> candidates) {
        Matcher matcher = MEDIA_STREAM_URL.matcher(source);
        while (matcher.find()) {
            collectCandidate(matcher.group(), candidates);
        }
    }

    private static void collectCandidate(String raw, Set<String> candidates) {
        String candidate = sanitizeUrl(raw);
        if (candidate.startsWith("https://") && !isBrowserOnlySource(candidate)
                && !isTrackingSource(candidate)) {
            candidates.add(candidate);
        }
    }

    private static String sanitizeUrl(String value) {
        return value == null ? "" : value
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\u003A", ":")
                .replace("\\x26", "&")
                .replace("\\x2F", "/")
                .replace("\\x3A", ":")
                .replace("&amp;", "&")
                .replaceAll("[\\\\),;]+$", "")
                .trim();
    }

    private static Set<String> sanitizeExcludedStreams(Set<String> excludedStreams) {
        if (excludedStreams == null || excludedStreams.isEmpty()) {
            return Collections.emptySet();
        }
        HashSet<String> sanitized = new HashSet<>();
        for (String value : excludedStreams) {
            String stream = sanitizeUrl(value);
            if (!stream.isEmpty()) {
                sanitized.add(stream);
            }
        }
        return sanitized;
    }

    private static String normalizeDocument(String value) {
        return sanitizeUrl(value)
                .replace("\\u002f", "/")
                .replace("\\u003a", ":")
                .replace("\\x2f", "/")
                .replace("\\x3a", ":");
    }

    private static boolean isPlayableStream(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains(".m3u8")
                || value.contains(".mpd")
                || value.contains(".mp4")
                || value.contains("format=m3u8")
                || value.contains("format=mpd");
    }

    private static boolean isLikelyPlayerPage(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        return value.startsWith("https://")
                && !isBrowserOnlySource(value)
                && !isTrackingSource(value)
                && (value.contains("/player")
                || value.contains("/embed")
                || value.contains("stream")
                || value.contains("live"));
    }

    private static boolean isTrackingSource(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("doubleclick")
                || value.contains("googlesyndication")
                || value.contains("google-analytics")
                || value.contains("facebook.com/tr")
                || value.contains("/ads/");
    }

    private static boolean isBrowserOnlySource(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("youtube.com")
                || value.contains("youtu.be")
                || value.contains("youtube-nocookie.com")
                || value.contains("googlevideo.com")
                || value.contains("doubleclick");
    }

    private static boolean isStreamReachable(String source, String referer) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(source);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return false;
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(6500);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
            connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, "
                    + "application/dash+xml, video/mp4, */*");
            if (referer != null && referer.startsWith("https://")) {
                connection.setRequestProperty("Referer", referer);
            }

            String lower = source.toLowerCase(Locale.ROOT);
            boolean manifest = lower.contains(".m3u8") || lower.contains(".mpd")
                    || lower.contains("format=m3u8") || lower.contains("format=mpd");
            if (!manifest) {
                connection.setRequestProperty("Range", "bytes=0-1");
            }

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK
                    && status != HttpURLConnection.HTTP_PARTIAL) {
                return false;
            }
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                return false;
            }
            if (!manifest) {
                return connection.getContentLength() != 0;
            }

            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read = input.read(buffer);
                if (read <= 0) {
                    return false;
                }
                String header = new String(buffer, 0, read, StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                return lower.contains(".mpd") || lower.contains("format=mpd")
                        ? header.contains("<mpd") : header.contains("#extm3u");
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<Channel> downloadCatalog() throws Exception {
        return parseCatalogHtml(downloadText(CATALOG_URL));
    }

    static List<Channel> parseCatalogHtml(String html) {
        ArrayList<Channel> channels = new ArrayList<>();
        HashSet<String> seenPlayers = new HashSet<>();
        if (html == null || html.isEmpty()) {
            return channels;
        }

        for (Element row : Jsoup.parse(html, CATALOG_URL).select("li.tv")) {
            String playerId = null;
            for (String className : row.classNames()) {
                Matcher matcher = PLAYER_CLASS.matcher(className);
                if (matcher.matches()) {
                    playerId = matcher.group(1);
                    break;
                }
            }
            if (playerId == null || seenPlayers.contains(playerId)) {
                continue;
            }

            Element link = row.selectFirst("a[href]");
            if (link == null) {
                continue;
            }
            String pageUrl = trustedCatalogUrl(link.attr("href"));
            if (pageUrl == null) {
                continue;
            }

            String title = link.attr("title").trim();
            String suffix = " canlı izle";
            String name = title;
            if (title.toLowerCase(Locale.forLanguageTag("tr-TR")).endsWith(suffix)) {
                name = title.substring(0, title.length() - suffix.length()).trim();
            }
            if (name.isEmpty()) {
                name = link.text().trim();
            }
            if (name.isEmpty()) {
                Element image = link.selectFirst("img[alt]");
                name = image == null ? "" : image.attr("alt").trim();
            }
            if (name.isEmpty()) {
                continue;
            }

            seenPlayers.add(playerId);
            String preferredStream = PREFERRED_STREAMS.get(normalize(name));
            String playbackUrl = preferredStream != null
                    ? preferredStream
                    : String.format(Locale.ROOT, PLAYER_URL, playerId);
            Element categoryLink = row.selectFirst("a[data-kat]");
            String category = categoryLink == null ? "" : categoryLink.attr("data-kat");
            channels.add(new Channel(channels.size() + 1, name, playbackUrl, pageUrl, category));
        }
        return channels;
    }

    private static String trustedCatalogUrl(String href) {
        try {
            URL url = new URL(new URL(CATALOG_URL), href);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !("canlitv.diy".equals(host) || "www.canlitv.diy".equals(host))) {
                return null;
            }
            return url.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String downloadText(String source) throws Exception {
        return downloadText(source, null);
    }

    private static String downloadText(String source, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(4500);
        connection.setReadTimeout(7000);
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
                    if (content.length() + line.length() > MAX_DOCUMENT_CHARS) {
                        throw new IllegalStateException("Player document is too large");
                    }
                    content.append(line).append('\n');
                }
                return content.toString();
            }
        } finally {
            connection.disconnect();
        }
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
                item.put("category", channel.sourceCategory);
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
                            pageUrl,
                            item.optString("category", "")));
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
        streams.put(normalize("TRT Spor Yıldız"), "https://tv-trtspor2.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Belgesel"), "https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Çocuk"), "https://tv-trtcocuk.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Müzik"), "https://tv-trtmuzik.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Türk"), "https://tv-trtturk.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Avaz"), "https://tv-trtavaz.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT World"), "https://tv-trtworld.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Kürdi"), "https://tv-trtkurdi.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("TRT Arapça"), "https://tv-trtarabi.medya.trt.com.tr/master.m3u8");
        streams.put(normalize("CNBC-e"), "https://hnpsechtsc.turknet.ercdn.net/xpnvudnlsv/cnbc-e/cnbc-e.m3u8");
        streams.put(normalize("Yol Tv"), "https://live.yoltv.com/hls/stream.m3u8");
        return streams;
    }

}
