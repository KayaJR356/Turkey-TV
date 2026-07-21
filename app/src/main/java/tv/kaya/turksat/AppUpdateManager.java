package tv.kaya.turksat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class AppUpdateManager {
    private static final String PREFS = "app_updates";
    private static final String LAST_CHECK_KEY = "last_successful_check";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L;
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;
    private static final int MAX_API_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CHECKSUM_BYTES = 4 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final Pattern SHA_256_PATTERN = Pattern.compile("(?i)\\b[0-9a-f]{64}\\b");

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Future<?> activeTask;
    private AlertDialog updateDialog;
    private AlertDialog progressDialog;
    private TextView progressText;
    private ProgressBar progressBar;
    private File pendingInstallFile;
    private boolean checking;
    private boolean waitingForInstallPermission;
    private boolean destroyed;

    AppUpdateManager(Activity activity) {
        this.activity = activity;
    }

    void checkForUpdates(boolean userInitiated) {
        if (destroyed || checking) {
            if (userInitiated && checking) {
                Toast.makeText(activity, "Güncelleme denetimi zaten çalışıyor", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        SharedPreferences preferences = activity.getSharedPreferences(PREFS, 0);
        long lastCheck = preferences.getLong(LAST_CHECK_KEY, 0L);
        if (!userInitiated && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }

        checking = true;
        if (userInitiated) {
            Toast.makeText(activity, "GitHub sürümleri denetleniyor…", Toast.LENGTH_SHORT).show();
        }
        activeTask = executor.submit(() -> {
            try {
                ReleaseInfo release = fetchNewestCompatibleRelease();
                preferences.edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply();
                mainHandler.post(() -> {
                    checking = false;
                    activeTask = null;
                    if (!canUseActivity()) {
                        return;
                    }
                    if (release == null) {
                        if (userInitiated) {
                            Toast.makeText(activity, "Uygulama güncel", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    showUpdateDialog(release);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    checking = false;
                    activeTask = null;
                    if (userInitiated && canUseActivity()) {
                        showError("Güncelleme denetlenemedi", friendlyMessage(exception));
                    }
                });
            }
        });
    }

    void onResume() {
        if (!waitingForInstallPermission || destroyed) {
            return;
        }
        waitingForInstallPermission = false;
        mainHandler.postDelayed(() -> {
            if (!canUseActivity() || pendingInstallFile == null || !pendingInstallFile.isFile()) {
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    || activity.getPackageManager().canRequestPackageInstalls()) {
                installApk(pendingInstallFile);
            } else {
                Toast.makeText(activity, "Kurulum izni verilmedi", Toast.LENGTH_LONG).show();
            }
        }, 350L);
    }

    void destroy() {
        destroyed = true;
        if (activeTask != null) {
            activeTask.cancel(true);
        }
        executor.shutdownNow();
        dismiss(updateDialog);
        dismiss(progressDialog);
        mainHandler.removeCallbacksAndMessages(null);
    }

    private ReleaseInfo fetchNewestCompatibleRelease() throws IOException, JSONException {
        String endpoint = "https://api.github.com/repos/" + BuildConfig.UPDATE_REPOSITORY
                + "/releases?per_page=20";
        HttpsURLConnection connection = openGitHubConnection(endpoint,
                "application/vnd.github+json");
        String body;
        try {
            body = readUtf8(connection.getInputStream(), MAX_API_BYTES);
        } finally {
            connection.disconnect();
        }

        JSONArray releases = new JSONArray(body);
        ReleaseInfo newest = null;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject release = releases.optJSONObject(index);
            if (release == null || release.optBoolean("draft", false)) {
                continue;
            }

            String tag = release.optString("tag_name", "").trim();
            if (tag.isEmpty() || compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                continue;
            }

            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            JSONObject apkAsset = selectApkAsset(assets);
            if (apkAsset == null) {
                continue;
            }

            long size = apkAsset.optLong("size", -1L);
            if (size <= 0L || size > MAX_APK_BYTES) {
                continue;
            }
            String apkName = apkAsset.optString("name", "");
            String apkUrl = apkAsset.optString("browser_download_url", "");
            if (apkName.isEmpty() || apkUrl.isEmpty()) {
                continue;
            }

            String digest = normalizeDigest(apkAsset.optString("digest", ""));
            String checksumUrl = findChecksumUrl(assets, apkName);
            if (digest == null && checksumUrl == null) {
                continue;
            }

            ReleaseInfo candidate = new ReleaseInfo(
                    tag,
                    release.optString("name", tag),
                    release.optString("body", ""),
                    release.optBoolean("prerelease", false),
                    apkName,
                    apkUrl,
                    checksumUrl,
                    digest,
                    size);
            if (newest == null || compareVersions(candidate.tag, newest.tag) > 0) {
                newest = candidate;
            }
        }
        return newest;
    }

    private JSONObject selectApkAsset(JSONArray assets) {
        JSONObject fallback = null;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) {
                continue;
            }
            if (fallback == null) {
                fallback = asset;
            }
            if (name.contains("universal") || name.contains("turkiye-canli-tv")) {
                return asset;
            }
        }
        return fallback;
    }

    private String findChecksumUrl(JSONArray assets, String apkName) {
        String expectedName = apkName + ".sha256";
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset != null && expectedName.equalsIgnoreCase(asset.optString("name", ""))) {
                String url = asset.optString("browser_download_url", "");
                return url.isEmpty() ? null : url;
            }
        }
        return null;
    }

    private void showUpdateDialog(ReleaseInfo release) {
        dismiss(updateDialog);
        StringBuilder message = new StringBuilder()
                .append("Kurulu sürüm: ").append(BuildConfig.VERSION_NAME)
                .append("\nYeni sürüm: ").append(release.tag);
        if (release.prerelease) {
            message.append(" (ön sürüm)");
        }
        String notes = cleanReleaseNotes(release.notes);
        if (!notes.isEmpty()) {
            message.append("\n\n").append(notes);
        }
        message.append("\n\nAPK indirilecek, SHA-256 ile doğrulanacak ve Android kurulum ekranı açılacak.");

        updateDialog = new AlertDialog.Builder(activity)
                .setTitle(release.title.isEmpty() ? "Yeni güncelleme hazır" : release.title)
                .setMessage(message.toString())
                .setNegativeButton("Sonra", null)
                .setPositiveButton("İndir ve kur", (dialog, which) -> downloadRelease(release))
                .create();
        updateDialog.setOnShowListener(dialog -> updateDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .requestFocus());
        updateDialog.show();
    }

    private void downloadRelease(ReleaseInfo release) {
        if (activeTask != null && !activeTask.isDone()) {
            return;
        }
        showProgressDialog(release.tag);
        activeTask = executor.submit(() -> {
            try {
                File apk = downloadAndVerify(release);
                mainHandler.post(() -> {
                    activeTask = null;
                    dismiss(progressDialog);
                    if (!canUseActivity()) {
                        return;
                    }
                    pendingInstallFile = apk;
                    requestInstall(apk);
                });
            } catch (InterruptedIOException ignored) {
                mainHandler.post(() -> {
                    activeTask = null;
                    dismiss(progressDialog);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    activeTask = null;
                    dismiss(progressDialog);
                    if (canUseActivity()) {
                        showError("Güncelleme indirilemedi", friendlyMessage(exception));
                    }
                });
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void showProgressDialog(String version) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(30), dp(18), dp(30), dp(10));

        progressText = new TextView(activity);
        progressText.setText("APK indiriliyor… %0");
        progressText.setTextSize(18);
        content.addView(progressText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        progressParams.setMargins(0, dp(18), 0, dp(8));
        content.addView(progressBar, progressParams);

        progressDialog = new AlertDialog.Builder(activity)
                .setTitle(version + " indiriliyor")
                .setView(content)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private File downloadAndVerify(ReleaseInfo release) throws IOException, NoSuchAlgorithmException {
        File downloadRoot = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File updateDirectory = downloadRoot == null
                ? new File(activity.getFilesDir(), "updates")
                : new File(downloadRoot, "updates");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw new IOException("Güncelleme klasörü oluşturulamadı");
        }
        removeStaleDownloads(updateDirectory);

        String safeVersion = release.tag.replaceAll("[^A-Za-z0-9._-]", "_");
        File target = new File(updateDirectory, "Turkey-TV-" + safeVersion + ".apk");
        File partial = new File(updateDirectory, target.getName() + ".part");
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Önceki yarım indirme temizlenemedi");
        }

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        HttpsURLConnection connection = openGitHubConnection(release.apkUrl,
                "application/vnd.android.package-archive, application/octet-stream");
        long total = 0L;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            int lastPercent = -1;
            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("İndirme iptal edildi");
                }
                total += read;
                if (total > MAX_APK_BYTES || total > release.size) {
                    throw new IOException("İndirilen APK beklenen boyutu aşıyor");
                }
                output.write(buffer, 0, read);
                sha256.update(buffer, 0, read);
                int percent = (int) Math.min(100L, total * 100L / release.size);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    publishProgress(percent);
                }
            }
            output.getFD().sync();
        } catch (IOException exception) {
            partial.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }

        if (total != release.size) {
            partial.delete();
            throw new IOException("APK boyutu GitHub bilgisiyle eşleşmiyor");
        }
        String expectedDigest = release.digest != null
                ? release.digest : downloadChecksum(release.checksumUrl);
        String actualDigest = toHex(sha256.digest());
        if (!actualDigest.equalsIgnoreCase(expectedDigest)) {
            partial.delete();
            throw new IOException("APK SHA-256 doğrulaması başarısız");
        }

        if (target.exists() && !target.delete()) {
            partial.delete();
            throw new IOException("Eski güncelleme paketi temizlenemedi");
        }
        if (!partial.renameTo(target)) {
            partial.delete();
            throw new IOException("Doğrulanan APK kaydedilemedi");
        }
        publishProgress(100);
        return target;
    }

    private String downloadChecksum(String checksumUrl) throws IOException {
        if (checksumUrl == null) {
            throw new IOException("Sürümde SHA-256 doğrulaması bulunmuyor");
        }
        HttpsURLConnection connection = openGitHubConnection(checksumUrl, "text/plain");
        try {
            String checksum = readUtf8(connection.getInputStream(), MAX_CHECKSUM_BYTES);
            Matcher matcher = SHA_256_PATTERN.matcher(checksum);
            if (!matcher.find()) {
                throw new IOException("SHA-256 dosyası geçersiz");
            }
            return matcher.group().toLowerCase(Locale.ROOT);
        } finally {
            connection.disconnect();
        }
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Kurulum izni gerekli")
                    .setMessage("Güncellemeyi kurabilmek için bu uygulamaya bir kez “bilinmeyen uygulama yükleme” izni verin.")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Ayarları aç", (dialog, which) -> openInstallPermissionSettings())
                    .show();
            return;
        }
        installApk(apk);
    }

    private void openInstallPermissionSettings() {
        waitingForInstallPermission = true;
        Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (Exception firstFailure) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Exception secondFailure) {
                waitingForInstallPermission = false;
                showError("Kurulum ayarı açılamadı",
                        "Cihaz ayarlarından bu uygulama için bilinmeyen uygulama yükleme iznini açın.");
            }
        }
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception exception) {
            showError("Kurulum başlatılamadı",
                    "Cihazın paket yükleyicisi açılamadı: " + friendlyMessage(exception));
        }
    }

    private HttpsURLConnection openGitHubConnection(String value, String accept) throws IOException {
        URL current = new URL(value);
        for (int redirect = 0; redirect <= 5; redirect++) {
            validateGitHubUrl(current);
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "Turkiye-TV/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("GitHub yönlendirmesi geçersiz");
                }
                current = new URL(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("GitHub HTTP " + status
                        + (status == 404 ? " (depo veya sürüm herkese açık değil)" : ""));
            }
            return connection;
        }
        throw new IOException("Çok fazla GitHub yönlendirmesi alındı");
    }

    private void validateGitHubUrl(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Güvenli olmayan güncelleme adresi reddedildi");
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        boolean trusted = "api.github.com".equals(host)
                || "github.com".equals(host)
                || host.endsWith(".githubusercontent.com");
        if (!trusted) {
            throw new IOException("Güvenilmeyen güncelleme sunucusu reddedildi");
        }
    }

    private static String readUtf8(InputStream input, int maximumBytes) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new IOException("Sunucu yanıtı izin verilen sınırı aşıyor");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void publishProgress(int percent) {
        mainHandler.post(() -> {
            if (progressBar != null) {
                progressBar.setProgress(percent);
            }
            if (progressText != null) {
                progressText.setText(percent >= 100
                        ? "APK doğrulandı" : "APK indiriliyor… %" + percent);
            }
        });
    }

    private void removeStaleDownloads(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && (name.endsWith(".apk") || name.endsWith(".part"))) {
                file.delete();
            }
        }
    }

    private void showError(String title, String message) {
        if (!canUseActivity()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private boolean canUseActivity() {
        return !destroyed && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void dismiss(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String friendlyMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName() : message;
    }

    private static String cleanReleaseNotes(String notes) {
        if (notes == null) {
            return "";
        }
        String clean = notes.replaceAll("(?m)^#{1,6}\\s*", "")
                .replace("**", "")
                .trim();
        return clean.length() > 1_000 ? clean.substring(0, 1_000) + "…" : clean;
    }

    private static String normalizeDigest(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return SHA_256_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    static int compareVersions(String first, String second) {
        ParsedVersion left = ParsedVersion.parse(first);
        ParsedVersion right = ParsedVersion.parse(second);
        int count = Math.max(left.core.size(), right.core.size());
        for (int index = 0; index < count; index++) {
            String leftPart = index < left.core.size() ? left.core.get(index) : "0";
            String rightPart = index < right.core.size() ? right.core.get(index) : "0";
            int comparison = compareIdentifier(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        if (left.prerelease.isEmpty() && right.prerelease.isEmpty()) {
            return 0;
        }
        if (left.prerelease.isEmpty()) {
            return 1;
        }
        if (right.prerelease.isEmpty()) {
            return -1;
        }
        count = Math.max(left.prerelease.size(), right.prerelease.size());
        for (int index = 0; index < count; index++) {
            if (index >= left.prerelease.size()) {
                return -1;
            }
            if (index >= right.prerelease.size()) {
                return 1;
            }
            int comparison = compareIdentifier(left.prerelease.get(index), right.prerelease.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int compareIdentifier(String first, String second) {
        boolean firstNumeric = first.matches("\\d+");
        boolean secondNumeric = second.matches("\\d+");
        if (firstNumeric && secondNumeric) {
            String left = first.replaceFirst("^0+(?!$)", "");
            String right = second.replaceFirst("^0+(?!$)", "");
            if (left.length() != right.length()) {
                return Integer.compare(left.length(), right.length());
            }
            return left.compareTo(right);
        }
        if (firstNumeric != secondNumeric) {
            return firstNumeric ? -1 : 1;
        }
        return first.compareToIgnoreCase(second);
    }

    private static final class ParsedVersion {
        final List<String> core;
        final List<String> prerelease;

        private ParsedVersion(List<String> core, List<String> prerelease) {
            this.core = core;
            this.prerelease = prerelease;
        }

        static ParsedVersion parse(String value) {
            String clean = value == null ? "" : value.trim();
            if (clean.startsWith("v") || clean.startsWith("V")) {
                clean = clean.substring(1);
            }
            int buildIndex = clean.indexOf('+');
            if (buildIndex >= 0) {
                clean = clean.substring(0, buildIndex);
            }
            String[] versionParts = clean.split("-", 2);
            List<String> core = splitIdentifiers(versionParts[0]);
            List<String> prerelease = versionParts.length > 1
                    ? splitIdentifiers(versionParts[1]) : new ArrayList<>();
            return new ParsedVersion(core, prerelease);
        }

        private static List<String> splitIdentifiers(String value) {
            List<String> identifiers = new ArrayList<>();
            for (String part : value.split("[._-]")) {
                if (!part.isEmpty()) {
                    identifiers.add(part);
                }
            }
            if (identifiers.isEmpty()) {
                identifiers.add("0");
            }
            return identifiers;
        }
    }

    private static final class ReleaseInfo {
        final String tag;
        final String title;
        final String notes;
        final boolean prerelease;
        final String apkName;
        final String apkUrl;
        final String checksumUrl;
        final String digest;
        final long size;

        ReleaseInfo(String tag, String title, String notes, boolean prerelease,
                    String apkName, String apkUrl, String checksumUrl, String digest, long size) {
            this.tag = tag;
            this.title = title;
            this.notes = notes;
            this.prerelease = prerelease;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
            this.digest = digest;
            this.size = size;
        }
    }
}
