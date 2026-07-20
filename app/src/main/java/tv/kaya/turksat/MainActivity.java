package tv.kaya.turksat;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private ExoPlayer player; private PlayerView playerView; private WebView webView; private FrameLayout root; private ScrollView channelScroll; private LinearLayout listPanel; private TextView info, digits;
    private List<Channel> channels=new ArrayList<>(); private int current=0; private boolean webGuideMode=false; private final StringBuilder numberBuffer=new StringBuilder();
    private final Handler handler=new Handler(Looper.getMainLooper()); private final Runnable tuneDigits=()->{ if(numberBuffer.length()>0){ int n=Integer.parseInt(numberBuffer.toString()); numberBuffer.setLength(0); digits.setVisibility(View.GONE); tuneNumber(n); } };
    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN); buildUi(); loadChannels(); }
    private TextView label(String t,int size){ TextView v=new TextView(this); v.setText(t); v.setTextColor(0xffffffff); v.setTextSize(size); v.setPadding(24,14,24,14); return v; }
    private void buildUi(){ root=new FrameLayout(this); root.setBackgroundColor(0xff000000); playerView=new PlayerView(this); playerView.setUseController(false); root.addView(playerView,new FrameLayout.LayoutParams(-1,-1)); webView=new WebView(this); WebSettings ws=webView.getSettings(); ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setMediaPlaybackRequiresUserGesture(false);ws.setLoadWithOverviewMode(true);ws.setUseWideViewPort(true);webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){super.onPageFinished(v,url);if(!webGuideMode&&url.contains("canlitv.diy")&&!url.contains("/player/"))v.evaluateJavascript("(function(){var f=document.getElementById('Player');if(f){document.body.innerHTML='';document.body.style.margin='0';document.body.style.background='#000';f.style.position='fixed';f.style.inset='0';f.style.width='100vw';f.style.height='100vh';f.style.border='0';document.body.appendChild(f);f.focus();}})();",null);}});webView.setWebChromeClient(new WebChromeClient());webView.setVisibility(View.GONE);root.addView(webView,new FrameLayout.LayoutParams(-1,-1));
        info=label("",22); info.setBackgroundColor(0xcc07111f); FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT); ip.setMargins(24,24,24,24); root.addView(info,ip);
        digits=label("",44); digits.setGravity(Gravity.CENTER); digits.setBackgroundColor(0xdd07111f); digits.setVisibility(View.GONE); FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(240,110,Gravity.CENTER); root.addView(digits,dp);
        channelScroll=new ScrollView(this);channelScroll.setBackgroundColor(0xeb07111f);channelScroll.setVisibility(View.GONE);listPanel=new LinearLayout(this); listPanel.setOrientation(LinearLayout.VERTICAL); listPanel.setPadding(20,20,20,20);channelScroll.addView(listPanel,new ScrollView.LayoutParams(-1,-2));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(520,-1,Gravity.LEFT); root.addView(channelScroll,lp); setContentView(root);
        player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player); player.addListener(new Player.Listener(){ @Override public void onPlayerError(androidx.media3.common.PlaybackException e){ Toast.makeText(MainActivity.this,"Yayın açılamadı — başka kanal deneyin",Toast.LENGTH_LONG).show(); }});
    }
    private void loadChannels(){ new Thread(()->{ List<Channel> r=ChannelRepository.load(this); runOnUiThread(()->{ channels=r; rebuildList(); if(!channels.isEmpty())tune(0); }); }).start(); }
    private void rebuildList(){ listPanel.removeAllViews(); TextView title=label("KANALLAR  •  OK: seç  •  ★: favori",18); title.setTextColor(0xff86efac); listPanel.addView(title); Set<String> fav=getFavorites(); for(int i=0;i<channels.size();i++){ Channel c=channels.get(i); TextView row=label((fav.contains(String.valueOf(c.number))?"★ ":"")+c.number+"   "+c.name,20); row.setFocusable(true); row.setTag(i); row.setOnClickListener(v->{ tune((Integer)v.getTag()); toggleList(false); }); row.setOnLongClickListener(v->{ toggleFavorite(channels.get((Integer)v.getTag()).number); return true; }); listPanel.addView(row,new LinearLayout.LayoutParams(-1,-2)); } }
    private void tune(int index){ if(index<0||index>=channels.size())return; current=index; webGuideMode=false; Channel c=channels.get(index); info.setText(c.number+"  "+c.name); info.setVisibility(View.VISIBLE); handler.removeCallbacksAndMessages(null); handler.postDelayed(()->info.setVisibility(View.GONE),4000); if(c.url.contains(".m3u8")){webView.stopLoading();webView.setVisibility(View.GONE);playerView.setVisibility(View.VISIBLE);player.setMediaItem(MediaItem.fromUri(c.url));player.prepare();player.play();}else{player.stop();playerView.setVisibility(View.GONE);webView.setVisibility(View.VISIBLE);webView.loadUrl(c.url);} }
    private void tuneNumber(int n){ for(int i=0;i<channels.size();i++)if(channels.get(i).number==n){tune(i);return;} Toast.makeText(this,"Kanal "+n+" listede yok",Toast.LENGTH_SHORT).show(); }
    private void toggleList(boolean show){ channelScroll.setVisibility(show?View.VISIBLE:View.GONE); if(show&&listPanel.getChildCount()>1)listPanel.getChildAt(Math.min(current+1,listPanel.getChildCount()-1)).requestFocus(); else root.requestFocus(); }
    private Set<String> getFavorites(){ return new HashSet<>(getSharedPreferences("tv_settings",0).getStringSet("favorites",new HashSet<>())); }
    private void toggleFavorite(int n){ Set<String>s=getFavorites(); String k=String.valueOf(n); if(!s.add(k))s.remove(k); getSharedPreferences("tv_settings",0).edit().putStringSet("favorites",s).apply(); rebuildList(); }
    private void settings(){ EditText e=new EditText(this); e.setSingleLine(); e.setHint("https://.../kanallar.json"); e.setText(ChannelRepository.getUrl(this)); new AlertDialog.Builder(this).setTitle("Otomatik kanal listesi adresi").setMessage("HTTPS JSON liste her açılışta indirilir. Boş bırakılırsa güvenli yerleşik liste kullanılır.").setView(e).setPositiveButton("Kaydet",(d,w)->{ChannelRepository.saveUrl(this,e.getText().toString());loadChannels();}).setNegativeButton("İptal",null).show(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e){ if(e.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(e); int k=e.getKeyCode(); if(k>=KeyEvent.KEYCODE_0&&k<=KeyEvent.KEYCODE_9){ numberBuffer.append(k-KeyEvent.KEYCODE_0); if(numberBuffer.length()>3)numberBuffer.deleteCharAt(0); digits.setText(numberBuffer);digits.setVisibility(View.VISIBLE);handler.removeCallbacks(tuneDigits);handler.postDelayed(tuneDigits,1300);return true; }
        if(channelScroll.getVisibility()==View.VISIBLE&&(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER))return super.dispatchKeyEvent(e);
        if(!channels.isEmpty()&&(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_DPAD_UP)){tune((current+1)%channels.size());return true;} if(!channels.isEmpty()&&(k==KeyEvent.KEYCODE_CHANNEL_DOWN||k==KeyEvent.KEYCODE_DPAD_DOWN)){tune((current-1+channels.size())%channels.size());return true;}
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_MENU||k==KeyEvent.KEYCODE_GUIDE){toggleList(channelScroll.getVisibility()!=View.VISIBLE);return true;} if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE){if(playerView.getVisibility()==View.VISIBLE){if(player.isPlaying())player.pause();else player.play();}else webView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));return true;} if(k==KeyEvent.KEYCODE_MEDIA_REWIND){player.seekBack();return true;} if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){player.seekForward();return true;} if(k==KeyEvent.KEYCODE_INFO||k==KeyEvent.KEYCODE_PROG_GREEN){if(webView.getVisibility()==View.VISIBLE&&!channels.isEmpty()){webGuideMode=!webGuideMode;webView.loadUrl(channels.get(current).url);}else info.setVisibility(View.VISIBLE);return true;} if(k==KeyEvent.KEYCODE_SETTINGS||k==KeyEvent.KEYCODE_PROG_BLUE){settings();return true;} if(k==KeyEvent.KEYCODE_STAR||k==KeyEvent.KEYCODE_PROG_RED){if(!channels.isEmpty()){toggleFavorite(channels.get(current).number);Toast.makeText(this,"Favoriler güncellendi",Toast.LENGTH_SHORT).show();}return true;} if(k==KeyEvent.KEYCODE_BACK&&channelScroll.getVisibility()==View.VISIBLE){toggleList(false);return true;} return super.dispatchKeyEvent(e); }
    @Override protected void onStop(){super.onStop();player.pause();webView.onPause();} @Override protected void onStart(){super.onStart();webView.onResume();if(player!=null&&playerView.getVisibility()==View.VISIBLE)player.play();} @Override protected void onDestroy(){webView.destroy();player.release();super.onDestroy();}
}
