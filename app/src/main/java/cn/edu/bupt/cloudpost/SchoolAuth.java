package cn.edu.bupt.cloudpost;

/** Public client identifiers used by the two official frontends, not student credentials. */
final class SchoolAuth {
    static boolean includeIdentity(Client client,String endpoint) {
        return client!=Client.PORTAL || !(endpoint.equals("/ykt-basics/userroledomaindept/listByUserId")
            || endpoint.equals("/ykt-basics/menu/role-grant") || endpoint.equals("/ykt-basics/info"));
    }
    enum Client {
        COURSE("course","Basic c3dvcmQ6c3dvcmRfc2VjcmV0"),
        PORTAL("portal","Basic cG9ydGFsOnBvcnRhbF9zZWNyZXQ=");
        private final String value,authorization;
        Client(String value,String authorization){this.value=value;this.authorization=authorization;}
        String value(){return value;}
        String authorization(){return authorization;}
        static Client fromSaved(String value) {
            if("course".equals(value))return COURSE;
            if("portal".equals(value))return PORTAL;
            throw new IllegalArgumentException("无法识别登录来源，请重新登录");
        }
    }
}
