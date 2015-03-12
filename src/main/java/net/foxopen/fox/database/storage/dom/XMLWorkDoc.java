package net.foxopen.fox.database.storage.dom;

import net.foxopen.fox.ContextUCon;
import net.foxopen.fox.XFUtil;
import net.foxopen.fox.cache.CacheManager;
import net.foxopen.fox.cache.BuiltInCacheDefinition;
import net.foxopen.fox.cache.FoxCache;
import net.foxopen.fox.database.UCon;
import net.foxopen.fox.database.sql.ExecutableQuery;
import net.foxopen.fox.database.sql.ScalarResultDeliverer;
import net.foxopen.fox.database.sql.ScalarResultType;
import net.foxopen.fox.database.sql.bind.BindSQLType;
import net.foxopen.fox.database.storage.WorkDoc;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.ex.ExDB;
import net.foxopen.fox.ex.ExDBTimeout;
import net.foxopen.fox.ex.ExDBTooFew;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.thread.storage.WorkingDataDOMStorageLocation;
import net.foxopen.fox.track.Track;


/**
 * A WorkDoc which deals with an XML document defined in a WorkingStorageLocation.
 * The XML can currently be stored using CLOB or Binary storage - two accessor classes cater for these different use cases,
 * which are written to/read from using the optimal implementation for the storage type.<br/><br/>
 *
 * A WorkDoc can be read only or writeable - see subclasses. Regardless of writeability, a WorkDoc, including its associated DOM,
 * is cached against the WSL's cache key (note the importance of a WSL's cache key containing the RO flag). When the WorkDoc
 * is opened, it first checks the change number attribute on the database to ensure it always has the most recent version of the
 * DOM in memory. A full read is only performed if the DOM in memory is out of date. For writeable WorkDocs, a write is only
 * performed on close if the DOM has been modified.
 */
