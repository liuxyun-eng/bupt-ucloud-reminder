package cn.edu.bupt.cloudpost;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import android.webkit.CookieManager;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;

final class SessionVault {
    static final Object LOCK=new Object();
    private static final String KEY="cloudpost_session_v1";
    record Session(String token,String refresh,String userId,String identity,long generation,SchoolAuth.Client client,String roleId) {}
    static Session load(Context c) throws Exception {
        synchronized (LOCK) {
            String saved=c.getSharedPreferences("session",0).getString("encrypted","");
            if (saved.isEmpty()) return null;
            String[] parts=saved.split(":",2);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            JSONObject o=new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
            return new Session(o.getString("token"),o.getString("refresh"),o.getString("userId"),o.getString("identity"),o.getLong("generation"),SchoolAuth.Client.fromSaved(o.optString("client","course")),o.optString("roleId",""));
        }
    }
    static SessionCookies.Credentials browserLogin(String url) throws Exception {
        if(!LoginNavigation.connectionPage(url))throw new IllegalStateException("请先完成学校登录");
        return SessionCookies.read(CookieManager.getInstance().getCookie(url));
    }
    static void captureLogin(Context c,String url,SchoolAuth.Client client) throws Exception {
        SessionCookies.Credentials credentials=browserLogin(url);
        String token=credentials.token(),refresh=credentials.refresh(),uid=credentials.userId(),identity=credentials.identity();
        synchronized (LOCK) {
            Session old=load(c);
            long generation=c.getSharedPreferences("session",0).getLong("generation",0)+1;
            if (old==null || !old.userId().equals(uid)) {
                ReminderScheduler.cancelAll(c);
                Repository.reset(c);
                c.getSystemService(android.app.NotificationManager.class).cancelAll();
            }
            save(c,new Session(token,refresh,uid,identity,generation,client,credentials.roleId()));
        }
        CookieManager.getInstance().flush();
    }
    static Session refresh(Context c,Session previous,String token,String refresh) throws Exception {
        synchronized (LOCK) {
            Session current=load(c);
            if (current==null || current.generation()!=previous.generation()) throw new IllegalStateException("登录状态已改变");
            Session next=new Session(token,refresh,current.userId(),current.identity(),current.generation(),current.client(),current.roleId());
            save(c,next); return next;
        }
    }
    static Session rememberRole(Context c,Session previous,String roleId) throws Exception {
        synchronized(LOCK) {
            Session current=load(c);
            if(current==null || current.generation()!=previous.generation())throw new IllegalStateException("登录状态已改变，请重新打开作业");
            Session next=new Session(current.token(),current.refresh(),current.userId(),current.identity(),current.generation(),current.client(),roleId);
            save(c,next);return next;
        }
    }
    static void clear(Context c) {
        synchronized (LOCK) {
            long generation=c.getSharedPreferences("session",0).getLong("generation",0)+1;
            c.getSharedPreferences("session",0).edit().remove("encrypted").putLong("generation",generation).commit();
        }
    }
    private static void save(Context c,Session s) throws Exception {
        JSONObject o=new JSONObject().put("token",s.token()).put("refresh",s.refresh()).put("userId",s.userId()).put("identity",s.identity()).put("generation",s.generation()).put("client",s.client().value()).put("roleId",s.roleId());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(o.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if (!c.getSharedPreferences("session",0).edit().putString("encrypted",encoded).putLong("generation",s.generation()).commit())
            throw new IllegalStateException("无法保存登录状态");
    }
    private static javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY)) return (javax.crypto.SecretKey)store.getKey(KEY,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
}
