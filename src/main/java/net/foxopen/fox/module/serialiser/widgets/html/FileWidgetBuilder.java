package net.foxopen.fox.module.serialiser.widgets.html;

import net.foxopen.fox.XFUtil;
import net.foxopen.fox.filetransfer.UploadServlet;
import net.foxopen.fox.module.UploadedFileInfo;
import net.foxopen.fox.module.fieldset.fieldmgr.FieldMgr;
import net.foxopen.fox.module.fieldset.clientaction.DeleteUploadedFileClientAction;
import net.foxopen.fox.module.datanode.EvaluatedNode;
import net.foxopen.fox.module.datanode.EvaluatedNodeInfoFileItem;
import net.foxopen.fox.module.datanode.NodeAttribute;
import net.foxopen.fox.module.datanode.NodeVisibility;
import net.foxopen.fox.module.serialiser.SerialisationContext;
import net.foxopen.fox.module.serialiser.html.HTMLSerialiser;
import net.foxopen.fox.module.serialiser.widgets.WidgetBuilder;
import net.foxopen.fox.module.serialiser.widgets.WidgetBuilderType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.List;


public class FileWidgetBuilder
extends WidgetBuilderHTMLSerialiser<EvaluatedNodeInfoFileItem> {

  private static final WidgetBuilder<HTMLSerialiser, EvaluatedNodeInfoFileItem> INSTANCE = new FileWidgetBuilder();
  private static final String MUSTACHE_TEMPLATE = "html/FileWidget.mustache"; //TODO PN proper widget template

  public static final WidgetBuilder<HTMLSerialiser, EvaluatedNodeInfoFileItem> getInstance() {
    return INSTANCE;
  }

  private static void insertDropzoneDiv(HTMLSerialiser pSerialiser, String pFieldId) {
    pSerialiser.append("<div class=\"dropzone\" data-dropzone-id=\"" + pFieldId + "\" style=\"display:none;\"><div class=\"dropzone-text-container\"><div class=\"dropzone-text\">Drop files here<div class=\"dropzone-max-files-text\"></div></div></div></div>");
  }

  private static String getFieldId(FieldMgr pFieldMgr) {
    return "FileUpload-" + pFieldMgr.getExternalFieldName();
  }

  private static boolean singleDropzoneRequired(SerialisationContext pSerialisationContext) {
    Collection<? extends EvaluatedNode> lFileENIs = pSerialisationContext.getEvaluatedNodesByWidgetBuilderType(WidgetBuilderType.FILE);
    return lFileENIs.size() == 1 && lFileENIs.iterator().next().getBooleanAttribute(NodeAttribute.UPLOAD_WHOLE_PAGE_DROPZONE, true);
  }

  public static void insertSingleDropzone(SerialisationContext pSerialisationContext, HTMLSerialiser pSerialiser){
    //If there's exactly 1 file widget on the page, and it's marked up to allow a whole page dropzone, add the dropzone now (in the body tag)
    if(singleDropzoneRequired(pSerialisationContext)) {
      EvaluatedNode lSingleFileENI = pSerialisationContext.getEvaluatedNodesByWidgetBuilderType(WidgetBuilderType.FILE).iterator().next();
      insertDropzoneDiv(pSerialiser, getFieldId(lSingleFileENI.getFieldMgr()));
    }
  }

  private FileWidgetBuilder() { }

  @Override
  public void buildWidgetInternal(SerialisationContext pSerialisationContext, HTMLSerialiser pSerialiser, EvaluatedNodeInfoFileItem pEvalNode) {

    FieldMgr lFieldMgr = pEvalNode.getFieldMgr();
    String lFieldId = getFieldId(lFieldMgr);

    String lWidgetMode =  pEvalNode.getUploadWidgetMode();
    pSerialiser.append("<div class=\"fileUpload " + lWidgetMode + "\" id=\"" + lFieldId + "\">");

    pSerialiser.append("<ul class=\"fileList\"></ul>");

    //Add the dropzone within the file upload container if it's not a whole page dropzone
    if(!singleDropzoneRequired(pSerialisationContext)) {
      insertDropzoneDiv(pSerialiser, lFieldId);
    }

    String lRefreshImgUrl = pSerialisationContext.getStaticResourceURI("img/refresh");

    if(lFieldMgr.getVisibility() == NodeVisibility.EDIT) {
      pSerialiser.append("<a class=\"fileControl uploadError replaceFile\" style=\"display:none;\" href=\"#\"><img src=\" " + lRefreshImgUrl + "\" alt=\"Replace File\"/></a>");
    }

    //TODO PN need to handle all attributes (like generic template vars for other widgets) e.g. tightField, fieldClass, etc

    // id="fileupload"
    boolean lHideFileInput = false; //TODO make configurable in widget
    if(lHideFileInput) {
      pSerialiser.append("<a href=\"#\" class=\"uploadControl chooseFile\">Choose file...</a>");
    }

    if(lFieldMgr.getVisibility() == NodeVisibility.EDIT) {
      pSerialiser.append("<input type=\"file\" " + (pEvalNode.getMaxFilesAllowed() > 1 ? "multiple" : "") + " name=\"file" + lFieldId + "\" " +
        "class=\"uploadControl fileUploadInput" + (lHideFileInput ? " offscreen" : "") + "\">");
    }

    List<UploadedFileInfo> lFileInfoList = pEvalNode.getUploadedFileInfoList();
    //Make the JSON array null if no files have been uploaded
    String lFileInfoJSON = "null";
    if(lFileInfoList.size() > 0) {
      JSONArray lJSONArray = new JSONArray();
      for(UploadedFileInfo lFileInfo : lFileInfoList) {
        lJSONArray.add(lFileInfo.asJSONObject());
      }

      lFileInfoJSON = lJSONArray.toJSONString();
    }

    String lURLBase = pSerialisationContext.createURIBuilder().buildServletURI(UploadServlet.UPLOAD_SERVLET_PATH);

    String lThreadId = pSerialisationContext.getThreadInfoProvider().getThreadId();
    String lCallId = pSerialisationContext.getThreadInfoProvider().getCurrentCallId();
    String lThreadAppMnem = pSerialisationContext.getThreadInfoProvider().getThreadAppMnem();
    String lItemRef = pEvalNode.getDataItem().getRef();
    String lURLParams = "thread_id=" + lThreadId + "&call_id="  + lCallId + "&app_mnem=" + lThreadAppMnem +  "&context_ref=" + lItemRef;


    String lOptionJSON = getWidgetOptionJSONString(pEvalNode, lItemRef, lFieldMgr.getVisibility().asInt() <  NodeVisibility.EDIT.asInt());
    String lJS = "<script>\n" +
    "$(document).ready(function() {" +
    "  new FileUpload($('#" + lFieldId + "'), '" + lURLBase +  "', '" + lURLParams + "', " + lFileInfoJSON  + ", " + lOptionJSON + ")\n" +
    "});\n" +
    "</script>";

    pSerialiser.append(lJS);
    pSerialiser.append("</div>");

    //MustacheFragmentBuilder.applyMapToTemplate(MUSTACHE_TEMPLATE, null, pSerialiser.getWriter());
  }

  private String getWidgetOptionJSONString(EvaluatedNodeInfoFileItem pEvalNode, String pItemRef, boolean pIsReadOnly) {

    JSONObject lWidgetOptions = new JSONObject();

    String lSuccessAction = pEvalNode.getSuccessAction();
    if(!XFUtil.isNull(lSuccessAction)) {
      lWidgetOptions.put("successAction", getActionJSON(lSuccessAction, pItemRef));
    }

    String lFailAction = pEvalNode.getFailAction();
    if(!XFUtil.isNull(lFailAction)) {
      lWidgetOptions.put("failAction", getActionJSON(lFailAction, pItemRef));
    }

    lWidgetOptions.put("widgetMode", pEvalNode.getUploadWidgetMode());

    lWidgetOptions.put("downloadModeParam", pEvalNode.getDownloadModeParameter());

    lWidgetOptions.put("readOnly", pIsReadOnly);

    lWidgetOptions.put("maxFiles", pEvalNode.getMaxFilesAllowed());

    lWidgetOptions.put("deleteActionKey", DeleteUploadedFileClientAction.generateActionKey(pEvalNode.getDataItem().getRef()));

    String lConfirm = pEvalNode.getStringAttribute(NodeAttribute.CONFIRM);
    if(!XFUtil.isNull(lConfirm)) {
      lWidgetOptions.put("deleteConfirmText", lConfirm);
    }

    return lWidgetOptions.toString();
  }
}
