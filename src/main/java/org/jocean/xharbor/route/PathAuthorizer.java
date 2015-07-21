package org.jocean.xharbor.route;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Function;
import org.jocean.idiom.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

public class PathAuthorizer {
    
    private static final Logger LOG = LoggerFactory
            .getLogger(PathAuthorizer.class);
    
    public PathAuthorizer(
            final Level level,
            final String pathPattern, 
            final String user, 
            final String password) {
        this._level = level;
        this._pathPattern = safeCompilePattern(pathPattern);
        this._user = user;
        this._password = password;
        
        this._level.addPathAuthorizer(this);
    }
    
    public void stop() {
        this._level.removePathAuthorizer(this);
    }
    
    public Function<HttpRequest, Boolean> genNeedAuthorization(final String path) {
        final Matcher matcher = _pathPattern.matcher(path);
        if ( matcher.find() ) {
            return new Function<HttpRequest, Boolean>() {
                @Override
                public Boolean apply(final HttpRequest request) {
                    return !isAuthorizeSuccess(request, _user, _password);
                }
                
                @Override
                public String toString() {
                    return "[" + _pathPattern.toString() + ":" + _user + "/" + _password + "]";
                }};
        } else {
            return null;
        }
    }
    
    private boolean isAuthorizeSuccess(
            final HttpRequest httpRequest, 
            final String authUser, 
            final String authPassword) {
        final String authorization = HttpHeaders.getHeader(httpRequest, HttpHeaders.Names.AUTHORIZATION);
        if ( null != authorization) {
            final String userAndPassBase64Encoded = validateBasicAuth(authorization);
            if ( null != userAndPassBase64Encoded ) {
                final Pair<String, String> userAndPass = getUserAndPassForBasicAuth(userAndPassBase64Encoded);
                if (null != userAndPass) {
                    final boolean ret = (userAndPass.getFirst().equals(authUser)
                            && userAndPass.getSecond().equals(authPassword));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("httpRequest [{}] basic authorization {}, input:{}/{}, setted auth:{}/{}", 
                                httpRequest, (ret ? "success" : "failure"), 
                                userAndPass.getFirst(), userAndPass.getSecond(),
                                authUser, authPassword);
                    }
                    return ret;
                }
            }
        }
        return false;
    }

    private static String validateBasicAuth(final String authorization) {
        if (authorization.startsWith("Basic")) {
            final String[] authFields = authorization.split(" ");
            if (authFields.length>=2) {
                return authFields[1];
            }
        }
        return null;
    }
    
    private static Pair<String, String> getUserAndPassForBasicAuth(
            final String userAndPassBase64Encoded) {
        try {
            final String userAndPass = new String(BaseEncoding.base64().decode(userAndPassBase64Encoded), 
                    "UTF-8");
            final String[] fields = userAndPass.split(":");
            return fields.length == 2 ? Pair.of(fields[0], fields[1]) : null;
        } catch (UnsupportedEncodingException e) {
            LOG.warn("exception when getUserAndPassForBasicAuth({}), detail:{}", 
                    userAndPassBase64Encoded, ExceptionUtils.exception2detail(e));
            return null;
        }
    }
    
    private static Pattern safeCompilePattern(final String regex) {
        return null != regex && !"".equals(regex) ? Pattern.compile(regex) : null;
    }
    
    private final Level _level;
    private final Pattern _pathPattern;
    private final String _user;
    private final String _password;
}