public abstract class XMLWorkDoc
implements WorkDoc {

  static final String CHANGE_NUMBER_ATTR_NAME = "fox-change-number";

  /** Access strategy for reading the DOM. */
  private final XMLWorkDocDOMAccessor mDOMAccessor;

  /** The cached DOM. */
  private DOM mDOM;

  /** The most recently read DOM change number. */
  private String mDOMChangeNumber;

  /** The WSL associated with this WorkDoc. */
  private final WorkingDataDOMStorageLocation mWorkingStoreLocation;

  /**
   * Gets an XMLWorkDoc for the given WSL, creating it if it doesn't exist. WorkDocs are cached against a cache key
   * generated by combining the actual cache key with the WSL definition. This allows markup developers to change their
   * WSL definitions during development time without having to flush the WorkDoc cache for their changes to take effect.
   * Production environments where WSL definitions are static should not be affected.
   * @param pWorkingStoreLocation WSL to get or create the WorkDoc for.
   * @param pIsAutoIDs If true, the WorkDoc's DOM will be set to have auto IDs.
   * @return Existing or new XMLWorkDoc.
   */
  public static XMLWorkDoc getOrCreateXMLWorkDoc(WorkingDataDOMStorageLocation pWorkingStoreLocation, boolean pIsAutoIDs) {

    String lCacheKey = pWorkingStoreLocation.getDefinitionDependentCacheKey();

    // Get or create work document in cache (if appropriate)
    //TODO PN is syncing required here?
    FoxCache<String, XMLWorkDoc> lFoxCache = CacheManager.getCache(BuiltInCacheDefinition.XML_WORKDOCS);
    XMLWorkDoc lWorkDoc = lFoxCache.get(lCacheKey);
    if(lWorkDoc == null) {
      lWorkDoc = createXMLWorkDoc(pWorkingStoreLocation, pIsAutoIDs);
      lFoxCache.put(lCacheKey, lWorkDoc);
    }

    return lWorkDoc;
  }

  /**
   * Determines what type of XMLWorkDoc should be created (binary or CLOB) and does some additional initialisation.
   * @param pWorkingStoreLocation WSL to create WorkDoc for.
   * @param pIsAutoIDs If true, DOM will be set to have auto IDs.
   * @return New WorkDoc.
   */
  private static XMLWorkDoc createXMLWorkDoc(WorkingDataDOMStorageLocation pWorkingStoreLocation, boolean pIsAutoIDs) {

    //Establish the DOM access type - binary or CLOB
    XMLWorkDocDOMAccessor lDOMAccessor;
    if(pWorkingStoreLocation.getStorageLocation().isBinaryXMLStorageType()) {
      lDOMAccessor = new BinaryDOMAccessor();
    }
    else {
      lDOMAccessor = new ClobDOMAccessor();
    }

    //Establish if new WorkDoc should be writeable
    XMLWorkDoc lNewWorkDoc;
    if(pWorkingStoreLocation.isReadOnly()) {
      lNewWorkDoc = new ReadOnlyXMLWorkDoc(pWorkingStoreLocation, lDOMAccessor);
    }
    else {
      lNewWorkDoc = new WriteableXMLWorkDoc(pWorkingStoreLocation, pIsAutoIDs, lDOMAccessor);
    }

    return lNewWorkDoc;
  }

  protected XMLWorkDoc(WorkingDataDOMStorageLocation pWorkingStoreLocation, XMLWorkDocDOMAccessor pDOMAccessor){
    mWorkingStoreLocation = pWorkingStoreLocation;
    mDOMAccessor = pDOMAccessor;
  }

  /**
   * Opens this WorkDoc, locking the underlying LOB and reading the XML into memory if the latest version is not already
   * cached. If no row is found an insert is attempted if an insert statement is available and the WorkDoc is writable.
   * You must ensure you either {@link #close} or {@link #abort} the WorkDoc after opening it and performing your DOM reads/writes.<br/><br/>
   *
   * <b>IMPORTANT:</b> Opening a writable WorkDoc effectively grants the opening thread a conceptual re-entrant lock on the object until
   * the database transaction it was opened in is rolled back or committed. If the WSL SELECT statement does not contain
   * a FOR UPDATE clause this behaviour will not be guaranteed. WorkDoc consumers must ensure they have called <tt>open</tt>
   * before performing any additional operations on the object.
   * @param pContextUCon
   */
  public abstract void open(ContextUCon pContextUCon);

  /**
   * Gets the XML document held by this WorkDoc. This may be null if the WorkDoc has not yet been open, and may be out
   * of date if the WorkDoc is not currently open.
   * @return Latest document retrived by this WorkDoc.
   */
  public DOM getDOM() {
    return mDOM;
  }

  public abstract boolean isOpen();

  /**
   * Closes this WorkDoc, writing the DOM to the database and running the update statement if the DOM was modified since
   * {@link #open} was called. The cached DOM will be made read only after this operation.
   * @param pContextUCon
   */
  public abstract void close(ContextUCon pContextUCon);


  public Object getLOBForBinding(UCon pUCon, BindSQLType pBindTypeRequired) {
    return mDOMAccessor.getLOBForBinding(pUCon, pBindTypeRequired, getDOM());
  }

  public WorkingDataDOMStorageLocation getWorkingStoreLocation() {
    return mWorkingStoreLocation;
  }

  /**
   * Gets the cache key associated with this WorkDoc, which it should be cached against in the WorkDoc cache.
   * @return
   */
  protected String getCacheKey() {
    return mWorkingStoreLocation.getDefinitionDependentCacheKey();
  }

  /**
   * Resets the internal state of this WorkDoc in the event of an error. Also purges the WorkDoc from cache so it
   * is properly recreated on the next access attempt.
   */
  public void abort() {
    //Remove from cache
    FoxCache<String, XMLWorkDoc> lFoxCache = CacheManager.getCache(BuiltInCacheDefinition.XML_WORKDOCS);
    lFoxCache.remove(getCacheKey());

    if(mDOM != null) {
      mDOM.getDocControl().setDocumentReadOnly();
    }
    mDOM = null;
    mDOMChangeNumber = null;

    //Delegate to subclass to do any further abort handling
    abortInternal();
  }

  protected abstract void abortInternal();

  /**
   * Executes the select statement and retrieves the LOB value from the result set.
   * @param pUCon
   * @return LOB retrieved.
   * @throws ExDBTooFew If the select statement retrieves no rows.
   * @throws ExDBTimeout If the target row is locked and NOWAIT was specified.
   * @throws ExDB If any other DB error occurs.
   */
  protected Object selectScalarLOB(UCon pUCon)
  throws ExDBTooFew, ExDBTimeout, ExDB {

    ExecutableQuery lSelectStatement = getWorkingStoreLocation().getExecutableSelectStatement();
    ScalarResultDeliverer lDeliverer = ScalarResultType.SQL_OBJECT.getResultDeliverer();
    lSelectStatement.executeAndDeliver(pUCon, lDeliverer);

    return lDeliverer.getResult();
  }


  /**
   * Runs the WorkDoc select statement to select a row, then reads the LOB locator from the row and opens it. If no
   * row is found the method returns false.
   * @param pUCon
   * @return True if a row was found, false otherwise.
   * @throws ExDBTimeout
   */
  protected boolean selectRowAndOpenLocator(UCon pUCon)
  throws ExDBTimeout {

    try {
      Object lResultLOB = selectScalarLOB(pUCon);
      //If the LOB column is not null, open the locator
      mDOMAccessor.openLocator(pUCon, lResultLOB);
      return true;
    }
    catch (ExDBTooFew e) {
      return false;
    }
    catch (ExDBTimeout e) {
      //Ensure timeouts are caught and re-raised (and not caught below by ExDB catch-all)
      throw e;
    }
    catch (ExDB e) {
      throw new ExInternal("Invalid select syntax: ", e);
    }
  }


  /**
   * Optionally reads the DOM from the current accessor if the change number on the database is different to the cached
   * change number. If the change numbers match, no action is taken.
   * @param pUCon
   */
  protected void readNonEmptyExistingRow(UCon pUCon) {
    //Check the change number
    String lCurrentChangeNum = XFUtil.nvl(mDOMAccessor.readChangeNumber(pUCon), "");

    if(!lCurrentChangeNum.equals(mDOMChangeNumber)) {
      Track.info("ChangeNumberMismatch", "Change number mismatch; reloading document " +
      (XFUtil.isNull(mDOMChangeNumber) ? "(cached change number was null)" : "(cached=" + mDOMChangeNumber + ", database=" + lCurrentChangeNum + ")"));
      Track.pushDebug("RetrieveDOM");
      try {
        //Important: update the DOM reference to a new object, don't just update the existing DOM contents. If another thread
        //is using the DOM it retrieved from this WorkDoc we could get concurrency issues if we didn't do this.
        mDOM = mDOMAccessor.retrieveDOM(pUCon);
      }
      finally {
        Track.pop("RetrieveDOM");
      }
      mDOMChangeNumber = mDOM.getAttr(CHANGE_NUMBER_ATTR_NAME);
      Track.info("RetrievedDOMChangeNumber", mDOMChangeNumber);
    }
    else {
      //We have the latest version of the DOM
      Track.info("ChangeNumberMatch", "Change numbers consistent; no reload required (" + mDOMChangeNumber + ")");
    }
  }

  protected void setDOM(DOM pDOM) {
    mDOM = pDOM;
  }

  protected void setDOMChangeNumber(String pDOMChangeNumber) {
    mDOMChangeNumber = pDOMChangeNumber;
  }

  protected XMLWorkDocDOMAccessor getDOMAccessor() {
    return mDOMAccessor;
  }

  protected String getDOMChangeNumber() {
    return mDOMChangeNumber;
  }
}
