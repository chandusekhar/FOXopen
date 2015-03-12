/*

Copyright (c) 2010, UK DEPARTMENT OF ENERGY AND CLIMATE CHANGE -
                    ENERGY DEVELOPMENT UNIT (IT UNIT)
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of the DEPARTMENT OF ENERGY AND CLIMATE CHANGE nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

$Id$

*/
package net.foxopen.fox.filetransfer;


import com.google.common.base.Joiner;
import net.foxopen.fox.App;
import net.foxopen.fox.FileUploadType;
import net.foxopen.fox.XFUtil;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.entrypoint.FoxGlobals;
import net.foxopen.fox.ex.ExApp;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.ex.ExServiceUnavailable;
import net.foxopen.fox.ex.ExTooMany;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable metadata about an upload which is currently being processed.
 */
public class UploadInfo {

  public static final int MAX_TRANSFER_RATE_SAMPLE_PERIOD = 10;

  private static final Iterator<String> gFileIdUniqueIterator = XFUtil.getUniqueIterator();

  private final String mThreadId;
  private final String mCallId;
  private final String mTargetContextRef;
  private final String mAppMnem;
  private final FileUploadType mFileUploadType;

  private final String mFileId;
  private final Date mUploadDate;

  private UploadStatus mStatus = UploadStatus.NOT_STARTED;
  private long mFileBytes = 0;
  private String mFilename = "";
  private String mOriginalFileLocation = "";
  private String mBrowserContentType = "";
  private String mTrueContentType = "";
  private Set<String> mMagicContentTypes = new LinkedHashSet<>();
  private long mHttpContentLength = 0;
  private String mStatusMsg = "";

  private String mCharEncoding = "not specified"; // Looks like it was never used... Retained for consistency
  private ForceFailReason mForceFailReason = null;

  private String mSystemMsg = "";
  private String mReadableErrorMessage;

  private boolean mUploadFailed = false;

  private FiletransferProgressListener mProgressListener;

  //Transfer rate
  private long mTransferRateStartTime = 0;
  private long mTransferRateBytesAtStartTime = 0;
  private int mTransferRateBytesPerSecond = 0;
  private int mTransferRateDelayDelta = 1;

  /** Reasons which can cause an upload to be forcibly failed. */
  enum ForceFailReason {
    CANCEL_REQUESTED("the upload was cancelled"),
    NEW_UPLOAD("a new upload was requested"),
    PAGE_CLOSED("the upload page was closed"),
    DEFAULT("the upload failed");

    private final String mMessage;
    private ForceFailReason(String pMessage) {
      mMessage = pMessage;
    }

    public static ForceFailReason fromURLParam(String pParam) {
      switch(pParam) {
        case "requested":
          return CANCEL_REQUESTED;
        default:
          return DEFAULT;
      }
    }

    public String getMessage() {
      return mMessage;
    }
  }

  public UploadInfo(String pThreadId, String pCallId, String pTargetContextRef, App pApp, FileUploadType pFileUploadType) {
    mThreadId = pThreadId;
    mCallId = pCallId;
    mTargetContextRef = pTargetContextRef;
    mAppMnem = pApp.getMnemonicName();
    mFileUploadType = pFileUploadType;

    mFileId = gFileIdUniqueIterator.next();
    mUploadDate = new Date();
  }

  public UploadStatus getStatus() {
    return mStatus;
  }

  public boolean isUploadInProgress(){
    if(isForceFailRequested()){
      return false;
    }

    return mStatus.isInProgress();
  }

  /**
   * Sets the status of this upload info but does NOT fire the status event API.
   * @param pStatus New status.
   */
  public void setStatus(UploadStatus pStatus) {
    setStatusInternal(pStatus);
    setStatusMsg(pStatus.getReadableMessage(mFilename));
  }

  private void setStatusInternal(UploadStatus pStatus) {
    if (isForceFailRequested() && pStatus != UploadStatus.FAILED) {
      mStatus = UploadStatus.FAILED;
      //throw new ExInternal("Upload was failed before completing.  Could not set upload status because upload was marked for failure.");
    }
    else
      mStatus = pStatus;
  }

