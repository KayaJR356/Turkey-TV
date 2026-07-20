package tv.kaya.turksat;

import android.content.Context;
import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelRepository {
    // Kullanıcı kendi HTTPS JSON adresini Ayarlar'dan tanımlayabilir. Format: [{"number":1,"name":"...","url":"https://...m3u8"}]
    private static final String PREFS="tv_settings", KEY="playlist_url";
    static List<Channel> load(Context c) {
        String remote=c.getSharedPreferences(PREFS,0).getString(KEY,"");
        if(!remote.isEmpty()) try { List<Channel> r=download(remote); if(!r.isEmpty()) return r; } catch(Exception ignored) {}
        try { List<Channel> online=downloadCanliTv(); if(online.size()>20){saveCache(c,online);return online;} } catch(Exception ignored) {}
        List<Channel> cached=loadCache(c);if(cached.size()>20)return cached;
        return defaults();
    }
    static void saveUrl(Context c,String url){ c.getSharedPreferences(PREFS,0).edit().putString(KEY,url.trim()).apply(); }
    static String getUrl(Context c){ return c.getSharedPreferences(PREFS,0).getString(KEY,""); }
    private static void saveCache(Context c,List<Channel> list){try{JSONArray a=new JSONArray();for(Channel ch:list){JSONObject o=new JSONObject();o.put("number",ch.number);o.put("name",ch.name);o.put("url",ch.url);a.put(o);}c.getSharedPreferences(PREFS,0).edit().putString("channel_cache",a.toString()).apply();}catch(Exception ignored){}}
    private static List<Channel> loadCache(Context c){ArrayList<Channel> out=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences(PREFS,0).getString("channel_cache","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Channel(o.getInt("number"),o.getString("name"),o.getString("url")));}}catch(Exception ignored){}return out;}
    private static List<Channel> download(String source) throws Exception {
        HttpURLConnection cn=(HttpURLConnection)new URL(source).openConnection(); cn.setConnectTimeout(6000); cn.setReadTimeout(8000); cn.setRequestProperty("User-Agent","TurksatTV/1.0");
        if(cn.getResponseCode()!=200) throw new Exception("HTTP "+cn.getResponseCode());
        BufferedReader br=new BufferedReader(new InputStreamReader(cn.getInputStream())); StringBuilder s=new StringBuilder(); String line; while((line=br.readLine())!=null)s.append(line); br.close();
        JSONArray a=new JSONArray(s.toString()); ArrayList<Channel> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i); String u=o.optString("url",""); if(u.startsWith("https://")) out.add(new Channel(o.getInt("number"),o.getString("name"),u)); }
        Collections.sort(out, Comparator.comparingInt(x->x.number)); return out;
    }
    private static String text(URL url) throws Exception {
        HttpURLConnection cn=(HttpURLConnection)url.openConnection(); cn.setConnectTimeout(7000); cn.setReadTimeout(10000); cn.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");
        if(cn.getResponseCode()!=200)throw new Exception("HTTP "+cn.getResponseCode());
        BufferedReader br=new BufferedReader(new InputStreamReader(cn.getInputStream(),"UTF-8")); StringBuilder s=new StringBuilder(); String line; while((line=br.readLine())!=null)s.append(line).append('\n'); br.close(); return s.toString();
    }
    private static String norm(String s){ return s.toUpperCase(java.util.Locale.forLanguageTag("tr-TR")).replace("İ","I").replace("Ş","S").replace("Ğ","G").replace("Ü","U").replace("Ö","O").replace("Ç","C").replaceAll("[^A-Z0-9]",""); }
    private static List<Channel> downloadCanliTv() throws Exception {
        String html=text(new URL("https://www.canlitv.diy/tr"));
        Pattern p=Pattern.compile("<a\\s+([^>]+)>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL); Pattern hrefPattern=Pattern.compile("href=[\\\"']([^\\\"']+)[\\\"']",Pattern.CASE_INSENSITIVE); Pattern titlePattern=Pattern.compile("title=[\\\"']([^\\\"']+)[\\\"']",Pattern.CASE_INSENSITIVE); Matcher m=p.matcher(html);
        ArrayList<String[]> found=new ArrayList<>(); boolean started=false;
        while(m.find()) { Matcher hm=hrefPattern.matcher(m.group(1)),tm=titlePattern.matcher(m.group(1)); if(!hm.find())continue; String href=hm.group(1); if(href.contains("trt1-izle"))started=true; if(!started)continue; if(!tm.find()||!tm.group(1).toLowerCase(java.util.Locale.ROOT).endsWith("canlı izle")){if(href.contains("turkmen-sport-tv"))break;continue;} String name=tm.group(1).substring(0,tm.group(1).length()-" canlı izle".length()).trim(); if(!href.startsWith("http"))href="https://www.canlitv.diy"+(href.startsWith("/")?href:"/"+href);
            boolean duplicate=false; for(String[] a:found)if(norm(a[0]).equals(norm(name))){duplicate=true;break;} if(!duplicate)found.add(new String[]{name,href}); if(href.contains("turkmen-sport-tv"))break;
        }
        Map<String,String> official=new HashMap<>(); official.put(norm("TRT 1"),"https://tv-trt1.medya.trt.com.tr/master.m3u8");official.put(norm("TRT 2"),"https://tv-trt2.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Haber"),"https://tv-trthaber.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Spor"),"https://tv-trtspor1.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Belgesel"),"https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Çocuk"),"https://tv-trtcocuk.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Müzik"),"https://tv-trtmuzik.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Türk"),"https://tv-trtturk.medya.trt.com.tr/master.m3u8");official.put(norm("TRT Avaz"),"https://tv-trtavaz.medya.trt.com.tr/master.m3u8");
        ArrayList<Channel> out=new ArrayList<>(); int n=1; for(String[] a:found)out.add(new Channel(n++,a[0]+(official.containsKey(norm(a[0]))?" HD":""),official.containsKey(norm(a[0]))?official.get(norm(a[0])):a[1]));
        Collections.sort(out,Comparator.comparingInt(x->x.number)); return out;
    }
    private static List<Channel> defaults(){ ArrayList<Channel> x=new ArrayList<>();
        x.add(new Channel(1,"TRT 1 HD","https://tv-trt1.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(2,"TRT 2 HD","https://tv-trt2.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(3,"TRT Haber HD","https://tv-trthaber.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(4,"TRT Spor HD","https://tv-trtspor1.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(5,"TRT Belgesel HD","https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(6,"TRT Çocuk HD","https://tv-trtcocuk.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(13,"TRT Müzik HD","https://tv-trtmuzik.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(14,"TRT Türk HD","https://tv-trtturk.medya.trt.com.tr/master.m3u8"));
        x.add(new Channel(15,"TRT Avaz HD","https://tv-trtavaz.medya.trt.com.tr/master.m3u8"));
        return x; }
}
