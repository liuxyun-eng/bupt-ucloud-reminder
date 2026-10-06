package cn.edu.bupt.cloudpost;

import java.net.URI;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Official portal CAS callbacks. Never logs credentials or changes the registered service. */
final class LoginNavigation {
    static final String PORTAL_HOME="https://ucloud.bupt.edu.cn/#/";
    // Verified in the current portal frontend, which uses the portal OAuth client.
    static final String AUTH_LOGIN="https://auth.bupt.edu.cn/authserver/login?service=https://ucloud.bupt.edu.cn";
    enum Action { ALLOW, LOAD_TOP, BLOCK, REUSED_TICKET }
    record Decision(Action action,String destination) {}
    private final Set<String> handledTickets=new HashSet<>();

    static boolean schoolUrl(String url) {
        try {
            URI u=new URI(url);String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && u.getRawUserInfo()==null && (u.getPort()==-1 || u.getPort()==443)
                && host!=null && (host.equalsIgnoreCase("bupt.edu.cn") || host.toLowerCase(Locale.ROOT).endsWith(".bupt.edu.cn"));
        } catch(Exception invalid){return false;}
    }
    static boolean portalPage(String url) {
        if(!schoolUrl(url))return false;
        try {URI u=new URI(url);return "ucloud.bupt.edu.cn".equalsIgnoreCase(u.getHost()) && portalPath(u.getPath());}
        catch(Exception invalid){return false;}
    }
    private static boolean portalPath(String path){return path==null || path.isEmpty() || "/".equals(path) || "/index.html".equals(path);}
    static boolean connectionPage(String url){return portalPage(url) || studentHome(url);}
    static boolean canConnect(String url) {
        if(!connectionPage(url))return false;
        try {return parameter(new URI(url),"ticket")==null && parameter(new URI(url),"token")==null;}
        catch(Exception invalid){return false;}
    }
    static boolean studentHome(String url) {
        if(!schoolUrl(url))return false;
        try {
            URI u=new URI(url);String path=u.getPath(),fragment=u.getFragment();
            if(!"ucloud.bupt.edu.cn".equalsIgnoreCase(u.getHost()) || fragment==null)return false;
            if(!"/uclass/".equals(path) && !"/uclass/index.html".equals(path))return false;
            return "/student/homepage".equalsIgnoreCase(fragment.split("\\?",2)[0]);
        } catch(Exception invalid){return false;}
    }
    Decision decide(String url,boolean mainFrame) {
        try {
            URI u=new URI(url);
            // A course frontend may fall back to its obsolete HTTP service. Start a fresh portal login instead.
            if(mainFrame && "auth.bupt.edu.cn".equalsIgnoreCase(u.getHost()) && schoolUrl(url)
                && "/authserver/login".equals(u.getPath())) {
                String service=parameter(u,"service");
                if(service!=null && service.startsWith("http://ucloud.bupt.edu.cn"))return new Decision(Action.LOAD_TOP,AUTH_LOGIN);
            }
            String rawTicket=parameter(u,"ticket");
            boolean campus="ucloud.bupt.edu.cn".equalsIgnoreCase(u.getHost()) && u.getRawUserInfo()==null;
            boolean normalPort=u.getPort()==-1 || ("https".equalsIgnoreCase(u.getScheme()) && u.getPort()==443)
                || ("http".equalsIgnoreCase(u.getScheme()) && u.getPort()==80);
            if(campus && normalPort && rawTicket!=null) {
                if(!"https".equalsIgnoreCase(u.getScheme()) || !portalPath(u.getPath()))
                    return new Decision(Action.LOAD_TOP,AUTH_LOGIN);
                String fingerprint=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(rawTicket.getBytes(StandardCharsets.UTF_8)));
                if(!handledTickets.add(fingerprint))return new Decision(Action.REUSED_TICKET,null);
                // Both official SPAs expect the ticket to terminate before a URL fragment.
                String target="https://ucloud.bupt.edu.cn/?"+ticketLast(u.getRawQuery())+"#/";
                return mainFrame && target.equals(url)?new Decision(Action.ALLOW,null):new Decision(Action.LOAD_TOP,target);
            }
            if(campus && normalPort && "http".equalsIgnoreCase(u.getScheme()) && mainFrame)
                return new Decision(Action.LOAD_TOP,"https://ucloud.bupt.edu.cn"+(u.getRawPath()==null?"/":u.getRawPath())+
                    (u.getRawQuery()==null?"":"?"+u.getRawQuery())+(u.getRawFragment()==null?"":"#"+u.getRawFragment()));
            return new Decision(schoolUrl(url)?Action.ALLOW:Action.BLOCK,null);
        } catch(Exception invalid){return new Decision(Action.BLOCK,null);}
    }
    static boolean hasTicket(String url){try{return parameter(new URI(url),"ticket")!=null;}catch(Exception invalid){return false;}}
    static boolean hasCredentialQuery(String url){try {URI u=new URI(url);return parameter(u,"ticket")!=null || parameter(u,"token")!=null;}catch(Exception invalid){return true;}}
    private static String ticketLast(String query) throws Exception {
        List<String> others=new ArrayList<>();String credential=null;
        for(String part:query.split("&",-1)) {
            int eq=part.indexOf('=');
            if(eq>=0 && "ticket".equals(URLDecoder.decode(part.substring(0,eq),"UTF-8")))credential=part;
            else if(!part.isEmpty())others.add(part);
        }
        others.add(credential);return String.join("&",others);
    }
    private static String parameter(URI u,String name) throws Exception {
        if(u.getRawQuery()==null)return null;
        String found=null;
        for(String part:u.getRawQuery().split("&")) {
            int eq=part.indexOf('=');
            if(eq<0 || !name.equals(URLDecoder.decode(part.substring(0,eq),"UTF-8")))continue;
            if(found!=null)throw new IllegalArgumentException("Duplicate parameter");
            found=URLDecoder.decode(part.substring(eq+1).replace("+","%2B"),"UTF-8");
            if(found.isEmpty())throw new IllegalArgumentException("Empty parameter");
        }
        return found;
    }
}