  public String getStatusMsg () {
    return mStatusMsg;
  }

  public void setStatusMsg (String pStatusMsg) {
    mStatusMsg = pStatusMsg != null ? pStatusMsg : "";
  }

  public long getFileSize () {
    return mFileBytes;
  }

  public void setFileSize (int pFileBytes) {
    mFileBytes = pFileBytes;
  }

  public String getFilename() {
    return mFilename;
  }

  public void setFilename(String pFilename) {
    mFilename = sanitiseFilename(pFilename);
  }

  public String getOriginalFileLocation() {
    return mOriginalFileLocation;
  }

  public void setOriginalFileLocation(String pOriginalFileLocation) {
    mOriginalFileLocation = sanitiseFilename(pOriginalFileLocation);
  }

  public String getBrowserContentType() {
    return mBrowserContentType;
  }

  public void setBrowserContentType(String pContentType) {
    mBrowserContentType = pContentType;
  }

  public String getTrueContentType() {
    return mTrueContentType;
  }

  public void setTrueContentType(String pContentType) {
    mTrueContentType = pContentType;
  }

  protected void setHttpContentLength (long pHttpContentLength) {
    mHttpContentLength = pHttpContentLength;
  }

  public long getHttpContentLength() {
    return mHttpContentLength;
  }

  public String getFileId () {
    return mFileId;
  }

  public Date getUploadDatetime() {
    return mUploadDate;
  }

  public String getUploadDatetimeXmlFormat() {
    DateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    mDateFormat.format(mUploadDate);
    return mDateFormat.format(mUploadDate);
  }

  public FileUploadType getFileUploadType(){
    return mFileUploadType;
  }

  /**
   * Forcibly fail an upload due to an external event.
   * @param pReason
   */
  public void forceFailUpload(ForceFailReason pReason) {
    mForceFailReason = pReason;
  }

  public ForceFailReason getForceFailReason(){
    return mForceFailReason;
  }

  public int getTransferRateBytesPerSecond(){

    long lCurTime = System.currentTimeMillis();
    if(mTransferRateStartTime == 0)
      mTransferRateStartTime = lCurTime;

    //Refresh every x seconds
    if(lCurTime - mTransferRateStartTime >= mTransferRateDelayDelta * 1000 && mProgressListener != null){
      mTransferRateBytesPerSecond = (int) ((mProgressListener.getBytesRead() - mTransferRateBytesAtStartTime) / ((lCurTime - mTransferRateStartTime) / 1000));
      mTransferRateStartTime = lCurTime;
      mTransferRateBytesAtStartTime = mProgressListener.getBytesRead();
      if(mTransferRateDelayDelta < MAX_TRANSFER_RATE_SAMPLE_PERIOD)
        mTransferRateDelayDelta++; //gradually make the calculation a smoother average
    }

    return mTransferRateBytesPerSecond;

  }


  public String calculateTimeRemaining(){

    int lTransferRate = getTransferRateBytesPerSecond();
    long lRemaining = getHttpContentLength() - ((mProgressListener != null) ? mProgressListener.getBytesRead() : 0);

    int lSecondsRemaining = 0;
    if(lTransferRate > 0)
      lSecondsRemaining = (int) (lRemaining / lTransferRate);

    if(lSecondsRemaining <= 0){
      return "unknown";
    }else if(lSecondsRemaining > 120){
      return ((int) Math.ceil(lSecondsRemaining / 60.0)) + " mins";
    } else {
      return lSecondsRemaining + " secs";
    }
  }

  public DOM getMetadataDOM() {
    DOM lDOM = DOM.createDocument("file-metadata");
    serialiseFileMetadataToXML(lDOM, false);
    return lDOM;
  }

