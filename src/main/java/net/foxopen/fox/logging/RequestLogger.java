package net.foxopen.fox.logging;

import net.foxopen.fox.XFUtil;
import net.foxopen.fox.auth.AuthUtil;
import net.foxopen.fox.cache.BuiltInCacheDefinition;
import net.foxopen.fox.cache.CacheManager;
import net.foxopen.fox.database.ConnectionAgent;
import net.foxopen.fox.database.UCon;
import net.foxopen.fox.database.UConBindMap;
import net.foxopen.fox.database.parser.ParsedStatement;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.enginestatus.EngineStatus;
import net.foxopen.fox.enginestatus.StatusCollection;
import net.foxopen.fox.enginestatus.StatusDestination;
import net.foxopen.fox.enginestatus.StatusMessage;
import net.foxopen.fox.enginestatus.StatusProvider;
import net.foxopen.fox.enginestatus.StatusTable;
import net.foxopen.fox.entrypoint.CookieBasedFoxSession;
import net.foxopen.fox.entrypoint.FoxGlobals;
import net.foxopen.fox.entrypoint.filter.RequestLogFilter;
import net.foxopen.fox.ex.ExDB;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.ex.ExServiceUnavailable;
import net.foxopen.fox.job.BasicFoxJobPool;
import net.foxopen.fox.job.FoxJobTask;
import net.foxopen.fox.job.TaskCompletionMessage;
import net.foxopen.fox.sql.SQLManager;
import net.foxopen.fox.track.ShowTrackBangHandler;
import net.foxopen.fox.track.TrackLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RequestLogger {

  public static final boolean LOG_USER_EXPERIENCE_TIMES = true;

  private static final String INSERT_LOG_FILENAME = "InsertRequestLog.sql";
  private static final String UPDATE_LOG_FILENAME = "UpdateRequestLog.sql";
  private static final String UPDATE_LOG_UX_TIME_FILENAME = "UpdateRequestLogUXTime.sql";

  private static final RequestLogger INSTANCE = new RequestLogger();

  private static final Iterator<String> REQUEST_ID_ITERATOR = XFUtil.getUniqueIterator();

  private static Set<Pattern> EXCLUDE_URI_PATTERNS = new HashSet<>();
  static {
    EXCLUDE_URI_PATTERNS.add(Pattern.compile("^.*/upload/status$"));
  }

  private static final Map<String, RequestLogEntry> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

  static {
    EngineStatus.instance().registerStatusProvider(new StatusProvider() {
      @Override
      public void refreshStatus(StatusDestination pDestination) {

        pDestination.addMessage("Active request counter", Integer.toString(RequestLogFilter.getActiveRequestCount()));

        StatusTable lTable = pDestination.addTable("Logged Active Requests", "Request ID", "URI", "Start Datetime", "Method", "Access Info", "User Agent", "Track");
        lTable.setRowProvider(pRowDestination -> {

          ACTIVE_REQUESTS.values().stream().sorted((a, b) -> a.mRequestTime.compareTo(b.mRequestTime)).forEach(lActiveRequest -> {

            StatusCollection lAccessInfo = new StatusCollection("AccessInfo");
            lAccessInfo.addItem(new StatusMessage("Remote IP", lActiveRequest.mRemotAddr));
            lAccessInfo.addItem(new StatusMessage("Forwarded For", lActiveRequest.mForwardedFor));

            StatusTable.Row lRow = pRowDestination.addRow(lActiveRequest.mRequestId)
              .setColumn(lActiveRequest.mRequestId)
              .setColumn(lActiveRequest.mRequestURI)
              .setColumn(EngineStatus.formatDate(lActiveRequest.mRequestTime))
              .setColumn(lActiveRequest.mHttpMethod)
              .setColumn(lAccessInfo)
              .setColumn(lActiveRequest.mUserAgent);

            TrackLogger lHotTrack = CacheManager.<String, TrackLogger>getCache(BuiltInCacheDefinition.HOT_TRACKS).get(lActiveRequest.mRequestId);
            if(lHotTrack != null) {
              lRow.setActionColumn("View", ShowTrackBangHandler.instance(), Collections.singletonMap(ShowTrackBangHandler.TRACK_ID_PARAM_NAME, lHotTrack.getTrackId()), true);
            }
            else {
              lRow.setColumn("No track found");
            }
          });
        });
      }

      @Override
      public String getCategoryTitle() {
        return "Active Requests";
      }

      @Override
      public String getCategoryMnemonic() {
        return "activeRequests";
      }

      @Override
      public boolean isCategoryExpandedByDefault() {
        return true;
      }
    });
  }

  private final BasicFoxJobPool mLogWriterJobPool = BasicFoxJobPool.createSingleThreadedPool("RequestLogger");

  public static RequestLogger instance() {
    return INSTANCE;
  }

  private RequestLogger() {
  }

  public String startRequestLog(final HttpServletRequest pHttpRequest) {

    //Read request info now so it doesn't mutate before being written to the log
    final String lRequestLogId = REQUEST_ID_ITERATOR.next();
    final String lRequestURI = pHttpRequest.getRequestURI();
    final Date lRequestTime = new Date();
    final String lHttpMethod = pHttpRequest.getMethod();
    String lQS = XFUtil.nvl(pHttpRequest.getQueryString());
    final String lQueryString = lQS.substring(0, Math.min(lQS.length(), 4000));
    final String lUserAgent = pHttpRequest.getHeader("User-Agent");
    final String lRemotAddr = pHttpRequest.getRemoteAddr();
    final String lForwardedFor = pHttpRequest.getHeader(AuthUtil.X_FORWARDED_FOR_HEADER_NAME);
    final String lSessionId = CookieBasedFoxSession.getSessionIdFromRequest(pHttpRequest);

    ACTIVE_REQUESTS.put(lRequestLogId, new RequestLogEntry(lRequestLogId, lRequestURI, lRequestTime, lHttpMethod, lUserAgent, lRemotAddr, lForwardedFor));

    mLogWriterJobPool.submitTask(new FoxJobTask() {
      public TaskCompletionMessage executeTask() {
        if(!isURIExcluded(lRequestURI)) {
          ParsedStatement lInsertStatement = SQLManager.instance().getStatement(INSERT_LOG_FILENAME, getClass());
          UConBindMap lBindMap = new UConBindMap()
          .defineBind(":id", lRequestLogId)
          .defineBind(":server_hostname", FoxGlobals.getInstance().getServerHostName())
          .defineBind(":server_context", FoxGlobals.getInstance().getContextPath())
          .defineBind(":request_uri", lRequestURI)
          .defineBind(":request_start_timestamp", lRequestTime)
          .defineBind(":http_method", lHttpMethod)
          .defineBind(":query_string", lQueryString)
          .defineBind(":user_agent", lUserAgent)
          .defineBind(":fox_session_id", lSessionId)
          .defineBind(":origin_ip", lRemotAddr)
          .defineBind(":forwarded_for", lForwardedFor);

          try (UCon lUCon = ConnectionAgent.getConnection(FoxGlobals.getInstance().getEngineConnectionPoolName(), "Request Log Insert")) {
            lUCon.executeAPI(lInsertStatement, lBindMap);
            lUCon.commit();
            //UCon closed by try-with-resources
          }
          catch (ExServiceUnavailable | ExDB e) {
            throw new ExInternal("Start request log failed for request " + lRequestLogId, e);
          }

          return new TaskCompletionMessage(this, "Request log start written for request " + lRequestLogId);
        }
        else {
          return new TaskCompletionMessage(this, "Request log start skipped to due exclusion pattern match for URI " + lRequestURI + ", request " + lRequestLogId);
        }
      }

      @Override
      public String getTaskDescription() {
        return "RequestLogStart";
      }
    });

    return lRequestLogId;
  }

  public void endRequestLog(final String pRequestLogId, HttpServletRequest pOriginalRequest, HttpServletResponse pOptionalResponse) {

    final String lRequestURI = pOriginalRequest.getRequestURI();
    final Date lEndTime = new Date();
    final Integer lResponseCode = pOptionalResponse != null ? pOptionalResponse.getStatus() : null;

    ACTIVE_REQUESTS.remove(pRequestLogId);

    mLogWriterJobPool.submitTask(new FoxJobTask() {
      public TaskCompletionMessage executeTask() {

        if(!isURIExcluded(lRequestURI)) {
          ParsedStatement lUpdateStatement = SQLManager.instance().getStatement(UPDATE_LOG_FILENAME, getClass());
          UConBindMap lBindMap = new UConBindMap()
          .defineBind(":request_end_timestamp", lEndTime)
          .defineBind(":response_code", lResponseCode)
          .defineBind(":id", pRequestLogId);

          try (UCon lUCon = ConnectionAgent.getConnection(FoxGlobals.getInstance().getEngineConnectionPoolName(), "Request Log Update")) {
            lUCon.executeAPI(lUpdateStatement, lBindMap);
            lUCon.commit();
            //UCon closed by try-with-resources
          }
          catch (ExServiceUnavailable | ExDB e) {
            throw new ExInternal("End request log failed for request " + pRequestLogId, e);
          }

          return new TaskCompletionMessage(this, "Request log finalise written for request " + pRequestLogId);
        }
        else {
          return new TaskCompletionMessage(this, "Request log end skipped to due exclusion pattern match for URI " + lRequestURI + ", request " + pRequestLogId);
        }
      }

      @Override
      public String getTaskDescription() {
        return "RequestLogFinalise";
      }
    });
  }

  private boolean isURIExcluded(String pRequestURI) {
    for(Pattern lPattern : EXCLUDE_URI_PATTERNS) {
      if(lPattern.matcher(pRequestURI).matches()) {
        return true;
      }
    }
    return false;
  }

  void logUserExperienceTime(final String pRequestId, final long pExperienceTimeMS, final DOM pXMLData) {

    mLogWriterJobPool.submitTask(new FoxJobTask() {
      @Override
      public TaskCompletionMessage executeTask() {
        ParsedStatement lUpdateStatement = SQLManager.instance().getStatement(UPDATE_LOG_UX_TIME_FILENAME, getClass());
        UConBindMap lBindMap = new UConBindMap()
          .defineBind(":user_experience_time_ms", pExperienceTimeMS)
          .defineBind(":user_experience_detail_xml", pXMLData)
          .defineBind(":id", pRequestId);

        try (UCon lUCon = ConnectionAgent.getConnection(FoxGlobals.getInstance().getEngineConnectionPoolName(), "Request Log UX Time Update")) {
          lUCon.executeAPI(lUpdateStatement, lBindMap);
          lUCon.commit();
          //UCon closed by try-with-resources
        }
        catch (ExServiceUnavailable | ExDB e) {
          throw new ExInternal("Set UX time failed for request " + pRequestId, e);
        }

        return new TaskCompletionMessage(this, "Request log UX time written for request " + pRequestId);
      }

      @Override
      public String getTaskDescription() {
        return "RequestLogUX";
      }
    });
  }

  private static class RequestLogEntry {

    final String mRequestId;
    final String mRequestURI;
    final Date mRequestTime;
    final String mHttpMethod;
    final String mUserAgent;

    final String mRemotAddr;
    final String mForwardedFor;

    public RequestLogEntry(String pRequestId, String pRequestURI, Date pRequestTime, String pHttpMethod, String pUserAgent, String pRemotAddr, String pForwardedFor) {
      mRequestId = pRequestId;
      mRequestURI = pRequestURI;
      mRequestTime = pRequestTime;
      mHttpMethod = pHttpMethod;
      mUserAgent = pUserAgent;
      mRemotAddr = pRemotAddr;
      mForwardedFor = pForwardedFor;
    }
  }
}
