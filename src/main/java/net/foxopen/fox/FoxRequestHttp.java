package net.foxopen.fox;

import net.foxopen.fox.entrypoint.FoxGlobals;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public final class FoxRequestHttp extends FoxRequest {

  private final HttpServletRequest mHttpRequest;
  private final HttpServletResponse mHttpResponse;
  private final String mRequestLogId;
  private final boolean mSecureCookies;

  public FoxRequestHttp(HttpServletRequest pHttpServletRequest, HttpServletResponse pHttpServletResponse) {
    super();
    mHttpRequest = pHttpServletRequest;
    mHttpResponse = pHttpServletResponse;
    mRequestLogId = "UNKNOWN";
    mSecureCookies = false;
  }

  public FoxRequestHttp(HttpServletRequest pHttpServletRequest, HttpServletResponse pHttpServletResponse, String pRequestLogId, boolean pSecureCookies) {
    super();
    mHttpRequest = pHttpServletRequest;
    mHttpResponse = pHttpServletResponse;
    mRequestLogId = pRequestLogId;
    mSecureCookies = pSecureCookies;
  }

  private String mRequestURI;
  public final String getRequestURI() {
    if(mRequestURI==null) {
      mRequestURI = getRequestURIStringBuffer().toString();
    }
    return mRequestURI;
  }

  @Deprecated
  public final StringBuffer getRequestURIStringBuffer() {
    StringBuffer lStringBuffer = new StringBuffer();
    XFUtil.pathPushTail(lStringBuffer, mHttpRequest.getPathInfo());
    return lStringBuffer;
  }

  @Override
  public final StringBuilder getRequestURIStringBuilder() {
    StringBuilder lStringBuilderURI = new StringBuilder();
    XFUtil.pathPushTail(lStringBuilderURI, mHttpRequest.getPathInfo());
    return lStringBuilderURI;
  }

  @Override
  public String getRequestLogId() {
    return mRequestLogId;
  }

  /**
   * setCookie
   * @param pName name of cookie
   * @param pValue value to set
   * @param pMaxAge max age in seconds
   * @param pSetPath set path?
   */
  private void addCookie (String pName, String pValue, Integer pMaxAge, boolean pSetPath, boolean pHttpOnly)
  {
    String cookieDomain = null;

    // get the request host - browser perspective rather than the machine name FOX derives
    String hostName = XFUtil.nvl(mHttpRequest.getHeader("host"),"");

    // strip off port (if any)
    hostName = hostName.substring(0, hostName.indexOf(":") > 0 ? hostName.indexOf(":") : hostName.length());

    // not an IP, process host name if at least one dot
    if (!XFUtil.isInet4IPAddress(hostName) && hostName.indexOf('.') > 0) {
      if ("LEGACY".equals(FoxGlobals.getInstance().getFoxEnvironment().getCookieDomainMethod())) {
        // split off what we assume to be a subdomain
        String split = hostName.substring(hostName.indexOf('.')+1);

        // if remaining dots, domain is type "subdomain.domain.com" -> set domain as "domain.com"
        if (split.indexOf('.') > 0) {
          cookieDomain = split;
        }
        // domain is type "domain.com" -> don't set domain
        else {
          cookieDomain = null;
        }
      }
      else {
        cookieDomain = hostName;
      }
    }

    // Sanitise name/value from CRLF injection
    pName = pName.replaceAll("(\r|\n)+", "");
    pValue = pValue.replaceAll("(\r|\n)+", "");

    Cookie cookie = new Cookie(pName, pValue);
    cookie.setHttpOnly(pHttpOnly);

    // Make cookies use secure flag if the app configured to send them
    cookie.setSecure(mSecureCookies);

    if (cookieDomain != null) {
      cookie.setDomain(cookieDomain);
    }
    if (pSetPath) {
      cookie.setPath(mHttpRequest.getContextPath());
    }
    else {
      // set to host root - forces browser to ignore (for example) /eng/fox/db/LOGIN001L/
      cookie.setPath("/");
    }

    if (!XFUtil.isNull(pMaxAge)) {
      cookie.setMaxAge(pMaxAge.intValue());
    }
    mHttpResponse.addCookie(cookie);
  }

  /*
   * Wrappers
   */
  public void addCookie (String pName, String pValue) {
    addCookie(pName, pValue, null, true, false);
  }

  public void addCookie (String pName, String pValue, int pMaxAge) {
    addCookie(pName, pValue, new Integer(pMaxAge), true);
  }

  public void addCookie (String pName, String pValue, boolean pSetPath) {
    addCookie(pName, pValue, null, pSetPath, false);
  }

  public void addCookie(String pName, String pValue, boolean pSetPath, boolean pHttpOnly) {
    addCookie(pName, pValue, null, pSetPath, pHttpOnly);
  }

  public void addCookie (String pName, String pValue, int pMaxAge, boolean pSetPath) {
    addCookie(pName, pValue, new Integer(pMaxAge), pSetPath, false);
  }

  public void addCookie(String pName, String pValue, int pMaxAge, boolean pSetPath, boolean pHttpOnly) {
    addCookie(pName, pValue, new Integer(pMaxAge), pSetPath, pHttpOnly);
  }

  public void removeCookie (String pName, boolean pSetPath) {
    addCookie(pName, "", 0, pSetPath);
  }

  public void removeCookie (String pName) {
    removeCookie(pName, false);
  }

  public String getCookieValue (String pCookieName) {
    return FoxRequestHttp.getCookieValue(getHttpRequest().getCookies(), pCookieName);
  }

  public static String getCookieValue (Cookie[] pCookieArray, String pCookieName){
    String lValue = null;
    if (pCookieArray != null) {
      SEEK_COOKIE: for(int i=0; i<pCookieArray.length; i++) {
        if(pCookieArray[i].getName().equals(pCookieName)) {
           lValue=pCookieArray[i].getValue();
           break SEEK_COOKIE;
        }
      }
    }
    return lValue;
  }

  public HttpServletRequest getHttpRequest () {
    return mHttpRequest;
  }

  public HttpServletResponse getHttpResponse () {
    return mHttpResponse;
  }

  @Override
  public String getParameter(String pParameterName) {
    return mHttpRequest.getParameter(pParameterName);
  }
}

