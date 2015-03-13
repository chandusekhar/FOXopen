package net.foxopen.fox.auth; //TODO package?

import net.foxopen.fox.ContextLabel;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.dom.handler.DOMHandler;
import net.foxopen.fox.thread.ActionRequestContext;

public class UserDOMHandler
implements DOMHandler {

  private final DOM mUserDOM;

  UserDOMHandler() {
    mUserDOM = DOM.createDocument(ContextLabel.USER.asString());
    mUserDOM.getDocControl().setDocumentReadOnly();
  }

  @Override
  public void open(ActionRequestContext pRequestContext) {
  }

  @Override
  public DOM getDOM() {
    return mUserDOM;
  }

  void refreshDOM(DOM pNewDOM){
    mUserDOM.getDocControl().setDocumentReadWriteAutoIds();
    mUserDOM.removeAllChildren();
    //Preserve FOXIDs from the source DOM so consistent refs generated by the User DOM code are not clobbered
    pNewDOM.copyContentsToPreserveFoxIDs(mUserDOM);
    mUserDOM.getDocControl().setDocumentReadOnly();
  }

  @Override
  public void close(ActionRequestContext pRequestContext) {
  }

  @Override
  public boolean isTransient() {
    return false;
  }

  @Override
  public String getContextLabel() {
    return ContextLabel.USER.asString();
  }

  @Override
  public int getLoadPrecedence() {
    return LOAD_PRECEDENCE_LOW;
  }
}