  public void serialiseFileMetadataToXML (DOM pSerialiseToElem, boolean pSuppressFileId) {

    pSerialiseToElem.removeAllChildren();

    pSerialiseToElem.addElem("filename", mFilename);
    pSerialiseToElem.addElem("content-type", mTrueContentType);
    pSerialiseToElem.addElem("browser-content-type", mBrowserContentType);
    pSerialiseToElem.addElem("original-file-location", mOriginalFileLocation);
    pSerialiseToElem.addElem("size", String.valueOf(mFileBytes));
    pSerialiseToElem.addElem("status", mStatus.getStringRepresentation());
    pSerialiseToElem.addElem("status-message", mStatusMsg);
    pSerialiseToElem.addElem("system-message", mSystemMsg);
    pSerialiseToElem.addElem("readable-error-message", mReadableErrorMessage);
    pSerialiseToElem.addElem("upload-date-time", getUploadDatetimeXmlFormat());
    pSerialiseToElem.addElem("file-upload-type", mFileUploadType.getName());
    if(!pSuppressFileId) {
      pSerialiseToElem.addElem("file-id", getFileId());
    }
    pSerialiseToElem.addElem("character-encoding", mCharEncoding);

    serialiseDiagnosticFileMetadataToXML(pSerialiseToElem);
  }

  public void serialiseDiagnosticFileMetadataToXML (DOM pSerialiseToElem) {

    try {
      pSerialiseToElem = pSerialiseToElem.getCreate1E("diagnostic-info");
    }
    catch (ExTooMany ignore) {}

    pSerialiseToElem.removeAllChildren();

    pSerialiseToElem.addElem("filename", mFilename);
    pSerialiseToElem.addElem("content-type", mTrueContentType);
    pSerialiseToElem.addElem("browser-content-type", mBrowserContentType);
    pSerialiseToElem.addElem("original-file-location", mOriginalFileLocation);
    pSerialiseToElem.addElem("estimated-size", String.valueOf(mHttpContentLength));
    pSerialiseToElem.addElem("status", mStatus.getStringRepresentation());
    pSerialiseToElem.addElem("status-message", mStatusMsg);
    pSerialiseToElem.addElem("system-message", mSystemMsg);
    pSerialiseToElem.addElem("readable-error-message", mReadableErrorMessage);
    pSerialiseToElem.addElem("upload-date-time", getUploadDatetimeXmlFormat());
    pSerialiseToElem.addElem("file-id", getFileId());
  }

  public App getApp(){
    try {
      return FoxGlobals.getInstance().getFoxEnvironment().getAppByMnem(mAppMnem);
    }
    catch (ExServiceUnavailable | ExApp e) {
      throw new ExInternal("Error getting App", e);
    }
  }

  /**
   * Adds an extra 2% into the upload progress so upload does not appear
   * complete until it is virus scanned and stored.
   * @return
   */
  public int getTransferProgress() {
    int lProgress = mProgressListener.getTransmissionProgress();

    switch(mStatus){
      case VIRUS_CHECK:
        return 98;
      case STORING:
        return 99;
      case COMPLETE:
        return 100;
      default:
        return (int) (lProgress * 0.98);
    }
  }

  public void setSystemMsg(String pSystemMsg) {
    this.mSystemMsg = pSystemMsg;
  }

  public static String sanitiseFilename(String pFilename) {
    StringBuffer lFilename = new StringBuffer(pFilename);
    for (int i = 0; i < lFilename.length(); i++) {
      if (lFilename.charAt(i) > 255) {
        lFilename.deleteCharAt(i);
        i--;
      }
    }
    return lFilename.toString().trim();
  }

  public String toString() {
    return "UploadInfo ThreadId=" + mThreadId + " CallId=" + mCallId + " ContextRef=" + mTargetContextRef;
  }

  public boolean isForceFailRequested() {
    return mForceFailReason != null;
  }

  public boolean isUploadFailed() {
    return mUploadFailed;
  }

  /**
   * Marks the upload as failed due to an error.
   * @param pError
   */
  public void failUpload(Throwable pError) {
    mUploadFailed = true;
    mReadableErrorMessage = UploadProcessor.getReadableErrorMessage(pError, this);
  }

  public String getSystemMsg() {
    return mSystemMsg;
  }

  public void addMagicContentType(String pMagicContentType) {
    mMagicContentTypes.add(pMagicContentType);
  }

  public String getMagicContentTypes() {
    return Joiner.on(",").join(mMagicContentTypes);
  }

  public void setProgressListener(FiletransferProgressListener pProgressListener) {
    mProgressListener = pProgressListener;
  }
}
