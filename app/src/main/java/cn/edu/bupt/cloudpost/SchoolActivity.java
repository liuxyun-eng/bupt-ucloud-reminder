package cn.edu.bupt.cloudpost;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class SchoolActivity extends Activity {
    private WebView web;
    private Button connected;
    private TextView tip;
    private boolean cleaningCallback,paused=true,destroyed,resettingLogin,bootstrapping,preparing;
    private String pendingDestination,requestedDestination;
    private long openingSerial;
    private Future<?> preparation;
    private final ExecutorService pageExecutor=Executors.newSingleThreadExecutor();
    private SchoolAuth.Client activeClient=SchoolAuth.Client.PORTAL;
    private LoginNavigation navigation=new LoginNavigation();
    private final LoginDiagnostics diagnostics=new LoginDiagnostics();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(paused || destroyed)return;updateConnection();handler.postDelayed(this,1000);}};
    static boolean schoolUrl(String url){return LoginNavigation.schoolUrl(url);}
    static void open(Context c,String url){c.startActivity(new Intent(c,SchoolActivity.class).putExtra("url",url));}

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout bar=new LinearLayout(this);
        Button back=button("返回");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(0,-2,.7f));
        Button retry=button("重新登录");bar.addView(retry,new LinearLayout.LayoutParams(0,-2,1));
        connected=button("连接提醒");connected.setEnabled(false);bar.addView(connected,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar);
        requestedDestination=getIntent().getStringExtra("url");
        if(!schoolUrl(requestedDestination) || LoginNavigation.hasCredentialQuery(requestedDestination) || Models.LOGIN.equals(requestedDestination))requestedDestination=Models.HOME;
        LinearLayout help=new LinearLayout(this);help.setGravity(Gravity.CENTER_VERTICAL);
        tip=new TextView(this);tip.setText("请在学校主入口完成统一认证。识别到学生身份后，「连接提醒」会亮起。");tip.setTextSize(12);tip.setPadding(16,8,8,8);help.addView(tip,new LinearLayout.LayoutParams(0,-2,1));
        Button diagnose=button("登录诊断");diagnose.setOnClickListener(v->showDiagnostics());help.addView(diagnose,new LinearLayout.LayoutParams(-2,-2));root.addView(help);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        Button browser=button("在手机浏览器中打开");browser.setOnClickListener(v->openInBrowser());root.addView(browser);
        setContentView(root);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
        // The official portal's "进入云邮" link uses window.open. Handle that same school link in this view.
        settings.setSupportMultipleWindows(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        retry.setOnClickListener(v->freshLogin());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return navigate(request.getUrl().toString(),request.isForMainFrame());}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
                connected.setEnabled(false);if(!bootstrapping)diagnostics.page(url);
                if(!bootstrapping && LoginNavigation.portalPage(url))activeClient=SchoolAuth.Client.PORTAL;
            }
            @Override public void onPageFinished(WebView view,String url){
                if(bootstrapping){prepareDestination();return;}
                if(activeClient==SchoolAuth.Client.PORTAL && LoginNavigation.portalPage(url)) {
                    // Same-origin sessionStorage is what the portal copies to its course window.
                    view.evaluateJavascript("sessionStorage.setItem('ykt_login_from','fromPortal');",ignored->{
                        if(destroyed || !LoginNavigation.portalPage(web.getUrl()))return;
                        if(pendingDestination!=null)beginPage(pendingDestination);
                        else updateConnection();
                    });
                } else updateConnection();
            }
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){updateConnection();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()){diagnostics.network(error.getErrorCode());tip.setText("页面加载失败。可点击「登录诊断」查看原因，或重新登录。");}
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                diagnostics.http(request.getUrl().toString(),response.getStatusCode());
                if(request.getUrl().getHost()!=null && request.getUrl().getHost().equalsIgnoreCase("apiucloud.bupt.edu.cn") && "/ykt-basics/oauth/token".equals(request.getUrl().getPath()))
                    tip.setText("学校凭证交换未成功，点击「登录诊断」可查看错误步骤和状态码。");
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();diagnostics.ssl();tip.setText("学校页面证书校验失败，请稍后重试。可查看登录诊断。");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,Message result){
                if(!userGesture)return false;
                WebView popup=new WebView(SchoolActivity.this);
                popup.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                        if(r.isForMainFrame()){
                            String destination=r.getUrl().toString();
                            if(schoolUrl(destination)){if(!navigate(destination,true))web.loadUrl(destination);}
                            else diagnostics.blocked();
                            v.stopLoading();handler.post(v::destroy);
                        }
                        return true;
                    }
                });
                ((WebView.WebViewTransport)result.obj).setWebView(popup);result.sendToTarget();return true;
            }
        });
        connected.setOnClickListener(v->{
            try {
                if(!LoginNavigation.canConnect(web.getUrl()))throw new IllegalStateException("请先完成学校登录");
                SessionVault.captureLogin(this,web.getUrl(),activeClient);SyncJobService.schedule(this);
                Repository.prefs(this).edit().putString("status","正在同步…").apply();
                Toast.makeText(this,"已连接，正在同步",Toast.LENGTH_SHORT).show();
                SyncRunner.start(getApplicationContext(),status->{});finish();
            } catch(Exception e){Toast.makeText(this,e instanceof IllegalStateException?e.getMessage():"连接失败，请重新完成学校登录",Toast.LENGTH_LONG).show();}
        });
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(web.canGoBack())web.goBack();else finish();});
        if(state!=null && !state.getBoolean("pendingCredential",false)) {
            try {activeClient=SchoolAuth.Client.fromSaved(state.getString("client","portal"));}catch(Exception ignored){}
            pendingDestination=state.getString("destination");
            if(pendingDestination==null && web.restoreState(state)!=null)return;
        }
        String url=pendingDestination!=null?pendingDestination:getIntent().getStringExtra("url");
        if((state!=null && state.getBoolean("pendingCredential",false)) || url==null || Models.LOGIN.equals(url) || !schoolUrl(url)){freshLogin();return;}
        try {
            SessionVault.Session session=SessionVault.load(this);
            if(session==null){freshLogin();return;}
            activeClient=session.client();
            beginPage(url);
        } catch(Exception ignored){freshLogin();}
    }
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);return b;}
    private boolean navigate(String url,boolean mainFrame) {
        LoginNavigation.Decision decision=navigation.decide(url,mainFrame);
        if(decision.action()==LoginNavigation.Action.ALLOW)return false;
        if(decision.action()==LoginNavigation.Action.LOAD_TOP){
            if(LoginNavigation.AUTH_LOGIN.equals(decision.destination()))activeClient=SchoolAuth.Client.PORTAL;
            web.loadUrl(decision.destination());return true;
        }
        if(decision.action()==LoginNavigation.Action.REUSED_TICKET){diagnostics.ticketReplay();tip.setText("该登录回调已处理过，请点击「重新登录」获取新凭证。");return true;}
        diagnostics.blocked();Toast.makeText(this,"该链接不属于学校 HTTPS 页面",Toast.LENGTH_LONG).show();return true;
    }
    private void freshLogin() {
        if(resettingLogin || destroyed)return;
        resettingLogin=true;openingSerial++;if(preparation!=null)preparation.cancel(true);
        preparing=false;bootstrapping=false;web.stopLoading();connected.setEnabled(false);pendingDestination=null;cleaningCallback=false;
        activeClient=SchoolAuth.Client.PORTAL;navigation=new LoginNavigation();
        tip.setText("正在重新打开学校主入口，旧版网页登录缓存会自动清除。请完成统一认证。");
        WebStorage.getInstance().deleteAllData();
        // Clear only this app's WebView data. The phone browser and encrypted native cache are separate.
        CookieManager.getInstance().removeAllCookies(removed->{
            resettingLogin=false;if(destroyed)return;
            CookieManager.getInstance().flush();web.clearHistory();web.loadUrl(LoginNavigation.PORTAL_HOME);
        });
    }
    private void updateConnection() {
        if(web==null || destroyed || resettingLogin)return;
        if(bootstrapping || preparing){connected.setEnabled(false);return;}
        String url=web.getUrl();boolean ready=false;
        if(LoginNavigation.connectionPage(url)) {
            try {
                SessionVault.browserLogin(url);
                if(LoginNavigation.hasCredentialQuery(url)) {
                    if(!cleaningCallback){cleaningCallback=true;web.loadUrl(LoginNavigation.portalPage(url)?LoginNavigation.PORTAL_HOME:Models.HOME);}
                    connected.setEnabled(false);return;
                }
                ready=LoginNavigation.canConnect(url);
                if(ready){cleaningCallback=false;diagnostics.ready();web.clearHistory();tip.setText("已完成学生身份登录，点击「连接提醒」即可同步通知与待办。");}
            } catch(IllegalStateException e){
                if(e.getMessage()!=null && (e.getMessage().contains("学生身份") || e.getMessage().contains("缓存有冲突")))tip.setText(e.getMessage());
            } catch(Exception ignored){}
        }
        connected.setEnabled(ready);
    }
    private void beginPage(String destination) {
        if(destroyed || !schoolUrl(destination) || LoginNavigation.hasCredentialQuery(destination))return;
        openingSerial++;if(preparation!=null)preparation.cancel(true);
        preparing=false;bootstrapping=true;pendingDestination=destination;requestedDestination=destination;
        connected.setEnabled(false);diagnostics.preparing();tip.setText("正在从学校加载学生身份和页面权限…");
        // A local, empty document with the school's HTTPS origin lets us initialize its storage before any route guard runs.
        // It contains no login form or remote scripts. Only the next page is the actual official school interface.
        web.loadDataWithBaseURL(LoginNavigation.PORTAL_HOME,"<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='font-family:sans-serif;padding:24px;color:#666'>正在准备学校页面，请稍候…</body></html>","text/html","UTF-8",LoginNavigation.PORTAL_HOME);
    }
    private void prepareDestination() {
        if(destroyed || !bootstrapping || preparing || pendingDestination==null)return;
        preparing=true;long serial=openingSerial;String destination=pendingDestination;
        preparation=pageExecutor.submit(()->{
            PageInitStep step=PageInitStep.SESSION;
            try {
                pageStep(serial,destination,step);
                SessionVault.Session session=SessionVault.load(getApplicationContext());
                if(session==null)throw new ApiClient.LoginExpired();
                ApiClient api=new ApiClient(getApplicationContext(),session);
                step=PageInitStep.ROLES;pageStep(serial,destination,step);
                JSONObject roles=api.get(ApiClient.ROLES,"GET");
                step=PageInitStep.MATCH_ROLE;pageStep(serial,destination,step);
                SchoolPageContext.Role role=SchoolPageContext.studentRole(roles,session.identity(),session.roleId());
                String roleId=role.selected().getString("roleId");
                step=PageInitStep.GRANTS;pageStep(serial,destination,step);
                JSONObject menu=api.get(ApiClient.GRANTS+"?roleId="+ApiParser.q(roleId),"GET");
                step=PageInitStep.PARSE_GRANTS;pageStep(serial,destination,step);
                List<String> grants=SchoolPageContext.grants(menu);
                step=PageInitStep.CHECK_GRANT;pageStep(serial,destination,step);
                SchoolPageContext.requirePageGrant(destination,grants);
                step=PageInitStep.USER_INFO;pageStep(serial,destination,step);
                JSONObject user=api.get(ApiClient.INFO,"GET");
                step=PageInitStep.CHECK_USER;pageStep(serial,destination,step);
                JSONObject info=SchoolPageContext.userInfo(user,session.userId());
                if(Thread.currentThread().isInterrupted())return;
                step=PageInitStep.SAVE_ROLE;pageStep(serial,destination,step);
                SessionVault.Session current=SessionVault.rememberRole(getApplicationContext(),session,roleId);
                handler.post(()->{
                    if(!openingMatches(serial,destination))return;
                    try {
                        SessionVault.Session latest=SessionVault.load(SchoolActivity.this);
                        if(latest==null || latest.generation()!=current.generation()){
                            pageFailed(serial,destination,PageInitStep.COOKIES,new PageInitFailure("SESSION_CHANGED","登录状态已改变，请重新连接账号"));return;
                        }
                        diagnostics.preparationStep(PageInitStep.COOKIES);
                        installPageCookies(latest,role,info,()->{
                            if(!openingMatches(serial,destination))return;
                            try {
                                SessionVault.Session check=SessionVault.load(SchoolActivity.this);
                                if(check==null || check.generation()!=latest.generation()){
                                    pageFailed(serial,destination,PageInitStep.STORAGE,new PageInitFailure("SESSION_CHANGED","登录状态已改变，请重新连接账号"));return;
                                }
                                diagnostics.preparationStep(PageInitStep.STORAGE);
                                web.evaluateJavascript(SchoolPageContext.storageScript(role,grants,latest.client()),result->{
                                    if(!openingMatches(serial,destination))return;
                                    String code=SchoolPageContext.storageResult(result);
                                    if(!"STORAGE_OK".equals(code)){
                                        pageFailed(serial,destination,PageInitStep.STORAGE,new PageInitFailure(code,"学校页面存储初始化失败，请复制登录诊断或在手机浏览器打开"));return;
                                    }
                                    activeClient=latest.client();pendingDestination=null;preparing=false;bootstrapping=false;
                                    diagnostics.prepared();tip.setText("学生权限已加载，正在打开学校页面…");web.loadUrl(destination);
                                });
                            } catch(Exception error){pageFailed(serial,destination,PageInitStep.STORAGE,PageInitFailure.from(error));}
                        },()->pageFailed(serial,destination,PageInitStep.COOKIES,new PageInitFailure("COOKIE_REJECTED","学校网页登录状态未能保存，请重试")));
                    } catch(Exception error){pageFailed(serial,destination,PageInitStep.COOKIES,PageInitFailure.from(error));}
                });
            } catch(Exception error){
                if(Thread.currentThread().isInterrupted())return;
                PageInitStep failedStep=step;
                PageInitFailure failure=step==PageInitStep.CHECK_GRANT && error instanceof IllegalStateException?
                    new PageInitFailure("PAGE_GRANT_MISSING","学校返回的学生权限中没有该页面，请在网页核对当前身份"):PageInitFailure.from(error);
                handler.post(()->pageFailed(serial,destination,failedStep,failure));
            }
        });
    }
    private void pageStep(long serial,String destination,PageInitStep step) {
        handler.post(()->{if(openingMatches(serial,destination))diagnostics.preparationStep(step);});
    }
    private boolean openingMatches(long serial,String destination){return !destroyed && bootstrapping && serial==openingSerial && destination.equals(pendingDestination);}
    private void installPageCookies(SessionVault.Session session,SchoolPageContext.Role role,JSONObject info,Runnable done,Runnable failed) {
        CookieManager cm=CookieManager.getInstance();
        String[] names={"token","refresh_token","uuid","identity","user-role","user-info"};
        String[] values={session.token(),session.refresh(),session.userId(),session.identity(),role.selected().toString(),info.toString()};
        int[] remaining={names.length};boolean[] allAccepted={true};
        for(int i=0;i<names.length;i++)cm.setCookie("https://ucloud.bupt.edu.cn","iClass-"+names[i]+"="+ApiParser.q(values[i]).replace("+","%20")+"; Path=/; Secure; Max-Age=86400",accepted->{
            if(!accepted)allAccepted[0]=false;
            if(--remaining[0]==0 && !destroyed){cm.flush();if(allAccepted[0])done.run();else failed.run();}
        });
    }
    private void pageFailed(long serial,String destination,PageInitStep step,PageInitFailure failure) {
        if(!openingMatches(serial,destination))return;
        preparing=false;diagnostics.preparationFailed(step,failure.code());tip.setText(failure.message());
        String message=failure.message()+"\n\n失败步骤："+step.label+"\n错误编号："+failure.code();
        new AlertDialog.Builder(this).setTitle("学校页面暂时未能打开").setMessage(message)
            .setPositiveButton("重试",(d,w)->beginPage(destination)).setNeutralButton("浏览器打开",(d,w)->openInBrowser()).setNegativeButton("关闭",null).show();
    }
    private void openInBrowser() {
        String destination=requestedDestination;
        if(!schoolUrl(destination) || LoginNavigation.hasCredentialQuery(destination))destination=Models.HOME;
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(destination)));}
        catch(ActivityNotFoundException missing){Toast.makeText(this,"手机未找到可打开网页的浏览器",Toast.LENGTH_LONG).show();}
    }
    private void showDiagnostics() {
        PackageInfo info=WebView.getCurrentWebViewPackage();
        String version=info==null?"未知":info.versionName;
        String report=diagnostics.report(Build.VERSION.RELEASE,version);
        new AlertDialog.Builder(this).setTitle("登录诊断").setMessage(report+"\n\n诊断只包含系统版本、登录步骤和错误编号。")
            .setPositiveButton("复制诊断",(d,w)->{
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("登录诊断",report));
                Toast.makeText(this,"诊断已复制",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("关闭",null).show();
    }
    @Override protected void onResume(){super.onResume();paused=false;handler.removeCallbacks(poll);handler.post(poll);if(web!=null)web.onResume();}
    @Override protected void onPause(){paused=true;handler.removeCallbacks(poll);if(web!=null)web.onPause();super.onPause();}
    @Override public void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(pendingDestination==null)web.saveState(out);out.putBoolean("pendingCredential",LoginNavigation.hasCredentialQuery(web.getUrl()));out.putString("client",activeClient.value());out.putString("destination",pendingDestination);}
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public void onDestroy(){destroyed=true;openingSerial++;pageExecutor.shutdownNow();handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
